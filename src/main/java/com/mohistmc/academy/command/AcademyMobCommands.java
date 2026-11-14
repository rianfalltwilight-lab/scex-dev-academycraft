package com.mohistmc.academy.command;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.api.mob.AcademyMobAbilities;
import com.mohistmc.academy.api.mob.AcademyMobDataMaps;
import com.mohistmc.academy.api.mob.MobAbilityData;
import com.mohistmc.academy.api.mob.MobSkillAi;
import com.mohistmc.academy.api.mob.MobSkills;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /academy mob ...}: non-player casters.  Returns successful casts or changed counts so
 * {@code execute store} works; per-entity feedback explains skips and cast results.
 */
final class AcademyMobCommands {
    private static final String KEY = "commands.academy.academy.";
    private static final String MOB = KEY + "mob.";

    private static final DynamicCommandExceptionType UNKNOWN_SKILL =
            new DynamicCommandExceptionType(value -> Component.translatable(KEY + "error.unknown_skill", value));
    private static final DynamicCommandExceptionType INVALID_ENTITY =
            new DynamicCommandExceptionType(value -> Component.translatable(MOB + "invalid_entity", value));

    private static final SuggestionProvider<CommandSourceStack> ALL_SKILLS =
            (context, builder) -> SharedSuggestionProvider.suggestResource(
                    SkillRegistry.getAllSkills().stream().map(Skill::getId).distinct().sorted()
                            .map(id -> ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, id)), builder);
    private static final SuggestionProvider<CommandSourceStack> SUPPORTED_SKILLS =
            (context, builder) -> SharedSuggestionProvider.suggestResource(
                    AcademyMobAbilities.supportedSkills().stream().distinct().sorted()
                            .map(id -> ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, id)), builder);

    private AcademyMobCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("mob")
                .then(Commands.literal("cast").then(Commands.argument("casters", EntityArgument.entities())
                        .then(skillArg(SUPPORTED_SKILLS)
                                .executes(c -> cast(c, null, false))
                                .then(Commands.argument("proficiency", FloatArgumentType.floatArg(0F, 1F))
                                        .executes(c -> cast(c, FloatArgumentType.getFloat(c, "proficiency"), false))
                                        .then(Commands.argument("target", EntityArgument.entity())
                                                .executes(c -> cast(c, FloatArgumentType.getFloat(c, "proficiency"), true)))))))
                .then(Commands.literal("skill").then(Commands.argument("entities", EntityArgument.entities())
                        .then(Commands.literal("set").then(skillArg(ALL_SKILLS)
                                .then(Commands.argument("proficiency", FloatArgumentType.floatArg(0F, 1F))
                                        .executes(AcademyMobCommands::setSkill))))
                        .then(Commands.literal("remove").then(skillArg(ALL_SKILLS)
                                .executes(AcademyMobCommands::removeSkill)))
                        .then(Commands.literal("clear").executes(AcademyMobCommands::clearSkills))))
                .then(Commands.literal("cooldown").then(Commands.argument("entities", EntityArgument.entities())
                        .then(Commands.literal("clear").executes(AcademyMobCommands::clearCooldowns))))
                .then(Commands.literal("query").then(Commands.argument("entity", EntityArgument.entity())
                        .executes(AcademyMobCommands::query)))
                .then(Commands.literal("supported").executes(AcademyMobCommands::supported));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ?> skillArg(SuggestionProvider<CommandSourceStack> s) {
        return Commands.argument("skill", ResourceLocationArgument.id()).suggests(s);
    }

    // ==================== cast ====================

    private static int cast(CommandContext<CommandSourceStack> c, @Nullable Float proficiency, boolean hasTarget)
            throws CommandSyntaxException {
        String id = skillId(c);
        LivingEntity explicitTarget = null;
        if (hasTarget) {
            Entity entity = EntityArgument.getEntity(c, "target");
            if (!(entity instanceof LivingEntity living)) throw INVALID_ENTITY.create(entity.getDisplayName());
            explicitTarget = living;
        }
        int cast = 0;
        for (LivingEntity caster : casters(c, "casters")) {
            LivingEntity target = explicitTarget != null ? explicitTarget
                    : caster instanceof Mob mob ? mob.getTarget() : null;
            AcademyMobAbilities.CastOptions options = AcademyMobAbilities.CastOptions.at(target);
            if (proficiency != null) options = options.withProficiency(proficiency);
            AcademyMobAbilities.Result result = AcademyMobAbilities.cast(caster, id, options);
            send(c, "cast.result", caster.getDisplayName(), id, result.name());
            if (result == AcademyMobAbilities.Result.CAST) cast++;
        }
        return cast;
    }

    // ==================== skills ====================

    private static int setSkill(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String id = skillId(c);
        float proficiency = FloatArgumentType.getFloat(c, "proficiency");
        boolean supported = AcademyMobAbilities.isSupported(id);
        int changed = 0;
        for (LivingEntity entity : casters(c, "entities")) {
            AcademyMobAbilities.data(entity).setSkill(id, proficiency);
            if (entity instanceof Mob mob) MobSkillAi.ensureAttached(mob);
            send(c, "skill.set", entity.getDisplayName(), id, percent(proficiency));
            changed++;
        }
        if (changed > 0 && !supported) send(c, "skill.unsupported", id);
        return changed;
    }

    private static int removeSkill(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String id = skillId(c);
        int changed = 0;
        for (LivingEntity entity : casters(c, "entities")) {
            if (entity.hasData(AcademyAttachments.MOB_ABILITY)
                    && entity.getData(AcademyAttachments.MOB_ABILITY).removeSkill(id)) {
                send(c, "skill.removed", entity.getDisplayName(), id);
                changed++;
            } else {
                send(c, "skill.not_known", entity.getDisplayName(), id);
            }
        }
        return changed;
    }

    private static int clearSkills(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int cleared = 0;
        for (LivingEntity entity : casters(c, "entities")) {
            int count = 0;
            if (entity.hasData(AcademyAttachments.MOB_ABILITY)) {
                MobAbilityData data = entity.getData(AcademyAttachments.MOB_ABILITY);
                count = data.skills().size();
                data.clear();
            }
            send(c, "skill.cleared", entity.getDisplayName(), count);
            cleared += count;
        }
        return cleared;
    }

    private static int clearCooldowns(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int cleared = 0;
        for (LivingEntity entity : casters(c, "entities")) {
            int count = 0;
            if (entity.hasData(AcademyAttachments.MOB_ABILITY)) {
                MobAbilityData data = entity.getData(AcademyAttachments.MOB_ABILITY);
                long now = entity.level().getGameTime();
                for (Skill skill : SkillRegistry.getAllSkills()) {
                    if (data.isOnCooldown(skill.getId(), now)) count++;
                }
                data.clearCooldowns();
            }
            send(c, "cooldown.cleared", entity.getDisplayName(), count);
            cleared += count;
        }
        return cleared;
    }

    // ==================== inspection ====================

    private static int query(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Entity raw = EntityArgument.getEntity(c, "entity");
        if (!(raw instanceof LivingEntity entity) || raw instanceof Player) throw INVALID_ENTITY.create(raw.getDisplayName());
        boolean goal = entity instanceof Mob mob && MobSkillAi.hasGoal(mob);
        MobSkills mapped = AcademyMobDataMaps.get(entity.getType());
        send(c, "query.header", entity.getDisplayName(), yesNo(goal), yesNo(mapped != null && !mapped.skills().isEmpty()));
        Map<String, Float> own = entity.hasData(AcademyAttachments.MOB_ABILITY)
                ? entity.getData(AcademyAttachments.MOB_ABILITY).skills() : Map.of();
        List<MobSkills.Entry> skills = MobSkillAi.resolve(entity);
        if (skills.isEmpty()) send(c, "query.none");
        long now = entity.level().getGameTime();
        for (MobSkills.Entry e : skills) {
            boolean inMap = mapped != null && mapped.skills().stream().anyMatch(m -> m.skill().equals(e.skill()));
            String source = inMap && own.containsKey(e.skill()) ? "both" : inMap ? "data_map" : "attachment";
            long cooldown = entity.hasData(AcademyAttachments.MOB_ABILITY)
                    ? entity.getData(AcademyAttachments.MOB_ABILITY).cooldownRemaining(e.skill(), now) : 0;
            send(c, "query.skill", e.skill(), percent(e.proficiency()), cooldown,
                    Component.translatable(MOB + "query.source." + source));
        }
        return skills.size();
    }

    private static int supported(CommandContext<CommandSourceStack> c) {
        List<String> ids = new ArrayList<>(AcademyMobAbilities.supportedSkills());
        ids.sort(String::compareTo);
        if (ids.isEmpty()) send(c, "supported.none");
        else send(c, "supported", ids.size(), String.join(", ", ids));
        return ids.size();
    }

    // ==================== helpers ====================

    /** Living, non-player entities from the selector; the rest are reported and skipped. */
    private static List<LivingEntity> casters(CommandContext<CommandSourceStack> c, String name)
            throws CommandSyntaxException {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity entity : EntityArgument.getEntities(c, name)) {
            if (entity instanceof LivingEntity living && !(entity instanceof Player)) out.add(living);
            else c.getSource().sendFailure(Component.translatable(MOB + "invalid_entity", entity.getDisplayName()));
        }
        return out;
    }

    /** {@code thunder_bolt} or {@code academy:thunder_bolt}; unknown skills are a command error. */
    private static String skillId(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ResourceLocation location = ResourceLocationArgument.getId(c, "skill");
        String namespace = location.getNamespace();
        if (!namespace.equals(AcademyCraft.MODID) && !namespace.equals(ResourceLocation.DEFAULT_NAMESPACE)) {
            throw UNKNOWN_SKILL.create(location.toString());
        }
        String id = location.getPath();
        if (SkillRegistry.getSkill(id) == null) throw UNKNOWN_SKILL.create(id);
        return id;
    }

    private static Component yesNo(boolean value) {
        return Component.translatable(MOB + (value ? "query.yes" : "query.no"));
    }

    private static String percent(float value) {
        return String.format(Locale.ROOT, "%.1f", value * 100.0f);
    }

    private static void send(CommandContext<CommandSourceStack> c, String key, Object... args) {
        c.getSource().sendSuccess(() -> Component.translatable(MOB + key, args), false);
    }
}
