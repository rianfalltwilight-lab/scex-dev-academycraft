package com.mohistmc.academy.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Regression contracts for Phase 3 & 4 single-session alignment, server tick persistence, HUD overlap, and session teardown. */
class Phase3And4SessionPolishContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy").resolve(relative))
                .replace("\r\n", "\n");
    }

    @Test
    void clientEnforcesSingleActiveChargeAndAvoidsRedundantPeriodicAcks() throws Exception {
        String input = source("client/KeyInputHandler.java");
        assertTrue(input.contains("if (hasOtherActiveCharge(i))"));
        assertTrue(input.contains("boolean firstAck = CHARGING_EPOCHS[slot] == 0;"));
    }

    @Test
    void serverPlayerTickDoesNotBypassSetDataWhileAwaitingAck() throws Exception {
        String handler = source("skill/SkillEventHandler.java");
        assertFalse(handler.contains("if (!state.acknowledged) return;"),
                "unacknowledged charging state must not skip player.setData at the end of onPlayerTick");
        assertTrue(handler.contains("if (state.acknowledged) {"));
    }

    @Test
    void penetrateHintDoesNotOverlapGenericChargeHintAndControllersResetOnSessionTeardown() throws Exception {
        String hud = source("client/ChargingHudOverlay.java");
        String session = source("client/ClientSessionState.java");
        assertTrue(hud.contains("boolean showedPenetrateHint"));
        assertTrue(hud.contains("y + barHeight + 14"));
        assertTrue(session.contains("ThunderClapMovementController.resetClientSession()"));
        assertTrue(session.contains("StormWingInputSender.resetClientSession()"));
    }
}
