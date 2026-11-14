package com.mohistmc.academy.skill.ability.vecmanip;

import com.mohistmc.academy.skill.ability.LoadedRaycast;

import com.mohistmc.academy.api.mob.MobCastContext;
import com.mohistmc.academy.api.mob.MobCastable;
import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.AcademySounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** 矢量加速 —— 蓄力后向前方高速冲刺 */
public class VecAccelEffect implements ChargingSkillEffect, MobCastable {

    private static final int MAX_CHARGE = 20;
    private static final double MAX_VELOCITY = 2.5;
    /** A mob launches at half charge. */
    private static final int MOB_CHARGE_TICKS = 10;

    @Override
    public String getId() {
        return "vec_accel";
    }
    @Override public boolean appliesBaseResourceCost(){return false;}
    @Override public boolean grantsActivationProficiency(){return false;}

    @Override
    public int getMinChargeTicks() {
        return 0;
    }

    @Override
    public int getMaxChargeTicks() {
        return MAX_CHARGE;
    }

    @Override public int getSessionTimeoutTicks(PlayerAbilityData data) { return Integer.MAX_VALUE; }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
        // 不预先消耗
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return true;
    }

    @Override
    public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return TickResult.CONTINUE;
    }

    @Override
    public boolean canRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float exp = data.getProficiency(getId());
        float cp = lerpf(120, 80, exp);
        float overload = lerpf(30, 15, exp);
        return ticks >= 0 && (exp > 0.5f || hasGroundWithinTwoBlocks(player))
                && DynamicSkillRules.canPay(data, getId(), cp, overload);
    }

    @Override
    public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float exp = data.getProficiency(getId());

        // 检查是否在地面（熟练度<=0.5时要求地面）
        boolean ignoreGround = exp > 0.5f;
        if (!ignoreGround && !hasGroundWithinTwoBlocks(player)) {
            return;
        }

        float cp = lerpf(120, 80, exp);
        float overload = lerpf(30, 15, exp);

        if (!DynamicSkillRules.tryPay(data,getId(),cp,overload)) return;

        // EntityLook(yaw, pitch - 10) in 1.0.7 deliberately lifts the launch
        // vector by ten degrees.  Subtracting from Y would invert that arc.
        launch(player, player.serverLevel(), ticks, player.getXRot(), player.getYRot());

        DynamicSkillRules.addExp(player,data, getId(), 0.002f);
    }

    /** Mob path: a short charge, then the dash along the aim; only casters that may move themselves. */
    @Override public int mobChargeTicks(float proficiency) { return MOB_CHARGE_TICKS; }

    @Override public boolean mobChargeTick(MobCastContext context, int tick) { return context.mayMoveCaster(); }

    @Override
    public boolean mobCast(MobCastContext context) {
        LivingEntity caster = context.caster();
        if (!context.mayMoveCaster()) return false;
        if (context.proficiency() <= 0.5f && !hasGroundWithinTwoBlocks(caster)) return false;
        Vec3 look = context.look();
        float yaw = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (Math.asin(Mth.clamp(-look.y, -1, 1)) * Mth.RAD_TO_DEG);
        launch(caster, context.level(), context.chargeTicks(), pitch, yaw);
        return true;
    }

    @Override public int mobCooldownTicks(float proficiency) { return getCooldownTicks(proficiency); }

    /** The dash after payment, shared by players and mob casters; {@code pitch}/{@code yaw} are the aim. */
    private static void launch(LivingEntity caster, ServerLevel level, int ticks, float pitch, float yaw) {
        int effectiveTicks = Math.min(ticks, MAX_CHARGE);
        double prog = Math.max(0, Math.min(1, effectiveTicks / (double) MAX_CHARGE));
        double speed = Math.sin(lerpf(0.4f, 1.0f, (float) prog)) * MAX_VELOCITY;

        Vec3 dir = Vec3.directionFromRotation(pitch - 10.0F, yaw).scale(speed);

        caster.setDeltaMovement(dir);
        caster.hurtMarked = true;
        caster.fallDistance = 0;
        if (caster.getVehicle() != null) {
            caster.stopRiding();
        }

        AcademySounds.playSound(level, caster.getX(), caster.getY(), caster.getZ(),
                AcademySounds.VM_VEC_ACCEL, caster instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE,
                0.35f, 1.0f);
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (!canRelease(player, data, ticks)) return false;
        onChargingRelease(player, data, ticks);
        return true;
    }

    @Override
    public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return (int) lerpf(80, 50, proficiency);
    }

    private static boolean hasGroundWithinTwoBlocks(LivingEntity player) {
        Vec3 from = player.position();
        return LoadedRaycast.clip(player.level(), new ClipContext(from, from.add(0, -2, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.BLOCK;
    }
}
