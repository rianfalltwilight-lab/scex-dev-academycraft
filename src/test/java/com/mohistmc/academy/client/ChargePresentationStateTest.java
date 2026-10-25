package com.mohistmc.academy.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChargePresentationStateTest {
    @Test void advancesBetweenPacketsAndAcceptsAuthoritativeCorrections() {
        var state = new ChargePresentationState();
        state.accept(10, 40, "railgun");
        for (int i = 0; i < 4; i++) state.tick();
        assertEquals(14, state.ticks());
        assertEquals(14.5f / 40, state.progress(.5f));
        state.accept(12, 40, "railgun");
        assertEquals(12, state.ticks(), "server correction must replace local extrapolation");
        state.accept(50, 40, "railgun");
        assertEquals(1f, state.progress(.9f));
    }

    @Test void abortAndTimeoutClearSkillAndProgress() {
        var state = new ChargePresentationState();
        state.accept(1, 30, "light_shield");
        state.accept(-1, 30, "light_shield");
        assertFalse(state.active());
        assertEquals("", state.skill());
        assertEquals(0, state.progress(.5f));
        state.accept(1, 30, "light_shield");
        for (int i = 0; i < 100; i++) state.tick();
        assertTrue(state.active());
        state.tick();
        assertFalse(state.active());
        assertEquals(0, state.ticks());
    }

    @Test void zeroMaximumIsSafeAndResetStopsProgress() {
        var state = new ChargePresentationState();
        state.accept(7, 0, "charging");
        assertEquals(7, state.ticks());
        assertEquals(0, state.progress(1));
        state.clear();
        state.tick();
        assertFalse(state.active());
    }
}
