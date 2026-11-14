package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.api.mob.MobCastContext;
import com.mohistmc.academy.api.mob.MobCastable;
import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.world.effect.EffectHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 空气喷射 —— 向后喷气，使施术者向视线方向短距离飞跃。
 */
public class AirJetEffect implements com.mohistmc.academy.skill.ability.DynamicOneShotSkillEffect, MobCastable {

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
        jet(player, player.serverLevel(), exp, player.getLookAngle().normalize());

        if (!data.isDevMode()) {
            DynamicSkillRules.addExp(player,data,getId(),0.002f + 0.001f * exp);
        }
    }

    /** Mob path: dashes toward the target (or along its look); never when its caster may not move. */
    @Override
    public boolean mobCast(MobCastContext context) {
        LivingEntity caster = context.caster();
        if (!context.mayMoveCaster() || caster.isPassenger() || caster.isSleeping()) return false;
        Vec3 look = context.look().normalize();
        if (!Double.isFinite(look.lengthSqr()) || look.lengthSqr() < 1.0e-8) return false;
        jet(caster, context.level(), context.proficiency(), look);
        return true;
    }

    @Override public int mobCooldownTicks(float proficiency) { return getCooldownTicks(proficiency); }

    /** The impulse, exhaust and sound shared by players and mobs; a player's sound source stays PLAYERS. */
    private static void jet(LivingEntity caster, ServerLevel level, float exp, Vec3 look) {
        double speed = AeroBehaviorMath.airJetSpeed(exp);
        Vec3 impulse = look.scale(speed);
        caster.setDeltaMovement(caster.getDeltaMovement().add(impulse));
        caster.resetFallDistance();
        caster.hurtMarked = true;

        Vec3 exhaust = caster.getEyePosition().subtract(look.scale(0.8));
        EffectHelper.windBurst(level, exhaust.x, exhaust.y, exhaust.z, 28, 0.8);

        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.FIRECHARGE_USE, caster.getSoundSource(), 1.0f, 1.0f);
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return (int) (40 - 30 * Math.clamp(proficiency, 0.0f, 1.0f));
    }
}
