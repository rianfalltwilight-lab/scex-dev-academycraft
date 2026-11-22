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

/** Replaces infrequent configuration while retaining the latest runtime state. */
public record SyncAbilityProfilePacket(CompoundTag data) implements CustomPacketPayload {
    public static final Type<SyncAbilityProfilePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "sync_ability_profile"));
    public static final StreamCodec<ByteBuf, SyncAbilityProfilePacket> STREAM_CODEC =
            ByteBufCodecs.COMPOUND_TAG.map(SyncAbilityProfilePacket::new, SyncAbilityProfilePacket::data);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(SyncAbilityProfilePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            var updated = player.getData(AcademyAttachments.PLAYER_ABILITY).withProfileSyncTag(packet.data());
            player.setData(AcademyAttachments.PLAYER_ABILITY, updated);
            com.mohistmc.academy.client.ClientPacketBridge.abilityDataSynced(updated);
        });
    }
}
