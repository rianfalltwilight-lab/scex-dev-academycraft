package com.mohistmc.academy.data;

import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client: the merged JSON of one {@link SyncedJsonData}. */
public record DataSyncPacket(String id, String json) implements CustomPacketPayload {
    public static final Type<DataSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "data_sync"));
    public static final StreamCodec<ByteBuf, DataSyncPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), DataSyncPacket::id,
            ByteBufCodecs.stringUtf8(1 << 20), DataSyncPacket::json,
            DataSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DataSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Over the in-memory connection of singleplayer / the LAN host, the integrated server
            // shares these statics and has already installed exactly this data.
            if (context.player().level().isClientSide() && !context.connection().isMemoryConnection()) {
                AcademyDataManager.receive(packet.id(), packet.json());
            }
        });
    }
}
