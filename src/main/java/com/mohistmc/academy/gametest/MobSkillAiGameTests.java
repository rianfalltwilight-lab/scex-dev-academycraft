package com.mohistmc.academy.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.api.mob.AbilityCastGoal;
import com.mohistmc.academy.api.mob.AcademyMobAbilities;
import com.mohistmc.academy.api.mob.MobAbilityData;
import com.mohistmc.academy.api.mob.MobSkillAi;
import com.mohistmc.academy.api.mob.MobSkills;
import com.mohistmc.academy.api.mob.WeightedSkillPick;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Mob skill AI layer: data-map codec, weighted pick, goal attachment and {@code /academy mob}. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MobSkillAiGameTests {
    private static final String EMPTY = "empty";
    private MobSkillAiGameTests() {}

    private static int run(GameTestHelper helper, ServerPlayer player, String command) {
        var source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        try {
            return helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            return -1;
        }
    }

    private static long goalCount(Mob mob) {
        return mob.goalSelector.getAvailableGoals().stream().filter(w -> w.getGoal() instanceof AbilityCastGoal).count();
    }

    private static boolean parses(String json) {
        return MobSkills.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).isSuccess();
    }

    @GameTest(template = EMPTY)
    public static void dataMapCodecRoundTripsAndValidates(GameTestHelper helper) {
        String json = "{\"skills\":[{\"skill\":\"thunder_bolt\"},"
                + "{\"skill\":\"academy:arc_gen\",\"proficiency\":0.9,\"weight\":3,\"min_range\":2,\"max_range\":8,"
                + "\"cooldown_ticks\":100}],\"cast_interval\":20,\"needs_line_of_sight\":false}";
        MobSkills parsed = MobSkills.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        MobSkills.Entry first = parsed.skills().get(0);
        MobSkills.Entry second = parsed.skills().get(1);
        if (!first.equals(MobSkills.Entry.defaults("thunder_bolt", 0.5F))) {
            helper.fail("bare entry did not take the defaults: " + first); return;
        }
        if (!second.equals(new MobSkills.Entry("arc_gen", 0.9F, 3F, 2, 8, 100))
                || parsed.castInterval() != 20 || parsed.needsLineOfSight()) {
            helper.fail("explicit values were not read: " + parsed); return;
        }
        JsonElement encoded = MobSkills.CODEC.encodeStart(JsonOps.INSTANCE, parsed).getOrThrow();
        MobSkills again = MobSkills.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        if (!again.equals(parsed)) {
            helper.fail("round trip changed the value: " + encoded); return;
        }
        MobSkills empty = MobSkills.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
        if (!empty.equals(MobSkills.DEFAULT)) {
            helper.fail("an empty object is not the default profile: " + empty); return;
        }
        String[] invalid = {
                "{\"skills\":[{\"skill\":\"thunder_bolt\",\"weight\":0}]}",
                "{\"skills\":[{\"skill\":\"thunder_bolt\",\"proficiency\":1.5}]}",
                "{\"skills\":[{\"skill\":\"minecraft:thunder_bolt\"}]}",
                "{\"skills\":[{\"skill\":\"thunder_bolt\",\"min_range\":10,\"max_range\":4}]}",
                "{\"cast_interval\":0}"
        };
        for (String bad : invalid) {
            if (parses(bad)) { helper.fail("accepted invalid data map value " + bad); return; }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void weightedPickFollowsWeights(GameTestHelper helper) {
        record W(String id, double w) {}
        List<W> items = List.of(new W("a", 1), new W("zero", 0), new W("b", 3), new W("nan", Double.NaN));
        String[][] expect = {{"0", "a"}, {"0.24", "a"}, {"0.26", "b"}, {"0.9999", "b"}, {"1", "b"}};
        for (String[] e : expect) {
            W got = WeightedSkillPick.pick(items, W::w, Double.parseDouble(e[0]));
            if (got == null || !got.id().equals(e[1])) {
                helper.fail("roll " + e[0] + " picked " + got + ", expected " + e[1]); return;
            }
        }
        if (WeightedSkillPick.pick(List.of(new W("zero", 0)), W::w, 0.5) != null
                || WeightedSkillPick.pick(List.<W>of(), W::w, 0.5) != null) {
            helper.fail("picked from a list with no positive weight"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void goalAttachesOnJoinOnceForAttachmentSkills(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) { helper.fail("could not create a zombie"); return; }
        zombie.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        zombie.getData(AcademyAttachments.MOB_ABILITY).setSkill("thunder_bolt", 0.8F);
        level.addFreshEntity(zombie);

        Entity fromNbt;
        try {
            CompoundTag tag = TagParser.parseTag("{id:\"minecraft:zombie\",\"neoforge:attachments\":"
                    + "{\"academy:mob_ability\":{skills:{arc_gen:0.6f}}}}");
            fromNbt = EntityType.loadEntityRecursive(tag, level, e -> {
                e.moveTo(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
                return e;
            });
        } catch (CommandSyntaxException e) {
            helper.fail("summon NBT did not parse: " + e.getMessage()); return;
        }
        if (fromNbt != null) level.addFreshEntity(fromNbt);
        Zombie plain = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        try {
            if (goalCount(zombie) != 1) { helper.fail("join added " + goalCount(zombie) + " cast goals"); return; }
            if (MobSkillAi.ensureAttached(zombie) || goalCount(zombie) != 1) {
                helper.fail("a second attach was not refused"); return;
            }
            if (!(fromNbt instanceof Mob nbtMob) || goalCount(nbtMob) != 1
                    || !Float.valueOf(0.6F).equals(nbtMob.getData(AcademyAttachments.MOB_ABILITY).proficiency("arc_gen"))) {
                helper.fail("the /summon attachment form did not give the goal and skill"); return;
            }
            if (MobSkillAi.hasGoal(plain)) { helper.fail("a mob without skills got the cast goal"); return; }
            List<MobSkills.Entry> resolved = MobSkillAi.resolve(zombie);
            if (!resolved.equals(List.of(MobSkills.Entry.defaults("thunder_bolt", 0.8F)))) {
                helper.fail("attachment-only skill did not use default range/weight: " + resolved); return;
            }
            helper.succeed();
        } finally {
            zombie.discard();
            plain.discard();
            if (fromNbt != null) fromNbt.discard();
        }
    }

    @GameTest(template = EMPTY)
    public static void mobCommandsReturnCounts(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        String uuid = zombie.getStringUUID();
        try {
            if (MobSkillAi.hasGoal(zombie)) { helper.fail("fresh zombie already had the goal"); return; }
            if (run(helper, player, "academy mob skill " + uuid + " set academy:thunder_bolt 0.7") != 1) {
                helper.fail("skill set did not return 1"); return;
            }
            MobAbilityData data = zombie.getData(AcademyAttachments.MOB_ABILITY);
            Float stored = data.proficiency("thunder_bolt");
            if (stored == null || Math.abs(stored - 0.7F) > 1e-4F || !MobSkillAi.hasGoal(zombie)) {
                helper.fail("skill set did not store 0.7 and attach the goal: " + stored); return;
            }
            if (run(helper, player, "academy mob skill @s set thunder_bolt 0.5") != 0) {
                helper.fail("a player counted as a mob caster"); return;
            }
            if (run(helper, player, "academy mob skill " + uuid + " set no_such_skill 0.5") != -1
                    || run(helper, player, "academy mob skill " + uuid + " set thunder_bolt 2") != -1) {
                helper.fail("an unknown skill or out-of-range proficiency was accepted"); return;
            }
            run(helper, player, "academy mob skill " + uuid + " set arc_gen 0.3");
            if (run(helper, player, "academy mob query " + uuid) != 2) {
                helper.fail("query did not return the skill count 2"); return;
            }
            data.setCooldown("arc_gen", helper.getLevel().getGameTime(), 200);
            if (run(helper, player, "academy mob cooldown " + uuid + " clear") != 1
                    || data.isOnCooldown("arc_gen", helper.getLevel().getGameTime())) {
                helper.fail("cooldown clear did not clear exactly one cooldown"); return;
            }
            if (run(helper, player, "academy mob skill " + uuid + " remove thunder_bolt") != 1
                    || run(helper, player, "academy mob skill " + uuid + " remove thunder_bolt") != 0) {
                helper.fail("remove did not return 1 then 0"); return;
            }
            if (run(helper, player, "academy mob skill " + uuid + " clear") != 1 || !data.skills().isEmpty()
                    || run(helper, player, "academy mob skill " + uuid + " clear") != 0) {
                helper.fail("clear did not return 1 then 0"); return;
            }
            int supported = AcademyMobAbilities.supportedSkills().size();
            if (run(helper, player, "academy mob supported") != supported) {
                helper.fail("supported did not return " + supported); return;
            }
            Skill unsupported = SkillRegistry.getAllSkills().stream()
                    .filter(s -> !AcademyMobAbilities.isSupported(s.getId())).findFirst().orElse(null);
            if (unsupported != null
                    && run(helper, player, "academy mob cast " + uuid + " " + unsupported.getId() + " 0.5") != 0) {
                helper.fail("casting the unsupported " + unsupported.getId() + " counted as a cast"); return;
            }
            if (run(helper, player, "academy mob query @s") != -1) {
                helper.fail("query accepted a player"); return;
            }
            helper.succeed();
        } finally {
            zombie.discard();
        }
    }
}
