package com.mohistmc.academy.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mohistmc.academy.skill.SkillPreset;
import org.junit.jupiter.api.Test;

class SetSkillSlotDuplicateTest {
    @Test
    void rejectsSkillAlreadyBoundToAnotherSlot() {
        SkillPreset preset = new SkillPreset();
        preset.setSlot(0, "railgun");
        assertTrue(SetSkillSlotPacket.isBoundElsewhere(preset, 2, "railgun"));
    }

    @Test
    void allowsRebindingTheSameSlotAndUnusedSkills() {
        SkillPreset preset = new SkillPreset();
        preset.setSlot(0, "railgun");
        assertFalse(SetSkillSlotPacket.isBoundElsewhere(preset, 0, "railgun"));
        assertFalse(SetSkillSlotPacket.isBoundElsewhere(preset, 1, "arc_gen"));
        assertFalse(SetSkillSlotPacket.isBoundElsewhere(null, 1, "arc_gen"));
    }
}
