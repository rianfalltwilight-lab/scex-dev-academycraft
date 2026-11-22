package com.mohistmc.academy.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Regression contracts for Phase 1 input stability, scroll/middle-click handling, and deadlock elimination. */
class Phase1InputStabilityContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy").resolve(relative))
                .replace("\r\n", "\n");
    }

    @Test
    void skillKeyDownRejectNeverDropsCorrelatedNacks() throws Exception {
        String packet = source("network/SkillKeyDownPacket.java");
        assertFalse(packet.contains("skill_key_down_feedback"),
                "1-per-second NACK rate limiting starves the client charging slot into a permanent deadlock");
        assertTrue(packet.contains("SafePayloadSender.send(player, new SyncChargingStatePacket(-1, 0, slot, skillId, 0, generation, false))"));
    }

    @Test
    void clientChargingTombstonesSelfHealAndEvictOnRepress() throws Exception {
        String input = source("client/KeyInputHandler.java");
        assertTrue(input.contains("ChargingHandshake.shouldExpireTombstone"));
        assertTrue(input.contains("recordStaleTombstone(i)"));
        assertTrue(input.contains("consumeStaleTombstone(slot, generation, skillId)"));
        assertTrue(input.contains("if (isToggleContextSkill(CHARGING_SKILLS[i])) continue;"),
                "opening a screen must preserve active toggle defenses/flight");
    }

    @Test
    void flashingRejectionCannotLatchClientActiveState() throws Exception {
        String input = source("client/KeyInputHandler.java");
        String bridge = source("client/ClientPacketBridge.java");
        assertFalse(input.contains("flashingActive = !flashingActive;"),
                "flashingActive must be server-authoritative via FlashingStatePacket");
        assertTrue(bridge.contains("|| !p.active()"),
                "server rejection FlashingStatePacket(false, 0) must always synchronize KeyInputHandler");
    }

    @Test
    void middleClickAndHeldScrollAreIntercepted() throws Exception {
        String listener = source("listener/ClientListener.java");
        String input = source("client/KeyInputHandler.java");
        assertTrue(listener.contains("event.isPickBlock()"));
        assertTrue(listener.contains("GLFW.GLFW_MOUSE_BUTTON_MIDDLE"));
        assertTrue(listener.contains("overridesInteractionKey"));
        assertTrue(input.contains("hasActiveHeldCharge()"));
        assertTrue(input.contains("!mc.isWindowActive()"));
    }
}
