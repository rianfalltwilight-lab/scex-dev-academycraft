package com.mohistmc.academy.regression;

import com.mohistmc.academy.network.*;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Counts actual emitted payloads, with their production codec size including the payload ID.
 * Does not claim TCP framing/compression bytes for the in-memory client connection. */
final class AbilitySyncWire {
    static boolean relevant(CustomPacketPayload payload) {
        return payload instanceof SyncAbilityDataPacket || payload instanceof SyncAbilityProfilePacket
                || payload instanceof SyncAbilityRuntimePacket;
    }
    static int bytes(CustomPacketPayload payload) {
        var buffer = Unpooled.buffer();
        try {
            ResourceLocation.STREAM_CODEC.encode(buffer, payload.type().id());
            if (payload instanceof SyncAbilityDataPacket full) SyncAbilityDataPacket.STREAM_CODEC.encode(buffer, full);
            else if (payload instanceof SyncAbilityProfilePacket profile) SyncAbilityProfilePacket.STREAM_CODEC.encode(buffer, profile);
            else if (payload instanceof SyncAbilityRuntimePacket runtime) SyncAbilityRuntimePacket.STREAM_CODEC.encode(buffer, runtime);
            else throw new IllegalArgumentException("not an ability payload");
            int size = buffer.readableBytes();
            ResourceLocation.STREAM_CODEC.decode(buffer);
            CustomPacketPayload decoded = payload instanceof SyncAbilityDataPacket ? SyncAbilityDataPacket.STREAM_CODEC.decode(buffer)
                    : payload instanceof SyncAbilityProfilePacket ? SyncAbilityProfilePacket.STREAM_CODEC.decode(buffer)
                    : SyncAbilityRuntimePacket.STREAM_CODEC.decode(buffer);
            if (!payload.equals(decoded) || buffer.isReadable()) throw new IllegalStateException("production codec round trip failed");
            return size;
        } finally { buffer.release(); }
    }
    static String summary(List<CustomPacketPayload> packets) {
        return "full=" + packets.stream().filter(p -> p instanceof SyncAbilityDataPacket).count()
                + ", profile=" + packets.stream().filter(p -> p instanceof SyncAbilityProfilePacket).count()
                + ", runtime=" + packets.stream().filter(p -> p instanceof SyncAbilityRuntimePacket).count()
                + ", payloadBytes=" + packets.stream().mapToInt(AbilitySyncWire::bytes).sum();
    }
}
