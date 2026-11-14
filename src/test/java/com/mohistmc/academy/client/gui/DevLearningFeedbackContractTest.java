package com.mohistmc.academy.client.gui;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DevLearningFeedbackContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/", path));
    }

    @Test void rejectionIsImmediateDetailedAndDoesNotPreSpendClientSession() throws Exception {
        String tree = source("com/mohistmc/academy/client/gui/SkillTreeGui.java");
        String server = source("com/mohistmc/academy/network/LearnSkillPacket.java");
        assertTrue(tree.contains("acceptServerResult"));
        assertFalse(tree.contains("sessionSpent = true;\n                pendingSince"));
        assertTrue(server.contains("\"ac.skill_tree.result.prereq\""));
        assertTrue(server.contains("\"ac.skill_tree.result.unavailable\""));
        assertTrue(server.contains("\"ac.skill_tree.result.sync_rate\""));
        assertTrue(server.contains("DevLearningSessionManager.start"));
    }

    @Test void energyIsLiveAndTinyAdvancedScreenDisablesInteraction() throws Exception {
        String tree = source("com/mohistmc/academy/client/gui/SkillTreeGui.java");
        String gui = source("com/mohistmc/academy/client/block/gui/DevAdvancedGui.java");
        String sessions = source("com/mohistmc/academy/network/DevLearningSessionManager.java");
        String registration = source("com/mohistmc/academy/AcademyCraft.java");
        assertFalse(tree.contains("refreshAuthoritativeEnergy"), "old client BE state must not overwrite authoritative results");
        assertTrue(tree.contains("acceptServerEnergy") && tree.contains("!gui.sessionSpent")
                && tree.contains("gui.sessionNonce.equals(snapshot.nonce())"));
        assertTrue(sessions.contains("syncIdleEnergy(player)") && sessions.contains("now + 5"));
        assertTrue(sessions.contains("boolean changed = snapshot.energy() != view.energy")
                && sessions.contains("changed && SafePayloadSender.send(player, snapshot)"));
        assertTrue(sessions.contains("validate(player, view.nonce, view.context.type, view.context.pos)"));
        assertTrue(registration.contains("registrar.playToClient(com.mohistmc.academy.network.DevEnergyPacket.TYPE"));
        assertTrue(gui.contains("compactFallback"));
        assertTrue(gui.contains("if (compactFallback) return true"));
        assertFalse(gui.contains("addAcademySlot"));
    }

    @Test void skillSelectionSessionAllowsNormalReadingTimeWithoutLosingOneShotAuthority() throws Exception {
        String sessions = source("com/mohistmc/academy/network/DevLearningSessionManager.java");
        assertTrue(sessions.contains("20L * 60L * 5L"));
        assertTrue(sessions.contains("SESSIONS.commit"));
        assertTrue(sessions.contains("clear(UUID playerId, UUID nonce)"));
    }
}
