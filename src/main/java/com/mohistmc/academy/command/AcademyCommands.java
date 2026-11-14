package com.mohistmc.academy.command;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.advancement.LegacyAdvancementBridge;
import com.mohistmc.academy.api.event.AbilityEvents;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillDamageMath;
import com.mohistmc.academy.skill.SkillEventHandler;
import com.mohistmc.academy.skill.SkillPreset;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

/**
 * {@code /academy}: modpack-author and testing tools. Unlike the legacy {@code /aim} library this tree
 * takes selectors everywhere, accepts namespaced skill ids, and returns counts or queried values for
 * {@code execute store}. Mutations are server-authoritative, publish the public ability events and sync.
 */
public final class AcademyCommands {
    private static final int REQUIRED_PERMISSION = 2;
    private static final String KEY = "commands.academy.academy.";
    private static final int MAX_COOLDOWN_TICKS = 20 * 60 * 60;

    private static final DynamicCommandExceptionType UNKNOWN_SKILL =
            new DynamicCommandExceptionType(value -> Component.translatable(KEY + "error.unknown_skill", value));
    private static final DynamicCommandExceptionType UNKNOWN_CATEGORY =
            new DynamicCommandExceptionType(value -> Component.translatable(KEY + "error.unknown_category", value));
    private static final DynamicCommandExceptionType NOT_PLAYER =
            new DynamicCommandExceptionType(value -> Component.translatable(KEY + "error.not_player", value));
    private static final DynamicCommandExceptionType NOT_LIVING =
            new DynamicCommandExceptionType(value -> Component.translatable(KEY + "error.not_living", value));

    private static final SuggestionProvider<CommandSourceStack> SKILL_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggestResource(
                    SkillRegistry.getAllSkills().stream().map(Skill::getId).distinct().sorted()
                            .map(id -> ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, id)), builder);
    private static final SuggestionProvider<CommandSourceStack> CATEGORY_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    AbilityCategory.all().stream().map(AbilityCategory::id), builder);

    private AcademyCommands() {}

    private enum Op { SET, ADD }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("academy").requires(source -> source.hasPermission(REQUIRED_PERMISSION));

        root.then(Commands.literal("learn").then(targets().then(skillArg()
                .executes(c -> learn(c, false))
                .then(Commands.argument("respectPrerequisites", BoolArgumentType.bool())
                        .executes(c -> learn(c, BoolArgumentType.getBool(c, "respectPrerequisites")))))));
        root.then(Commands.literal("unlearn").then(targets().then(skillArg()
                .executes(AcademyCommands::unlearn))));
        root.then(Commands.literal("learn_all").then(targets().executes(AcademyCommands::learnAll)));

        root.then(Commands.literal("category").then(targets()
                .then(Commands.literal("clear").executes(AcademyCommands::clearCategory))
                .then(Commands.argument("category", StringArgumentType.word()).suggests(CATEGORY_SUGGESTIONS)
                        .executes(AcademyCommands::setCategory))));

        root.then(Commands.literal("level").then(targets()
                .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                        .executes(AcademyCommands::setLevel))));

        root.then(Commands.literal("proficiency").then(targets().then(skillArg()
                .then(Commands.literal("set").then(Commands.argument("value", FloatArgumentType.floatArg())
                        .executes(c -> proficiency(c, Op.SET))))
                .then(Commands.literal("add").then(Commands.argument("value", FloatArgumentType.floatArg())
                        .executes(c -> proficiency(c, Op.ADD)))))));

        root.then(Commands.literal("cp").then(targets()
                .then(Commands.literal("set").then(Commands.argument("value", FloatArgumentType.floatArg(0))
                        .executes(c -> cp(c, Op.SET, FloatArgumentType.getFloat(c, "value")))))
                .then(Commands.literal("add").then(Commands.argument("value", FloatArgumentType.floatArg())
                        .executes(c -> cp(c, Op.ADD, FloatArgumentType.getFloat(c, "value")))))
                .then(Commands.literal("fill").executes(c -> cp(c, Op.SET, Float.POSITIVE_INFINITY)))));

        root.then(Commands.literal("overload").then(targets()
                .then(Commands.literal("set").then(Commands.argument("value", FloatArgumentType.floatArg(0))
                        .executes(c -> overload(c, Op.SET))))
                .then(Commands.literal("add").then(Commands.argument("value", FloatArgumentType.floatArg())
                        .executes(c -> overload(c, Op.ADD))))
                .then(Commands.literal("unlock").executes(AcademyCommands::unlockOverload))));

        root.then(Commands.literal("cooldown").then(targets()
                .then(Commands.literal("clear").executes(c -> clearCooldown(c, null))
                        .then(skillArg().executes(c -> clearCooldown(c, skillId(c)))))
                .then(Commands.literal("set").then(skillArg()
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1, MAX_COOLDOWN_TICKS))
                                .executes(AcademyCommands::setCooldown))))));

        root.then(Commands.literal("active").then(targets()
                .then(Commands.literal("on").executes(c -> active(c, true)))
                .then(Commands.literal("off").executes(c -> active(c, false)))));

        root.then(Commands.literal("query").then(Commands.argument("target", EntityArgument.entity())
                .executes(AcademyCommands::query)));

        root.then(Commands.literal("debug").then(Commands.literal("damage")
                .then(Commands.argument("attacker", EntityArgument.entity())
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(skillArg().then(Commands.argument("raw", FloatArgumentType.floatArg(0))
                                        .executes(AcademyCommands::debugDamage)))))));

        root.then(AcademyMobCommands.build());

        dispatcher.register(root);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> targets() {
        return Commands.argument("targets", EntityArgument.entities());
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> skillArg() {
        return Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILL_SUGGESTIONS);
    }

    // ==================== skills ====================

    private static int learn(CommandContext<CommandSourceStack> c, boolean respectPrerequisites)
            throws CommandSyntaxException {
        String id = skillId(c);
        int changed = 0;
        for (ServerPlayer player : players(c)) {
            PlayerAbilityData data = data(player);
            Skill skill = skillForPlayer(c.getSource(), player, data, id);
            if (skill == null) continue;
            if (data.getLearnedSkills().contains(skill.getId())) {
                send(c, "learn.already", player.getDisplayName(), skillName(skill));
                continue;
            }
            if (respectPrerequisites && !data.canLearnSkill(skill)) {
                fail(c, "error.prerequisites", player.getDisplayName(), skillName(skill));
                continue;
            }
            learnSkill(player, data, skill);
            data.syncTo(player);
            send(c, "learn.success", player.getDisplayName(), skillName(skill));
            changed++;
        }
        return changed;
    }

    private static int unlearn(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String id = skillId(c);
        int changed = 0;
        for (ServerPlayer player : players(c)) {
            PlayerAbilityData data = data(player);
            Skill skill = skillForPlayer(c.getSource(), player, data, id);
            if (skill == null) continue;
            List<int[]> boundSlots = new ArrayList<>();
            for (int preset = 0; preset < PlayerAbilityData.PRESET_COUNT; preset++) {
                for (int slot = 0; slot < SkillPreset.SLOT_COUNT; slot++) {
                    if (skill.getId().equals(data.getSlotSkillId(preset, slot))) boundSlots.add(new int[]{preset, slot});
                }
            }
            if (!data.unlearnSkill(skill.getId())) {
                send(c, "unlearn.not_learned", player.getDisplayName(), skillName(skill));
                continue;
            }
            for (int[] bound : boundSlots) {
                NeoForge.EVENT_BUS.post(new AbilityEvents.PresetUpdated(player, bound[0], bound[1], skill.getId(), null));
            }
            data.syncTo(player);
            send(c, "unlearn.success", player.getDisplayName(), skillName(skill));
            changed++;
        }
        return changed;
    }

    private static int learnAll(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int total = 0;
        for (ServerPlayer player : players(c)) {
            PlayerAbilityData data = data(player);
            if (!requireAbility(c, player, data)) continue;
            int learned = 0;
            for (Skill skill : SkillRegistry.getSkillsByCategory(data.getCurrentAbility())) {
                if (!data.getLearnedSkills().contains(skill.getId())) {
                    learnSkill(player, data, skill);
                    learned++;
                }
            }
            if (learned > 0) data.syncTo(player);
            send(c, "learn_all", player.getDisplayName(), learned);
            total += learned;
        }
        return total;
    }

    // ==================== category / level ====================

    private static int setCategory(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(c, "category");
        AbilityCategory category = AbilityCategory.fromId(raw.toLowerCase(Locale.ROOT));
        if (category == null) throw UNKNOWN_CATEGORY.create(raw);
        int changed = 0;
        for (ServerPlayer player : players(c)) {
            PlayerAbilityData data = data(player);
            AbilityCategory oldCategory = data.getCurrentAbility();
            if (category.equals(oldCategory)) {
                send(c, "category.unchanged", player.getDisplayName(), categoryName(category));
                continue;
            }
            int oldLevel = data.getPlayerLevel();
            boolean wasActive = data.isAbilityActive();
            // Same as /aim cat: level kept (0 -> 1), learned skills, exp, presets and cooldowns cleared.
            data.changeCategory(category);
            data.setAbilityActive(false);
            int newLevel = data.getPlayerLevel();
            if (wasActive) NeoForge.EVENT_BUS.post(new AbilityEvents.Deactivate(player));
            NeoForge.EVENT_BUS.post(new AbilityEvents.CategoryChanged(player, oldCategory, category));
            if (oldLevel != newLevel) NeoForge.EVENT_BUS.post(new AbilityEvents.LevelChanged(player, oldLevel, newLevel));
            LegacyAdvancementBridge.levels(player, data);
            data.syncTo(player);
            send(c, "category.set", player.getDisplayName(), categoryName(oldCategory), categoryName(category), newLevel);
            changed++;
        }
        return changed;
    }

    private static int clearCategory(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int changed = 0;
        for (ServerPlayer player : players(c)) {
            PlayerAbilityData data = data(player);
            AbilityCategory oldCategory = data.getCurrentAbility();
            if (oldCategory == null) {
                send(c, "category.already_clear", player.getDisplayName());
                continue;
            }
            int oldLevel = data.getPlayerLevel();
            boolean wasActive = data.isAbilityActive();
            data.changeCategory(null);
            if (wasActive) NeoForge.EVENT_BUS.post(new AbilityEvents.Deactivate(player));
            NeoForge.EVENT_BUS.post(new AbilityEvents.CategoryChanged(player, oldCategory, null));
            if (oldLevel != 0) NeoForge.EVENT_BUS.post(new AbilityEvents.LevelChanged(player, oldLevel, 0));
            data.syncTo(player);
            send(c, "category.cleared", player.getDisplayName());
            changed++;
        }
        return changed;
    }

    private static int setLevel(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int level = IntegerArgumentType.getInteger(c, "level");
        int changed = 0;
        for (ServerPlayer player : players(c)) {
            PlayerAbilityData data = data(player);
            if (!requireAbility(c, player, data)) continue;
            int oldLevel = data.getPlayerLevel();
            if (oldLevel == level) {
                send(c, "level.unchanged", player.getDisplayName(), level);
                continue;
            }
            data.setPlayerLevel(level);
            NeoForge.EVENT_BUS.post(new AbilityEvents.LevelChanged(player, oldLevel, level));
            LegacyAdvancementBridge.levels(player, data);
            data.syncTo(player);
            send(c, "level.set", player.getDisplayName(), oldLevel, level);
            changed++;
        }
        return changed;
    }

    // ==================== proficiency / resources ====================

    /**
     * Same write as {@code /aim exp}: a direct proficiency write plus {@link AbilityEvents.SkillExpChanged}.
     * {@code AbilityMutationService.addSkillExp} is the skill-use path (it also advances the level gauge
     * and is a no-op in developer mode), so an administrative set/add does not go through it.
     */
    private static int proficiency(CommandContext<CommandSourceStack> c, Op op) throws CommandSyntaxException {
        String id = skillId(c);
        float value = FloatArgumentType.getFloat(c, "value");
        int changed = 0;
        for (ServerPlayer player : players(c)) {
            PlayerAbilityData data = data(player);
            Skill skill = skillForPlayer(c.getSource(), player, data, id);
            if (skill == null) continue;
            if (!data.getLearnedSkills().contains(skill.getId())) {
                fail(c, "unlearn.not_learned", player.getDisplayName(), skillName(skill));
                continue;
            }
            float oldExp = data.getProficiency(skill.getId());
            float target = Math.clamp(op == Op.SET ? value : oldExp + value, 0.0f, 1.0f);
            data.setProficiency(skill.getId(), target);
            float exp = data.getProficiency(skill.getId());
            if (Float.compare(oldExp, exp) != 0) {
                NeoForge.EVENT_BUS.post(new AbilityEvents.SkillExpChanged(player, skill, oldExp, exp));
                data.syncTo(player);
            }
            send(c, "proficiency.set", player.getDisplayName(), skillName(skill), percent(oldExp), percent(exp));
            changed++;
        }
        return changed;
    }

    private static int cp(CommandContext<CommandSourceStack> c, Op op, float value) throws CommandSyntaxException {
        int changed = 0;
        for (ServerPlayer player : players(c)) {
            PlayerAbilityData data = data(player);
            if (!requireAbility(c, player, data)) continue;
            float target = Float.isInfinite(value) ? data.getMaxCp()
                    : op == Op.SET ? value : data.getCurrentCp() + value;
            data.setCurrentCp(target);
            data.syncTo(player);
            send(c, "cp.set", player.getDisplayName(), number(data.getCurrentCp()), number(data.getMaxCp()));
            changed++;
        }
        return changed;
    }

    private static int overload(CommandContext<CommandSourceStack> c, Op op) throws CommandSyntaxException {
        float value = FloatArgumentType.getFloat(c, "value");
        int changed = 0;
        for (ServerPlayer player : players(c)) {
            PlayerAbilityData data = data(player);
            if (!requireAbility(c, player, data)) continue;
            data.setCurrentOverload(op == Op.SET ? value : data.getCurrentOverload() + value);
            data.syncTo(player);
            send(c, "overload.set", player.getDisplayName(),
                    number(data.getCurrentOverload()), number(data.getMaxOverload()));
            changed++;
        }
        return changed;
    }

    private static int unlockOverload(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int changed = 0;
        for (ServerPlayer player : players(c)) {
            PlayerAbilityData data = data(player);
            if (!data.isOverloadLocked()) {
                send(c, "overload.not_locked", player.getDisplayName());
                continue;
            }
            data.setOverloadLocked(false);
            data.syncTo(player);
            send(c, "overload.unlocked", player.getDisplayName());
            changed++;
        }
        return changed;
    }

    // ==================== cooldowns / activation ====================

    private static int clearCooldown(CommandContext<CommandSourceStack> c, String skillId)
            throws CommandSyntaxException {
        if (skillId != null) requireKnownSkill(skillId);
        int cleared = 0;
        for (ServerPlayer player : players(c)) {
            PlayerAbilityData data = data(player);
            if (skillId == null) {
                int count = data.clearCooldowns();
                send(c, "cooldown.cleared_all", player.getDisplayName(), count);
                cleared += count;
            } else if (data.clearCooldown(skillId)) {
                send(c, "cooldown.cleared", player.getDisplayName(), skillId);
                cleared++;
            } else {
                send(c, "cooldown.none", player.getDisplayName(), skillId);
                continue;
            }
            data.syncTo(player);
        }
        return cleared;
    }

    private static int setCooldown(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String skillId = skillId(c);
        requireKnownSkill(skillId);
        int ticks = IntegerArgumentType.getInteger(c, "ticks");
        int changed = 0;
        for (ServerPlayer player : players(c)) {
            PlayerAbilityData data = data(player);
            // setCooldown keeps the longer cooldown; an explicit set replaces it.
            data.clearCooldown(skillId);
            data.setCooldownRaw(skillId, ticks);
            data.syncTo(player);
            send(c, "cooldown.set", player.getDisplayName(), skillId, data.getCooldownTicks(skillId));
            changed++;
        }
        return changed;
    }

    private static int active(CommandContext<CommandSourceStack> c, boolean on) throws CommandSyntaxException {
        int changed = 0;
        for (ServerPlayer player : players(c)) {
            PlayerAbilityData data = data(player);
            if (!requireAbility(c, player, data)) continue;
            if (data.isAbilityActive() == on) {
                send(c, on ? "active.already_on" : "active.already_off", player.getDisplayName());
                continue;
            }
            // Like the V key: running charges and special modes end before the ability is switched off.
            if (!on) SkillEventHandler.endSpecialFromActivateKey(player, data);
            data.setAbilityActive(on);
            NeoForge.EVENT_BUS.post(on ? new AbilityEvents.Activate(player) : new AbilityEvents.Deactivate(player));
            data.syncTo(player);
            send(c, on ? "active.on" : "active.off", player.getDisplayName());
            changed++;
        }
        return changed;
    }

    // ==================== inspection ====================

    private static int query(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Entity entity = EntityArgument.getEntity(c, "target");
        if (!(entity instanceof ServerPlayer player)) throw NOT_PLAYER.create(entity.getDisplayName());
        PlayerAbilityData data = data(player);
        send(c, "query.header", player.getDisplayName());
        send(c, "query.category", categoryName(data.getCurrentAbility()), data.getPlayerLevel(),
                percent(data.getLevelProgress()));
        send(c, "query.resources", number(data.getCurrentCp()), number(data.getMaxCp()),
                number(data.getCurrentOverload()), number(data.getMaxOverload()), state(data.isOverloadLocked()));
        send(c, "query.state", state(data.isAbilityActive()), state(data.isDevMode()));
        List<String> learned = new ArrayList<>(data.getLearnedSkills());
        learned.sort(String::compareTo);
        send(c, "query.skills", learned.size());
        for (String id : learned) {
            Skill skill = SkillRegistry.getSkill(data.getCurrentAbility(), id);
            Component name = skill != null ? skillName(skill) : Component.literal(id);
            send(c, "query.skill", name, id, percent(data.getProficiency(id)));
        }
        List<String> cooldowns = new ArrayList<>();
        for (Skill skill : SkillRegistry.getAllSkills()) {
            int ticks = data.getCooldownTicks(skill.getId());
            if (ticks > 0) cooldowns.add(skill.getId() + " " + ticks + "t");
        }
        cooldowns.sort(String::compareTo);
        send(c, "query.cooldowns", cooldowns.isEmpty()
                ? Component.translatable("commands.academy.aim.none") : Component.literal(String.join(", ", cooldowns)));
        return data.getPlayerLevel();
    }

    private static int debugDamage(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Entity attacker = EntityArgument.getEntity(c, "attacker");
        Entity target = EntityArgument.getEntity(c, "target");
        if (!(attacker instanceof LivingEntity)) throw NOT_LIVING.create(attacker.getDisplayName());
        if (!(target instanceof LivingEntity)) throw NOT_LIVING.create(target.getDisplayName());
        String skillId = skillId(c);
        requireKnownSkill(skillId);
        float raw = FloatArgumentType.getFloat(c, "raw");
        SkillDamageMath.Breakdown b = SkillDamageMath.breakdown(skillId, raw, attacker, target);
        send(c, "debug.damage", attacker.getDisplayName(), target.getDisplayName(), skillId,
                number(b.raw()), number(b.skillMultiplier()), number(b.globalMultiplier()),
                number(b.attackerMultiplier()), number(b.targetMultiplier()), number(b.result()));
        return (int) b.result();
    }

    // ==================== helpers ====================

    private static List<ServerPlayer> players(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        List<ServerPlayer> players = new ArrayList<>();
        for (Entity entity : EntityArgument.getEntities(c, "targets")) {
            if (entity instanceof ServerPlayer player) players.add(player);
            else fail(c, "skipped_non_player", entity.getDisplayName());
        }
        return players;
    }

    /** {@code railgun} and {@code academy:railgun} both name the skill; other namespaces are unknown. */
    private static String skillId(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ResourceLocation location = ResourceLocationArgument.getId(c, "skill");
        String namespace = location.getNamespace();
        if (!namespace.equals(AcademyCraft.MODID) && !namespace.equals(ResourceLocation.DEFAULT_NAMESPACE)) {
            throw UNKNOWN_SKILL.create(location.toString());
        }
        String id = location.getPath();
        requireKnownSkill(id);
        return id;
    }

    private static void requireKnownSkill(String id) throws CommandSyntaxException {
        if (SkillRegistry.getAllSkills().stream().noneMatch(skill -> skill.getId().equals(id))) {
            throw UNKNOWN_SKILL.create(id);
        }
    }

    /** The skill node in the player's own tree, or null after explaining why it is not there. */
    private static Skill skillForPlayer(CommandSourceStack source, ServerPlayer player, PlayerAbilityData data,
                                        String id) {
        Skill own = SkillRegistry.getSkill(data.getCurrentAbility(), id);
        if (own != null) return own;
        Skill other = SkillRegistry.getSkill(id);
        Component otherCategory = other != null ? categoryName(other.getCategory())
                : Component.translatable("commands.academy.aim.none");
        source.sendFailure(Component.translatable(KEY + "error.wrong_category", player.getDisplayName(), id,
                otherCategory, categoryName(data.getCurrentAbility())));
        return null;
    }

    private static void learnSkill(ServerPlayer player, PlayerAbilityData data, Skill skill) {
        data.learnSkill(skill.getId());
        NeoForge.EVENT_BUS.post(new AbilityEvents.SkillLearned(player, skill));
        LegacyAdvancementBridge.learned(player, data, skill);
    }

    private static boolean requireAbility(CommandContext<CommandSourceStack> c, ServerPlayer player,
                                          PlayerAbilityData data) {
        if (data.hasAbility()) return true;
        fail(c, "error.no_ability", player.getDisplayName());
        return false;
    }

    private static PlayerAbilityData data(ServerPlayer player) {
        return player.getData(AcademyAttachments.PLAYER_ABILITY);
    }

    private static Component skillName(Skill skill) {
        return Component.translatable(skill.getTranslationKey());
    }

    private static Component categoryName(AbilityCategory category) {
        return category == null ? Component.translatable("commands.academy.aim.none")
                : Component.translatable(category.getTranslationKey());
    }

    private static Component state(boolean on) {
        return Component.translatable(on ? "commands.academy.aim.state.on" : "commands.academy.aim.state.off");
    }

    private static String percent(float value) {
        return String.format(Locale.ROOT, "%.1f", value * 100.0f);
    }

    private static String number(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static void send(CommandContext<CommandSourceStack> c, String key, Object... args) {
        c.getSource().sendSuccess(() -> Component.translatable(KEY + key, args), false);
    }

    private static void fail(CommandContext<CommandSourceStack> c, String key, Object... args) {
        c.getSource().sendFailure(Component.translatable(KEY + key, args));
    }
}
