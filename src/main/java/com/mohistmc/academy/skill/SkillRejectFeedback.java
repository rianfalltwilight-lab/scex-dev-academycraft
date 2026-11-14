package com.mohistmc.academy.skill;

import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.network.PayloadRateLimiter;
import com.mohistmc.academy.world.AcademySounds;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * Why a skill key did nothing, shown on the action bar with the deny sound.  1.12.2 rejected these
 * casts silently (only the icon mask showed a cooldown); every reason is rate-limited to once a second.
 */
public final class SkillRejectFeedback {
    private SkillRejectFeedback() {}

    /** The reason {@code skill} cannot be cast now, or a generic one when no specific gate failed. */
    public static Component reason(PlayerAbilityData data, Skill skill) {
        Component name = Component.translatable(skill.getTranslationKey());
        if (!ACConfig.Server.skill(skill.getId()).enabled())
            return Component.translatable("message.academy.skill.disabled", name);
        if (!data.isDevMode() && data.isOverloadLocked())
            return Component.translatable("message.academy.skill.overloaded");
        if (data.isOnCooldown(skill.getId()))
            return Component.translatable("message.academy.skill.cooldown", name, seconds(data.getCooldownTicks(skill.getId())));
        float shortfall = data.takeCpShortfall();
        if (!data.isDevMode() && shortfall > 0)
            return Component.translatable("message.academy.skill.insufficient_cp_amount",
                    whole(data.getCurrentCp()), whole(shortfall));
        SkillEffect effect = skill.getEffect();
        if (!data.isDevMode() && (effect == null || effect.appliesBaseResourceCost())) {
            float cost = skill.getBaseCpCost() * ACConfig.Server.skill(skill.getId()).cp();
            if (!data.canAffordCp(cost))
                return Component.translatable("message.academy.skill.insufficient_cp_amount",
                        whole(data.getCurrentCp()), whole(cost));
        }
        return Component.translatable("message.academy.skill.cannot_use", name);
    }

    /** Action bar + deny sound, at most once a second per reason. */
    public static void send(ServerPlayer player, long now, String reason, Component message) {
        if (!PayloadRateLimiter.allow(player.getUUID(), "skill_reject:" + reason, now, 20, 1)) return;
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        player.playNotifySound(AcademySounds.ABILITY_DENY.value(), SoundSource.PLAYERS, 0.5f, 1.0f);
    }

    /** {@link #reason} and {@link #send}, keyed by the reason's translation key. */
    public static void sendFor(ServerPlayer player, long now, PlayerAbilityData data, Skill skill) {
        Component message = reason(data, skill);
        String key = message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                ? t.getKey() : "other";
        send(player, now, key, message);
    }

    static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", Math.max(0, ticks) / 20.0);
    }

    private static String whole(float value) {
        return Integer.toString((int) Math.floor(Math.max(0, value)));
    }
}
