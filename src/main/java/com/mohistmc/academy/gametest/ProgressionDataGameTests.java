package com.mohistmc.academy.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.data.ProgressionData;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.LegacyLevelProgress;
import com.mohistmc.academy.skill.LegacyResourceProgression;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.block.DevMachineType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Datapack {@code academy_progression}: defaults equal 1.0.7, overrides reach every reader, bad data is ignored. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ProgressionDataGameTests {
    private static final String EMPTY = "empty";
    private ProgressionDataGameTests() {}

    private static Map<ResourceLocation, JsonElement> files(String... idAndJson) {
        Map<ResourceLocation, JsonElement> out = new LinkedHashMap<>();
        for (int i = 0; i < idAndJson.length; i += 2) {
            out.put(ResourceLocation.parse(idAndJson[i]), JsonParser.parseString(idAndJson[i + 1]));
        }
        return out;
    }

    private static String machine(DevMachineType type) {
        return type + "[max=" + type.maxLevel() + " sync=" + type.syncRate() + " tps=" + type.stimulationTicks()
                + " cps=" + type.energyPerStimulation() + "]";
    }

    @GameTest(template = EMPTY)
    public static void defaultsEqualTheLegacyNumbers(GameTestHelper helper) {
        ProgressionData.resetForTest();
        float[] cp = {1800, 1800, 2800, 4000, 5800, 8000};
        float[] cpAdded = {0, 900, 1000, 1500, 1700, 12000};
        float[] ov = {100, 100, 150, 240, 350, 500};
        float[] ovAdded = {0, 40, 70, 80, 100, 500};
        for (int l = 0; l <= 5; l++) {
            if (LegacyResourceProgression.initialCp(l) != cp[l] || LegacyResourceProgression.maxAddedCp(l) != cpAdded[l]
                    || LegacyResourceProgression.initialOverload(l) != ov[l]
                    || LegacyResourceProgression.maxAddedOverload(l) != ovAdded[l]) {
                helper.fail("resource table differs from 1.0.7 at level " + l); return;
            }
        }
        if (LegacyResourceProgression.courseCpBonus(true, true) != 2500
                || LegacyResourceProgression.courseOverloadBonus(true) != 100
                || LegacyResourceProgression.recoveryMultiplier(true) != 1.2f) {
            helper.fail("course bonuses differ from 1.0.7"); return;
        }
        if (LegacyLevelProgress.threshold(1, 2) != 2 * 0.666f || LegacyLevelProgress.threshold(4, 3) != 3 * 1.333f) {
            helper.fail("level-up thresholds differ from 1.0.7"); return;
        }
        int[][] dev = {{2, 30, 25, 750}, {3, 70, 20, 700}, {Integer.MAX_VALUE, 100, 15, 600}};
        for (DevMachineType type : DevMachineType.values()) {
            int[] e = dev[type.ordinal()];
            if (type.maxLevel() != e[0] || type.syncRate() != e[1] || type.stimulationTicks() != e[2]
                    || type.energyPerStimulation() != e[3]) {
                helper.fail("developer defaults changed: " + machine(type)); return;
            }
        }
        for (int l = 1; l <= 5; l++) {
            if (ProgressionData.learnStimulations(l) != (int) (3 + l * l * .5f)
                    || ProgressionData.levelUpStimulations(l) != 5 * (l + 1)
                    || ProgressionData.resetStimulations(l) != l * 10) {
                helper.fail("stimulation curve changed at level " + l); return;
            }
        }
        if (ProgressionData.inductionStimulations() != 5) { helper.fail("induction is not 5 stimulations"); return; }
        for (AbilityCategory c : AbilityCategory.all()) {
            if (ProgressionData.inductionWeight(c) != 1.0) { helper.fail("default weight of " + c + " is not 1"); return; }
        }
        // Equal weights keep the legacy single nextInt draw.
        List<AbilityCategory> all = new ArrayList<>(AbilityCategory.all());
        RandomSource a = RandomSource.create(42), b = RandomSource.create(42);
        for (int i = 0; i < 64; i++) {
            if (ProgressionData.pickInductionCategory(all, a) != all.get(b.nextInt(all.size()))) {
                helper.fail("default induction pick no longer matches nextInt(size)"); return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void cpCurveOverrideReachesPlayerMaxCp(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        try {
            data.setCurrentAbility(AbilityCategory.ELECTROMASTER);
            data.setPlayerLevel(5);
            data.setAbilityActive(true);
            data.recalculateMaxResources(true);
            float before = data.getMaxCp();
            ProgressionData.applyForTest(files(
                    "pack:a", "{\"cp\":{\"initial\":[1800,1800,2800,4000,5800,8000]},\"courses\":{\"brain_course_cp\":7}}",
                    "pack:b", "{\"cp\":{\"initial\":[1800,1800,2800,4000,5800,16000]},"
                            + "\"overload\":{\"initial\":[1,2,3,4,5,600]}}"));
            data.recalculateMaxResources(true);
            if (data.getMaxCp() != before + 8000) {
                helper.fail("level 5 initial CP override not applied: " + before + " -> " + data.getMaxCp()); return;
            }
            if (data.getMaxOverload() != 600 || LegacyResourceProgression.courseCpBonus(true, false) != 7) {
                helper.fail("overload/course override (merged from two files) missing"); return;
            }
            ProgressionData.applyForTest(files("pack:a", "{\"level_up\":{\"threshold_per_skill\":[1,1,1,1,2,1]}}"));
            if (LegacyLevelProgress.threshold(4, 3) != 6 || LegacyLevelProgress.threshold(2, 3) != 3) {
                helper.fail("level-up threshold override missing"); return;
            }
        } finally {
            ProgressionData.resetForTest();
            data.recalculateMaxResources(true);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void developerAndStimulationOverrides(GameTestHelper helper) {
        try {
            ProgressionData.applyForTest(files("pack:dev", "{\"developer\":{\"advanced\":{\"ticks_per_stimulation\":7,"
                    + "\"energy_per_stimulation\":280},\"portable\":{\"max_level\":1,\"sync_rate\":500}},"
                    + "\"stimulations\":{\"learn_base\":1,\"learn_per_level_squared\":2,\"level_up_per_level\":3,"
                    + "\"reset_per_level\":4,\"induction\":9}}"));
            DevMachineType adv = DevMachineType.ADVANCED;
            if (adv.stimulationTicks() != 7 || adv.energyPerStimulation() != 280 || adv.energyPerTick() != 40
                    || adv.developmentTicksPerStimulation() != 8 || adv.actualEnergyPerStimulation() != 320
                    || adv.maxLevel() != Integer.MAX_VALUE || adv.syncRate() != 100) {
                helper.fail("advanced override wrong: " + machine(adv)); return;
            }
            DevMachineType portable = DevMachineType.PORTABLE;
            if (portable.maxLevel() != 1 || portable.syncRate() != 100 || portable.stimulationTicks() != 25) {
                helper.fail("portable override/clamp wrong: " + machine(portable)); return;
            }
            if (DevMachineType.NORMAL.stimulationTicks() != 20) { helper.fail("normal changed without data"); return; }
            if (ProgressionData.learnStimulations(3) != 19 || ProgressionData.levelUpStimulations(2) != 9
                    || ProgressionData.resetStimulations(3) != 12 || ProgressionData.inductionStimulations() != 9) {
                helper.fail("stimulation overrides not applied"); return;
            }
            ProgressionData.applyForTest(files("pack:z", "{\"stimulations\":{\"learn_base\":0,"
                    + "\"learn_per_level_squared\":0,\"induction\":-3}}"));
            if (ProgressionData.learnStimulations(5) != 1 || ProgressionData.inductionStimulations() != 5) {
                helper.fail("stimulation counts are not clamped / negatives not ignored"); return;
            }
        } finally {
            ProgressionData.resetForTest();
        }
        if (DevMachineType.ADVANCED.stimulationTicks() != 15) { helper.fail("reset did not restore defaults"); return; }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void inductionWeightsSteerTheNoFactorPick(GameTestHelper helper) {
        List<AbilityCategory> all = new ArrayList<>(AbilityCategory.all());
        RandomSource random = RandomSource.create(7);
        try {
            ProgressionData.applyForTest(files("pack:w",
                    "{\"induction\":{\"category_weights\":{\"teleporter\":0,\"meltdowner\":3}}}"));
            int meltdowner = 0;
            for (int i = 0; i < 4000; i++) {
                AbilityCategory pick = ProgressionData.pickInductionCategory(all, random);
                if (pick == AbilityCategory.TELEPORTER) { helper.fail("weight 0 category was chosen"); return; }
                if (pick == AbilityCategory.MELTDOWNER) meltdowner++;
            }
            // 3 of 7 total weight: expect ~1714 (sd ~31).
            if (meltdowner < 1550 || meltdowner > 1880) {
                helper.fail("weight 3 category picked " + meltdowner + "/4000 times"); return;
            }
            ProgressionData.applyForTest(files("pack:w", "{\"induction\":{\"category_weights\":{\"electromaster\":0,"
                    + "\"meltdowner\":0,\"teleporter\":0,\"vecmanip\":0,\"aerohand\":0,\"telekinesis\":0}}}"));
            Set<AbilityCategory> seen = new HashSet<>();
            for (int i = 0; i < 600; i++) {
                AbilityCategory pick = ProgressionData.pickInductionCategory(all, random);
                if (pick == null) { helper.fail("all-zero weights picked nothing"); return; }
                seen.add(pick);
            }
            if (seen.size() != all.size()) { helper.fail("all-zero weights did not fall back to uniform"); return; }
        } finally {
            ProgressionData.resetForTest();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void invalidFieldsAreIgnored(GameTestHelper helper) {
        try {
            ProgressionData.applyForTest(files(
                    "pack:a", "{\"cp\":{\"initial\":[1,2,3],\"max_added\":[0,0,0,0,0,\"x\"]},"
                            + "\"overload\":{\"initial\":[1,2,3,4,5,-6]},\"level_up\":{\"threshold_per_skill\":5},"
                            + "\"courses\":{\"mind_course_recovery\":-1},\"developer\":{\"normal\":[1],"
                            + "\"bogus\":{}},\"induction\":{\"category_weights\":{\"vecmanip\":-2}}}",
                    "pack:b", "[1,2,3]"));
            if (LegacyResourceProgression.initialCp(5) != 8000 || LegacyResourceProgression.maxAddedCp(5) != 12000
                    || LegacyResourceProgression.initialOverload(5) != 500
                    || LegacyResourceProgression.recoveryMultiplier(true) != 1.2f
                    || LegacyLevelProgress.threshold(4, 1) != 1.333f
                    || DevMachineType.NORMAL.stimulationTicks() != 20
                    || ProgressionData.inductionWeight(AbilityCategory.VECMANIP) != 1.0) {
                helper.fail("an invalid field replaced a default"); return;
            }
            // A valid later file still overrides, and a bad array there keeps the earlier file's value.
            ProgressionData.applyForTest(files(
                    "pack:a", "{\"cp\":{\"initial\":[1,2,3,4,5,6]}}",
                    "pack:b", "{\"cp\":{\"initial\":[9,9,9,9,9,9,9]}}"));
            if (LegacyResourceProgression.initialCp(5) != 6) {
                helper.fail("a bad later array clobbered an earlier valid one"); return;
            }
        } finally {
            ProgressionData.resetForTest();
        }
        helper.succeed();
    }
}
