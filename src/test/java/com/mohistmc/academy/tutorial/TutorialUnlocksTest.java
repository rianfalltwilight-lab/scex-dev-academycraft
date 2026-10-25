package com.mohistmc.academy.tutorial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TutorialUnlocksTest {
    @Test
    void everyLegacyConditionalTutorialHasAServerSafeTrigger() {
        Map<String, String> samples = Map.of(
                "academy:constraint_metal", "ores",
                "academy:phase_gen", "phase_generator",
                "academy:solar_gen", "solar_generator",
                "academy:windgen_fan", "wind_generator",
                "academy:metal_former", "metal_former",
                "academy:imag_fusor", "imag_fusor",
                "academy:app_skill_tree", "terminal",
                "academy:dev_normal", "ability_developer",
                "academy:rf_output", "energy_bridge");
        samples.forEach((item, tutorial) -> assertEquals(tutorial, TutorialUnlocks.tutorialForItem(item)));
        assertNull(TutorialUnlocks.tutorialForItem("minecraft:stone"));
    }
}
