package com.mohistmc.academy.skill.ability.meltdowner;

import com.mohistmc.academy.skill.ability.LoadedRaycast;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

import com.mohistmc.academy.api.mob.MobCastContext;
import com.mohistmc.academy.api.mob.MobCastable;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillDamageSource;
import com.mohistmc.academy.skill.SkillEffect;
import com.mohistmc.academy.skill.passive.PassiveDamageHelper;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.effect.EffectHelper;
import com.mohistmc.academy.world.entity.EntitySilbarn;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** 1.0.7 key-down barrage, including the special Silbarn fan branch. */
public final class RayBarrageEffect implements SkillEffect, MobCastable {
    private static final double RANGE = 20.0D;
    private static final float HALF_YAW = 27.5F;
    private static final float HALF_PITCH = 55.0F;

    private record Lock(EntitySilbarn silbarn, Vec3 point) {}
    private record LookingHit(Entity entity, Vec3 point) {}
    /** Where the caster stands and looks; players use their own rotation, mobs the context aim. */
    private record Aim(Vec3 origin, Vec3 eye, Vec3 look, float yaw, float pitch, Vec3 preRayFrom) {}

    @Override public String getId() { return "ray_barrage"; }
    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }

    private float cp(PlayerAbilityData data) { return lerpf(450, 380, data.getProficiency(getId())); }
    private float overload(PlayerAbilityData data) { return lerpf(300, 140, data.getProficiency(getId())); }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.canPay(data, getId(), cp(data), overload(data));
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        Aim aim = aim(player);
        Lock lock = acquire(player, player.serverLevel(), aim);
        if (!DynamicSkillRules.tryPay(data, getId(), cp(data), overload(data))) return;
        perform(player, player.serverLevel(), data, lock, aim, data.getProficiency("ray_barrage"));
        DynamicSkillRules.addExp(player, data, "ray_barrage", 0.005F);
    }

    /** Mob cast: same lock, fan and rays, no CP or exp; damage goes through AcademyDamageHelper. */
    @Override
    public boolean mobCast(MobCastContext context) {
        LivingEntity caster = context.caster();
        Vec3 look = context.look().normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(look.y, -1.0D, 1.0D)));
        Aim aim = new Aim(caster.position(), context.eye(), look, yaw, pitch, context.eye());
        perform(caster, context.level(), null, acquire(caster, context.level(), aim), aim, context.proficiency());
        return true;
    }

    @Override public int mobCooldownTicks(float proficiency) { return getCooldownTicks(proficiency); }

    private static Aim aim(ServerPlayer player) {
        return new Aim(player.position(), player.getEyePosition(), player.getLookAngle(),
                player.getYRot(), player.getXRot(), player.position().add(0, 1.6, 0));
    }

    private static Lock acquire(LivingEntity caster, ServerLevel level, Aim aim) {
        LookingHit hit = lookingHit(caster, level, aim);
        EntitySilbarn special = hit.entity instanceof EntitySilbarn candidate && !candidate.isHit()
                ? candidate : null;
        return new Lock(special, special == null ? hit.point : special.position());
    }

    /** The barrage after CP payment; {@code data} is non-null only for a player caster. */
    private static void perform(LivingEntity caster, ServerLevel level, @Nullable PlayerAbilityData data,
                                Lock lock, Aim aim, float exp) {
        // RBContext kept the lock it acquired in MSG_START; MSG_EXECUTE did
        // not silently demote it if another callback changed Silbarn state.
        EntitySilbarn silbarn = lock.silbarn;
        Vec3 point = silbarn == null ? lock.point : silbarn.position();
        SoundSource sounds = caster instanceof ServerPlayer ? SoundSource.PLAYERS : SoundSource.HOSTILE;

        if (silbarn != null) {
            silbarn.breakByRayBarrage();
            float damage = lerpf(10, 18, exp);
            for (Entity target : level.getEntities(caster, legacyFanBounds(aim),
                    target -> target != caster && target != silbarn)) {
                if (insideLegacyFan(aim, target)) {
                    attack(caster, data, target, damage);
                }
            }
            EffectHelper.barrageFan(level, point, aim.yaw, aim.pitch);
            level.playSound(null, point.x, point.y, point.z,
                    AcademySounds.MD_RAY_SMALL, sounds, 0.5F, 1.0F);
        } else {
            LookingHit plain = lookingHit(caster, level, aim);
            point = plain.point;
            if (plain.entity != null) {
                attack(caster, data, plain.entity, lerpf(25, 60, exp));
            }
        }

        EffectHelper.barragePreRay(level, aim.preRayFrom, point, silbarn == null ? 30 : 50);
        level.playSound(null, caster.getX(), caster.getEyeY(), caster.getZ(),
                AcademySounds.MD_RAY_SMALL, sounds, 0.8F, 1.0F);
    }

    private static void attack(LivingEntity caster, @Nullable PlayerAbilityData data, Entity target, float damage) {
        if (caster instanceof ServerPlayer player && data != null) {
            PassiveDamageHelper.meltdownerAttack(player, data, target, "ray_barrage", damage);
        } else {
            AcademyDamageHelper.hurt(caster, target, SkillDamageSource.attack(caster),
                    DynamicSkillRules.damage("ray_barrage", damage));
        }
    }

    private static boolean insideLegacyFan(Aim aim, Entity target) {
        Vec3 delta = target.getEyePosition().subtract(aim.eye);
        if (delta.lengthSqr() < 1.0E-8D) return false;
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float targetPitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        return Math.abs(Mth.wrapDegrees(targetYaw - aim.yaw)) <= HALF_YAW
                && Math.abs(targetPitch - aim.pitch) <= HALF_PITCH;
    }

    private static Vec3 blockEnd(LivingEntity caster, ServerLevel level, Vec3 from, Vec3 to) {
        var hit = LoadedRaycast.clip(level, new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
    }

    private static LookingHit lookingHit(LivingEntity caster, ServerLevel level, Aim aim) {
        Vec3 from = aim.eye;
        Vec3 intended = from.add(aim.look.scale(RANGE));
        Vec3 block = blockEnd(caster, level, from, intended);
        double bestDistance = from.distanceToSqr(block);
        Entity best = null;
        for (Entity candidate : level.getEntities(caster,
                new AABB(from, intended).inflate(1.0D),
                entity -> entity != caster && entity.isAlive() && entity.isPickable())) {
            var intercept = candidate.getBoundingBox().inflate(0.3D).clip(from, intended);
            if (intercept.isPresent() && from.distanceToSqr(intercept.get()) < bestDistance) {
                bestDistance = from.distanceToSqr(intercept.get());
                best = candidate;
            }
        }
        return best == null ? new LookingHit(null, block)
                : new LookingHit(best, best.position().add(0, best.getEyeHeight() * .6, 0));
    }

    private static AABB legacyFanBounds(Aim aim) {
        Vec3 origin = aim.origin;
        float minYaw = aim.yaw - HALF_YAW, maxYaw = aim.yaw + HALF_YAW;
        float minPitch = aim.pitch - HALF_PITCH, maxPitch = aim.pitch + HALF_PITCH;
        Vec3[] points = {origin,
                origin.add(Vec3.directionFromRotation(minPitch, minYaw).scale(RANGE)),
                origin.add(Vec3.directionFromRotation(maxPitch, minYaw).scale(RANGE)),
                origin.add(Vec3.directionFromRotation(maxPitch, maxYaw).scale(RANGE)),
                origin.add(Vec3.directionFromRotation(minPitch, maxYaw).scale(RANGE))};
        double minX=origin.x,minY=origin.y,minZ=origin.z,maxX=origin.x,maxY=origin.y,maxZ=origin.z;
        for(Vec3 point:points){minX=Math.min(minX,point.x);minY=Math.min(minY,point.y);minZ=Math.min(minZ,point.z);maxX=Math.max(maxX,point.x);maxY=Math.max(maxY,point.y);maxZ=Math.max(maxZ,point.z);}
        return new AABB(minX,minY,minZ,maxX,maxY,maxZ);
    }

    @Override public int getCooldownTicks(float exp) { return (int) lerpf(100, 40, exp); }
}
