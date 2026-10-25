package com.mohistmc.academy.regression;

import com.mohistmc.academy.client.block.gui.MetalFomerGui;
import com.mohistmc.academy.client.gui.SettingsAppGui;
import com.mohistmc.academy.client.gui.SkillSlotGui;
import com.mohistmc.academy.client.gui.InfoArea;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.crafting.MetalFormerRecipes.Mode;
import com.mohistmc.academy.skill.*;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.entity.MetalFomerBlockEntity;
import com.mohistmc.academy.world.menu.MetalFomerMenu;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.glfw.GLFW;

/** Opt-in real client. Programmatic KeyboardHandler input, real packets, and native narration text; no human/audio claim. */
@EventBusSubscriber(modid = "academy_audit", value = Dist.CLIENT)
public final class AutomationUiClientGate {
    private static final String[] LANGUAGES = {"en_us", "zh_cn", "ja_jp"};
    private static final String[] HUD_LABELS = {"Show HUD", "显示 HUD", "HUDを表示"};
    private static final String[] INFO_KEYS = {"energy", "buffer", "liquid", "capacity", "information", "mode"};
    private static final String[][] INFO_LABELS = {
            {"Energy", "Buffer", "Liquid", "Capacity", "Information", "Mode"},
            {"能量", "缓存", "液体", "容量", "信息", "模式"},
            {"エネルギー", "バッファ", "液体", "容量", "情報", "モード"}
    };
    private static final List<String> EVIDENCE = new ArrayList<>();
    private static int ticks, combination, stage, stageTicks;
    private static boolean worldRequested, initialized, done, oldHud;
    private static int oldScale;
    private static String oldLanguage, oldOptionLanguage;
    private static CompletableFuture<Void> operation, reload;
    private static BlockPos metalPos;
    private static String captureName;
    private static boolean capturing;
    private static volatile boolean captured;
    private static volatile Throwable captureFailure;
    private static volatile boolean saveRequested, saveCompleted;
    private static volatile Throwable saveFailure;
    private static Path checkpointPath;
    private static int settleTicks, stableSaveTicks;
    private static List<Field> holderMaps;
    private static final StringBuilder SAVE_EVIDENCE = new StringBuilder();

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.automationUiClient") || done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (++ticks > 6000) throw new IllegalStateException("UI timeout combination=" + combination + " stage=" + stage);
            if (Files.exists(mc.gameDirectory.toPath().resolve("stop-request.txt"))) throw new IllegalStateException("runner requested shutdown");
            if (!worldRequested && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen && mc.getOverlay() == null) {
                worldRequested = true;
                mc.createWorldOpenFlows().createFreshLevel("AutomationUi32",
                        new net.minecraft.world.level.LevelSettings("Automation UI isolated", GameType.CREATIVE, false,
                                net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(),
                                net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(20261025L, false, false),
                        registries -> registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(),
                        new net.minecraft.client.gui.screens.TitleScreen());
                return;
            }
            if (mc.player == null || mc.level == null || mc.getOverlay() != null) return;
            if (captureFailure != null) throw new IllegalStateException("Screenshot failed", captureFailure);
            if (!initialized) {
                initialized = true;
                oldHud = ACConfig.Client.showHud(); oldScale = mc.options.guiScale().get();
                oldLanguage = mc.getLanguageManager().getSelected(); oldOptionLanguage = mc.options.languageCode;
                mc.setScreen(null);
                // A 1280x960 framebuffer permits effective scales 2 and 3, unlike the small default runner window.
                mc.getWindow().setWindowed(1280, 960);
                operation = server(mc, player -> {
                    var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
                    data.setCurrentAbility(AbilityCategory.ELECTROMASTER);
                    data.learnSkill("railgun"); data.learnSkill("arc_gen"); data.setCurrentPreset(0);
                    for (int i = 0; i < PlayerAbilityData.PRESET_COUNT; i++) data.getPreset(i).clearAll();
                    data.syncFullTo(player);
                    metalPos = player.blockPosition().offset(3, 1, 0);
                    require(player.serverLevel().isEmptyBlock(metalPos), "Machine fixture position must be empty");
                    player.serverLevel().setBlock(metalPos, AcademyBlocks.METAL_FORMER.get().defaultBlockState(), Block.UPDATE_ALL);
                });
                EVIDENCE.add("Input source=programmatic Minecraft.KeyboardHandler.keyPress (GLFW press/release), plus Screen mouse events. Narration=actual native TITLE/USAGE output; audio and manual human input not tested.");
                return;
            }
            if (operation != null) {
                if (!operation.isDone()) return;
                operation.getNow(null); operation = null;
            }
            if (!mc.player.getData(AcademyAttachments.PLAYER_ABILITY).hasLearnedSkill("railgun")) return;
            if (combination == 6) { finishChecks(mc); return; }
            if (++stageTicks > 250) throw new IllegalStateException("UI stage did not converge: combination=" + combination + " stage=" + stage);
            if (stageTicks < 4) return;
            runStage(mc);
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static void runStage(Minecraft mc) {
        PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        switch (stage) {
            case 0 -> {
                String language = LANGUAGES[combination / 2];
                if (!mc.getLanguageManager().getSelected().equals(language) && reload == null) {
                    mc.getLanguageManager().setSelected(language); mc.options.languageCode = language;
                    reload = mc.reloadResourcePacks(); return;
                }
                if (reload != null) {
                    if (!reload.isDone() || mc.getOverlay() != null) return;
                    reload.getNow(null); reload = null;
                }
                int requested = 2 + combination % 2;
                mc.options.guiScale().set(requested); mc.resizeDisplay();
                require(mc.getWindow().getGuiScale() == requested, "Effective GUI scale differs: requested=" + requested + " actual=" + mc.getWindow().getGuiScale());
                EVIDENCE.add("PASS locale=" + language + " requestedScale=" + requested + " actualScale=" + mc.getWindow().getGuiScale()
                        + " framebuffer=" + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()
                        + " gui=" + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight());
                mc.setScreen(new SettingsAppGui());
                require(Component.translatable("ac.settings.prop.showHud").getString().equals(HUD_LABELS[combination / 2]),
                        "Expected language was not loaded for the production setting label");
                AbstractWidget toggle = widgets(mc.screen).stream().filter(w -> w.getMessage().getString()
                        .startsWith(Component.translatable("ac.settings.prop.showHud").getString())).findFirst().orElseThrow();
                tabTo(mc, toggle);
                require(toggle.getY() >= (mc.screen.height - 200) / 2 + 29 && toggle.getY() + toggle.getHeight() <= (mc.screen.height + 200) / 2,
                        "Settings Tab did not scroll focused row into viewport");
                narration(toggle, "settings");
                boolean old = ACConfig.Client.showHud();
                press(mc, GLFW.GLFW_KEY_ENTER); require(ACConfig.Client.showHud() != old, "Enter did not toggle setting");
                press(mc, GLFW.GLFW_KEY_SPACE); require(ACConfig.Client.showHud() == old, "Space did not restore setting");
                click(mc, toggle, 0); require(ACConfig.Client.showHud() != old, "Mouse did not toggle setting");
                click(mc, toggle, 0); require(ACConfig.Client.showHud() == old, "Mouse did not restore setting");
                mc.screen.mouseScrolled(toggle.getX() + 4, toggle.getY() + 4, 0, -1000);
                require(mc.screen.getFocused() == null, "Wheel-scrolled hidden setting retained keyboard focus");
                press(mc, GLFW.GLFW_KEY_ENTER); press(mc, GLFW.GLFW_KEY_SPACE);
                require(ACConfig.Client.showHud() == old, "Keyboard activated a setting scrolled out of view");
                tabTo(mc, toggle);
                EVIDENCE.add("PASS " + tag() + " wheel hides focused setting; Enter/Space cause no hidden change; Tab restores visible focus");
                capture("settings"); next();
            }
            case 1 -> {
                if (!captureFinished()) return;
                mc.setScreen(new SkillSlotGui());
                tabTo(mc, widgets(mc.screen).get(1));
                press(mc, GLFW.GLFW_KEY_ENTER); next();
            }
            case 2 -> {
                if (data.getCurrentPresetIndex() != 1) return;
                AbstractWidget slot = widgets(mc.screen).get(4);
                tabTo(mc, slot); narration(slot, "skill-slot"); press(mc, GLFW.GLFW_KEY_SPACE);
                require(widgets(mc.screen).subList(0, 8).stream().noneMatch(w -> w.active), "Modal dropdown left base controls focusable");
                var choices = widgets(mc.screen).subList(8, widgets(mc.screen).size());
                require(choices.size() == 3, "Expected clear and two learned active skills");
                for (int i = 0; i < choices.size() + 2; i++) {
                    press(mc, GLFW.GLFW_KEY_TAB);
                    require(choices.contains(mc.screen.getFocused()), "Tab escaped modal dropdown");
                }
                AbstractWidget railgun = choices.stream().filter(w -> w.getMessage().getString().equals(
                        Component.translatable(SkillRegistry.getSkill(AbilityCategory.ELECTROMASTER, "railgun").getTranslationKey()).getString())).findFirst().orElseThrow();
                tabTo(mc, railgun); narration(railgun, "skill-choice"); capture("slots"); next();
            }
            case 3 -> { if (captureFinished()) { press(mc, GLFW.GLFW_KEY_ENTER); next(); } }
            case 4 -> {
                if (!"railgun".equals(data.getPreset(1).getSlot(0))) return;
                require(mc.screen.getFocused() == widgets(mc.screen).get(4), "Selecting a skill did not restore slot focus");
                press(mc, GLFW.GLFW_KEY_SPACE);
                require(((AbstractWidget) mc.screen.getFocused()).getMessage().getString().equals(Component.translatable("ac.skill_slot.clear").getString()),
                        "Clear is not the initial dropdown choice");
                narration((AbstractWidget) mc.screen.getFocused(), "skill-clear");
                press(mc, GLFW.GLFW_KEY_ENTER); next();
            }
            case 5 -> {
                if (data.getPreset(1).getSlot(0) != null) return;
                Screen screen = mc.screen;
                press(mc, GLFW.GLFW_KEY_ENTER); press(mc, GLFW.GLFW_KEY_ESCAPE);
                require(mc.screen == screen && widgets(screen).size() == 8 && screen.getFocused() == widgets(screen).get(4),
                        "Escape did not close only the dropdown and restore focus");
                EVIDENCE.add("PASS " + tag() + " settings Enter/Space/mouse; skill preset server ACK, modal Tab containment, slot select/clear server ACK, Escape focus return");
                operation = server(mc, player -> {
                    var level = player.serverLevel();
                    var machine = (MetalFomerBlockEntity) level.getBlockEntity(metalPos);
                    while (machine.getMode() != Mode.PLATE) machine.cycleMode(1);
                    var state = machine.getBlockState();
                    player.openMenu(state.getMenuProvider(level, metalPos), buffer -> buffer.writeBlockPos(metalPos));
                });
                next();
            }
            case 6 -> {
                if (!(mc.screen instanceof MetalFomerGui) || !(mc.player.containerMenu instanceof MetalFomerMenu menu) || menu.getMode() != Mode.PLATE) return;
                verifyInfoLabels();
                tabTo(mc, widgets(mc.screen).get(1)); narration((AbstractWidget) mc.screen.getFocused(), "metal-next");
                press(mc, GLFW.GLFW_KEY_ENTER); next();
            }
            case 7 -> {
                if (!(mc.player.containerMenu instanceof MetalFomerMenu menu) || menu.getMode() != Mode.INCISE) return;
                press(mc, GLFW.GLFW_KEY_SPACE); next();
            }
            case 8 -> {
                if (!(mc.player.containerMenu instanceof MetalFomerMenu menu) || menu.getMode() != Mode.ETCH) return;
                click(mc, widgets(mc.screen).getFirst(), 0); next();
            }
            case 9 -> {
                if (!(mc.player.containerMenu instanceof MetalFomerMenu menu) || menu.getMode() != Mode.INCISE) return;
                narration((AbstractWidget) mc.screen.getFocused(), "metal-previous"); capture("metal"); next();
            }
            case 10 -> {
                if (!captureFinished()) return;
                EVIDENCE.add("PASS " + tag() + " native mode buttons Enter -> INCISE, Space -> ETCH, mouse previous -> INCISE; real menu synchronized from server");
                mc.player.closeContainer();
                operation = server(mc, player -> { var ability = player.getData(AcademyAttachments.PLAYER_ABILITY); ability.setCurrentPreset(0); ability.syncFullTo(player); });
                combination++; stage = 0; stageTicks = 0;
            }
            default -> throw new IllegalStateException("Unknown stage");
        }
    }

    private static void next() { stage++; stageTicks = 0; }
    private static void verifyInfoLabels() {
        String[] expected = INFO_LABELS[combination / 2];
        for (int i = 0; i < INFO_KEYS.length; i++) {
            require(Component.translatable("ac.ui.info." + INFO_KEYS[i]).getString().equals(expected[i]),
                    "Information-card label not localized: " + INFO_KEYS[i]);
        }
        List<String> actualLegendLabels = List.of(InfoArea.histEnergy(0, 1).label(), InfoArea.histBuffer(0, 1).label(),
                InfoArea.histPhaseLiquid(0, 1).label(), InfoArea.histCapacity(0, 1).label());
        require(actualLegendLabels.equals(Arrays.asList(expected).subList(0, 4)), "Production information legends bypass localization");
        EVIDENCE.add("PASS " + tag() + " localized information-card labels=" + String.join(",", expected));
    }
    private static String tag() { return LANGUAGES[combination / 2] + "-scale" + (2 + combination % 2); }
    private static List<AbstractWidget> widgets(Screen screen) {
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();
    }
    private static void tabTo(Minecraft mc, AbstractWidget target) {
        for (int i = 0; i < 64 && mc.screen.getFocused() != target; i++) press(mc, GLFW.GLFW_KEY_TAB);
        require(mc.screen.getFocused() == target && target.isFocused(), "Native Tab could not reach " + target.getMessage().getString());
        require(target.getX() >= 0 && target.getY() >= 0 && target.getX() + target.getWidth() <= mc.screen.width
                && target.getY() + target.getHeight() <= mc.screen.height, "Focused control outside screen");
    }
    private static void press(Minecraft mc, int key) {
        long window = mc.getWindow().getWindow();
        mc.keyboardHandler.keyPress(window, key, 0, GLFW.GLFW_PRESS, 0);
        mc.keyboardHandler.keyPress(window, key, 0, GLFW.GLFW_RELEASE, 0);
    }
    private static void click(Minecraft mc, AbstractWidget widget, int button) {
        double x = widget.getX() + widget.getWidth() / 2.0, y = widget.getY() + widget.getHeight() / 2.0;
        require(mc.screen.mouseClicked(x, y, button), "Mouse event was not accepted");
        mc.screen.mouseReleased(x, y, button);
    }
    private static void narration(AbstractWidget widget, String control) {
        Map<NarratedElementType, String> spoken = new EnumMap<>(NarratedElementType.class);
        widget.updateNarration(new NarrationElementOutput() {
            @Override public void add(NarratedElementType type, NarrationThunk<?> text) {
                text.getText(value -> spoken.merge(type, value, (a, b) -> a + " " + b));
            }
            @Override public NarrationElementOutput nest() { return this; }
        });
        String title = spoken.getOrDefault(NarratedElementType.TITLE, "");
        require(title.contains(widget.getMessage().getString()) && !title.contains("ac.ui.") && !title.contains("ac.skill_slot."), "Missing localized button narration: " + title);
        require(!spoken.getOrDefault(NarratedElementType.USAGE, "").isBlank(), "Focused native button lacks usage narration");
        EVIDENCE.add("PASS narration " + tag() + " " + control + " title=" + title + " usage=" + spoken.get(NarratedElementType.USAGE));
    }
    private static void capture(String screen) {
        captureName = "automation-ui-" + tag() + "-" + screen + ".png";
        capturing = captured = false;
    }
    private static boolean captureFinished() {
        if (!captured) return false;
        EVIDENCE.add("PASS screenshot=" + captureName);
        captureName = null; captured = false; return true;
    }
    @SubscribeEvent
    public static void render(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("academy.automationUiClient") || done || captureName == null || capturing) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.screen == null) return;
        capturing = true;
        Screenshot.grab(mc.gameDirectory, captureName, mc.getMainRenderTarget(), message -> {
            if (message.getContents() instanceof TranslatableContents contents && contents.getKey().equals("screenshot.failure"))
                captureFailure = new IllegalStateException(message.getString());
            captured = true;
        });
    }
    private static void finishChecks(Minecraft mc) {
        if (!saveRequested) {
            if (reload == null && !mc.getLanguageManager().getSelected().equals(oldLanguage)) {
                mc.getLanguageManager().setSelected(oldLanguage); mc.options.languageCode = oldOptionLanguage;
                reload = mc.reloadResourcePacks(); return;
            }
            if (reload != null) {
                if (!reload.isDone() || mc.getOverlay() != null) return;
                reload.getNow(null); reload = null;
            }
            ACConfig.Client.SHOW_HUD.set(oldHud); mc.options.guiScale().set(oldScale); mc.resizeDisplay(); mc.setScreen(null);
            // Fixture cleanup completes on the server before the readiness request is published.
            operation = server(mc, player -> {
                player.closeContainer(); player.serverLevel().removeBlock(metalPos, false);
                checkpointPath = mc.gameDirectory.toPath().resolve("academy-automation-ui-save-checkpoint.txt");
                saveRequested = true;
            });
            return;
        }
        if (saveFailure != null) throw new IllegalStateException("Native save checkpoint failed", saveFailure);
        if (saveCompleted) { EVIDENCE.add("PASS native save checkpoint after 20 consecutive ready server ticks"); finish(mc, null); }
    }
    private static CompletableFuture<Void> server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        require(server != null, "Integrated server missing");
        return server.submit(() -> { var player = server.getPlayerList().getPlayer(id); require(player != null, "Server player missing"); action.accept(player); });
    }
    private static void finish(Minecraft mc, Throwable failure) {
        if (done) return; done = true;
        if (initialized) { ACConfig.Client.SHOW_HUD.set(oldHud); mc.options.guiScale().set(oldScale); mc.options.languageCode = oldOptionLanguage; }
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("academy-automation-ui-client.txt"),
                    "status=" + (failure == null ? "PASS" : "FAIL") + "\n" + String.join("\n", EVIDENCE)
                            + "\n" + (failure == null ? "" : "failure=" + failure));
        } catch (Exception writeFailure) { throw new IllegalStateException(writeFailure); }
        if (failure != null) com.mojang.logging.LogUtils.getLogger().error("AUTOMATION_UI_CLIENT failed", failure);
        mc.stop();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    // Normal server ticks must drain generation references before the native shutdown loop.
    @SubscribeEvent
    public static void prepareNativeSave(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.automationUiClient") || !saveRequested || saveCompleted || saveFailure != null) return;
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
                com.mojang.logging.LogUtils.getLogger().info("AUTOMATION_UI_SAVE_READINESS {}", sample);
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
