package com.mohistmc.academy.regression;

import static com.mohistmc.academy.listener.ClientModListener.*;

import com.mohistmc.academy.client.ChargingHudOverlay;
import com.mohistmc.academy.client.TerminalInstallProgress;
import com.mohistmc.academy.client.MediaPlayerManager;
import com.mohistmc.academy.client.gui.*;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.skill.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.glfw.GLFW;

/** Opt-in isolated client; observes the actual native layer pipeline, never calls a renderer as a substitute. */
@EventBusSubscriber(modid = "academy_audit", value = Dist.CLIENT)
public final class HudLayersClientGate {
    private static final List<ResourceLocation> IDS = List.of(HUD_VECTOR_WAVE, HUD_SCREEN_MASK, HUD_CP_BAR,
            HUD_SKILL_HINTS, HUD_MEDIA_PLAYER, HUD_NOTIFICATIONS, HUD_CHARGING, HUD_TERMINAL_INSTALL);
    private static final String[] CASES = {"visible", "f1-hidden", "f1-restored", "cancel-cp", "cancel-hotbar",
            "hud-disabled", "individual-disabled", "screen-open", "screen-closed"};
    private static final List<String> EVIDENCE = new ArrayList<>();
    private static final Map<ResourceLocation, DrawCount> FRAME = new LinkedHashMap<>();
    private static final List<ResourceLocation> ORDER = new ArrayList<>();
    private static int ticks, scale = 2, scenario, scenarioTicks, wrapped;
    private static boolean worldRequested, initialized, prepared, done, observing, captureStarted;
    private static boolean oldHide, oldHud, oldCp, oldKeys, oldCharging;
    private static int oldScale;
    private static ResourceLocation canceled;
    private static CompletableFuture<Void> operation;
    private static volatile boolean captured;
    private static volatile Throwable failure;
    private static volatile boolean saveRequested, saveCompleted;
    private static volatile Throwable saveFailure;
    private static Path checkpointPath;
    private static int settleTicks, stableSaveTicks;
    private static List<Field> holderMaps;
    private static final StringBuilder SAVE_EVIDENCE = new StringBuilder();

    /** academy_audit declares AFTER academy, so these wrappers see the already registered production layers. */
    @EventBusSubscriber(modid = "academy_audit", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void wrap(RegisterGuiLayersEvent event) {
            if (!Boolean.getBoolean("academy.hudLayersClient")) return;
            for (ResourceLocation id : IDS) {
                event.wrapLayer(id, original -> (graphics, partialTick) -> {
                    if (!observing) { original.render(graphics, partialTick); return; }
                    try {
                        DrawCount count = FRAME.computeIfAbsent(id, unused -> new DrawCount());
                        count.calls++;
                        TrackingGraphics tracked = new TrackingGraphics(Minecraft.getInstance(), graphics, count);
                        var before = new org.joml.Matrix4f(tracked.pose().last().pose());
                        original.render(tracked, partialTick);
                        tracked.flush();
                        require(tracked.pose().clear(), "Layer leaked pose stack depth: " + id);
                        require(before.equals(tracked.pose().last().pose()), "Layer leaked pose transform: " + id);
                    } catch (Throwable problem) { failure = problem; }
                });
                wrapped++;
            }
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.hudLayersClient") || done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (failure != null) throw new IllegalStateException("Native HUD observation failed", failure);
            if (++ticks > 6000) throw new IllegalStateException("HUD gate timeout scale=" + scale + " scenario=" + scenario);
            if (Files.exists(mc.gameDirectory.toPath().resolve("stop-request.txt"))) throw new IllegalStateException("Runner requested shutdown");
            if (!worldRequested && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                require(wrapped == IDS.size(), "Expected exactly eight production wrappers, got " + wrapped);
                worldRequested = true;
                mc.createWorldOpenFlows().createFreshLevel("HudLayers33",
                        new net.minecraft.world.level.LevelSettings("Native HUD layers isolated", GameType.CREATIVE, false,
                                net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(),
                                net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(20261025L, false, false),
                        registries -> registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(),
                        new TitleScreen());
                return;
            }
            if (mc.player == null || mc.level == null || mc.getOverlay() != null) return;
            if (!initialized) {
                initialized = true;
                oldHide = mc.options.hideGui; oldScale = mc.options.guiScale().get();
                oldHud = ACConfig.Client.showHud(); oldCp = ACConfig.Client.showCpBar();
                oldKeys = ACConfig.Client.showKeyHints(); oldCharging = ACConfig.Client.showChargingHud();
                mc.setScreen(null); mc.options.hideGui = false; mc.getWindow().setWindowed(1280, 960);
                var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
                require(server != null, "Integrated server missing");
                operation = server.submit(() -> {
                    var player = server.getPlayerList().getPlayer(id); require(player != null, "Server player missing");
                    var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
                    data.setCurrentAbility(AbilityCategory.VECMANIP); data.learnSkill("vec_reflection");
                    data.setSlot(0, 0, "vec_reflection"); data.setCurrentPreset(0); data.setAbilityActive(true); data.syncFullTo(player);
                });
                EVIDENCE.add("Input=programmatic native KeyboardHandler F1 press/release; fixtures=integrated-server ability plus presentation APIs and an audit-only seeded ripple. Counts wrap actual registered render calls; no manual renderer invocation.");
                return;
            }
            if (operation != null) {
                if (!operation.isDone()) return;
                operation.getNow(null); operation = null;
            }
            var data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
            if (!data.hasLearnedSkill("vec_reflection") || !data.isAbilityActive()) return;
            if (scale > 3) { finishChecks(mc); return; }
            if (!prepared) prepare(mc);
            if (++scenarioTicks > 240) throw new IllegalStateException("Scenario did not converge: " + label() + " counts=" + FRAME);
            if (scenario != 7) refreshPresentation(mc);
            if (captured) {
                prepared = captured = captureStarted = observing = false;
                canceled = null;
                scenario++; if (scenario == CASES.length) { scenario = 0; scale++; }
            }
        } catch (Throwable problem) { finish(mc, problem); }
    }

    private static void prepare(Minecraft mc) throws Exception {
        prepared = true; scenarioTicks = 0; captureStarted = captured = false;
        mc.setScreen(null);
        mc.options.guiScale().set(scale); mc.resizeDisplay();
        require(mc.getWindow().getGuiScale() == scale, "Effective GUI scale mismatch: " + mc.getWindow().getGuiScale());
        ACConfig.Client.SHOW_HUD.set(scenario != 5);
        ACConfig.Client.SHOW_CP_BAR.set(scenario != 6);
        ACConfig.Client.SHOW_KEY_HINTS.set(scenario != 6);
        ACConfig.Client.SHOW_CHARGING_HUD.set(scenario != 6);
        canceled = scenario == 3 ? HUD_CP_BAR : scenario == 4 ? VanillaGuiLayers.HOTBAR : null;
        if (mc.options.hideGui != (scenario == 1)) pressF1(mc);
        require(mc.options.hideGui == (scenario == 1), "Native F1 failed to change visibility");
        ChargingHudOverlay.setChargingState(20, 60, "vec_reflection");
        CPBarOverlay.setShowNumbers(true);
        TerminalInstallProgress.start();
        NotifyOverlay.resetClientSession(); NotifyOverlay.notify("HUD layer audit", "Named layers / native input");
        if (!MediaPlayerManager.isPlaying()) MediaPlayerManager.play("only_my_railgun");
        seedRipple(mc);
        if (scenario == 7) mc.setScreen(new SettingsAppGui());
        EVIDENCE.add("START " + label() + " effectiveScale=" + mc.getWindow().getGuiScale() + " framebuffer="
                + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight());
    }

    private static void refreshPresentation(Minecraft mc) throws Exception {
        ChargingHudOverlay.setChargingState(20, 60, "vec_reflection");
        if (scenarioTicks % 40 == 0) {
            TerminalInstallProgress.start();
            NotifyOverlay.resetClientSession(); NotifyOverlay.notify("HUD layer audit", "Named layers / native input");
            if (!MediaPlayerManager.isPlaying()) MediaPlayerManager.play("only_my_railgun");
        }
        if (((List<?>) field(LegacyVectorWaveOverlay.class, "RIPPLES").get(null)).isEmpty()) seedRipple(mc);
    }

    /** Deterministic visual fixture only; ripple rendering/age and native dispatch remain production code. */
    private static void seedRipple(Minecraft mc) throws Exception {
        Class<?> type = Class.forName(LegacyVectorWaveOverlay.class.getName() + "$Ripple");
        var constructor = type.getDeclaredConstructor(float.class, float.class, float.class); constructor.setAccessible(true);
        Object ripple = constructor.newInstance(mc.getWindow().getGuiScaledWidth() * .25f,
                mc.getWindow().getGuiScaledHeight() * .65f, 110f);
        field(type, "age").setFloat(ripple, .6f);
        @SuppressWarnings("unchecked") List<Object> ripples = (List<Object>) field(LegacyVectorWaveOverlay.class, "RIPPLES").get(null);
        ripples.clear(); ripples.add(ripple);
        field(LegacyVectorWaveOverlay.class, "activeSkill").set(null, "vec_reflection");
        field(LegacyVectorWaveOverlay.class, "lastFrameNanos").setLong(null, System.nanoTime());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beginFrame(RenderGuiEvent.Pre event) {
        if (!Boolean.getBoolean("academy.hudLayersClient")) return;
        FRAME.clear(); ORDER.clear();
        observing = initialized && prepared && !captureStarted && scenarioTicks >= 12 && !done;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void layerPre(RenderGuiLayerEvent.Pre event) {
        if (!Boolean.getBoolean("academy.hudLayersClient") || !observing) return;
        ORDER.add(event.getName());
        if (event.getName().equals(canceled)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void rendered(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("academy.hudLayersClient") || !observing || captureStarted || done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (failure != null) throw new IllegalStateException("Layer rendering failed", failure);
            verifyOrder();
            for (ResourceLocation id : IDS) {
                DrawCount count = FRAME.getOrDefault(id, new DrawCount());
                int expectedCalls = id.equals(canceled) ? 0 : 1;
                require(count.calls == expectedCalls, "Duplicate/missing native callback for " + id + ": " + count);
                boolean hidden = hidden(id);
                if (hidden) require(count.draws() == 0, "Hidden/canceled layer still drew: " + id + " " + count);
                else if (count.draws() == 0) return; // Wait for actual sound/resource startup and mask fade-in.
            }
            if (!hidden(HUD_CP_BAR) && !hidden(HUD_NOTIFICATIONS)) {
                DrawCount cp = FRAME.get(HUD_CP_BAR), notification = FRAME.get(HUD_NOTIFICATIONS);
                require(cp.maxTextBottom > 0 && notification.minFillY > cp.maxTextBottom,
                        "Notification obscures the actual CP/OL text: cp=" + cp + " notification=" + notification);
            }
            if (scenario == 1) require(ChargingHudOverlay.isCharging("vec_reflection") && TerminalInstallProgress.isInstalling()
                    && MediaPlayerManager.isPlaying(), "F1 test lost positive presentation state");
            if (scenario == 7) require(((List<?>) field(LegacyVectorWaveOverlay.class, "RIPPLES").get(null)).isEmpty(),
                    "Opening a screen failed to reset the vector ripple presentation");
            EVIDENCE.add("PASS " + label() + " native order, calls, draw suppression and pose balance: " + FRAME);
            captureStarted = true;
            Screenshot.grab(mc.gameDirectory, "hud-layers-" + label() + ".png", mc.getMainRenderTarget(), message -> {
                if (message.getContents() instanceof TranslatableContents t && t.getKey().equals("screenshot.failure"))
                    failure = new IllegalStateException(message.getString());
                captured = true;
            });
        } catch (Throwable problem) { failure = problem; }
    }

    private static boolean hidden(ResourceLocation id) {
        if (scenario == 1 || scenario == 7 || id.equals(canceled)) return true;
        if (scenario == 5) return Set.of(HUD_CP_BAR, HUD_SKILL_HINTS, HUD_MEDIA_PLAYER, HUD_NOTIFICATIONS, HUD_CHARGING).contains(id);
        return scenario == 6 && Set.of(HUD_CP_BAR, HUD_SKILL_HINTS, HUD_CHARGING).contains(id);
    }
    private static void verifyOrder() {
        List<ResourceLocation> expected = List.of(HUD_VECTOR_WAVE, VanillaGuiLayers.CAMERA_OVERLAYS,
                VanillaGuiLayers.HOTBAR, HUD_SCREEN_MASK, HUD_CP_BAR, HUD_SKILL_HINTS, HUD_MEDIA_PLAYER,
                HUD_NOTIFICATIONS, VanillaGuiLayers.JUMP_METER, VanillaGuiLayers.SAVING_INDICATOR, HUD_CHARGING, HUD_TERMINAL_INSTALL);
        int previous = -1;
        for (ResourceLocation id : expected) {
            int current = ORDER.indexOf(id);
            require(current > previous && ORDER.lastIndexOf(id) == current, "Missing, duplicate or misordered layer " + id + " order=" + ORDER);
            previous = current;
        }
    }
    private static String label() { return "scale" + scale + "-" + CASES[scenario]; }
    private static void pressF1(Minecraft mc) {
        require(mc.screen == null, "F1 must run in world");
        long window = mc.getWindow().getWindow();
        mc.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_F1, 0, GLFW.GLFW_PRESS, 0);
        mc.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_F1, 0, GLFW.GLFW_RELEASE, 0);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name); result.setAccessible(true); return result;
    }
    private static void finishChecks(Minecraft mc) throws Exception {
        if (!saveRequested) {
            observing = false; mc.setScreen(null); canceled = null;
            checkpointPath = mc.gameDirectory.toPath().resolve("academy-hud-layers-save-checkpoint.txt"); saveRequested = true;
        }
        // Keep positive transient state alive until the real disconnect exercises teardown.
        scenarioTicks++;
        refreshPresentation(mc);
        if (saveFailure != null) throw new IllegalStateException("Native save checkpoint failed", saveFailure);
        if (!saveCompleted) return;
        TerminalInstallProgress.start();
        NotifyOverlay.notify("Disconnect fixture", "Must be cleared by native teardown");
        seedRipple(mc);
        CPBarOverlay.setShowNumbers(true);
        if (!MediaPlayerManager.isPlaying()) MediaPlayerManager.play("only_my_railgun");
        require(ChargingHudOverlay.isCharging("vec_reflection") && TerminalInstallProgress.isInstalling()
                && MediaPlayerManager.getCurrentTrack() != null, "Teardown lacked active fixtures");
        // Match PauseScreen: close the connection before waiting for integrated-server teardown.
        mc.level.disconnect();
        mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        mc.setScreen(new TitleScreen());
        require(mc.player == null && mc.level == null, "Disconnect retained client world");
        require(!ChargingHudOverlay.isCharging("vec_reflection") && !TerminalInstallProgress.isInstalling()
                && !MediaPlayerManager.isPlaying(), "Disconnect retained charge/install/media state");
        require(((List<?>) field(NotifyOverlay.class, "notifications").get(null)).isEmpty(), "Disconnect retained notifications");
        require(((List<?>) field(LegacyVectorWaveOverlay.class, "RIPPLES").get(null)).isEmpty(), "Disconnect retained ripples");
        require(field(ScreenMaskOverlay.class, "a").getDouble(null) == 0 && !field(CPBarOverlay.class, "showNumbers").getBoolean(null),
                "Disconnect retained mask/numeric-display state");
        EVIDENCE.add("PASS native save checkpoint and real disconnect: charge/install/media/notifications/ripples/mask/numeric hint reset; client world released");
        finish(mc, null);
    }
    private static void finish(Minecraft mc, Throwable problem) {
        if (done) return; done = true; observing = false; canceled = null;
        if (initialized) {
            mc.options.hideGui = oldHide; mc.options.guiScale().set(oldScale);
            ACConfig.Client.SHOW_HUD.set(oldHud); ACConfig.Client.SHOW_CP_BAR.set(oldCp);
            ACConfig.Client.SHOW_KEY_HINTS.set(oldKeys); ACConfig.Client.SHOW_CHARGING_HUD.set(oldCharging);
        }
        try { Files.writeString(mc.gameDirectory.toPath().resolve("academy-hud-layers-client.txt"),
                "status=" + (problem == null ? "PASS" : "FAIL") + "\n" + String.join("\n", EVIDENCE)
                        + "\n" + (problem == null ? "" : "failure=" + problem)); }
        catch (Exception writeFailure) { throw new IllegalStateException(writeFailure); }
        if (problem != null) com.mojang.logging.LogUtils.getLogger().error("HUD_LAYERS_CLIENT failed", problem);
        mc.stop();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static final class DrawCount {
        int calls, fills, text, blits;
        int minFillY = Integer.MAX_VALUE, maxTextBottom;
        int draws() { return fills + text + blits; }
        @Override public String toString() { return "calls=" + calls + "/fills=" + fills + "/text=" + text + "/blits=" + blits
                + "/minFillY=" + minFillY + "/textBottom=" + maxTextBottom; }
    }
    private static final class TrackingGraphics extends GuiGraphics {
        private final DrawCount count;
        TrackingGraphics(Minecraft mc, GuiGraphics source, DrawCount count) {
            super(mc, source.bufferSource()); this.count = count;
            pose().last().pose().set(source.pose().last().pose()); pose().last().normal().set(source.pose().last().normal());
        }
        @Override public void fill(RenderType type, int x1, int y1, int x2, int y2, int z, int color) {
            count.fills++; count.minFillY = Math.min(count.minFillY, Math.min(y1, y2));
            super.fill(type, x1, y1, x2, y2, z, color);
        }
        @Override public int drawString(Font font, String value, int x, int y, int color, boolean shadow) {
            count.text++; count.maxTextBottom = Math.max(count.maxTextBottom, y + font.lineHeight);
            return super.drawString(font, value, x, y, color, shadow);
        }
        @Override public int drawString(Font font, Component value, int x, int y, int color, boolean shadow) {
            count.text++; count.maxTextBottom = Math.max(count.maxTextBottom, y + font.lineHeight);
            return super.drawString(font, value, x, y, color, shadow);
        }
        @Override public void blit(ResourceLocation texture, int x, int y, int width, int height,
                                   float u, float v, int sourceWidth, int sourceHeight, int textureWidth, int textureHeight) {
            count.blits++; super.blit(texture, x, y, width, height, u, v, sourceWidth, sourceHeight, textureWidth, textureHeight);
        }
        @Override public void blit(ResourceLocation texture, int x, int y, int z, float u, float v,
                                   int width, int height, int textureWidth, int textureHeight) {
            count.blits++; super.blit(texture, x, y, z, u, v, width, height, textureWidth, textureHeight);
        }
    }

    // Reuse the normal-tick save-readiness checkpoint validated by the 0.0.31 and 0.0.32 client gates.
    @SubscribeEvent
    public static void prepareNativeSave(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.hudLayersClient") || !saveRequested || saveCompleted || saveFailure != null) return;
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
                com.mojang.logging.LogUtils.getLogger().info("HUD_LAYERS_SAVE_READINESS {}", sample);
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

}
