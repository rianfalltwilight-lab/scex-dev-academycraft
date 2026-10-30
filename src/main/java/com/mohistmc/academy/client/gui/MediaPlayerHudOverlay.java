package com.mohistmc.academy.client.gui;

import com.mohistmc.academy.client.MediaPlayerManager;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.terminal.MediaTrack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/** Restores the 145x36 bottom-right playback HUD from media_player_aux.xml. */
public final class MediaPlayerHudOverlay {
    private static final int WIDTH = 145;
    private static final int HEIGHT = 36;

    private MediaPlayerHudOverlay() {}

    public static void render(GuiGraphics graphics, DeltaTracker partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui || !ACConfig.Client.showHud()
                || !MediaPlayerManager.isPlaying()) return;

        MediaTrack track = MediaPlayerManager.getTrack(MediaPlayerManager.getCurrentTrack());
        if (track == null) return;
        int x = mc.getWindow().getGuiScaledWidth() - WIDTH - 6;
        int y = mc.getWindow().getGuiScaledHeight() - HEIGHT - 6;
        String title = track.displayName().getString();
        if (MediaPlayerManager.isPaused()) title = "‖ " + title;
        title = mc.font.plainSubstrByWidth(title, 120);

        graphics.drawString(mc.font, title, x + 13, y + 16, 0xFFFFFFFF, true);
        graphics.fill(x + 14, y + 27, x + 134, y + 29, 0x44000000);
        graphics.fill(x + 14, y + 27,
                x + 14 + Math.round(120 * MediaPlayerManager.getProgress()), y + 29, 0xCCFFFFFF);
        String time = MediaPlayerManager.formatTime(MediaPlayerManager.getElapsedSeconds());
        graphics.drawString(mc.font, time, x + 140 - mc.font.width(time), y + 27, 0xFFFFFFFF, true);
    }
}
