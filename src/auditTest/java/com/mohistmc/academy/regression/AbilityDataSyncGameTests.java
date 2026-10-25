package com.mohistmc.academy.regression;

import com.mohistmc.academy.network.*;
import com.mohistmc.academy.skill.*;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder("academy_data_sync")
@PrefixGameTestTemplate(false)
public final class AbilityDataSyncGameTests {
    @GameTest(template = "empty", batch = "data_sync", timeoutTicks = 100)
    public static void idleOwnerEmitsNoRepeatedAbilitySnapshot(GameTestHelper h) {
        Peer peer = new Peer(h);
        var data = seed(peer.player);
        data.syncFullTo(peer.player);
        var initial = peer.drain();
        h.assertTrue(initial.size() == 1 && initial.getFirst() instanceof SyncAbilityDataPacket,
                "Initial registered channel did not emit exactly one complete snapshot");
        h.runAfterDelay(45, () -> {
            try {
                var idle = peer.drain();
                h.assertTrue(idle.isEmpty(), "Idle unchanged player kept sending ability data: " + AbilitySyncWire.summary(idle));
                LogUtils.getLogger().info("DATA_SYNC_IDLE_PASS ticks=45 {}", AbilitySyncWire.summary(idle));
                h.succeed();
            } finally { peer.close(); }
        });
    }

    @GameTest(template = "empty", batch = "data_sync", timeoutTicks = 120)
    public static void actualRecoverySendsOnlyRuntimeDeltasAndMeasuresBytes(GameTestHelper h) {
        Peer peer = new Peer(h), observer = new Peer(h);
        var data = seed(peer.player);
        for (int i = 0; i < 160; i++) data.markObtained("minecraft:audit_item_" + i);
        for (int i = 0; i < 100; i++) data.addLoadedMedia("audit_media_" + i);
        data.setCurrentCp(100);
        data.setCurrentOverload(90);
        data.setCooldown("arc_gen", 200);
        data.syncFullTo(peer.player);
        var initial = (SyncAbilityDataPacket)peer.drain().getFirst();
        PlayerAbilityData client = PlayerAbilityData.fromSyncTag(initial.data());
        observer.drain();
        var legacyTag = initial.data().copy();
        legacyTag.remove("cp_recovery_delay"); legacyTag.remove("overload_recovery_delay");
        int oldThreeSnapshots = AbilitySyncWire.bytes(new SyncAbilityDataPacket(legacyTag)) * 3;
        h.runAfterDelay(60, () -> {
            try {
                List<CustomPacketPayload> packets = peer.drain();
                h.assertTrue(packets.size() >= 10 && packets.size() <= 13,
                        "Expected bounded 5-tick runtime updates over 60 natural player ticks: " + packets.size());
                for (var packet : packets) {
                    h.assertTrue(packet instanceof SyncAbilityRuntimePacket, "Recovery resent full/configuration state");
                    var delta = ((SyncAbilityRuntimePacket)packet).data();
                    h.assertTrue(!delta.contains("learned") && !delta.contains("presets")
                            && !delta.contains("obtained") && !delta.contains("loaded_media")
                            && !delta.contains("proficiency"), "Unchanged collections leaked into a runtime delta");
                    client.applyRuntimeSyncTag(delta);
                }
                h.assertTrue(client.getCurrentCp() > 100 && client.getCurrentOverload() < 90
                                && client.getCooldownTicks("arc_gen") < 160 && client.getCooldownTicks("arc_gen") >= 130,
                        "Runtime deltas did not carry real regeneration and cooldown ticking");
                h.assertTrue(client.getObtainedItems().size() == 160 && client.getLoadedMedia().size() == 100,
                        "Runtime application overwrote profile collections");
                h.assertTrue(observer.drain().isEmpty(), "Owner ability state leaked to another player");
                int actual = packets.stream().mapToInt(AbilitySyncWire::bytes).sum();
                h.assertTrue(actual < oldThreeSnapshots, "Rich-profile runtime bytes did not improve over prior 20-tick full snapshots");
                LogUtils.getLogger().info("DATA_SYNC_ACTIVE_PASS ticks=60 {} legacySameProfileThreeSnapshotsBytes={} transportFramingExcluded=true",
                        AbilitySyncWire.summary(packets), oldThreeSnapshots);
                h.succeed();
            } finally { peer.close(); observer.close(); }
        });
    }

    @GameTest(template = "empty", batch = "data_sync")
    public static void profileMutationAndRuntimeResetPreserveEachOther(GameTestHelper h) {
        try (Peer peer = new Peer(h)) {
            var data = seed(peer.player);
            data.setCurrentCp(500); data.setCooldown("arc_gen", 90); data.setProficiency("arc_gen", .25F);
            data.syncFullTo(peer.player);
            var client = PlayerAbilityData.fromSyncTag(((SyncAbilityDataPacket)peer.drain().getFirst()).data());
            data.installApp("audit_app"); data.addLoadedMedia("audit_song"); data.activateTutorial("audit_tutorial");
            data.setSlot(2, 1, "arc_gen"); data.addTeleportLocation(new PlayerAbilityData.TeleportLocation(
                    "Home", "minecraft:overworld", 1, 64, 2));
            data.syncTo(peer.player);
            var changed = peer.drain();
            h.assertTrue(changed.size() == 2 && changed.get(0) instanceof SyncAbilityProfilePacket
                            && changed.get(1) instanceof SyncAbilityRuntimePacket,
                    "Profile change did not emit ordered configuration and runtime state");
            client = client.withProfileSyncTag(((SyncAbilityProfilePacket)changed.get(0)).data());
            client.applyRuntimeSyncTag(((SyncAbilityRuntimePacket)changed.get(1)).data());
            h.assertTrue(client.hasApp("audit_app") && client.hasLoadedMedia("audit_song")
                            && client.hasActivatedTutorial("audit_tutorial") && "arc_gen".equals(client.getPreset(2).getSlot(1))
                            && client.getTeleportLocations().size() == 1 && client.getCurrentCp() == 500
                            && client.getCooldownTicks("arc_gen") == 90 && client.getProficiency("arc_gen") == .25F,
                    "Profile merge lost runtime or configuration state");
            data.clearCooldowns(); data.setProficiency("arc_gen", .5F); data.setCurrentPreset(2); data.syncTo(peer.player);
            var cleared = peer.drain();
            h.assertTrue(cleared.size() == 1 && cleared.getFirst() instanceof SyncAbilityRuntimePacket,
                    "Progress/preset/cooldown changes resent configuration");
            client.applyRuntimeSyncTag(((SyncAbilityRuntimePacket)cleared.getFirst()).data());
            h.assertTrue(!client.isOnCooldown("arc_gen") && client.getProficiency("arc_gen") == .5F
                    && client.getCurrentPresetIndex() == 2 && client.hasApp("audit_app"), "Explicit runtime removals were lost");
            data.syncTo(peer.player);
            h.assertTrue(peer.drain().isEmpty(), "Repeated identical explicit synchronization was not suppressed");
            data.resendRuntimeTo(peer.player);
            h.assertTrue(peer.drain().size() == 1, "Bounded stale-UI correction could not resend unchanged runtime");
            data.setPlayerLevel(3); data.learnSkill("brain_course"); data.syncTo(peer.player);
            var course = peer.drain();
            h.assertTrue(course.size() == 2 && course.get(0) instanceof SyncAbilityProfilePacket
                    && course.get(1) instanceof SyncAbilityRuntimePacket, "Level/course update was not a profile/runtime pair");
            client = client.withProfileSyncTag(((SyncAbilityProfilePacket)course.get(0)).data());
            client.applyRuntimeSyncTag(((SyncAbilityRuntimePacket)course.get(1)).data());
            h.assertTrue(client.getPlayerLevel() == 3 && client.hasLearnedSkill("brain_course")
                    && client.getMaxCp() == data.getMaxCp() && client.getCurrentCp() == data.getCurrentCp(),
                    "Changed level/course derived resource cap or refill diverged on client");
            data.reset(); data.syncTo(peer.player);
            var reset = peer.drain();
            h.assertTrue(reset.size() == 2 && reset.get(0) instanceof SyncAbilityProfilePacket
                    && reset.get(1) instanceof SyncAbilityRuntimePacket, "Reset did not replace profile and runtime");
            client = client.withProfileSyncTag(((SyncAbilityProfilePacket)reset.get(0)).data());
            client.applyRuntimeSyncTag(((SyncAbilityRuntimePacket)reset.get(1)).data());
            h.assertTrue(!client.hasAbility() && client.getPlayerLevel() == 0 && client.getLearnedSkills().isEmpty()
                    && client.getMaxCp() == PlayerAbilityData.BASE_MAX_CP && client.getCurrentCp() == data.getCurrentCp()
                    && client.getProficiency("arc_gen") == 0 && client.hasApp("audit_app"),
                    "Cleared category/learned/proficiency did not replace previous client state");
            h.succeed();
        }
    }

    @GameTest(template = "empty", batch = "data_sync")
    public static void actualDimensionAndDiskReconnectEachSendFullState(GameTestHelper h) {
        GameProfile profile;
        try (Peer peer = new Peer(h)) {
            var data = seed(peer.player); data.installApp("audit_reconnect"); data.setProficiency("arc_gen", .375F);
            data.syncFullTo(peer.player); peer.drain();
            var nether = peer.player.server.getLevel(Level.NETHER);
            h.assertTrue(nether != null, "Nether fixture missing");
            peer.player.teleportTo(nether, peer.player.getX(), 200, peer.player.getZ(), Set.of(), 0, 0);
            var dimension = peer.drain();
            h.assertTrue(dimension.stream().anyMatch(p -> p instanceof SyncAbilityDataPacket),
                    "Actual dimension event failed to send complete state");
            profile = peer.player.getGameProfile();
        }
        try (Peer peer = new Peer(h, profile, false)) {
            var reconnect = peer.drain();
            var complete = reconnect.stream().filter(p -> p instanceof SyncAbilityDataPacket)
                    .map(p -> (SyncAbilityDataPacket)p).findFirst().orElseThrow();
            var restored = PlayerAbilityData.fromSyncTag(complete.data());
            h.assertTrue(restored.hasApp("audit_reconnect") && restored.getProficiency("arc_gen") == .375F,
                    "Disk reload/login snapshot omitted previously saved profile or progress");
            h.succeed();
        }
    }

    @GameTest(template = "empty", batch = "data_sync", timeoutTicks = 100)
    public static void actualDeathRespawnReplacesSnapshotAndKeepsPersistentProfile(GameTestHelper h) {
        Peer peer = new Peer(h);
        var data = seed(peer.player); data.installApp("audit_respawn"); data.setProficiency("arc_gen", .625F);
        data.syncFullTo(peer.player); peer.drain();
        h.runAfterDelay(65, () -> {
            try {
                peer.player.invulnerableTime = 0; peer.player.setHealth(.01F);
                h.assertTrue(peer.player.hurt(peer.player.damageSources().genericKill(), 10) && !peer.player.isAlive(),
                        "Ordinary player lethal damage did not cause death");
                var previous = peer.player;
                previous.connection.handleClientCommand(new ServerboundClientCommandPacket(
                        ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                var replacement = previous.server.getPlayerList().getPlayer(previous.getUUID());
                h.assertTrue(replacement != null && replacement != previous && replacement.connection.player == replacement,
                        "Native respawn handler did not rebind its listener to the replacement player");
                peer.player = replacement;
                var packets = peer.drain();
                h.assertTrue(packets.stream().anyMatch(p -> p instanceof SyncAbilityDataPacket),
                        "Respawn emitted no complete payload after flush: " + AbilitySyncWire.summary(packets)
                                + " channel=" + SafePayloadSender.canSend(peer.player, SyncAbilityDataPacket.TYPE));
                var full = packets.stream().filter(p -> p instanceof SyncAbilityDataPacket)
                        .map(p -> (SyncAbilityDataPacket)p).findFirst().orElseThrow();
                var client = PlayerAbilityData.fromSyncTag(full.data());
                h.assertTrue(client.hasApp("audit_respawn") && client.getProficiency("arc_gen") == .625F,
                        "Respawn full synchronization lost copied attachment data");
                h.succeed();
            } finally { peer.close(); }
        });
    }

    static PlayerAbilityData seed(ServerPlayer player) {
        var data = new PlayerAbilityData();
        data.setCurrentAbility(AbilityCategory.ELECTROMASTER); data.setPlayerLevel(5);
        data.learnSkill("arc_gen"); data.setSlot(0, 0, "arc_gen"); data.setTerminalInstalled(true);
        data.setCurrentCp(data.getMaxCp()); data.setCurrentOverload(0);
        player.setData(AcademyAttachments.PLAYER_ABILITY, data);
        return data;
    }

    private static final class Peer implements AutoCloseable, GameTestListener {
        ServerPlayer player;
        final Connection connection;
        final EmbeddedChannel channel;
        boolean closed;
        Peer(GameTestHelper h) { this(h, new GameProfile(UUID.randomUUID(), "Sync" + UUID.randomUUID().toString().substring(0, 8)), true); }
        Peer(GameTestHelper h, GameProfile profile, boolean drainLogin) {
            var cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false, ConnectionType.NEOFORGE);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), profile, cookie.clientInformation());
            connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
            var pos = h.absolutePos(new BlockPos(2, 2, 2)); player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            player.server.getConnection().getConnections().add(connection);
            h.assertTrue(SafePayloadSender.canSend(player, SyncAbilityRuntimePacket.TYPE), "New runtime payload channel unregistered");
            h.testInfo.addListener(this);
            if (drainLogin) drain();
        }
        List<CustomPacketPayload> drain() {
            // Server ticks suspend flushing until their end. Observe actual queued sends only after
            // the connection's normal flush seam, including a respawn performed inside this tick.
            channel.runPendingTasks(); connection.flushChannel(); channel.runPendingTasks();
            var result = new ArrayList<CustomPacketPayload>(); Object value;
            while ((value = channel.readOutbound()) != null) {
                if (value instanceof ClientboundCustomPayloadPacket packet && AbilitySyncWire.relevant(packet.payload())) {
                    AbilitySyncWire.bytes(packet.payload()); result.add(packet.payload());
                }
                io.netty.util.ReferenceCountUtil.release(value);
            }
            return result;
        }
        @Override public void close() {
            if (closed) return; closed = true;
            var server = player.server; var id = player.getUUID();
            try {
                var current = server.getPlayerList().getPlayer(id);
                if (current != null) server.getPlayerList().remove(current);
            } finally {
                server.getConnection().getConnections().remove(connection);
                try { connection.disconnect(net.minecraft.network.chat.Component.literal("Ability sync test complete")); }
                finally { channel.finishAndReleaseAll(); }
            }
            if (server.getPlayerList().getPlayer(id) != null || server.getConnection().getConnections().contains(connection))
                throw new IllegalStateException("Ability sync fixture retained registered player/connection " + id);
            for (var level : server.getAllLevels())
                if (level.getEntity(id) != null)
                    throw new IllegalStateException("Ability sync fixture retained entity " + id + " in " + level.dimension().location());
            LogUtils.getLogger().info("DATA_SYNC_CLEANUP_PASS uuid={} playerAndConnectionAbsent=true allLevelsEntityAbsent=true", id);
        }
        @Override public void testStructureLoaded(GameTestInfo info) {}
        @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) { close(); }
    }
}
