package com.mohistmc.academy.skill.ability.teleporter;

import com.mohistmc.academy.skill.ability.LoadedRaycast;

import com.mohistmc.academy.api.mob.MobCastContext;
import com.mohistmc.academy.api.mob.MobCastable;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.passive.PassiveDamageHelper;
import com.mohistmc.academy.world.AcademyParticles;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** 1.0.7 Flesh Ripping: continuously mark a living target and settle on key-up. */
public final class FleshRippingEffect implements ChargingSkillEffect, MobCastable {
    private record Target(Vec3 destination, Entity entity) {}
    private static final Map<UUID, Target> ACTIVE = new ConcurrentHashMap<>();
    /** Mob casters' current marks, kept apart from the player sessions. */
    private static final Map<UUID, Target> MOB_ACTIVE = new ConcurrentHashMap<>();
    /** Server stop: forget mob marks. */
    public static void clearMobSessions() { MOB_ACTIVE.clear(); }
    /** A mob keeps marking for half a second before it rips. */
    private static final int MOB_MARK_TICKS = 10;

    @Override public String getId() { return "flesh_ripping"; }
    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }
    @Override public int getMinChargeTicks() { return 0; }
    @Override public int getMaxChargeTicks() { return 1; }
    @Override public int getSessionTimeoutTicks(PlayerAbilityData data) { return Integer.MAX_VALUE; }

    @Override
    public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        float rawCp = lerpf(130, 270, data.getProficiency(getId()));
        return DynamicSkillRules.enabled(getId())
                && (data.isDevMode() || data.getCurrentCp() >= DynamicSkillRules.cp(getId(), rawCp));
    }

    @Override public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
        ACTIVE.put(player.getUUID(), findTarget(player, player.getEyePosition(), player.getLookAngle(),
                lerpf(6, 14, data.getProficiency(getId()))));
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float rawCp = lerpf(130, 270, data.getProficiency(getId()));
        if (!data.isDevMode() && data.getCurrentCp() < DynamicSkillRules.cp(getId(), rawCp)) return false;
        Target target = findTarget(player, player.getEyePosition(), player.getLookAngle(),
                lerpf(6, 14, data.getProficiency(getId())));
        ACTIVE.put(player.getUUID(), target);
        return true;
    }

    @Override public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return onChargingTick(player, data, ticks) ? TickResult.CONTINUE : TickResult.ABORT_RESOURCE;
    }

    @Override public boolean canRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        Target target = ACTIVE.get(player.getUUID());
        return target != null && target.entity != null && target.entity.isAlive()
                && target.entity.level() == player.level();
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        Target marked = ACTIVE.remove(player.getUUID());
        if (marked == null || marked.entity == null || !marked.entity.isAlive()
                || marked.entity.level() != player.level()) return false;
        float exp = data.getProficiency(getId());
        if (!DynamicSkillRules.payForced(data, getId(), lerpf(130, 270, exp), lerpf(60, 50, exp))) return false;

        ServerLevel level = player.serverLevel();
        Entity target = marked.entity;
        rip(player, level, target,
                PassiveDamageHelper.teleporter(player, data, target, getId(), lerpf(5, 12, exp)).damage());
        if (level.random.nextFloat() < .05f)
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100));
        DynamicSkillRules.addExp(player, data, getId(), .005f);
        return true;
    }

    /** Mob path: mark along the aim each tick, then rip the last marked entity on release. */
    @Override public int mobChargeTicks(float proficiency) { return MOB_MARK_TICKS; }

    @Override
    public boolean mobChargeTick(MobCastContext context, int tick) {
        if (context.target() == null) return false;
        MOB_ACTIVE.put(context.caster().getUUID(), findTarget(context.caster(), context.eye(), context.look(),
                lerpf(6, 14, context.proficiency())));
        return true;
    }

    @Override public void mobChargeAborted(MobCastContext context) { MOB_ACTIVE.remove(context.caster().getUUID()); }

    @Override
    public boolean mobCast(MobCastContext context) {
        LivingEntity caster = context.caster();
        Target marked = MOB_ACTIVE.remove(caster.getUUID());
        if (context.target() == null || marked == null || marked.entity == null || !marked.entity.isAlive()
                || marked.entity.level() != caster.level()) return false;
        rip(caster, context.level(), marked.entity, context.damage(lerpf(5, 12, context.proficiency())));
        return true;
    }

    @Override public int mobCooldownTicks(float proficiency) { return getCooldownTicks(proficiency); }

    /** The rip after payment, shared by players and mob casters; {@code damage} is already config-scaled. */
    private static void rip(LivingEntity caster, ServerLevel level, Entity target, float damage) {
        AcademyDamageHelper.hurt(caster, target,
                com.mohistmc.academy.skill.SkillDamageSource.magic(caster), damage);
        int splashes = 5 + level.random.nextInt(2);
        for (int i = 0; i < splashes; i++) {
            double theta = level.random.nextDouble() * Math.PI * 2;
            double radius = target.getBbWidth() * (.4 + level.random.nextDouble() * .1);
            EffectHelper.bloodSplash(level,
                    target.getX() + radius * Math.sin(theta),
                    target.getY() + level.random.nextDouble() * target.getBbHeight(),
                    target.getZ() + radius * Math.cos(theta),
                    .8f + level.random.nextFloat() * .5f);
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                AcademySounds.TP_GUTS, caster instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE, .6f, 1f);
    }

    @Override public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        tryRelease(player, data, ticks);
    }
    @Override public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {
        ACTIVE.remove(player.getUUID());
    }
    @Override public void execute(ServerPlayer player, PlayerAbilityData data) {}
    @Override public int getCooldownTicks(float proficiency) { return (int) lerpf(90, 40, proficiency); }

    private static Target findTarget(LivingEntity player, Vec3 start, Vec3 look, double range) {
        Vec3 intended = start.add(look.scale(range));
        HitResult wall = LoadedRaycast.clip(player.level(), new ClipContext(start, intended,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = wall.getType() == HitResult.Type.MISS ? intended : wall.getLocation();
        AABB search = new AABB(start, end).inflate(1);
        List<Entity> entities = player.level().getEntities(player, search,
                entity -> entity != player && entity.isAlive() && entity.isPickable()
                        && (entity instanceof LivingEntity || entity instanceof EnderDragonPart));
        Entity closest = null;
        Vec3 closestPoint = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : entities) {
            var clip = entity.getBoundingBox().inflate(.3).clip(start, end);
            if (clip.isPresent() && start.distanceToSqr(clip.get()) < best) {
                best = start.distanceToSqr(clip.get());
                closest = entity;
                closestPoint = clip.get();
            }
        }
        return new Target(closestPoint == null ? end : closestPoint, closest);
    }
}
