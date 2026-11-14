package com.mohistmc.academy.client.gui;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.utils.RenderUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 通知浮层 — 短暂显示系统消息（教程更新、能力解锁等）。
 */
@OnlyIn(Dist.CLIENT)
public class NotifyOverlay {

    private static final int MAX_NOTIFICATIONS = 3;
    private static final long DURATION_MS = 5000;
    private static final long FADE_MS = 500;
    /** A frame gap longer than this means the overlay was hidden rather than just a slow frame. */
    private static final long HIDDEN_GAP_MS = 250;
    private static long lastVisibleFrame;

    private static final List<Notification> notifications = new ArrayList<>();

    private static final ResourceLocation TEX_BG =
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/notification/back.png");

    public static void resetClientSession() {
        synchronized (notifications) { notifications.clear(); }
    }

    /**
     * 发送一条通知（线程安全，可从任何地方调用）。
     */
    public static void notify(String title, String content) {
        synchronized (notifications) {
            notifications.add(new Notification(title, content, System.currentTimeMillis()));
            while (notifications.size() > MAX_NOTIFICATIONS) {
                notifications.remove(0);
            }
        }
    }

    /** 发送一条通知，带图标提示。 */
    public static void notify(String title, String content, ResourceLocation icon) {
        synchronized (notifications) {
            notifications.add(new Notification(title, content, icon, System.currentTimeMillis()));
            while (notifications.size() > MAX_NOTIFICATIONS) {
                notifications.remove(0);
            }
        }
    }

    public static void render(GuiGraphics graphics, DeltaTracker partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui) return;
        if (!ACConfig.Client.showHud()) return;

        GuiGraphics g = graphics;
        int screenW = mc.getWindow().getGuiScaledWidth();
        long now = System.currentTimeMillis();

        synchronized (notifications) {
            // Time spent hidden (a screen open, F1) does not count toward a notice's lifetime,
            // so a tutorial unlock raised while the terminal is open is still shown afterwards.
            long gap = lastVisibleFrame == 0 ? 0 : now - lastVisibleFrame;
            if (gap > HIDDEN_GAP_MS) for (Notification n : notifications) n.time += gap;
            lastVisibleFrame = now;
            notifications.removeIf(n -> now - n.time > DURATION_MS + FADE_MS);

            int notifW = 220;
            int notifH = 50;
            int x = screenW - notifW - 20;
            int stackHeight = notifications.size() * (notifH + 4) - 4;
            int y = CPBarOverlay.avoidNotificationOverlap(x, 50, notifW, stackHeight);
            for (int i = notifications.size() - 1; i >= 0; i--) {
                Notification n = notifications.get(i);
                long elapsed = now - n.time;
                float alpha = 1.0f;
                if (elapsed < FADE_MS) {
                    alpha = (float) elapsed / FADE_MS;
                } else if (elapsed > DURATION_MS) {
                    alpha = 1.0f - (float)(elapsed - DURATION_MS) / FADE_MS;
                }
                if (alpha <= 0) continue;

                int bgColor = (int)(alpha * 200) << 24 | 0x0A1A30;
                g.fill(x, y, x + notifW, y + notifH, bgColor);
                int borderColor = (int)(alpha * 180) << 24 | 0x00BFFF;
                g.renderOutline(x, y, notifW, notifH, borderColor);

                int textX = x + 8;
                if (n.icon != null) {
                    RenderUtils.render(16, 16, x + 4, y + 4, g, n.icon);
                    textX = x + 26;
                }

                // Font treats an alpha below 4 as opaque, which flashed the text at both fade ends.
                int titleAlpha = (int)(alpha * 255), contentAlpha = (int)(alpha * 200);
                if (titleAlpha >= 4) g.drawString(mc.font, n.title, textX, y + 6, titleAlpha << 24 | 0xFFFFFF, false);
                if (contentAlpha >= 4) g.drawString(mc.font, n.content, textX, y + 24, contentAlpha << 24 | 0xCCDDEE, false);

                y += notifH + 4;
            }
        }
    }

    private static class Notification {
        final String title;
        final String content;
        final ResourceLocation icon;
        long time;

        Notification(String title, String content, long time) {
            this(title, content, null, time);
        }

        Notification(String title, String content, ResourceLocation icon, long time) {
            this.title = title;
            this.content = content;
            this.icon = icon;
            this.time = time;
        }
    }
}
