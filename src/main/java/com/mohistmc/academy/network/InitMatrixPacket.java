package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mohistmc.academy.world.block.entity.MatrixBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.mohistmc.academy.world.menu.MatrixMenu;
import com.mohistmc.academy.world.AcademyItems;

/**
 * 客户端→服务端：初始化矩阵网络。
 */
public record InitMatrixPacket(MenuActionToken actionToken, BlockPos pos, String ssid, String password) implements CustomPacketPayload {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final int MAX_SSID_LENGTH = NetworkInputLimits.SSID;
    private static final int MAX_PASSWORD_LENGTH = NetworkInputLimits.PASSWORD;

    public static final Type<InitMatrixPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "init_matrix"));

    public static final StreamCodec<ByteBuf, InitMatrixPacket> STREAM_CODEC =
            StreamCodec.composite(
                    MenuActionToken.STREAM_CODEC, InitMatrixPacket::actionToken,
                    BlockPos.STREAM_CODEC, InitMatrixPacket::pos,
                    ByteBufCodecs.stringUtf8(MAX_SSID_LENGTH), InitMatrixPacket::ssid,
                    ByteBufCodecs.stringUtf8(MAX_PASSWORD_LENGTH), InitMatrixPacket::password,
                    InitMatrixPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InitMatrixPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (!PayloadRateLimiter.allow(player.getUUID(), "matrix_init", player.serverLevel().getGameTime(), 20, 4)) return;
                boolean accepted = false;
                try {
                    if (!NetworkInputLimits.validRequired(packet.ssid(), MAX_SSID_LENGTH)
                            || !NetworkInputLimits.validOptional(packet.password(), MAX_PASSWORD_LENGTH)) return;
                    ServerLevel level = player.serverLevel();
                    if (!(player.containerMenu instanceof MatrixMenu menu)
                            || !packet.pos().equals(menu.pos) || !menu.stillValid(player) || !menu.acceptAction(packet.actionToken(), player)) return;
                    if (!com.mohistmc.academy.skill.ability.LoadedRaycast.isLoaded(level, packet.pos())
                            || player.distanceToSqr(packet.pos().getX() + 0.5, packet.pos().getY() + 0.5,
                            packet.pos().getZ() + 0.5) > 64.0
                            || !level.mayInteract(player, packet.pos())) return;
                    BlockEntity be = level.getBlockEntity(packet.pos());
                    if (be instanceof MatrixBlockEntity matrix) {
                        // 检查所有者权限
                        if (!matrix.canManage(player)) {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.academy.matrix.owner_only_init").withStyle(net.minecraft.ChatFormatting.RED));
                            return;
                        }
                        var networkState = WirelessSystem.reconcileMatrixNetwork(level, matrix);
                        if (networkState.active()) {
                            player.sendSystemMessage(
                                    networkState == WirelessSystem.MatrixNetworkState.RECOVERED
                                            ? net.minecraft.network.chat.Component.translatable("message.academy.matrix.recovered_from_save").withStyle(net.minecraft.ChatFormatting.GREEN)
                                            : net.minecraft.network.chat.Component.translatable("message.academy.matrix.already_initialized").withStyle(net.minecraft.ChatFormatting.RED));
                            return;
                        }
                        if (matrix.isInitialized()) {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.academy.matrix.recovery_failed").withStyle(net.minecraft.ChatFormatting.RED));
                            return;
                        }
                        int coreLevel = matrix.initializationCoreLevel();
                        if (!matrix.hasInitializationMaterials()) {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                                    "message.academy.matrix.init_materials", "1", "3")
                                    .withStyle(net.minecraft.ChatFormatting.RED));
                            return;
                        }

                        String oldSsid = matrix.getSSID();
                        String oldPassword = matrix.getPassword();
                        int oldCapacity = matrix.getCapacity();
                        double oldBandwidth = matrix.getBandwidth();
                        double oldRange = matrix.getRange();
                        boolean created = false;
                        try {
                            // WirelessNet snapshots matrix characteristics during creation. Stage
                            // them first, but do not publish the initialized flag until creation wins.
                            matrix.setSSID(packet.ssid());
                            matrix.setPassword(packet.password());
                            matrix.applyCoreLevel(coreLevel);
                            created = WirelessSystem.createNetwork(level, matrix, packet.ssid(), packet.password());
                            if (created) {
                                matrix.setInitialized(true);
                                // 1.12 treats the core and three plates as installed machine
                                // components. They remain in the four slots, keep the shields
                                // visible, and are returned with the stateful machine on break.
                                matrix.setChanged();
                            }
                        } catch (RuntimeException failure) {
                            WirelessSystem.removeNetwork(level, matrix);
                            restore(matrix, oldSsid, oldPassword, oldCapacity, oldBandwidth, oldRange);
                            LOGGER.error("Matrix network initialization rolled back at {}", packet.pos(), failure);
                            return;
                        }
                        accepted = created;
                        if (created) {
                            level.sendBlockUpdated(packet.pos(), matrix.getBlockState(), matrix.getBlockState(), 3);
                            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                                    "message.academy.matrix.init_success", packet.ssid())
                                    .withStyle(net.minecraft.ChatFormatting.GREEN));
                        } else {
                            restore(matrix, oldSsid, oldPassword, oldCapacity, oldBandwidth, oldRange);
                            level.sendBlockUpdated(packet.pos(), matrix.getBlockState(), matrix.getBlockState(), 3);
                            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.academy.matrix.init_failed").withStyle(net.minecraft.ChatFormatting.RED));
                        }
                    }
                } finally {
                    MatrixConfigPacket.reply(player, packet.actionToken(), packet.pos(), accepted);
                }
            }
        });
    }


    private static void restore(MatrixBlockEntity matrix, String ssid, String password,
                                int capacity, double bandwidth, double range) {
        matrix.setInitialized(false);
        matrix.setSSID(ssid);
        matrix.setPassword(password);
        matrix.setCapacity(capacity);
        matrix.setBandwidth(bandwidth);
        matrix.setRange(range);
    }
}
