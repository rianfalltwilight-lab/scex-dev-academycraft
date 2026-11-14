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

/**
 * Immediate authoritative result and resource snapshot for the open developer tree.
 * {@code inProgress} marks a progress report of a still-running session; the client must not
 * infer it from the (display-only, translatable) reason text.
 */
public record DevLearningResultPacket(UUID nonce, boolean success, int energy, int maxEnergy,
                                      Component reason, boolean inProgress) implements CustomPacketPayload {
    public static final Type<DevLearningResultPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "dev_learning_result"));
    /** Server-built translatable reason; S2C only, so the trusted component codec is safe. */
    private static final StreamCodec<RegistryFriendlyByteBuf,Component> TEXT = ComponentSerialization.TRUSTED_STREAM_CODEC;
    public static final StreamCodec<RegistryFriendlyByteBuf,DevLearningResultPacket> STREAM_CODEC = StreamCodec.ofMember(
            (p,b)->{b.writeLong(p.nonce.getMostSignificantBits());b.writeLong(p.nonce.getLeastSignificantBits());
                b.writeBoolean(p.success);b.writeInt(p.energy);b.writeInt(p.maxEnergy);TEXT.encode(b,p.reason);
                b.writeBoolean(p.inProgress);},
            b->new DevLearningResultPacket(new UUID(b.readLong(),b.readLong()),b.readBoolean(),b.readInt(),b.readInt(),
                    TEXT.decode(b),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(DevLearningResultPacket p, IPayloadContext c){c.enqueueWork(()->{
        if(c.player().level().isClientSide()) com.mohistmc.academy.client.gui.SkillTreeGui.acceptServerResult(p);
    });}
}
