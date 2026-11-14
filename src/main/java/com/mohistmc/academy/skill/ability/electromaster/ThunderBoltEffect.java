package com.mohistmc.academy.skill.ability.electromaster;

import com.mohistmc.academy.skill.ability.LoadedRaycast;
import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.api.mob.MobCastContext;
import com.mohistmc.academy.api.mob.MobCastable;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.world.effect.EffectHelper;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import com.mohistmc.academy.world.AcademySounds;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** 雷击之枪 —— 对视线方向发射闪电，命中目标+AOE伤害 */
public class ThunderBoltEffect implements SkillEffect, MobCastable {

    private static final double RANGE = 20.0;
    private static final double AOE_RANGE = 8.0;

    @Override
    public String getId() {
        return "thunder_bolt";
    }

    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        float exp = data.getProficiency(getId());
        return DynamicSkillRules.canPay(data, getId(), (int) lerpf(280, 420, exp), lerpf(50, 27, exp));
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        ServerLevel level = player.serverLevel();
        float proficiency = data.getProficiency(getId());

        int cp = (int) lerpf(280, 420, proficiency);
        float overload = lerpf(50, 27, proficiency);
        if (!DynamicSkillRules.tryPay(data,getId(),cp,overload)) return;

        boolean effective = bolt(player, level, proficiency, player.getEyePosition(), player.getLookAngle());
        DynamicSkillRules.addExp(player,data, getId(), effective ? 0.005f : 0.003f);
    }

    @Override
    public boolean mobCast(MobCastContext context) {
        bolt(context.caster(), context.level(), context.proficiency(), context.eye(), context.look());
        return true;
    }

    @Override
    public int mobCooldownTicks(float proficiency) {
        return getCooldownTicks(proficiency);
    }

    /**
     * The bolt after CP payment, shared by players and mob casters; returns whether anything was hit
     * (the player path turns that into exp).  Mob casters never slow targets they may not hit.
     */
    private boolean bolt(LivingEntity caster, ServerLevel level, float proficiency, Vec3 eyePos, Vec3 lookVec) {
        float damage = lerpf(10, 25, proficiency);
        float aoeDamage = lerpf(6, 15, proficiency);

        Vec3 endPos = eyePos.add(lookVec.scale(RANGE));

        EntityHitResult entityHit = rayTraceEntities(caster, eyePos, endPos);
        // Same clip as LoadedRaycast.pick(player, RANGE), along the given aim.
        BlockHitResult blockHit = LoadedRaycast.clip(level, new ClipContext(eyePos, endPos,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));

        Vec3 impactPoint;
        final Entity targetEntity;
        boolean hitEntity = false;

        if (entityHit != null) {
            double entityDist = eyePos.distanceTo(entityHit.getLocation());
            double blockDist = eyePos.distanceTo(blockHit.getLocation());
            if (entityDist < blockDist) {
                targetEntity = entityHit.getEntity();
                impactPoint = targetEntity.position().add(0,targetEntity.getEyeHeight(),0);
                hitEntity = true;
            } else {
                targetEntity = null;
                impactPoint = blockHit.getLocation();
            }
        } else {
            targetEntity = null;
            impactPoint = blockHit.getLocation();
        }

        if (hitEntity && targetEntity != null && targetEntity.isAlive()) {
            ElectromasterDamageHelper.attack(caster,targetEntity,com.mohistmc.academy.skill.SkillDamageSource.attack(caster),
                    DynamicSkillRules.damage(getId(),damage));

            if (proficiency > 0.2 && level.random.nextDouble() < 0.8 && targetEntity instanceof LivingEntity living
                    && affects(caster, living)) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
            }

        }

        List<Entity> aoes = level.getEntities(caster,
                new AABB(impactPoint.x - AOE_RANGE, impactPoint.y - AOE_RANGE, impactPoint.z - AOE_RANGE,
                        impactPoint.x + AOE_RANGE, impactPoint.y + AOE_RANGE, impactPoint.z + AOE_RANGE),
                e -> e instanceof LivingEntity && e.isAlive() && e != caster
                        && (targetEntity == null || e != targetEntity)
                        && ElectromasterRules.thunderBoltAoeContains(
                                e.getX()-impactPoint.x,e.getY()-impactPoint.y,e.getZ()-impactPoint.z));

        for (Entity e : aoes) {
            ElectromasterDamageHelper.attack(caster,e,com.mohistmc.academy.skill.SkillDamageSource.attack(caster),
                    DynamicSkillRules.damage(getId(),aoeDamage));

            // Preserve the actual 1.0.7 branch: it checked and slowed ad.target
            // inside the AOE loop, rather than applying the effect to e.
            if (proficiency > 0.2 && level.random.nextDouble() < 0.8
                    && targetEntity instanceof LivingEntity primary && affects(caster, primary)) {
                primary.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3));
            }
        }

        AcademySounds.playSound(level, caster.getX(), caster.getY(), caster.getZ(),
                AcademySounds.EM_ARC_STRONG, caster instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE, 0.6f, 1.0f);

        // The official client spawned three overlapping strong main arcs.
        Vec3 fullArcEnd = eyePos.add(lookVec.scale(RANGE));
        for(int i=0;i<3;i++) EffectHelper.electricTether(level, eyePos, fullArcEnd, 20);
        for (Entity e : aoes) {
            EffectHelper.electricTether(level, impactPoint,
                    e.position().add(0,e.getEyeHeight(),0),15+level.random.nextInt(11));
        }

        return hitEntity || !aoes.isEmpty();
    }

    /** Players keep the legacy unconditional slow; a mob only slows what it may damage. */
    private static boolean affects(LivingEntity caster, Entity target) {
        return caster instanceof Player || AcademyDamageHelper.allowsTarget(caster, target);
    }

    private EntityHitResult rayTraceEntities(LivingEntity caster, Vec3 start, Vec3 end) {
        AABB searchArea = new AABB(start,end).inflate(1.0);
        List<Entity> entities = caster.level().getEntities(caster, searchArea,
                e -> e != caster && e.isAlive() && e.isPickable()
                        // Legacy Raytrace.traceLiving: only living entities are targets.
                        && e instanceof net.minecraft.world.entity.LivingEntity);

        Entity closest = null;
        double closestDist = Double.MAX_VALUE;
        Vec3 closestHit = null;

        for (Entity entity : entities) {
            AABB box = entity.getBoundingBox().inflate(0.3);
            var result = box.clip(start, end);
            if (result.isPresent()) {
                double dist = start.distanceTo(result.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = entity;
                    closestHit = result.get();
                }
            }
        }

        if (closest != null) {
            return new EntityHitResult(closest, closestHit);
        }
        return null;
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return (int) lerpf(120, 50, proficiency);
    }

    @Override public boolean cooldownUsesPreActivationProficiency() { return true; }
}

