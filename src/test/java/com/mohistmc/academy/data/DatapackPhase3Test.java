package com.mohistmc.academy.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Behavioral and contract tests for Datapack Phase 3: PhaseGenFuel DataMap and metal entity tag. */
class DatapackPhase3Test {
    @Test
    void phaseGenFuelCodecValidatesBoundsAndOptionalDrainCap() {
        var validFull = JsonParser.parseString("{\"energy_per_mb\": 1.25, \"max_drain_per_tick\": 200}");
        var decoded = AcademyDataMaps.PhaseGenFuel.CODEC.parse(JsonOps.INSTANCE, validFull).getOrThrow();
        assertEquals(1.25, decoded.energyPerMb(), 1e-9);
        assertEquals(Optional.of(200), decoded.maxDrainPerTick());

        var validMinimal = JsonParser.parseString("{\"energy_per_mb\": 0.5}");
        var minimal = AcademyDataMaps.PhaseGenFuel.CODEC.parse(JsonOps.INSTANCE, validMinimal).getOrThrow();
        assertEquals(0.5, minimal.energyPerMb(), 1e-9);
        assertTrue(minimal.maxDrainPerTick().isEmpty());

        var zeroEnergy = JsonParser.parseString("{\"energy_per_mb\": 0}");
        assertTrue(AcademyDataMaps.PhaseGenFuel.CODEC.parse(JsonOps.INSTANCE, zeroEnergy).isError());

        var negativeDrain = JsonParser.parseString("{\"energy_per_mb\": 1.0, \"max_drain_per_tick\": 0}");
        assertTrue(AcademyDataMaps.PhaseGenFuel.CODEC.parse(JsonOps.INSTANCE, negativeDrain).isError());
    }

    @Test
    void phaseGenAndMetalEntityHooksAreWiredToDatapackRegistries() throws Exception {
        String phaseGen = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/world/block/entity/PhaseGenBlockEntity.java"));
        assertTrue(phaseGen.contains("AcademyDataMaps.phaseGenFuel"));

        String metal = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/skill/ability/electromaster/ElectromasterMetalTargets.java"));
        assertTrue(metal.contains("METAL_ENTITY_TAG"));
        assertTrue(Files.exists(Path.of("src/main/resources/data/academy/tags/entity_type/metal.json")));
    }
}
