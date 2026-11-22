package com.mohistmc.academy.data;

import com.mohistmc.academy.AcademyCraft;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jetbrains.annotations.Nullable;

/** Native 1.21.1 NeoForge DataMaps for modpack-driven machine and fluid customization. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class AcademyDataMaps {
    private AcademyDataMaps() {}

    /**
     * Custom fluid fuel entry for the Phase Generator ({@code data/<ns>/data_maps/fluid/phase_gen_fuels.json}).
     *
     * @param energyPerMb     IF generated per mB consumed (must be finite and > 0).
     * @param maxDrainPerTick Optional cap on mB consumed per tick; absent uses {@code machines.phaseGen.maxDrain}.
     */
    public record PhaseGenFuel(double energyPerMb, Optional<Integer> maxDrainPerTick) {
        private static final Codec<Double> POSITIVE_FINITE_DOUBLE = Codec.DOUBLE.validate(value ->
                Double.isFinite(value) && value > 0 && value <= 1_000_000.0
                        ? DataResult.success(value)
                        : DataResult.error(() -> "energy_per_mb must be finite and in (0, 1000000]"));

        public static final Codec<PhaseGenFuel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                POSITIVE_FINITE_DOUBLE.fieldOf("energy_per_mb").forGetter(PhaseGenFuel::energyPerMb),
                Codec.intRange(1, 100_000).optionalFieldOf("max_drain_per_tick").forGetter(PhaseGenFuel::maxDrainPerTick)
        ).apply(instance, PhaseGenFuel::new));
    }

    public static final DataMapType<Fluid, PhaseGenFuel> PHASE_GEN_FUELS = DataMapType
            .builder(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "phase_gen_fuels"),
                    Registries.FLUID, PhaseGenFuel.CODEC)
            .synced(PhaseGenFuel.CODEC, false)
            .build();

    @SubscribeEvent
    public static void register(RegisterDataMapTypesEvent event) {
        event.register(PHASE_GEN_FUELS);
    }

    public static @Nullable PhaseGenFuel phaseGenFuel(@Nullable Fluid fluid) {
        if (fluid == null) return null;
        return BuiltInRegistries.FLUID.wrapAsHolder(fluid).getData(PHASE_GEN_FUELS);
    }
}
