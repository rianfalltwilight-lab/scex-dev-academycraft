package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.ability.vecmanip.StormWingEffect;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Movement keys for an active Storm Wing.
 *
 * <p>Vanilla 1.21.1 only forwards {@code xxa}/{@code zza} to the server while the player
 * is riding, so the server-side flight controller cannot read WASD from the player itself.
 * The client reports the sign of each axis; the server only stores it for a live flight.
 */
public record StormWingInputPacket(byte forward, byte left) implements CustomPacketPayload {
    public static final Type<StormWingInputPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "storm_wing_input"));
    public static final StreamCodec<ByteBuf, StormWingInputPacket> STREAM_CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeByte(packet.forward);
                buf.writeByte(packet.left);
            },
            buf -> new StormWingInputPacket(buf.readByte(), buf.readByte()));

    public static StormWingInputPacket of(float forward, float left) {
        return new StormWingInputPacket(axis(forward), axis(left));
    }

    /** Collapses any impulse (or hostile byte) to -1, 0 or 1. */
    public static byte axis(float value) {
        return (byte) (value > 0 ? 1 : value < 0 ? -1 : 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StormWingInputPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !SkillInputSessionManager.isCurrentPlayer(player)) return;
            if (!PayloadRateLimiter.allow(player.getUUID(), "storm-wing-input",
                    player.serverLevel().getGameTime(), 1, 4)) return;
            StormWingEffect.acceptInput(player, axis(packet.forward), axis(packet.left));
        });
    }
}
