package com.mohistmc.academy.skill.ability.vecmanip;

import com.mohistmc.academy.skill.ability.LoadedRaycast;
import com.mohistmc.academy.skill.AcademyDamageHelper;

import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.api.mob.MobCastContext;
import com.mohistmc.academy.api.mob.MobCastable;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.AcademySounds;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** 定向冲击 —— 蓄力后向前猛击，击退目标并造成伤害 */
public class DirShockEffect implements ChargingSkillEffect, MobCastable {

    private static final int MIN_TICKS = 7;
    private static final int MAX_TICKS = 49;
    private static final int MAX_TOLERANT_TICKS = 200;
    /** A mob releases inside the legacy 7..49 window. */
    private static final int MOB_CHARGE_TICKS = 15;
    private static final double RANGE = 3.0;

    @Override
    public String getId() {
        return "dir_shock";
    }
    @Override public boolean appliesBaseResourceCost(){return false;}
    @Override public boolean grantsActivationProficiency(){return false;}

    @Override
    public int getMinChargeTicks() {
        return MIN_TICKS;
    }

    @Override
    public int getMaxChargeTicks() {
        return MAX_TICKS;
    }

    @Override public int getSessionTimeoutTicks(PlayerAbilityData data) { return MAX_TOLERANT_TICKS; }
    @Override public TickResult getSessionTimeoutResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return TickResult.ABORT_RESOURCE;
    }

    @Override
    public boolean canRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float cp = lerpf(50, 100, data.getProficiency(getId()));
        float overload = lerpf(18, 12, data.getProficiency(getId()));
        return ChargingSkillEffect.super.canRelease(player, data, ticks)
                && DynamicSkillRules.canPay(data, getId(), cp, overload);
    }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
        // 不预先消耗，释放时才消耗
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return ticks <= MAX_TOLERANT_TICKS;
    }

    @Override
    public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return onChargingTick(player, data, ticks) ? TickResult.CONTINUE : TickResult.RELEASE;
    }

    @Override
    public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (ticks < MIN_TICKS || ticks > MAX_TICKS) {
            return;
        }

        float exp = data.getProficiency(getId());
        float cp = lerpf(50, 100, exp);
        float overload = lerpf(18, 12, exp);

        if (!DynamicSkillRules.tryPay(data,getId(),cp,overload)) return;

        ServerLevel level = player.serverLevel();
        EntityHitResult hit = rayTraceEntities(player, player.getEyePosition(), player.getLookAngle(), RANGE);

        if (hit != null) {
            shock(player, level, hit.getEntity(), exp, player.getLookAngle());
            DynamicSkillRules.addExp(player,data, getId(), 0.0035f);
            if (!data.isDevMode()) data.setCooldown(getId(), getCooldownTicks(exp));
        } else {
            DynamicSkillRules.addExp(player,data, getId(), 0.0010f);
        }
    }

    /** Mob path: a fixed charge inside the legacy window, then a strike at whatever the aim meets in reach. */
    @Override public int mobChargeTicks(float proficiency) { return MOB_CHARGE_TICKS; }

    @Override public boolean mobChargeTick(MobCastContext context, int tick) { return context.target() != null; }

    @Override
    public boolean mobCast(MobCastContext context) {
        if (context.target() == null) return false;
        EntityHitResult hit = rayTraceEntities(context.caster(), context.eye(), context.look(), RANGE);
        if (hit == null) return false;
        shock(context.caster(), context.level(), hit.getEntity(), context.proficiency(), context.look());
        return true;
    }

    @Override public int mobCooldownTicks(float proficiency) { return getCooldownTicks(proficiency); }

    /** The strike after payment, shared by players and mob casters. */
    private void shock(LivingEntity caster, ServerLevel level, Entity target, float exp, Vec3 look) {
        float damage = lerpf(7, 15, exp);
        AcademyDamageHelper.hurt(caster,target,com.mohistmc.academy.skill.SkillDamageSource.attack(caster), DynamicSkillRules.damage(getId(),damage));
        // Players keep the legacy unconditional push; a mob only pushes what it may hit.
        if (caster instanceof Player || AcademyDamageHelper.allowsTarget(caster, target)) {
            knockback(caster, target, exp);
            // The old context adds a second, small outward impulse even below
            // the 0.25 proficiency threshold.
            Vec3 outward = target.position().subtract(caster.position()).normalize().scale(0.24);
            target.setDeltaMovement(target.getDeltaMovement().add(outward));
            target.hurtMarked = true;
        }

        AcademySounds.playSound(level, target.getX(), target.getY(), target.getZ(),
                AcademySounds.VM_DIRECTED_SHOCK, caster instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE,
                0.5f, 1.0f);
        com.mohistmc.academy.world.effect.EffectHelper.waveRings(level,
                target.getEyePosition(), look, 1, .6);
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

    private void knockback(LivingEntity caster, Entity target, float exp) {
        if (exp < 0.25f) return;

        Vec3 delta = caster.getEyePosition().subtract(target.getEyePosition()).normalize();
        delta = new Vec3(delta.x, -0.6, delta.z).normalize();

        target.setPos(target.getX(), target.getY() + 0.1, target.getZ());
        target.setDeltaMovement(delta.x * -0.7, delta.y * -0.7, delta.z * -0.7);
        target.hurtMarked = true;
    }

    private EntityHitResult rayTraceEntities(LivingEntity caster, Vec3 start, Vec3 look, double range) {
        Vec3 end = start.add(look.scale(range));
        var block=LoadedRaycast.clip(caster.level(), new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,caster));
        double wallDist=block.getType()==HitResult.Type.MISS?Double.MAX_VALUE:start.distanceTo(block.getLocation());
        AABB area = caster.getBoundingBox().inflate(range);
        List<Entity> entities = caster.level().getEntities(caster, area,
                e -> e.isAlive() && e.isPickable()
                        && (e instanceof LivingEntity || e instanceof EnderDragonPart));

        Entity closest = null;
        double closestDist = Double.MAX_VALUE;
        Vec3 closestHit = null;

        for (Entity e : entities) {
            var result = e.getBoundingBox().inflate(0.3).clip(start, end);
            if (result.isPresent()) {
                double dist = start.distanceTo(result.get());
                if (dist < closestDist && dist < wallDist) {
                    closestDist = dist;
                    closest = e;
                    closestHit = result.get();
                }
            }
        }

        return closest != null ? new EntityHitResult(closest, closestHit) : null;
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return (int) lerpf(60, 20, proficiency);
    }

    @Override
    public boolean shouldApplyCooldownAfterRelease(ServerPlayer player, PlayerAbilityData data, int chargedTicks) {
        // 1.0.7 consumes CP/OL on a miss but only starts cooldown on a hit.
        return false;
    }
}
