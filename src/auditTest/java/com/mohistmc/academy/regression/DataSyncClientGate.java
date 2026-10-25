package com.mohistmc.academy.regression;

import com.mohistmc.academy.network.*;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real integrated-server mutations, actual client payload handling, and production-codec byte counts. */
@EventBusSubscriber(modid = "academy_audit", value = Dist.CLIENT)
public final class DataSyncClientGate {
    private static final List<String> EVIDENCE = new ArrayList<>();
    private static final ConcurrentLinkedQueue<CustomPacketPayload> RECEIVED = new ConcurrentLinkedQueue<>();
    private static int ticks, phase, waited;
    private static boolean worldRequested, initialized, done;
    private static CompletableFuture<Void> operation;
    private static volatile Throwable recorderFailure;
    private static volatile int baselineFullBytes;
    private static volatile boolean saveRequested, saveCompleted;
    private static volatile Throwable saveFailure;
    private static Path checkpointPath;
    // Owned by the integrated-server thread; the completion flag publishes its final evidence.
    private static int settleTicks, stableSaveTicks;
    private static List<Field> holderMaps;
    private static final StringBuilder SAVE_EVIDENCE = new StringBuilder();

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.dataSyncClient") || done) return;
        var mc = Minecraft.getInstance();
        try {
            if (++ticks > 6000) throw new IllegalStateException("data sync client timeout phase=" + phase);
            if (Files.exists(mc.gameDirectory.toPath().resolve("stop-request.txt"))) throw new IllegalStateException("runner requested stop");
            if (!worldRequested && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen && mc.getOverlay() == null) {
                worldRequested = true;
                mc.createWorldOpenFlows().createFreshLevel("NativeDataSync31",
                        new net.minecraft.world.level.LevelSettings("Native data sync isolated", GameType.SURVIVAL,
                                false, net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(),
                                net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(20261025L, false, false),
                        registries -> registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(),
                        new net.minecraft.client.gui.screens.TitleScreen());
                return;
            }
            if (mc.player == null || mc.level == null || mc.getOverlay() != null) return;
            if (!initialized) {
                mc.setScreen(null);
                var pipeline = mc.getConnection().getConnection().channel().pipeline();
                pipeline.addBefore("packet_handler", "academy_data_sync_audit", new ChannelInboundHandlerAdapter() {
                    @Override public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
                        try {
                            if (message instanceof ClientboundCustomPayloadPacket packet && AbilitySyncWire.relevant(packet.payload())) {
                                AbilitySyncWire.bytes(packet.payload()); RECEIVED.add(packet.payload());
                            }
                        } catch (Throwable failure) { recorderFailure = failure; }
                        super.channelRead(ctx, message);
                    }
                });
                initialized = true;
            }
            if (recorderFailure != null) throw new IllegalStateException("payload recorder failed", recorderFailure);
            if (phase >= 8) {
                if (MachineInventoryClientChecks.tick(mc, EVIDENCE)) {
                    if (!saveRequested) {
                        mc.setScreen(null);
                        checkpointPath = mc.gameDirectory.toPath().resolve("academy-data-sync-save-checkpoint.txt");
                        saveRequested = true;
                    }
                    if (saveFailure != null) throw new IllegalStateException("Native save checkpoint failed", saveFailure);
                    if (saveCompleted) {
                        EVIDENCE.add("PASS native save checkpoint after 20 consecutive ready server ticks; "
                                + "details=academy-data-sync-save-checkpoint.txt");
                        finish(mc, null);
                    }
                }
                return;
            }
            if (operation == null) {
                RECEIVED.clear(); waited = 0;
                int requested = phase;
                operation = server(mc, player -> prepare(requested, player));
            }
            if (++waited > 600) throw new IllegalStateException("client phase did not converge: " + phase);
            if (!operation.isDone()) return;
            operation.getNow(null);
            PlayerAbilityData client = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
            if (!accepted(mc, client)) return;
            var actual = new ArrayList<>(RECEIVED);
            if (phase == 0 || phase == 5 || phase == 6 || phase == 7)
                require(actual.stream().anyMatch(p -> p instanceof SyncAbilityDataPacket), "Lifecycle phase omitted full packet");
            if (phase == 1) require(actual.isEmpty(), "Idle client received redundant ability state");
            if (phase == 2) {
                require(actual.size() >= 4 && actual.stream().allMatch(p -> p instanceof SyncAbilityRuntimePacket),
                        "Dynamic resource phase resent profile or full data");
                int bytes = actual.stream().mapToInt(AbilitySyncWire::bytes).sum();
                require(bytes < baselineFullBytes * 2, "Dynamic two-second sample exceeded previous full snapshots");
                EVIDENCE.add("MEASURE activeClientTicks=40 legacyTwoFullPayloadBytes=" + baselineFullBytes * 2
                        + " actual=" + AbilitySyncWire.summary(actual) + " excludesTransportFraming=true");
            }
            if (phase == 3) require(actual.stream().anyMatch(p -> p instanceof SyncAbilityProfilePacket)
                    && actual.stream().noneMatch(p -> p instanceof SyncAbilityDataPacket), "Configuration change used wrong packet class");
            EVIDENCE.add("PASS player sync phase=" + phase + " " + description(phase) + " " + AbilitySyncWire.summary(actual));
            phase++; operation = null;
        } catch (Throwable failure) { finish(mc, failure); }
    }

    /** Keep normal server ticks running until native chunk generation and saving are ready.
     * Calling stopServer during a cold dimension's generation can repeatedly reschedule an
     * unfinished holder in the vanilla unload queue, which prevents that work from advancing. */
    @SubscribeEvent
    public static void prepareNativeSave(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.dataSyncClient") || !saveRequested || saveCompleted || saveFailure != null) return;
        try {
            int holders = 0, generationRefs = 0, notReady = 0, queuedTasks = 0;
            boolean lightWork = false;
            if (holderMaps == null) {
                holderMaps = new ArrayList<>();
                for (String name : List.of("updatingChunkMap", "visibleChunkMap", "pendingUnloads")) {
                    Field field = ChunkMap.class.getDeclaredField(name);
                    field.setAccessible(true); holderMaps.add(field);
                }
            }
            for (var level : event.getServer().getAllLevels()) {
                var source = level.getChunkSource();
                Set<ChunkHolder> unique = Collections.newSetFromMap(new IdentityHashMap<>());
                for (Field field : holderMaps)
                    for (Object value : ((Map<?, ?>)field.get(source.chunkMap)).values()) unique.add((ChunkHolder)value);
                for (var holder : unique) {
                    holders++; generationRefs += holder.getGenerationRefCount();
                    if (!holder.isReadyForSaving()) notReady++;
                }
                queuedTasks += source.getPendingTasksCount();
                lightWork |= source.getLightEngine().hasLightWork();
            }
            boolean ready = generationRefs == 0 && notReady == 0 && queuedTasks == 0 && !lightWork;
            stableSaveTicks = ready ? stableSaveTicks + 1 : 0;
            ++settleTicks;
            String sample = "serverTick=" + event.getServer().getTickCount() + " settleTicks=" + settleTicks
                    + " holders=" + holders + " generationRefs=" + generationRefs + " notSaveReady=" + notReady
                    + " queuedTasks=" + queuedTasks + " lightWork=" + lightWork + " stableTicks=" + stableSaveTicks;
            if (settleTicks == 1 || settleTicks % 20 == 0 || stableSaveTicks == 20) {
                SAVE_EVIDENCE.append(sample).append('\n');
                com.mojang.logging.LogUtils.getLogger().info("DATA_SYNC_SAVE_READINESS {}", sample);
                Files.writeString(checkpointPath, "status=WAITING\n" + SAVE_EVIDENCE);
            }
            if (settleTicks > 1200) throw new IllegalStateException("Chunk readiness timeout: " + sample);
            if (stableSaveTicks == 20) {
                require(event.getServer().saveEverything(false, true, true), "Native saveEverything did not report a saved level");
                SAVE_EVIDENCE.append("nativeSaveEverythingCompleted=true flush=true\n");
                Files.writeString(checkpointPath, "status=PASS\n" + SAVE_EVIDENCE);
                saveCompleted = true;
            }
        } catch (Throwable failure) {
            saveFailure = failure;
            try { Files.writeString(checkpointPath, "status=FAIL\n" + SAVE_EVIDENCE + "failure=" + failure); }
            catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
        }
    }

    private static void prepare(int stage, ServerPlayer player) {
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        switch (stage) {
            case 0 -> {
                data = AbilityDataSyncGameTests.seed(player);
                for (int i = 0; i < 160; i++) data.markObtained("minecraft:audit_item_" + i);
                for (int i = 0; i < 100; i++) data.addLoadedMedia("audit_media_" + i);
                player.setNoGravity(true);
                var legacy = data.toSyncTag(); legacy.remove("cp_recovery_delay"); legacy.remove("overload_recovery_delay");
                baselineFullBytes = AbilitySyncWire.bytes(new SyncAbilityDataPacket(legacy));
                data.syncFullTo(player);
            }
            case 1 -> { /* Observe natural ticks with full CP and no cooldown. */ }
            case 2 -> {
                require(data.tryConsumeDynamic(900, 35), "Actual resource transaction rejected fixture");
                data.setCooldown("arc_gen", 160); data.syncTo(player);
            }
            case 3 -> {
                data.installApp("audit_client_profile"); data.addLoadedMedia("audit_client_song");
                data.activateTutorial("audit_client_tutorial"); data.setSlot(2, 1, "arc_gen"); data.setCurrentPreset(2);
                data.setProficiency("arc_gen", .625F); data.syncTo(player);
            }
            case 4 -> {
                CompoundTag reset = new CompoundTag(); reset.putFloat("cp", data.getMaxCp()); reset.putFloat("overload", 0);
                reset.putInt("cp_recovery_delay", 0); reset.putInt("overload_recovery_delay", 0);
                reset.put("cooldowns", new CompoundTag()); data.applyRuntimeSyncTag(reset); data.syncTo(player);
            }
            case 5, 6 -> {
                var target = player.server.getLevel(stage == 5 ? Level.NETHER : Level.OVERWORLD);
                require(target != null, "Dimension fixture missing");
                player.teleportTo(target, 0.5, 200, 0.5, Set.of(), 0, 0);
            }
            case 7 -> {
                player.setNoGravity(false); player.invulnerableTime = 0; player.setHealth(.01F);
                require(player.hurt(player.damageSources().genericKill(), 10) && !player.isAlive(), "Real death fixture failed");
                player.connection.handleClientCommand(new ServerboundClientCommandPacket(
                        ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                var replacement = player.server.getPlayerList().getPlayer(player.getUUID());
                require(replacement != null && replacement != player && replacement.connection.player == replacement,
                        "Native respawn handler did not rebind the active player connection");
            }
            default -> throw new IllegalStateException("unknown stage");
        }
    }

    private static boolean accepted(Minecraft mc, PlayerAbilityData data) {
        return switch (phase) {
            case 0 -> data.getObtainedItems().size() == 160 && data.getLoadedMedia().size() == 100
                    && data.hasLearnedSkill("arc_gen") && data.getPlayerLevel() == 5;
            case 1 -> waited >= 60;
            case 2 -> waited >= 40 && data.getCurrentCp() < data.getMaxCp() && data.getCurrentOverload() > 0
                    && data.getCooldownTicks("arc_gen") > 0 && data.getCooldownTicks("arc_gen") < 160;
            case 3 -> data.hasApp("audit_client_profile") && data.hasLoadedMedia("audit_client_song")
                    && data.hasActivatedTutorial("audit_client_tutorial") && "arc_gen".equals(data.getPreset(2).getSlot(1))
                    && data.getCurrentPresetIndex() == 2 && data.getProficiency("arc_gen") == .625F;
            case 4 -> waited >= 6 && !data.isOnCooldown("arc_gen") && data.getCurrentCp() == data.getMaxCp()
                    && data.getCurrentOverload() == 0 && data.hasApp("audit_client_profile");
            case 5 -> mc.level.dimension().equals(Level.NETHER) && data.hasApp("audit_client_profile")
                    && data.getProficiency("arc_gen") == .625F;
            case 6 -> mc.level.dimension().equals(Level.OVERWORLD) && data.hasApp("audit_client_profile")
                    && data.getCurrentPresetIndex() == 2;
            case 7 -> mc.player.isAlive() && mc.player.getHealth() > 1 && mc.player.getY() < 150
                    && data.hasApp("audit_client_profile") && data.getProficiency("arc_gen") == .625F;
            default -> false;
        };
    }

    private static String description(int stage) {
        return switch (stage) {
            case 0 -> "full state delivered to real client";
            case 1 -> "60 idle ticks emitted zero ability payloads";
            case 2 -> "paid resources and cooldown delivered without profile collections";
            case 3 -> "profile, presets and proficiency remain complete";
            case 4 -> "empty cooldown delta clears previous state";
            case 5 -> "actual Nether transfer full synchronization";
            case 6 -> "actual Overworld return full synchronization";
            case 7 -> "actual lethal damage and native respawn full synchronization";
            default -> "unknown";
        };
    }

    private static CompletableFuture<Void> server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer(); require(server != null, "Integrated server unavailable");
        var id = mc.player.getUUID();
        return server.submit(() -> {
            var player = server.getPlayerList().getPlayer(id); require(player != null, "Server player unavailable");
            action.accept(player);
        });
    }
    private static void finish(Minecraft mc, Throwable failure) {
        done = true;
        try {
            if (failure != null) { EVIDENCE.add("FAIL " + failure); failure.printStackTrace(); }
            Files.writeString(mc.gameDirectory.toPath().resolve("academy-data-sync-client.txt"),
                    "status=" + (failure == null ? "PASS" : "FAIL") + "\n" + String.join("\n", EVIDENCE));
        } catch (Exception writeFailure) { writeFailure.printStackTrace(); }
        mc.stop();
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
