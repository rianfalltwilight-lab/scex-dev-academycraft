package com.mohistmc.academy.client.gui;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;

/**
 * CP 和 Overload 栏 HUD —— 坐标与渲染方式对齐 Return CPBar。
 */
@OnlyIn(Dist.CLIENT)
public class CPBarOverlay {

    private static boolean showNumbers;

    public static void setShowNumbers(boolean value) { showNumbers = value; }

    private static final float WIDTH = 964;
    private static final float HEIGHT = 147;
    private static final float SCALE = 0.2f;
    private static final int MARGIN_RIGHT = 12;
    private static final int MARGIN_TOP = 12;

    // OL 区域(顶部条)
    private static final float OL_X0 = 0, OL_Y0 = 21, OL_W = 943, OL_H = 104;
    // CP 区域
    private static final float CP_X0 = 47, CP_Y0 = 30, CP_W = 883, CP_H = 84;

    private static final ResourceLocation TEX_BACK_NORMAL =
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/cpbar/back_normal.png");
    private static final ResourceLocation TEX_BACK_OVERLOAD =
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/cpbar/back_overload.png");
    private static final ResourceLocation TEX_CP =
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/cpbar/cp.png");
    private static final ResourceLocation TEX_FRONT_OVERLOAD =
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/cpbar/front_overload.png");
    private static final ResourceLocation TEX_OVERLOAD_HIGHLIGHT =
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/cpbar/highlight_overload.png");

    /** CP 条颜色渐变：红 → 橙 → 白 */
    private static final float[][] CP_COLORS = {
            {0.0f, 0xf0 / 255f, 0x67 / 255f, 0x67 / 255f},
            {0.35f, 0xff / 255f, 0xae / 255f, 0x44 / 255f},
            {1.0f, 1.0f, 1.0f, 1.0f},
    };
    /** OL 条颜色渐变：青 → 橙 → 红 */
    private static final float[][] OL_COLORS = {
            {0.0f, 0xdf / 255f, 0xdf / 255f, 0xdf / 255f},
            {0.55f, 0xf0 / 255f, 0xd4 / 255f, 0x9d / 255f},
            {1.0f, 0xf5 / 255f, 0x64 / 255f, 0x64 / 255f},
    };

    public static void render(GuiGraphics graphics, DeltaTracker partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui) return;
        if (!ACConfig.Client.showHud() || !ACConfig.Client.showCpBar()) return;

        PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (!data.hasAbility()) return;
        if (!data.isAbilityActive()) return;

        GuiGraphics g = graphics;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        float hudW = WIDTH * SCALE;
        HudPosition position = position(screenW, screenH);
        float hudX = position.x(), hudY = position.y();

        g.pose().pushPose();
        g.pose().translate(hudX, hudY, 0);
        g.pose().scale(SCALE, SCALE, 1.0f);

        // Legacy CPBar: overload art during the recovery delay, then the normal bar
        // drawn faint (alpha .3) until overload drains to zero and the lock lifts.
        if (data.isOverloaded()) {
            drawOverload(g);
        } else {
            barAlpha = data.isOverloadLocked() ? 0.3f : 1.0f;
            drawNormal(g, data);
            drawCPBar(g, data);
            barAlpha = 1.0f;
        }

        g.pose().popPose();

        // 1.0.7 showed exact resource values only while the V activation key
        // was held. A short (<300 ms) release toggles the ability separately.
        if (showNumbers) {
            String cp = "CP " + Math.round(data.getCurrentCp()) + " / " + Math.round(data.getMaxCp());
            String ol = "OL " + Math.round(data.getCurrentOverload()) + " / " + Math.round(data.getMaxOverload());
            int textX = Math.round(hudX + hudW - Math.max(mc.font.width(cp), mc.font.width(ol)));
            int textY = Math.round(hudY + HEIGHT * SCALE + 2);
            g.drawString(mc.font, cp, textX, textY, 0xFFFFFFFF, true);
            g.drawString(mc.font, ol, textX, textY + 10, 0xFFFFC0A0, true);
        }
    }

    private record HudPosition(float x, float y) {}

    private static HudPosition position(int screenW, int screenH) {
        float hudW = WIDTH * SCALE;
        float hudX = screenW - hudW - MARGIN_RIGHT + ACConfig.Client.cpBarX();
        // Jade's default tooltip occupies the upper centre/right on narrow windows.
        // Keep both overlays readable without taking a compile/runtime dependency on Jade.
        float configuredY = ACConfig.Client.cpBarY();
        float hudY = ModList.get().isLoaded("jade") && ACConfig.Client.autoAvoidJade()
                ? Math.max(configuredY, screenH - HEIGHT * SCALE - 44)
                : configuredY;
        hudX = Math.clamp(hudX, 0, Math.max(0, screenW - hudW));
        hudY = Math.clamp(hudY, 0, Math.max(0, screenH - HEIGHT * SCALE));
        return new HudPosition(hudX, hudY);
    }

    /** Keep transient notification cards clear of the visible bar and its held-key resource values. */
    static int avoidNotificationOverlap(int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ACConfig.Client.showHud() || !ACConfig.Client.showCpBar()) return y;
        PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (!data.hasAbility() || !data.isAbilityActive()) return y;
        int screenH = mc.getWindow().getGuiScaledHeight();
        HudPosition p = position(mc.getWindow().getGuiScaledWidth(), screenH);
        int bottom = showNumbers ? Math.round(p.y() + HEIGHT * SCALE + 2) + 10 + mc.font.lineHeight
                : (int) Math.ceil(p.y() + HEIGHT * SCALE);
        if (x + width <= p.x() || x >= p.x() + WIDTH * SCALE || y + height <= p.y() || y >= bottom) return y;
        int below = bottom + 4;
        if (below + height <= screenH) return below;
        int above = (int) p.y() - height - 4;
        return above >= 0 ? above : y;
    }

    /** Alpha multiplier for the normal bar; lowered while the overload lockout drains. */
    private static float barAlpha = 1.0f;

    /** 正常模式：背景 + OL 条（背景 alpha 0.8，与 Return setColor(1,1,1,.8) 一致） */
    private static void drawNormal(GuiGraphics g, PlayerAbilityData data) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.setColor(1, 1, 1, 0.8f * barAlpha);
        g.blit(TEX_BACK_NORMAL, 0, 0, 0, 0, (int) WIDTH, (int) HEIGHT, (int) WIDTH, (int) HEIGHT);
        g.setColor(1, 1, 1, 1);

        float olRatio = data.getMaxOverload() > 0
                ? Math.min(1.0f, data.getCurrentOverload() / data.getMaxOverload()) : 0;
        if (olRatio > 0) {
            float[] col = autoLerp(OL_COLORS, olRatio);
            float len = OL_W * olRatio;
            float x = OL_X0 + OL_W - len;
            g.fill((int) x, (int) OL_Y0, (int) (x + len), (int) (OL_Y0 + OL_H),
                    colorOf(col[1], col[2], col[3], 0.8f * barAlpha));
        }
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    /** CP 条：右对齐填充，0.16~0.96 比例 */
    private static void drawCPBar(GuiGraphics g, PlayerAbilityData data) {
        float cpRatio = data.getMaxCp() > 0
                ? Math.min(1.0f, data.getCurrentCp() / data.getMaxCp()) : 0;
        if (cpRatio <= 0) return;

        float[] col = autoLerp(CP_COLORS, cpRatio);
        float prog = 0.16f + cpRatio * 0.8f;
        float len = CP_W * prog;
        float x = CP_X0 + (CP_W - len);

        // 用 cp.png 纹理按比例右对齐绘制,叠加渐变着色
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.setColor(col[1], col[2], col[3], barAlpha);
        g.blit(TEX_CP, (int) x, (int) CP_Y0, (int) x, (int) CP_Y0,
                (int) len, (int) CP_H, (int) WIDTH, (int) HEIGHT);
        g.setColor(1, 1, 1, 1);

        // 职业图标：CP 条右侧正方形
        var ability = Minecraft.getInstance().player.getData(
                com.mohistmc.academy.skill.AcademyAttachments.PLAYER_ABILITY).getCurrentAbility();
        if (ability != null) {
            float iconSize = 64;
            float iconX = CP_X0 + CP_W - iconSize;
            float iconY = CP_Y0 + (CP_H - iconSize) / 2;
            g.setColor(1, 1, 1, 0.8f * barAlpha);
            g.blit(ability.getOverlayIcon(), (int) iconX, (int) iconY,
                    0, 0, (int) iconSize, (int) iconSize, 64, 64);
            g.setColor(1, 1, 1, 1);
        }
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    /** 过载模式：背景 + 前景 + 高亮呼吸 */
    private static void drawOverload(GuiGraphics g) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.setColor(1, 1, 1, 0.8f);
        g.blit(TEX_BACK_OVERLOAD, 0, 0, 0, 0, (int) WIDTH, (int) HEIGHT, (int) WIDTH, (int) HEIGHT);
        g.setColor(1, 1, 1, 1);
        g.blit(TEX_FRONT_OVERLOAD, 0, 0, 0, 0, (int) WIDTH, (int) HEIGHT, (int) WIDTH, (int) HEIGHT);
        float breathe = 0.3f + 0.35f * (float) (Math.sin(System.currentTimeMillis() / 200.0) + 1);
        g.setColor(1, 1, 1, breathe);
        g.blit(TEX_OVERLOAD_HIGHLIGHT, 0, 0, 0, 0, (int) WIDTH, (int) HEIGHT, (int) WIDTH, (int) HEIGHT);
        g.setColor(1, 1, 1, 1);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    /** 渐变颜色插值 */
    private static float[] autoLerp(float[][] list, double prog) {
        for (int i = 0; i < list.length; i++) {
            if (list[i][0] >= prog) {
                if (i == 0) return list[0];
                float[] last = list[i - 1];
                float f = (float) ((prog - last[0]) / (list[i][0] - last[0]));
                return new float[]{
                        0, lerp(last[1], list[i][1], f), lerp(last[2], list[i][2], f), lerp(last[3], list[i][3], f)};
            }
        }
        return list[list.length - 1];
    }

    private static float lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }

    private static int colorOf(float r, float g, float b, float a) {
        return ((int) (a * 255) << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
}
