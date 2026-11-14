package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillDamageMath;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** {@code /academy} modpack command tree: selectors, return values and authoritative writes. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class AcademyCommandGameTests {
    private static final String EMPTY = "empty";
    private AcademyCommandGameTests() {}

    private static PlayerAbilityData electromaster(ServerPlayer player) {
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.changeCategory(AbilityCategory.ELECTROMASTER);
        data.setPlayerLevel(5);
        return data;
    }

    /** Runs the command as the mock player with permission 4; -1 when it fails to parse or throws. */
    private static int run(GameTestHelper helper, ServerPlayer player, String command) {
        var source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        try {
            return helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            return -1;
        }
    }

    @GameTest(template = EMPTY)
    public static void learnRefusesSkillsOfAnotherCategory(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = electromaster(player);
        String uuid = player.getStringUUID();
        int result = run(helper, player, "academy learn " + uuid + " academy:mine_ray_basic");
        if (result != 0 || data.getLearnedSkills().contains("mine_ray_basic")) {
            helper.fail("learning a Meltdowner skill as an Electromaster was not refused (" + result + ")"); return;
        }
        if (data.getCurrentAbility() != AbilityCategory.ELECTROMASTER) {
            helper.fail("the refused learn changed the category"); return;
        }
        if (run(helper, player, "academy learn @s no_such_skill") != -1) {
            helper.fail("an unknown skill id was accepted"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void learnThenSetAndAddProficiency(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = electromaster(player);
        if (run(helper, player, "academy learn @s academy:arc_gen") != 1 || !data.getLearnedSkills().contains("arc_gen")) {
            helper.fail("namespaced learn did not learn arc_gen"); return;
        }
        if (run(helper, player, "academy learn @s arc_gen") != 0) {
            helper.fail("re-learning a learned skill counted as a change"); return;
        }
        if (run(helper, player, "academy learn @s mag_manip true") != 0 || data.getLearnedSkills().contains("mag_manip")) {
            helper.fail("respectPrerequisites learned a skill whose prerequisites are missing"); return;
        }
        if (run(helper, player, "academy proficiency @s arc_gen set 0.5") != 1
                || Math.abs(data.getProficiency("arc_gen") - 0.5f) > 1e-4f) {
            helper.fail("proficiency set did not write 0.5"); return;
        }
        run(helper, player, "academy proficiency @s arc_gen add 0.7");
        if (data.getProficiency("arc_gen") != 1.0f) {
            helper.fail("proficiency add did not clamp to 1: " + data.getProficiency("arc_gen")); return;
        }
        if (run(helper, player, "academy query @s") != 5) {
            helper.fail("query did not return the ability level"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void cpSetAddAndFill(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = electromaster(player);
        if (run(helper, player, "academy cp @s set 100") != 1 || data.getCurrentCp() != 100f) {
            helper.fail("cp set did not write 100: " + data.getCurrentCp()); return;
        }
        run(helper, player, "academy cp @s add -1000");
        if (data.getCurrentCp() != 0f) {
            helper.fail("cp add did not clamp at zero"); return;
        }
        run(helper, player, "academy cp @s fill");
        if (data.getCurrentCp() != data.getMaxCp()) {
            helper.fail("cp fill did not refill to max"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void cooldownSetReplacesAndClearRemoves(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = electromaster(player);
        data.setCooldown("arc_gen", 400);
        if (run(helper, player, "academy cooldown @s set arc_gen 40") != 1 || data.getCooldownTicks("arc_gen") != 40) {
            helper.fail("cooldown set kept the longer cooldown: " + data.getCooldownTicks("arc_gen")); return;
        }
        if (run(helper, player, "academy cooldown @s clear arc_gen") != 1 || data.isOnCooldown("arc_gen")) {
            helper.fail("cooldown clear <skill> did not remove it"); return;
        }
        data.setCooldown("arc_gen", 100);
        data.setCooldown("charging", 100);
        if (run(helper, player, "academy cooldown @s clear") != 2 || data.isOnCooldown("charging")) {
            helper.fail("cooldown clear did not clear every cooldown"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void debugDamageEqualsRawWithUnitMultipliers(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        SkillDamageMath.Breakdown expected = SkillDamageMath.breakdown("arc_gen", 10f, player, pig);
        int result = run(helper, player, "academy debug damage @s " + pig.getStringUUID() + " arc_gen 10");
        if (result != (int) expected.result()) {
            helper.fail("debug damage returned " + result + ", breakdown says " + expected.result()); return;
        }
        boolean unit = expected.skillMultiplier() == 1f && expected.globalMultiplier() == 1f
                && expected.attackerMultiplier() == 1f && expected.targetMultiplier() == 1f;
        if (unit && result != 10) {
            helper.fail("with every multiplier at 1 the damage was not the raw 10: " + result); return;
        }
        helper.succeed();
    }
}
