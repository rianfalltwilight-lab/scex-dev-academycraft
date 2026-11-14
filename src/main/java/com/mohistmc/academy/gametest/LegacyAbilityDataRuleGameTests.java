package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillRegistry;
import java.util.function.Predicate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * AcademyCraft 1.12.2 ability-data rules: recovery from the raw maximum (CPData), /aim cat keeping
 * the level (AbilityData.setCategory), disabled skills hidden from learning (Skill.isEnabled) and
 * CooldownData.doSet keeping the longer cooldown.
 */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class LegacyAbilityDataRuleGameTests {
    private static final String EMPTY = "empty";
    private LegacyAbilityDataRuleGameTests() {}

    private static PlayerAbilityData detached(AbilityCategory category, int level) {
        PlayerAbilityData data = new PlayerAbilityData();
        data.setCurrentAbility(category);
        data.setPlayerLevel(level);
        data.recalculateMaxResources(true);
        return data;
    }

    @GameTest(template = EMPTY)
    public static void recoverySpeedIgnoresUsageGrowth(GameTestHelper helper) {
        PlayerAbilityData plain = detached(AbilityCategory.ELECTROMASTER, 3);
        PlayerAbilityData grown = detached(AbilityCategory.ELECTROMASTER, 3);
        grown.setUsageResourceGrowth(100_000, 100_000);
        if (grown.getUsageMaxCp() <= 0 || grown.getUsageMaxOverload() <= 0
                || grown.getMaxCp() <= plain.getMaxCp()) {
            helper.fail("usage growth did not raise the maximum"); return;
        }
        if (grown.getRawMaxCp() != plain.getRawMaxCp() || grown.getRawMaxOverload() != plain.getRawMaxOverload()) {
            helper.fail("usage growth changed the raw maximum"); return;
        }
        for (PlayerAbilityData data : new PlayerAbilityData[] {plain, grown}) {
            data.setCurrentCp(plain.getRawMaxCp() / 4);
            data.setCurrentOverload(plain.getRawMaxOverload() / 2);
        }
        plain.tick();
        grown.tick();
        float cpPlain = plain.getCurrentCp() - plain.getRawMaxCp() / 4;
        float cpGrown = grown.getCurrentCp() - plain.getRawMaxCp() / 4;
        float oPlain = plain.getRawMaxOverload() / 2 - plain.getCurrentOverload();
        float oGrown = plain.getRawMaxOverload() / 2 - grown.getCurrentOverload();
        if (cpPlain <= 0 || oPlain <= 0) {
            helper.fail("no recovery happened (cp " + cpPlain + ", overload " + oPlain + ")"); return;
        }
        if (Math.abs(cpPlain - cpGrown) > 1e-4f || Math.abs(oPlain - oGrown) > 1e-4f) {
            helper.fail("usage growth changed recovery speed: cp " + cpPlain + " vs " + cpGrown
                    + ", overload " + oPlain + " vs " + oGrown); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aimCatKeepsLevelAndUsageGrowth(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(AbilityCategory.ELECTROMASTER);
        data.setPlayerLevel(3);
        data.learnSkill("arc_gen");
        data.setProficiency("arc_gen", .5f);
        data.setUsageResourceGrowth(50, 20);
        data.setLevelProgressExp(.5f);
        data.setCooldown("arc_gen", 100);
        data.recalculateMaxResources(false);
        float usageCp = data.getUsageMaxCp(), usageOverload = data.getUsageMaxOverload();
        var server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4), "aim cat teleporter");
        if (data.getCurrentAbility() != AbilityCategory.TELEPORTER) {
            helper.fail("/aim cat did not change the category"); return;
        }
        if (data.getPlayerLevel() != 3 || data.getLevelProgressExp() != .5f) {
            helper.fail("/aim cat reset the level to " + data.getPlayerLevel()
                    + " (progress " + data.getLevelProgressExp() + ")"); return;
        }
        if (data.getUsageMaxCp() != usageCp || data.getUsageMaxOverload() != usageOverload) {
            helper.fail("/aim cat dropped usage growth"); return;
        }
        if (!data.getLearnedSkills().isEmpty() || data.getProficiency("arc_gen") != 0
                || data.isOnCooldown("arc_gen")) {
            helper.fail("/aim cat kept learned skills, skill exp or cooldowns"); return;
        }
        if (data.getCurrentCp() != data.getMaxCp() || data.getCurrentOverload() != 0) {
            helper.fail("/aim cat did not refill CP and drain overload"); return;
        }
        PlayerAbilityData fresh = new PlayerAbilityData();
        fresh.changeCategory(AbilityCategory.TELEPORTER);
        if (fresh.getPlayerLevel() != 1) {
            helper.fail("gaining a category from none did not start at Level 1"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void disabledSkillsAreHiddenFromLearning(GameTestHelper helper) {
        Skill magMovement = SkillRegistry.getSkill(AbilityCategory.ELECTROMASTER, "mag_movement");
        if (magMovement == null) { helper.fail("mag_movement is not registered"); return; }
        PlayerAbilityData data = detached(AbilityCategory.ELECTROMASTER, 2);
        data.learnSkill("arc_gen");
        Predicate<String> all = id -> true;
        if (data.canLearnSkill(magMovement, all)) {
            helper.fail("mag_movement was learnable without its charging prerequisite"); return;
        }
        if (!data.canLearnSkill(magMovement, id -> !id.equals("charging"))) {
            helper.fail("a disabled prerequisite still blocked its child"); return;
        }
        if (data.canLearnSkill(magMovement, id -> !id.equals("mag_movement"))) {
            helper.fail("a disabled skill could be learned"); return;
        }
        float allCount = data.getLevelProgressThreshold(all);
        float withoutOne = data.getLevelProgressThreshold(id -> !id.equals("mag_movement"));
        if (!(withoutOne < allCount)) {
            helper.fail("a disabled skill still counted toward level progress: " + allCount + " / " + withoutOne);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void setCooldownKeepsTheLongerValue(GameTestHelper helper) {
        PlayerAbilityData data = detached(AbilityCategory.ELECTROMASTER, 1);
        data.setCooldown("arc_gen", 100);
        data.setCooldown("arc_gen", 20);
        if (data.getCooldownTicks("arc_gen") != 100) {
            helper.fail("a shorter cooldown overwrote a longer one"); return;
        }
        data.setCooldown("arc_gen", 300);
        if (data.getCooldownTicks("arc_gen") != 300) {
            helper.fail("a longer cooldown did not replace a shorter one"); return;
        }
        data.setCooldown("arc_gen", 0);
        if (data.getCooldownTicks("arc_gen") != 300) {
            helper.fail("setCooldown(0) changed a running cooldown"); return;
        }
        if (data.clearCooldowns() != 1 || data.isOnCooldown("arc_gen")) {
            helper.fail("clearCooldowns did not clear the cooldown"); return;
        }
        helper.succeed();
    }
}
