package com.mohistmc.academy.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.config.LegacyAbilityRules;
import com.mohistmc.academy.data.SkillTuningData;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.PlayerAbilityDataCodec;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Datapack {@code data/academy/academy_skills/<skill>.json} overrides.  Each test installs its files,
 * checks synchronously on the server thread and restores the built-in defaults in {@code finally}, so
 * no other test can observe the override.
 */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SkillTuningDataGameTests {
    private static final String EMPTY = "empty";
    private SkillTuningDataGameTests() {}

    private static Map<ResourceLocation, JsonElement> files(String... idAndJson) {
        Map<ResourceLocation, JsonElement> out = new LinkedHashMap<>();
        for (int i = 0; i < idAndJson.length; i += 2) {
            out.put(ResourceLocation.parse(idAndJson[i]), JsonParser.parseString(idAndJson[i + 1]));
        }
        return out;
    }

    private static PlayerAbilityData detached(AbilityCategory category, int level) {
        PlayerAbilityData data = new PlayerAbilityData();
        data.setCurrentAbility(category);
        data.setPlayerLevel(level);
        data.recalculateMaxResources(true);
        return data;
    }

    @GameTest(template = EMPTY)
    public static void damageMultiplierReachesDynamicSkillRules(GameTestHelper helper) {
        try {
            float before = DynamicSkillRules.damage("railgun", 10);
            float otherBefore = DynamicSkillRules.damage("arc_gen", 10), cpBefore = DynamicSkillRules.cp("railgun", 80);
            SkillTuningData.applyForTest(files("academy:railgun", "{\"damage\": 2.5, \"cp\": 0.5}"));
            float after = DynamicSkillRules.damage("railgun", 10);
            if (Math.abs(after - before * 2.5f) > 1e-4f) { helper.fail("damage " + before + " -> " + after); return; }
            if (Math.abs(DynamicSkillRules.cp("railgun", 80) - cpBefore * .5f) > 1e-4f) {
                helper.fail("cp multiplier not applied: " + DynamicSkillRules.tuning("railgun").cp()); return;
            }
            if (DynamicSkillRules.damage("arc_gen", 10) != otherBefore) { helper.fail("override leaked to arc_gen"); return; }
        } finally {
            SkillTuningData.applyForTest(Map.of());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void configOverrideLinesStillCombine(GameTestHelper helper) {
        try {
            SkillTuningData.applyForTest(files("academy:railgun",
                    "{\"cp\": 3, \"cooldown\": 2, \"destroy_blocks\": true, \"enabled\": true}"));
            var t = LegacyAbilityRules.parseSkill("railgun", List.of("railgun.cp=0.5", "railgun.cooldown=1.5",
                    "railgun.destroy_blocks=false", "railgun.enabled=false"));
            if (Math.abs(t.cp() - 1.5f) > 1e-6f) { helper.fail("cp config x datapack = " + t.cp()); return; }
            if (Math.abs(t.cooldown() - 3f) > 1e-6f) { helper.fail("cooldown config x datapack = " + t.cooldown()); return; }
            if (t.destroyBlocks() || t.enabled()) { helper.fail("a config 'false' was overridden by the datapack"); return; }
            SkillTuningData.applyForTest(files("academy:railgun", "{\"enabled\": false}"));
            if (LegacyAbilityRules.parseSkill("railgun", List.of()).enabled()) {
                helper.fail("datapack enabled=false ignored"); return;
            }
        } finally {
            SkillTuningData.applyForTest(Map.of());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void disabledSkillCannotBeUsedOrLearned(GameTestHelper helper) {
        Skill arcGen = SkillRegistry.getSkill(AbilityCategory.ELECTROMASTER, "arc_gen");
        PlayerAbilityData learner = detached(AbilityCategory.ELECTROMASTER, 1);
        PlayerAbilityData user = detached(AbilityCategory.ELECTROMASTER, 1);
        user.learnSkill("arc_gen");
        if (!learner.canLearnSkill(arcGen) || !user.canUseSkill(arcGen)) {
            helper.fail("baseline: arc_gen should be learnable and usable"); return;
        }
        try {
            SkillTuningData.applyForTest(files("academy:arc_gen", "{\"enabled\": false}"));
            if (learner.canLearnSkill(arcGen)) { helper.fail("disabled arc_gen still learnable"); return; }
            if (user.canUseSkill(arcGen)) { helper.fail("disabled arc_gen still usable"); return; }
            if (DynamicSkillRules.enabled("arc_gen")) { helper.fail("DynamicSkillRules.enabled ignored the datapack"); return; }
        } finally {
            SkillTuningData.applyForTest(Map.of());
        }
        if (!learner.canLearnSkill(arcGen) || !user.canUseSkill(arcGen)) { helper.fail("reset did not re-enable arc_gen"); return; }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void cooldownMultiplierScalesStartsButNotReloads(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            SkillTuningData.applyForTest(files("academy:arc_gen", "{\"cooldown\": 2}"));
            PlayerAbilityData data = detached(AbilityCategory.ELECTROMASTER, 1);
            data.setCooldown("arc_gen", 100);
            data.setCooldown("charging", 100);
            if (data.getCooldownTicks("arc_gen") != 200) { helper.fail("2x cooldown gave " + data.getCooldownTicks("arc_gen")); return; }
            if (data.getCooldownTicks("charging") != 100) { helper.fail("multiplier leaked to charging"); return; }
            data.setCooldownRaw("mag_movement", 50);
            if (data.getCooldownTicks("mag_movement") != 50) { helper.fail("raw setter scaled"); return; }

            int synced = PlayerAbilityData.fromSyncTag(data.toSyncTag()).getCooldownTicks("arc_gen");
            if (synced != 200) { helper.fail("fromSyncTag re-scaled to " + synced); return; }
            PlayerAbilityData runtime = detached(AbilityCategory.ELECTROMASTER, 1);
            runtime.applyRuntimeSyncTag(data.toRuntimeSyncTag());
            if (runtime.getCooldownTicks("arc_gen") != 200) {
                helper.fail("applyRuntimeSyncTag re-scaled to " + runtime.getCooldownTicks("arc_gen")); return;
            }
            var provider = helper.getLevel().registryAccess();
            CompoundTag saved = PlayerAbilityDataCodec.INSTANCE.write(data, provider);
            int loaded = PlayerAbilityDataCodec.INSTANCE.read(player, saved, provider).getCooldownTicks("arc_gen");
            if (loaded != 200) { helper.fail("codec read re-scaled to " + loaded); return; }
        } finally {
            SkillTuningData.applyForTest(Map.of());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void levelAndPrerequisiteOverridesChangeLearning(GameTestHelper helper) {
        Skill magMovement = SkillRegistry.getSkill(AbilityCategory.ELECTROMASTER, "mag_movement");
        PlayerAbilityData data = detached(AbilityCategory.ELECTROMASTER, 2);
        data.learnSkill("arc_gen");
        data.learnSkill("charging");
        data.setProficiency("charging", .7f);
        if (!data.canLearnSkill(magMovement)) { helper.fail("baseline: mag_movement should be learnable"); return; }
        try {
            SkillTuningData.applyForTest(files("academy:mag_movement",
                    "{\"level\": 3, \"tree_x\": 10, \"base_cp\": 4, \"prerequisites\": ["
                            + "{\"skill\": \"arc_gen\", \"proficiency\": 0.9}, {\"skill\": \"no_such_skill\"}]}",
                    "academy:no_such_skill", "{\"level\": 1}",
                    "othermod:arc_gen", "{\"enabled\": false}"));
            if (magMovement.getLevel() != 3 || magMovement.getTreeX() != 10 || magMovement.getBaseCpCost() != 4) {
                helper.fail("getters ignore the override: level " + magMovement.getLevel()); return;
            }
            if (magMovement.getPrerequisites().size() != 1) {
                helper.fail("unknown prerequisite not skipped: " + magMovement.getPrerequisites()); return;
            }
            if (!DynamicSkillRules.enabled("arc_gen")) { helper.fail("a non-academy namespace was read"); return; }
            if (data.canLearnSkill(magMovement)) { helper.fail("level 3 override ignored"); return; }
            data.setPlayerLevel(3);
            if (data.canLearnSkill(magMovement)) { helper.fail("arc_gen 0.9 prerequisite ignored"); return; }
            data.setProficiency("arc_gen", .9f);
            data.unlearnSkill("charging");
            if (!data.canLearnSkill(magMovement)) { helper.fail("replaced prerequisite list still needs charging"); return; }

            SkillTuningData.applyForTest(files("academy:mag_movement",
                    "{\"prerequisites\": [{\"any_level\": 1}], \"level\": 9}"));
            if (magMovement.getLevel() != 5) { helper.fail("level not clamped to 5: " + magMovement.getLevel()); return; }
            data.setPlayerLevel(5);
            data.setProficiency("arc_gen", 0);
            if (!data.canLearnSkill(magMovement)) { helper.fail("any_level prerequisite not honoured"); return; }
            data.unlearnSkill("arc_gen");
            if (data.canLearnSkill(magMovement)) { helper.fail("any_level 1 satisfied without a level-1 skill"); return; }
        } finally {
            SkillTuningData.applyForTest(Map.of());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void emptyDataRestoresBuiltInDefaults(GameTestHelper helper) {
        Skill magMovement = SkillRegistry.getSkill(AbilityCategory.ELECTROMASTER, "mag_movement");
        int level = magMovement.getLevel();
        var prereqs = List.copyOf(magMovement.getPrerequisites());
        double x = magMovement.getTreeX(), y = magMovement.getTreeY();
        float cp = magMovement.getBaseCpCost(), overload = magMovement.getBaseOverload();
        var tuning = DynamicSkillRules.tuning("mag_movement");
        try {
            SkillTuningData.applyForTest(files("academy:mag_movement", "{\"level\": 4, \"prerequisites\": [],"
                    + " \"tree_x\": 1, \"tree_y\": 2, \"base_cp\": 99, \"base_overload\": 99, \"damage\": 3, \"cooldown\": 0}"));
            if (magMovement.getLevel() != 4 || !magMovement.getPrerequisites().isEmpty()
                    || DynamicSkillRules.cooldown("mag_movement", 40) != 0) {
                helper.fail("override was not installed"); return;
            }
        } finally {
            SkillTuningData.applyForTest(Map.of());
        }
        if (magMovement.getLevel() != level || !magMovement.getPrerequisites().equals(prereqs)
                || magMovement.getTreeX() != x || magMovement.getTreeY() != y
                || magMovement.getBaseCpCost() != cp || magMovement.getBaseOverload() != overload) {
            helper.fail("skill fields not restored"); return;
        }
        if (!DynamicSkillRules.tuning("mag_movement").equals(tuning) || DynamicSkillRules.cooldown("mag_movement", 40) != 40) {
            helper.fail("tuning not restored: " + DynamicSkillRules.tuning("mag_movement")); return;
        }
        helper.succeed();
    }
}
