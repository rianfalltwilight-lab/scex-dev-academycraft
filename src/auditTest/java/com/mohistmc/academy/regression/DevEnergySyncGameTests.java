package com.mohistmc.academy.regression;

import com.mohistmc.academy.network.*;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.DevMachineType;
import com.mohistmc.academy.world.block.entity.DevNormalBlockEntity;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real ServerPlayer and production send path over NeoForge's registered-payload EmbeddedChannel setup. */
@GameTestHolder("academy_audit")
@PrefixGameTestTemplate(false)
public final class DevEnergySyncGameTests {
    private static final class Peer implements AutoCloseable {
        final ServerPlayer player;
        final Connection connection;
        final EmbeddedChannel channel;
        boolean closed;
        Peer(GameTestHelper h) {
            var profile = new GameProfile(UUID.randomUUID(), "DevAudit" + UUID.randomUUID().toString().substring(0, 7));
            var cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false, ConnectionType.NEOFORGE);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
            connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
            BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2)); player.setPos(pos.getX()+.5, pos.getY(), pos.getZ()+.5);
            player.server.getConnection().getConnections().add(connection);
            h.assertTrue(SafePayloadSender.canSend(player, DevEnergyPacket.TYPE), "Registered PLAY energy channel unavailable");
            snapshots();
        }
        List<DevEnergyPacket> snapshots() {
            channel.runPendingTasks();
            List<DevEnergyPacket> result = new ArrayList<>();
            Object value;
            while ((value = channel.readOutbound()) != null) {
                if (value instanceof ClientboundCustomPayloadPacket packet && packet.payload() instanceof DevEnergyPacket energy)
                    result.add(energy);
                io.netty.util.ReferenceCountUtil.release(value);
            }
            return result;
        }
        public void close() {
            if (closed) return; closed = true;
            player.server.getPlayerList().remove(player);
            player.server.getConnection().getConnections().remove(connection);
            channel.finishAndReleaseAll();
        }
    }
    private static void later(GameTestHelper h, Peer owner, Peer observer, Runnable action) {
        h.runAfterDelay(6, () -> {
            try { action.run(); }
            catch (RuntimeException | Error failure) { owner.close(); observer.close(); throw failure; }
        });
    }

    @GameTest(template = "empty", batch = "core_dev_energy", timeoutTicks = 60)
    public static void idleMachineChargeSyncsOnlyDirtyOwnerNonceAndStopsOnClose(GameTestHelper h) {
        Peer owner = new Peer(h), observer = new Peer(h);
        try {
            BlockPos pos = h.absolutePos(new BlockPos(4, 2, 2));
            h.getLevel().setBlockAndUpdate(pos, AcademyBlocks.DEV_NORMAL.get().defaultBlockState());
            var machine = (DevNormalBlockEntity)h.getLevel().getBlockEntity(pos);
            h.assertTrue(machine != null, "Developer BE fixture missing");
            machine.setEnergy(1_000);
            UUID first = DevLearningSessionManager.issue(owner.player, DevMachineType.NORMAL, Optional.of(pos));
            owner.snapshots(); observer.snapshots();
            h.assertTrue(machine.injectEnergy(125) == 0 && machine.getEnergyStored() == 1_125, "Actual idle charging failed");
            later(h, owner, observer, () -> {
                var packets = owner.snapshots();
                h.assertTrue(packets.size() == 1 && packets.getFirst().nonce().equals(first)
                        && packets.getFirst().energy() == 1_125 && packets.getFirst().maxEnergy() == 50_000,
                        "Idle dirty energy did not reach the owner through the registered payload channel");
                h.assertTrue(observer.snapshots().isEmpty(), "Idle energy leaked to an unrelated nearby player");
                later(h, owner, observer, () -> {
                    h.assertTrue(owner.snapshots().isEmpty(), "Unchanged energy produced repeated snapshots");
                    UUID current = DevLearningSessionManager.issue(owner.player, DevMachineType.NORMAL, Optional.of(pos));
                    DevLearningSessionManager.clear(owner.player.getUUID(), first);
                    machine.injectEnergy(75);
                    later(h, owner, observer, () -> {
                        var replacement = owner.snapshots();
                        h.assertTrue(replacement.size() == 1 && replacement.getFirst().nonce().equals(current)
                                && replacement.getFirst().energy() == 1_200, "Stale close revoked or corrupted a replacement view");
                        DevLearningSessionManager.clear(owner.player.getUUID(), current);
                        machine.injectEnergy(100);
                        later(h, owner, observer, () -> {
                            h.assertTrue(owner.snapshots().isEmpty() && observer.snapshots().isEmpty(),
                                    "Closed developer view kept emitting energy snapshots");
                            owner.close(); observer.close(); h.succeed();
                        });
                    });
                });
            });
        } catch (RuntimeException | Error failure) { owner.close(); observer.close(); throw failure; }
    }
}
