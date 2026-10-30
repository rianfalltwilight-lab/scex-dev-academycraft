package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Owner-only idle developer energy snapshot; never a learning result or nonce renewal. */
public record DevEnergyPacket(UUID nonce, int energy, int maxEnergy) implements CustomPacketPayload {
    public static final Type<DevEnergyPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "dev_energy"));
    public static final StreamCodec<ByteBuf, DevEnergyPacket> STREAM_CODEC = StreamCodec.ofMember(
            (p, b) -> { b.writeLong(p.nonce.getMostSignificantBits()); b.writeLong(p.nonce.getLeastSignificantBits());
                b.writeInt(p.energy); b.writeInt(p.maxEnergy); },
            b -> new DevEnergyPacket(new UUID(b.readLong(), b.readLong()), b.readInt(), b.readInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(DevEnergyPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().isClientSide())
                com.mohistmc.academy.client.gui.SkillTreeGui.acceptServerEnergy(packet);
        });
    }
}
