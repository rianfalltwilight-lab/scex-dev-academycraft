package com.mohistmc.academy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class TerminalInstallProgress {
    private static boolean installing = false;
    private static long startTime = 0;
    private static final int DURATION_MS = 3000;

    public static void start() {
        installing = true;
        startTime = System.currentTimeMillis();
    }

    public static boolean isInstalling() {
        return installing;
    }

    public static void resetClientSession() { installing = false; startTime = 0; }

    public static void render(GuiGraphics graphics, DeltaTracker partialTick) {
        if (!installing) return;

        long elapsed = System.currentTimeMillis() - startTime;
        float progress = Math.min(elapsed / (float) DURATION_MS, 1.0f);

        if (progress >= 1.0f) {
            installing = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.options.hideGui) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int barWidth = 200;
        int barHeight = 20;
        int x = (screenWidth - barWidth) / 2;
        int y = screenHeight / 2 - barHeight / 2;

        graphics.fill(x, y, x + barWidth, y + barHeight, 0xFF1a1a2e);
        graphics.fill(x, y, x + (int)(barWidth * progress), y + barHeight, 0xFF00bcd4);

        Component text = Component.translatable("hud.academy.terminal.installing", Math.round(progress * 100));
        int textWidth = mc.font.width(text);
        graphics.drawString(mc.font, text, (screenWidth - textWidth) / 2, y - 15, 0xFF00e5ff);
    }
}
