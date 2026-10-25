package com.mohistmc.academy.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mohistmc.academy.skill.ability.telekinesis.TelekinesisRules;
import org.junit.jupiter.api.Test;

class TelekinesisRulesTest {
    @Test void overloadThinkingTradesOverloadForIncreasingCpRecovery() {
        assertEquals(100.0f, TelekinesisRules.overloadThinkingCost(0), 0.001f);
        assertEquals(100.0f, TelekinesisRules.overloadThinkingCost(1), 0.001f);
        assertEquals(1000.0f, TelekinesisRules.overloadThinkingRestore(0), 0.001f);
        assertEquals(2000.0f, TelekinesisRules.overloadThinkingRestore(1), 0.001f);
    }

    @Test void insulationIsStrongestAgainstElectromasterAndMeltdowner() {
        assertEquals(.2F, TelekinesisRules.insulationReduction(1, 1), .001F);
        assertEquals(.4F, TelekinesisRules.insulationReduction(1, 2), .001F);
        assertEquals(.5F, TelekinesisRules.insulationReduction(1, 2.5F), .001F);
    }

    @Test void paperDrillHasAnExactFullStackAndBoundedPulseContract() {
        assertEquals(64, TelekinesisRules.PAPER_DRILL_REQUIRED_PAPER);
        assertEquals(1, TelekinesisRules.PAPER_DRILL_PULSE_INTERVAL);
        assertTrue(TelekinesisRules.paperDrillDamage(1) > TelekinesisRules.paperDrillDamage(0));
        assertEquals(5.0f, TelekinesisRules.paperDrillRange(0), 0.001f);
        assertEquals(5.0f, TelekinesisRules.paperDrillRange(1), 0.001f);
    }
}
