package com.mohistmc.academy.client;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mohistmc.academy.utils.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public class ChargingHudOverlay {

    private static final ChargePresentationState STATE = new ChargePresentationState();

    public static void setChargingState(int ticks, int max, String skillId) {
        STATE.accept(ticks, max, skillId);
    }

    public static void resetClientSession() {
        STATE.clear();
    }

    /** Read-only presentation state; skill authority remains on the server. */
    public static boolean isCharging(String skillId) {
        return STATE.active() && STATE.skill().equals(skillId);
    }

    public static float chargeProgress() {
        return STATE.progress(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
    }

    /** Interpolated presentation tick; never used to authorize a cast. */
    public static int currentTicks() { return STATE.ticks(); }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isAlive()) STATE.clear();
        else if (!mc.isPaused()) STATE.tick();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!STATE.active()) return;
        if (!ACConfig.Client.showHud() || !ACConfig.Client.showChargingHud()) return;

        if (Minecraft.getInstance().screen != null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int barWidth = 120;
        int barHeight = 10;
        int x = (screenWidth - barWidth) / 2;
        int y = screenHeight - 60;

        event.getGuiGraphics().fill(x, y, x + barWidth, y + barHeight, 0xFF333333);

        float progress = chargeProgress();
        String currentSkillId = STATE.skill();
        int progressWidth = (int) (barWidth * progress);
        int color = progress >= 1.0f ? 0xFF00FF00 : 0xFFFFFF00;
        if ("railgun".equals(currentSkillId) && progress >= 0.65f) {
            // A deliberately broad, readable QTE window cue. It is presentation only:
            // the server still decides whether a coin and the release timing are valid.
            color = ((System.currentTimeMillis() / 120L) & 1L) == 0L ? 0xFF66EEFF : 0xFFFFFFFF;
        }
        event.getGuiGraphics().fill(x, y, x + progressWidth, y + barHeight, color);

        event.getGuiGraphics().renderOutline(x, y, barWidth, barHeight, 0xFFFFFFFF);

        if (!currentSkillId.isEmpty()) {
            PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
            Skill skill = data.hasAbility()
                    ? SkillRegistry.getSkill(data.getCurrentAbility(), currentSkillId)
                    : null;
            if (skill != null) {
                Component name = Component.translatable(skill.getTranslationKey());
                int textWidth = mc.font.width(name);
                event.getGuiGraphics().drawString(mc.font, name, (screenWidth - textWidth) / 2, y - 12, 0xFFFFFFFF);
                RenderUtils.render(16, 16, x - 18, y - 3,
                        event.getGuiGraphics(), skill.getIconLocation());
                if ("railgun".equals(currentSkillId)) {
                    // The old railgun charge identity: coin/ray art and a server-neutral QTE cue.
                    ResourceLocation railgun = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,
                            "textures/effects/railgun.png");
                    RenderUtils.render(16, 16, x + barWidth + 3, y - 3,
                            event.getGuiGraphics(), railgun);
                }
                Component hint = chargeHint(currentSkillId, progress);
                int hintWidth = mc.font.width(hint);
                event.getGuiGraphics().drawString(mc.font, hint, (screenWidth - hintWidth) / 2,
                        y + barHeight + 3, 0xFFB8D8FF);
            }
        }
    }

    private static Component chargeHint(String skillId, float progress) {
        if ("railgun".equals(skillId)) {
            return Component.translatable(progress >= 0.65f ? "hud.academy.charge.release" : "hud.academy.charge.hold");
        }
        if ("light_shield".equals(skillId)) return Component.translatable("hud.academy.charge.shield");
        if ("electron_missile".equals(skillId)) return Component.translatable("hud.academy.charge.missiles");
        if ("ray_barrage".equals(skillId)) return Component.translatable("hud.academy.charge.targeting");
        if ("storm_wing".equals(skillId)) return Component.translatable("hud.academy.charge.flight");
        if ("vec_deviation".equals(skillId)) return Component.translatable("hud.academy.charge.vector");
        return Component.translatable(progress >= 1.0f ? "hud.academy.charge.ready" : "hud.academy.charge.hold");
    }
}
