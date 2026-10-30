package com.mohistmc.academy.world.item;

import com.mohistmc.academy.AcademyCraft;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Persistent item values use vanilla stack persistence, equality and menu synchronization. */
public final class AcademyDataComponents {
    public static final DeferredRegister<DataComponentType<?>> TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, AcademyCraft.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY =
            TYPES.register("energy", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(0, Integer.MAX_VALUE))
                    .networkSynchronized(ByteBufCodecs.VAR_INT).build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ExtraItemData.TeleportTarget>> TELEPORT_TARGET =
            TYPES.register("teleport_target", () -> DataComponentType.<ExtraItemData.TeleportTarget>builder()
                    .persistent(ExtraItemData.TeleportTarget.CODEC)
                    .networkSynchronized(ExtraItemData.TeleportTarget.STREAM_CODEC).build());

    private AcademyDataComponents() {}
}
