package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AcademyAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Changed resource/progress fields only; does not contain skills, presets, tutorial or terminal lists. */
public record SyncAbilityRuntimePacket(CompoundTag data) implements CustomPacketPayload {
    public static final Type<SyncAbilityRuntimePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "sync_ability_runtime"));
    public static final StreamCodec<ByteBuf, SyncAbilityRuntimePacket> STREAM_CODEC =
            ByteBufCodecs.COMPOUND_TAG.map(SyncAbilityRuntimePacket::new, SyncAbilityRuntimePacket::data);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(SyncAbilityRuntimePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var data = context.player().getData(AcademyAttachments.PLAYER_ABILITY);
            data.applyRuntimeSyncTag(packet.data());
            com.mohistmc.academy.client.ClientPacketBridge.abilityDataSynced(data);
        });
    }
}
