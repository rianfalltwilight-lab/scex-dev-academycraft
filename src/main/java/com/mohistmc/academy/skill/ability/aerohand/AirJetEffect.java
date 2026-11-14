package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.world.effect.EffectHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * 空气喷射 —— 向后喷气，使施术者向视线方向短距离飞跃。
 */
public class AirJetEffect implements com.mohistmc.academy.skill.ability.DynamicOneShotSkillEffect {

    @Override
    public String getId() {
        return "air_jet";
    }

    @Override public float rawCp(float proficiency) { return 200 + 200 * Math.clamp(proficiency, 0, 1); }
    @Override public float rawOverload(float proficiency) { return 80 - 40 * Math.clamp(proficiency, 0, 1); }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        return !player.isPassenger() && !player.isSleeping()
                && !AeroPassiveRuntime.isOffenseArmourEngaged(player)
                && com.mohistmc.academy.skill.ability.DynamicOneShotSkillEffect.super.canActivate(player, data);
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        float exp = data.getProficiency(getId());
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle().normalize();
        double speed = AeroBehaviorMath.airJetSpeed(exp);
        Vec3 impulse = look.scale(speed);
        player.setDeltaMovement(player.getDeltaMovement().add(impulse));
        player.resetFallDistance();
        player.hurtMarked = true;

        Vec3 exhaust = player.getEyePosition().subtract(look.scale(0.8));
        EffectHelper.windBurst(level, exhaust.x, exhaust.y, exhaust.z, 28, 0.8);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);

        if (!data.isDevMode()) {
            DynamicSkillRules.addExp(player,data,getId(),0.002f + 0.001f * exp);
        }
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return (int) (40 - 30 * Math.clamp(proficiency, 0.0f, 1.0f));
    }
}

