package com.mohistmc.academy.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Regression contracts and behavioral checks for the performance overhaul (P-01 through P-11). */
class PerformanceOverhaulContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy").resolve(relative))
                .replace("\r\n", "\n");
    }

    @Test
    void abilityFingerprintsDetectRuntimeAndProfileChangesWithoutAllocation() {
        PlayerAbilityData data = new PlayerAbilityData();
        long baseProfile = data.profileFingerprint();
        long baseRuntime = data.runtimeFingerprint();
        assertEquals(baseProfile, data.profileFingerprint());
        assertEquals(baseRuntime, data.runtimeFingerprint());

        data.setCurrentCp(data.getCurrentCp() - 5f);
        assertEquals(baseProfile, data.profileFingerprint(), "runtime CP change must not dirty profile fingerprint");
        assertNotEquals(baseRuntime, data.runtimeFingerprint(), "runtime CP change must change runtime fingerprint");

        data.markObtained("minecraft:iron_ingot");
        assertNotEquals(baseProfile, data.profileFingerprint(), "profile change must change profile fingerprint");
    }

    @Test
    void serverHotPathsAvoidGlobalEntityPersistentDataAndRedundantSetData() throws Exception {
        String rad = source("skill/passive/RadiationIntensifyRuntime.java");
        assertTrue(rad.contains("if (TRACKED.isEmpty()) return;"));
        assertTrue(rad.contains("TRACKED.contains(entity.getUUID())"));

        String sync = source("network/AbilitySyncState.java");
        assertTrue(sync.contains("data.profileFingerprint()"));
        assertTrue(sync.contains("data.runtimeFingerprint()"));

        String skillHandler = source("skill/SkillEventHandler.java");
        assertFalse(skillHandler.contains("player.setData(AcademyAttachments.PLAYER_ABILITY, data);"));

        String interferer = source("world/block/entity/AbilityInterfererBlockEntity.java");
        assertTrue(interferer.contains("if (registeredInService)"));
    }

    @Test
    void mineDetectAndWirelessNetworksUseFastPaths() throws Exception {
        String mine = source("skill/ability/electromaster/MineDetectEffect.java");
        assertTrue(mine.contains("currentSection.maybeHas(MineDetectEffect::isOre)"));
        assertTrue(mine.contains("if (state.isAir()) return false;"));

        String net = source("energy/impl/WirelessNet.java");
        String conn = source("energy/impl/NodeConn.java");
        assertFalse(net.contains("new LinkedList<>()"));
        assertFalse(conn.contains("new LinkedList<>()"));
        assertTrue(net.contains("List<IWirelessNode> loadedNodes"));
    }

    @Test
    void clientRenderersEliminatePerFrameVarargsAndQuadRebuilds() throws Exception {
        String mineRender = source("client/render/MineDetectBatchRenderer.java");
        assertFalse(mineRender.contains("float... points"));
        assertTrue(mineRender.contains("maxVisibleDistSq"));

        assertTrue(source("client/block/entity/render/MatrixRender.java").contains("cachedShieldQuads"));
        assertTrue(source("client/block/entity/render/WindGenFanRender.java").contains("cachedFanQuads"));
        assertTrue(source("client/block/entity/render/PhaseLiquidRender.java").contains("getViewDistance()"));
        assertTrue(source("client/render/LegacyAbilityPresentationRenderer.java").contains("TP_MARK_FRAMES"));
    }
}
