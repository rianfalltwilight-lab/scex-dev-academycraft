package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.config.BalanceProfile;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.data.ProgressionData;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AbilityMutationService;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.world.AcademyAttributes;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** MZ2: the modern progression layer, its rules, and the player resource attributes. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ModernProgressionGameTests {
    private static final String EMPTY = "empty";
    private static final String BATCH = "modern_progression";
    private static final ResourceLocation MODIFIER = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "mz2_test");
    private ModernProgressionGameTests() {}

    private static void check(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }

    private static boolean near(float a, float b) { return Math.abs(a - b) <= Math.max(1e-3f, Math.abs(b) * 1e-4f); }

    private static PlayerAbilityData data(int level) {
        PlayerAbilityData data = new PlayerAbilityData();
        data.setCurrentAbility(AbilityCategory.ELECTROMASTER);
        data.setPlayerLevel(level);
        data.recalculateMaxResources(true);
        return data;
    }

    private interface Body { void run(); }

    private static void withProfile(BalanceProfile profile, Body body) {
        BalanceProfile before = ACConfig.Server.BALANCE_PROFILE.get();
        ACConfig.Server.BALANCE_PROFILE.set(profile);
        try { body.run(); } finally { ACConfig.Server.BALANCE_PROFILE.set(before); }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void shippedModernLayerChangesOnlyTheModernProfile(GameTestHelper helper) {
        withProfile(BalanceProfile.LEGACY, () -> {
            check(ProgressionData.initialCp(5) == 8000 && ProgressionData.maxAddedCp(5) == 12000, "legacy curve changed");
            check(!ProgressionData.keepUsageGrowthOnLevelUp() && ProgressionData.overloadUnlockFraction() == 0,
                    "legacy rules changed");
            check(near(ACConfig.Server.legacyRules().cpSpeed(), ACConfig.Server.CP_RECOVER_SPEED.get().floatValue()),
                    "legacy cp speed scaled");
        });
        withProfile(BalanceProfile.MODERN, () -> {
            check(ProgressionData.initialCp(5) == 16000, "modern curve missing: " + ProgressionData.initialCp(5));
            check(near(ACConfig.Server.legacyRules().cpSpeed(),
                    ACConfig.Server.CP_RECOVER_SPEED.get().floatValue() * 2.25f), "modern cp recovery missing");
            check(near(ProgressionData.prerequisiteProficiencyCap(), .75f), "modern prerequisite cap missing");
            check(near(new Skill.Prerequisite("arc_gen", 1f).effectiveProficiency(), .75f)
                    && near(new Skill.Prerequisite("arc_gen", .5f).effectiveProficiency(), .5f), "cap misapplied");
            PlayerAbilityData data = data(5);
            check(data.getMaxCp() == 16000, "modern max CP not applied to players: " + data.getMaxCp());
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void modernRulesKeepGrowthUnlockEarlyAndDiscount(GameTestHelper helper) {
        withProfile(BalanceProfile.MODERN, () -> {
            PlayerAbilityData data = data(3);
            data.setUsageResourceGrowth(500, 20);
            data.setPlayerLevel(4);
            check(data.getUsageMaxCp() == 500 && data.getUsageMaxOverload() == 20, "growth cleared on level-up");

            data.setCurrentOverload(data.getMaxOverload() * .6f);
            data.setOverloadLocked(true);
            data.tick();
            check(data.isOverloadLocked(), "unlocked above half of max overload");
            data.setCurrentOverload(data.getMaxOverload() * .5f);
            data.tick();
            check(!data.isOverloadLocked(), "lock did not lift at half of max overload");

            data.setProficiency("arc_gen", 1f);
            check(near(DynamicSkillRules.playerCp(data, "arc_gen", 70), 35), "full-proficiency discount missing");
            data.setProficiency("arc_gen", 0f);
            check(near(DynamicSkillRules.playerCp(data, "arc_gen", 70), 70), "zero proficiency was discounted");
        });
        withProfile(BalanceProfile.LEGACY, () -> {
            PlayerAbilityData data = data(3);
            data.setUsageResourceGrowth(500, 20);
            data.setPlayerLevel(4);
            check(data.getUsageMaxCp() == 0, "legacy kept growth across a level-up");
            data.setProficiency("arc_gen", 1f);
            check(near(DynamicSkillRules.playerCp(data, "arc_gen", 70), 70), "legacy discounted CP");
        });
        helper.succeed();
    }

    private static void modifier(ServerPlayer player, Holder<Attribute> attribute, double amount) {
        var instance = player.getAttribute(attribute);
        check(instance != null, attribute.getRegisteredName() + " missing on players");
        instance.removeModifier(MODIFIER);
        instance.addTransientModifier(new AttributeModifier(MODIFIER, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void resourceAttributesScalePoolsCostsCooldownsAndGain(GameTestHelper helper) {
        withProfile(BalanceProfile.LEGACY, () -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            data.setCurrentAbility(AbilityCategory.ELECTROMASTER);
            data.setPlayerLevel(5);
            data.learnSkill("arc_gen");
            data.applyAttributes(player);
            data.recalculateMaxResources(true);
            float baseMax = data.getMaxCp(), baseOverload = data.getMaxOverload(), baseRegen = data.getCpRegenRate();

            modifier(player, AcademyAttributes.MAX_CP, .5);
            modifier(player, AcademyAttributes.MAX_OVERLOAD, 1.0);
            modifier(player, AcademyAttributes.CP_REGEN, 1.0);
            modifier(player, AcademyAttributes.CP_COST, -.5);
            modifier(player, AcademyAttributes.COOLDOWN, -.5);
            modifier(player, AcademyAttributes.PROFICIENCY_GAIN, 1.0);
            try {
                data.applyAttributes(player);
                data.recalculateMaxResources(false);
                check(near(data.getMaxCp(), baseMax * 1.5f), "max_cp: " + data.getMaxCp() + " vs " + baseMax);
                check(near(data.getMaxOverload(), baseOverload * 2), "max_overload: " + data.getMaxOverload());
                check(near(data.getCpRegenRate(), baseRegen * 2), "cp_regen: " + data.getCpRegenRate());
                check(near(DynamicSkillRules.playerCp(data, "arc_gen", 70), 35), "cp_cost not applied");
                data.clearCooldowns();
                data.setCooldown("arc_gen", 100);
                check(data.getCooldownTicks("arc_gen") == 50, "cooldown attribute: " + data.getCooldownTicks("arc_gen"));
                float before = data.getProficiency("arc_gen");
                AbilityMutationService.addSkillExp(player, data, "arc_gen", .01f);
                check(near(data.getProficiency("arc_gen") - before, .02f), "proficiency_gain not applied");
            } finally {
                for (var attribute : AcademyAttributes.PLAYER_RESOURCES) {
                    var instance = player.getAttribute(attribute);
                    if (instance != null) instance.removeModifier(MODIFIER);
                }
            }
        });
        helper.succeed();
    }
}
