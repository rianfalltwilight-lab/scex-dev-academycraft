package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C state for the frequency-transmitter UI/targeting overlay. */
public record FreqTransmitterStatePacket(UUID nonce, int state, int sourceKind,
                                         Component sourceLabel, Component message)
        implements CustomPacketPayload {
    public static final int SELECT_SOURCE = 0;
    public static final int PASSWORD_REQUIRED = 1;
    public static final int SELECT_TARGET = 2;
    public static final int CLOSED = 3;

    public static final Type<FreqTransmitterStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "freq_transmitter_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FreqTransmitterStatePacket> STREAM_CODEC =
            StreamCodec.ofMember((packet, buf) -> {
                buf.writeLong(packet.nonce.getMostSignificantBits());
                buf.writeLong(packet.nonce.getLeastSignificantBits());
                buf.writeByte(packet.state);
                buf.writeByte(packet.sourceKind);
                ComponentSerialization.STREAM_CODEC.encode(buf, packet.sourceLabel);
                ComponentSerialization.STREAM_CODEC.encode(buf, packet.message);
            }, buf -> new FreqTransmitterStatePacket(
                    new UUID(buf.readLong(), buf.readLong()),
                    buf.readUnsignedByte(), buf.readUnsignedByte(),
                    ComponentSerialization.STREAM_CODEC.decode(buf),
                    ComponentSerialization.STREAM_CODEC.decode(buf)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FreqTransmitterStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().isClientSide()) {
                com.mohistmc.academy.client.ClientPacketBridge.freqTransmitter(packet);
            }
        });
    }
}
