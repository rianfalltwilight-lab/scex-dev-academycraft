package com.mohistmc.academy.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Regression contracts for Datapack Phase 1: new modpack tags, blasting recipes, dead singleton cleanup, and sync resilience. */
class DatapackOverhaulContractTest {
    private static Path dataPath(String relative) {
        return Path.of("src/main/resources/data/academy").resolve(relative);
    }

    @Test
    void newModpackTagsExistAreValidJsonAndWiredInEffects() throws Exception {
        for (String rel : new String[]{
                "tags/item/railgun_ammo.json",
                "tags/block/mine_detectable.json",
                "tags/block/mine_detect_excluded.json"}) {
            String raw = Files.readString(dataPath(rel));
            assertTrue(JsonParser.parseString(raw).getAsJsonObject().has("values"), rel);
        }
        String railgun = Files.readString(Path.of("src/main/java/com/mohistmc/academy/skill/ability/electromaster/RailgunEffect.java"));
        String mine = Files.readString(Path.of("src/main/java/com/mohistmc/academy/skill/ability/electromaster/MineDetectEffect.java"));
        assertTrue(railgun.contains("RAILGUN_AMMO") && railgun.contains("stack.is(RAILGUN_AMMO)"));
        assertTrue(mine.contains("MINE_DETECTABLE") && mine.contains("MINE_DETECT_EXCLUDED"));
    }

    @Test
    void academyOresProvideBlastingRecipesAtHalfSmeltingTime() throws Exception {
        for (String name : new String[]{"blasting_cons_ingot.json", "blasting_crystal0.json", "blasting_imagsil_ingot.json"}) {
            var json = JsonParser.parseString(Files.readString(dataPath("recipe/" + name))).getAsJsonObject();
            assertTrue("minecraft:blasting".equals(json.get("type").getAsString()), name);
            assertTrue(json.get("cookingtime").getAsInt() == 100, name);
        }
    }

    @Test
    void legacyHardcodedRecipeInitRemovedFromCommonSetup() throws Exception {
        String mainMod = Files.readString(Path.of("src/main/java/com/mohistmc/academy/AcademyCraft.java"));
        assertFalse(mainMod.contains("MFIFRecipes.init()"));
    }

    @Test
    void syncedJsonDataHandlesMalformedSnapshotsWithoutThrowing() {
        var previous = SkillTuningData.INSTANCE.installed();
        try {
            assertDoesNotThrow(() -> SkillTuningData.INSTANCE.acceptSnapshot("{not valid json"));
            assertDoesNotThrow(() -> SkillTuningData.INSTANCE.acceptSnapshot("[]"));
            assertTrue(SkillTuningData.INSTANCE.installed().isEmpty());
        } finally {
            SkillTuningData.applyForTest(previous);
        }
    }

    @Test
    void testOnlyStructureNamespacesDoNotPolluteMainDatapackAndExtraRecipesUseCommonTags() throws Exception {
        assertFalse(Files.exists(Path.of("src/main/resources/data/academy_charging")));
        assertFalse(Files.exists(Path.of("src/main/resources/data/academy_recheck_compat")));
        String optical = Files.readString(dataPath("recipe/extra_optical_chip.json"));
        String helmet = Files.readString(dataPath("recipe/extra_reso_helmet.json"));
        assertTrue(optical.contains("\"tag\":\"c:ingots/imag_silicon\""));
        assertTrue(helmet.contains("\"tag\":\"c:plates/constraint_metal\""));
        assertTrue(helmet.contains("\"tag\":\"c:gems/resonant_crystal\""));
    }
}
