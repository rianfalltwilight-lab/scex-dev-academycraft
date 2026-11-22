package com.mohistmc.academy.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Regression contracts for Phase 2 unified mouse/key auxiliary routing and ability-sync reconciliation. */
class Phase2InputRouterContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy").resolve(relative))
                .replace("\r\n", "\n");
    }

    @Test
    void auxiliaryBindingsSupportBothKeyboardAndMouseButtons() throws Exception {
        String input = source("client/KeyInputHandler.java");
        assertTrue(input.contains("InputEvent.MouseButton.Post"));
        assertTrue(input.contains("flashingMouseDirection(mc, event.getButton())"));
        assertTrue(input.contains("handleAuxiliaryInput(mc, event.getAction(), direction)"));
        assertTrue(input.contains("boolean rePressed = WAS_DOWN[i] && down && clicked;"));
    }

    @Test
    void presetSwitchPrechecksLocalChargeStateBeforeSending() throws Exception {
        String input = source("client/KeyInputHandler.java");
        int switchIdx = input.indexOf("if (SWITCH_PRESET.consumeClick())");
        int activeSwitchIdx = input.indexOf("if (SWITCH_PRESET.consumeClick())", switchIdx + 1);
        assertTrue(activeSwitchIdx > 0);
        String block = input.substring(activeSwitchIdx, Math.min(input.length(), activeSwitchIdx + 320));
        assertTrue(block.contains("hasAnyActiveCharge()"),
                "client must not play misleading switch feedback while a charge is active");
    }

    @Test
    void allAbilitySyncPacketsReconcileClientInputState() throws Exception {
        String bridge = source("client/ClientPacketBridge.java");
        assertTrue(bridge.contains("KeyInputHandler.reconcileWithAbilityData(data)"));
        for (String packet : new String[]{
                "network/SyncAbilityDataPacket.java",
                "network/SyncAbilityProfilePacket.java",
                "network/SyncAbilityRuntimePacket.java"}) {
            assertTrue(source(packet).contains("ClientPacketBridge.abilityDataSynced"), packet);
        }
    }
}
