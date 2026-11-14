package com.mohistmc.academy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The [machines] server config must default to the AcademyCraft 1.0.7 numbers. */
class MachineConfigDefaultsTest {
    @Test
    void defaultsEqualLegacyHardCodedValues() {
        assertTrue(ACConfig.Server.SPEC != null);
        assertEquals(4, MachineConfig.FE_PER_IF.get());
        assertEquals(2000, MachineConfig.WIRELESS_NETWORK_BUFFER.get());
        tier(MachineConfig.NODE_BASIC, 15000, 150, 9.0, 5);
        tier(MachineConfig.NODE_STANDARD, 50000, 300, 12.0, 10);
        tier(MachineConfig.NODE_ADVANCED, 200000, 900, 19.0, 20);
        assertEquals(8.0, MachineConfig.MATRIX_CAPACITY_PER_LEVEL.get());
        assertEquals(60.0, MachineConfig.MATRIX_BANDWIDTH_PER_LEVEL_SQUARED.get());
        assertEquals(24.0, MachineConfig.MATRIX_RANGE_PER_SQRT_LEVEL.get());
        assertEquals(1000, MachineConfig.SOLAR_BUFFER.get());
        assertEquals(3.0, MachineConfig.SOLAR_DAY_RATE.get());
        assertEquals(0.6, MachineConfig.SOLAR_RAIN_RATE.get());
        assertEquals(100.0, MachineConfig.SOLAR_BANDWIDTH.get());
        assertEquals(20000, MachineConfig.WIND_BUFFER.get());
        assertEquals(7.5, MachineConfig.windRate(70), 1e-9);
        assertEquals(11.25, MachineConfig.windRate(115), 1e-9);
        assertEquals(15.0, MachineConfig.windRate(160), 1e-9);
        assertEquals(300.0, MachineConfig.WIND_BANDWIDTH.get());
        assertEquals(6000, MachineConfig.PHASE_BUFFER.get());
        assertEquals(0.5, MachineConfig.PHASE_ENERGY_PER_MB.get());
        assertEquals(100, MachineConfig.PHASE_MAX_DRAIN.get());
        assertEquals(50.0, MachineConfig.PHASE_BANDWIDTH.get());
        assertEquals(2000, MachineConfig.CAT_BUFFER.get());
        assertEquals(500.0, MachineConfig.CAT_GENERATION.get());
        assertEquals(200.0, MachineConfig.CAT_BANDWIDTH.get());
        assertEquals(2000.0, MachineConfig.FUSOR_BUFFER.get());
        assertEquals(50.0, MachineConfig.FUSOR_BANDWIDTH.get());
        assertEquals(120, MachineConfig.FUSOR_PROCESS_TIME.get());
        assertEquals(12.0, MachineConfig.FUSOR_ENERGY_PER_TICK.get());
        assertEquals(3000.0, MachineConfig.FORMER_BUFFER.get());
        assertEquals(50.0, MachineConfig.FORMER_BANDWIDTH.get());
        assertEquals(60, MachineConfig.FORMER_PROCESS_TIME.get());
        assertEquals(13.3, MachineConfig.FORMER_ENERGY_PER_TICK.get());
        assertEquals(2000, MachineConfig.BRIDGE_BUFFER_IF.get());
        assertEquals(100.0, MachineConfig.BRIDGE_BANDWIDTH.get());
        assertEquals(10_000, MachineConfig.INTERFERER_BUFFER.get());
        assertEquals(50, MachineConfig.INTERFERER_BANDWIDTH.get());
        assertEquals(100, MachineConfig.interfererCost(100));
        assertEquals(10_000, MachineConfig.interfererCost(10_000));
    }

    @Test
    void machinesReadTheConfigInsteadOfLiterals() throws Exception {
        Path root = Path.of("src/main/java/com/mohistmc/academy/world/block/entity");
        assertTrue(Files.readString(root.resolve("ImagFusorBlockEntity.java")).contains("recipe.processTime()"));
        assertTrue(Files.readString(root.resolve("MetalFomerBlockEntity.java")).contains("current.processTime()"));
        assertTrue(Files.readString(root.resolve("BaseNodeBlockEntity.java")).contains("tier().maxEnergy.get()"));
        assertTrue(Files.readString(root.resolve("MatrixBlockEntity.java")).contains("MATRIX_BANDWIDTH_PER_LEVEL_SQUARED"));
    }

    private static void tier(MachineConfig.NodeTier tier, int energy, int bandwidth, double range, int connections) {
        assertEquals(energy, tier.maxEnergy.get());
        assertEquals(bandwidth, tier.bandwidth.get());
        assertEquals(range, tier.range.get());
        assertEquals(connections, tier.maxConnections.get());
    }
}
