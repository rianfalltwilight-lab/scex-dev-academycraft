package com.mohistmc.academy.regression;

import com.mohistmc.academy.client.ChargingHudOverlay;
import com.mohistmc.academy.client.KeyInputHandler;
import com.mohistmc.academy.client.TerminalInstallProgress;
import com.mohistmc.academy.client.gui.AcademyColors;
import com.mohistmc.academy.client.gui.SettingsAppGui;
import com.mohistmc.academy.client.gui.SkillSlotGui;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillPreset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;

/** Real F1 input, production draw calls and resource reloads; opt-in isolated client only. */
@EventBusSubscriber(modid = "academy_audit", value = Dist.CLIENT)
public final class NativeCompatibilityClientGate {
    private static final String[] LANGUAGES = {"en_us", "zh_cn", "ja_jp"};
    // Independent expected player-visible labels, not a comparison of a translation with itself.
    private static final String[][] EXPECTED = {
            {"Key bindings", "Active", "Inactive", "No ability acquired", "Skill slots", "Preset 1", "Clear"},
            {"快捷键设置", "已激活", "未激活", "尚未获得能力", "技能槽", "预设 1", "清空"},
            {"キー設定", "発動中", "停止中", "能力未取得", "スキルスロット", "セット 1", "解除"}
    };
    private static final List<String> EVIDENCE = new ArrayList<>();
    private static int ticks, step;
    private static boolean worldRequested, initialized, prepared, rendered, done;
    private static boolean oldHideGui, oldShowHud, oldChargingHud;
    private static String oldLanguage, oldOptionLanguage;
    private static CompletableFuture<Void> reload;
    private static Screen captureScreen;
    private static volatile boolean captured;
    private static volatile Throwable captureFailure;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.nativeCompatibilityClient") || done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (++ticks > 6000) throw new IllegalStateException("native client gate timeout step=" + step);
            if (Files.exists(mc.gameDirectory.toPath().resolve("stop-request.txt")))
                throw new IllegalStateException("runner requested shutdown");
            if (!worldRequested && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen && mc.getOverlay() == null) {
                worldRequested = true;
                mc.createWorldOpenFlows().createFreshLevel("NativeCompatibility30",
                        new net.minecraft.world.level.LevelSettings("Native compatibility isolated", net.minecraft.world.level.GameType.CREATIVE,
                                false, net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(),
                                net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(20261025L, false, false),
                        registries -> registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(),
                        new net.minecraft.client.gui.screens.TitleScreen());
                return;
            }
            if (mc.player == null || mc.level == null) return;
            if (captureFailure != null) throw new IllegalStateException("screenshot failed", captureFailure);
            if (!initialized) {
                if (mc.getOverlay() != null) return;
                oldHideGui = mc.options.hideGui;
                oldShowHud = ACConfig.Client.SHOW_HUD.get();
                oldChargingHud = ACConfig.Client.SHOW_CHARGING_HUD.get();
                oldLanguage = mc.getLanguageManager().getSelected();
                oldOptionLanguage = mc.options.languageCode;
                initialized = true;
                mc.setScreen(null);
                mc.options.hideGui = false;
                ACConfig.Client.SHOW_HUD.set(true);
                ACConfig.Client.SHOW_CHARGING_HUD.set(true);
            }
            if (!NativeFlightClientChecks.tick(mc, EVIDENCE)) return;
            if (rendered) {
                if (!captured) return;
                rendered = prepared = captured = false;
                captureScreen = null;
                step++;
            }
            if (step == 9) {
                if (reload == null) {
                    mc.getLanguageManager().setSelected(oldLanguage);
                    mc.options.languageCode = oldOptionLanguage;
                    reload = mc.reloadResourcePacks();
                    return;
                }
                if (!reload.isDone() || mc.getOverlay() != null) return;
                reload.getNow(null);
                finish(mc, null);
                return;
            }
            if (!prepared) {
                if (step < 3) {
                    if (step > 0) pressF1(mc);
                    require(mc.options.hideGui == (step == 1), "real F1 input did not toggle hideGui");
                    prepared = true;
                } else {
                    int language = (step - 3) / 2;
                    if (reload == null && !mc.getLanguageManager().getSelected().equals(LANGUAGES[language])) {
                        mc.getLanguageManager().setSelected(LANGUAGES[language]);
                        mc.options.languageCode = LANGUAGES[language];
                        reload = mc.reloadResourcePacks();
                        return;
                    }
                    if (reload != null) {
                        if (!reload.isDone() || mc.getOverlay() != null) return;
                        reload.getNow(null);
                        reload = null;
                    }
                    if (mc.getOverlay() != null) return;
                    prepared = true;
                }
            }
            if (step < 3) {
                // Real production presentation APIs keep both positive controls alive during capture.
                ChargingHudOverlay.setChargingState(20, 40, "railgun");
                TerminalInstallProgress.start();
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(RenderGuiEvent.Post event) {
        if (!Boolean.getBoolean("academy.nativeCompatibilityClient") || done || !prepared || rendered) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getOverlay() != null) return;
        try {
            String screenshot;
            if (step < 3) {
                drawHud(mc, event);
                screenshot = new String[]{"native-hud-visible.png", "native-hud-f1-hidden.png", "native-hud-f1-restored.png"}[step];
            } else {
                ChargingHudOverlay.resetClientSession();
                TerminalInstallProgress.resetClientSession();
                int language = (step - 3) / 2;
                var graphics = new TrackingGraphics(mc, event.getGuiGraphics().bufferSource());
                if (captureScreen == null) {
                    if ((step - 3) % 2 == 0) drawSettings(mc, graphics, language);
                    else drawSlots(mc, graphics, language);
                    graphics.flush();
                    // Assertions intentionally draw several states. Let the next world frame
                    // clear them before drawing one readable production-screen state for capture.
                    return;
                }
                var original = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
                mc.player.setData(AcademyAttachments.PLAYER_ABILITY, abilityFixture());
                try { captureScreen.render(graphics, 0, 0, 0); }
                finally { mc.player.setData(AcademyAttachments.PLAYER_ABILITY, original); }
                graphics.flush();
                screenshot = "native-" + LANGUAGES[language] + ((step - 3) % 2 == 0 ? "-settings.png" : "-slots.png");
            }
            rendered = true;
            Screenshot.grab(mc.gameDirectory, screenshot, mc.getMainRenderTarget(), message -> {
                if (message.getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals("screenshot.failure")) {
                    captureFailure = new IllegalStateException(message.getString());
                }
                captured = true;
            });
        } catch (Throwable failure) { finish(mc, failure); }
    }

    private static void pressF1(Minecraft mc) {
        require(mc.screen == null, "F1 probe must run in the active world");
        long window = mc.getWindow().getWindow();
        mc.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_F1, 0, GLFW.GLFW_PRESS, 0);
        mc.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_F1, 0, GLFW.GLFW_RELEASE, 0);
    }

    private static void drawHud(Minecraft mc, RenderGuiEvent.Post event) {
        var original = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        mc.player.setData(AcademyAttachments.PLAYER_ABILITY, abilityFixture());
        try {
            require(ChargingHudOverlay.isCharging("railgun") && TerminalInstallProgress.isInstalling(),
                    "HUD positive fixtures expired before drawing");
            var charge = new TrackingGraphics(mc, event.getGuiGraphics().bufferSource());
            var install = new TrackingGraphics(mc, event.getGuiGraphics().bufferSource());
            ChargingHudOverlay.onRenderGui(new RenderGuiEvent.Post(charge, event.getPartialTick()));
            TerminalInstallProgress.onRenderGui(new RenderGuiEvent.Post(install, event.getPartialTick()));
            if (step == 1) {
                require(charge.fills == 0 && charge.text.isEmpty(), "F1 left charging HUD draw calls");
                require(install.fills == 0 && install.text.isEmpty(), "F1 left installation HUD draw calls");
            } else {
                require(charge.fills >= 2 && !charge.text.isEmpty(), "visible charging HUD has no real geometry/text");
                require(install.fills >= 2 && !install.text.isEmpty(), "visible installation HUD has no real geometry/text");
            }
            charge.flush(); install.flush();
            EVIDENCE.add("PASS HUD " + (step == 1 ? "F1 hidden" : step == 2 ? "F1 restored" : "visible positive control")
                    + ": charging fills=" + charge.fills + ", installation fills=" + install.fills
                    + "; actual production handlers, active state retained");
        } finally { mc.player.setData(AcademyAttachments.PLAYER_ABILITY, original); }
    }

    private static void drawSettings(Minecraft mc, TrackingGraphics graphics, int language) throws Exception {
        var original = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        boolean hud = ACConfig.Client.SHOW_HUD.get();
        try {
            mc.player.setData(AcademyAttachments.PLAYER_ABILITY, new PlayerAbilityData());
            renderSettings(mc, graphics);
            graphics.expect("ac.settings.cat.keys", EXPECTED[language][0], null);
            graphics.expect("ac.settings.ability.none", EXPECTED[language][3], null);
            graphics.clearText();
            var data = abilityFixture();
            data.setAbilityActive(false);
            mc.player.setData(AcademyAttachments.PLAYER_ABILITY, data);
            ACConfig.Client.SHOW_HUD.set(false);
            renderSettings(mc, graphics);
            graphics.expect("ac.settings.ability.inactive", EXPECTED[language][2], AcademyColors.ERROR);
            graphics.expect("ac.settings.disabled", Component.translatable("ac.settings.disabled").getString(), AcademyColors.ERROR);
            graphics.clearText();
            data.setAbilityActive(true);
            ACConfig.Client.SHOW_HUD.set(true);
            var screen = renderSettings(mc, graphics);
            graphics.expect("ac.settings.ability.active", EXPECTED[language][1], AcademyColors.SUCCESS);
            graphics.expect("ac.settings.enabled", Component.translatable("ac.settings.enabled").getString(), AcademyColors.SUCCESS);
            // Capture the actual lower portion containing ability status, not rows hidden by the scissor.
            screen.mouseScrolled(0, 0, 0, -1000);
            screen.render(graphics, 0, 0, 0);
            graphics.requireTranslated(language);
            captureScreen = screen;
            EVIDENCE.add("PASS " + LANGUAGES[language] + " settings actual render: translated header/no-ability/active/inactive; boolean on/off state draws green/red");
        } finally {
            ACConfig.Client.SHOW_HUD.set(hud);
            mc.player.setData(AcademyAttachments.PLAYER_ABILITY, original);
        }
    }

    private static SettingsAppGui renderSettings(Minecraft mc, TrackingGraphics graphics) {
        var screen = new SettingsAppGui();
        screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        screen.render(graphics, 0, 0, 0);
        return screen;
    }

    private static void drawSlots(Minecraft mc, TrackingGraphics graphics, int language) {
        var original = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        mc.player.setData(AcademyAttachments.PLAYER_ABILITY, abilityFixture());
        try {
            var screen = new SkillSlotGui();
            int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
            screen.init(mc, width, height);
            require(screen.getTitle().getString().equals(EXPECTED[language][4]), "skill slot title did not translate");
            for (int preset = 1; preset <= PlayerAbilityData.PRESET_COUNT; preset++) {
                int labelWidth = mc.font.width(Component.translatable("ac.skill_slot.preset", preset));
                require(labelWidth <= 50, "preset label exceeds its actual 50px tab: " + LANGUAGES[language] + " width=" + labelWidth);
            }
            int x = (width - 280) / 2 + 26 + 10, y = (height - 180) / 2 + 50 + 10;
            screen.render(graphics, x, y, 0);
            var bindings = graphics.text.stream().filter(t -> t.value.startsWith("§7[") && t.value.endsWith("]")).toList();
            require(bindings.size() == SkillPreset.SLOT_COUNT, "production draw did not emit one key hint per slot");
            for (DrawnText binding : bindings) {
                require(mc.font.width(binding.value) <= 48,
                        "actual key hint exceeds its 48px slot: " + LANGUAGES[language] + " text=" + binding.value);
            }
            graphics.expect("ac.skill_slot.preset", EXPECTED[language][5], null);
            graphics.requireKey("ac.skill_slot.hint");
            graphics.requireKey("ac.skill_slot.level_type");
            graphics.requireKey("ac.skill_slot.proficiency");
            graphics.requireKey("ac.skill_slot.cp");
            for (int slot = 0; slot < SkillPreset.SLOT_COUNT; slot++) {
                screen.render(graphics, x + slot * 60, y, 0);
                graphics.expect("", "[" + KeyInputHandler.getSkillKeys()[slot].getTranslatedKeyMessage().getString() + "]", 0);
            }
            screen.render(graphics, x, y, 0);
            // Actual mouse hit-testing opens the selector; it does not send a slot-change packet.
            require(screen.mouseClicked(x, y, 0), "skill selector failed to open through its real click handler");
            screen.render(graphics, 0, 0, 0);
            graphics.expect("ac.skill_slot.clear", EXPECTED[language][6], null);
            graphics.requireTranslated(language);
            captureScreen = screen;
            EVIDENCE.add("PASS " + LANGUAGES[language] + " skill slots actual render/click: title, preset, hint, populated tooltip and clear entry translated; actual-font preset widths <= 50px; key hints <= 48px, full native bindings retained in tooltips");
        } finally { mc.player.setData(AcademyAttachments.PLAYER_ABILITY, original); }
    }

    private static PlayerAbilityData abilityFixture() {
        var data = new PlayerAbilityData();
        data.setCurrentAbility(AbilityCategory.ELECTROMASTER);
        data.learnSkill("railgun");
        data.learnSkill("arc_gen");
        data.getPreset(0).setSlot(0, "railgun");
        data.setAbilityActive(true);
        return data;
    }

    private static void finish(Minecraft mc, Throwable failure) {
        if (done) return;
        done = true;
        ChargingHudOverlay.resetClientSession();
        TerminalInstallProgress.resetClientSession();
        if (initialized) {
            mc.options.hideGui = oldHideGui;
            mc.getLanguageManager().setSelected(oldLanguage);
            mc.options.languageCode = oldOptionLanguage;
            ACConfig.Client.SHOW_HUD.set(oldShowHud);
            ACConfig.Client.SHOW_CHARGING_HUD.set(oldChargingHud);
        }
        try {
            if (failure != null) { EVIDENCE.add("FAIL " + failure); failure.printStackTrace(); }
            Files.writeString(mc.gameDirectory.toPath().resolve("academy-native-compat-client.txt"),
                    "status=" + (failure == null ? "PASS" : "FAIL") + "\n" + String.join("\n", EVIDENCE));
        } catch (Exception writeFailure) { writeFailure.printStackTrace(); }
        mc.stop();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record DrawnText(String key, String value, int color) {}

    private static final class TrackingGraphics extends GuiGraphics {
        private final List<DrawnText> text = new ArrayList<>();
        private int fills;
        TrackingGraphics(Minecraft mc, MultiBufferSource.BufferSource buffers) { super(mc, buffers); }
        @Override public void fill(int left, int top, int right, int bottom, int color) {
            fills++;
            super.fill(left, top, right, bottom, color);
        }
        @Override public int drawString(Font font, Component value, int x, int y, int color, boolean shadow) {
            record(value, color);
            return super.drawString(font, value, x, y, color, shadow);
        }
        @Override public int drawString(Font font, String value, int x, int y, int color, boolean shadow) {
            if (value != null) text.add(new DrawnText("", value, color));
            return super.drawString(font, value, x, y, color, shadow);
        }
        @Override public void renderTooltip(Font font, List<Component> lines, Optional<TooltipComponent> image, int x, int y) {
            for (Component line : lines) record(line, 0);
            super.renderTooltip(font, lines, image, x, y);
        }
        private void record(Component value, int color) {
            String key = value.getContents() instanceof TranslatableContents translation ? translation.getKey() : "";
            text.add(new DrawnText(key, value.getString(), color));
        }
        private void clearText() { text.clear(); }
        private void expect(String key, String value, Integer color) {
            require(text.stream().anyMatch(t -> t.key.equals(key) && t.value.equals(value) && (color == null || t.color == color)),
                    "missing actual draw: " + key + " expected=" + value + " color=" + color);
        }
        private void requireKey(String key) {
            require(text.stream().anyMatch(t -> t.key.equals(key)), "production draw/tooltip omitted " + key);
        }
        private void requireTranslated(int language) {
            require(!text.isEmpty(), "no actual text was drawn");
            for (DrawnText item : text) {
                if (!item.key.isEmpty()) require(Language.getInstance().has(item.key) && !item.key.equals(item.value),
                        "unresolved translation " + item.key);
                if (language == 0) require(item.value.codePoints().noneMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN),
                        "English UI contains hard-coded Chinese: " + item.value);
            }
        }
    }
}
