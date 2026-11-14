package com.mohistmc.academy.skill.ability.vecmanip;

import com.mohistmc.academy.skill.ability.LoadedRaycast;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

import com.mohistmc.academy.api.mob.MobCastContext;
import com.mohistmc.academy.api.mob.MobCastable;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.entity.PlasmaOrbEntity;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.AcademySounds;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 1.0.7 PlasmaCannonContext: a visible fixed overhead body while held, then a 1-block/tick projectile. */
public final class PlasmaCannonEffect implements ChargingSkillEffect, MobCastable {
    private record State(float overloadFloor, UUID orbId,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {}
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    /** Mob casters' charging bodies, kept apart from the player sessions. */
    private static final Map<UUID, State> MOB_STATES = new ConcurrentHashMap<>();
    /** Server stop: forget mob charges (their orbs went with the world). */
    public static void clearMobSessions() { MOB_STATES.clear(); }

    @Override public String getId() { return "plasma_cannon"; }
    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }

    @Override public int getMinChargeTicks() { return 60; }
    @Override public int getMaxChargeTicks() { return 60; }
    @Override public int getMinChargeTicks(PlayerAbilityData data) { return chargeTime(data); }
    @Override public int getMaxChargeTicks(PlayerAbilityData data) { return chargeTime(data); }
    @Override public int getSessionTimeoutTicks(PlayerAbilityData data) { return Integer.MAX_VALUE; }

    @Override
    public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.canPay(data, getId(), 0, overloadToKeep(data));
    }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
        if (!DynamicSkillRules.tryPay(data, getId(), 0, overloadToKeep(data))) return;

        State stale = STATES.remove(player.getUUID());
        discard(player.serverLevel(), stale);

        PlasmaOrbEntity orb = AcademyEntities.PLASMA_ORB.get().create(player.serverLevel());
        if (orb == null) return;
        Vec3 chargePosition = player.position().add(0, 15, 0);
        orb.configureCharging(player, chargePosition);
        if (!player.serverLevel().addFreshEntity(orb)) return;

        STATES.put(player.getUUID(), new State(data.getCurrentOverload(), orb.getUUID(), player.level().dimension()));
        // The legacy FollowEntitySound was attached to the caster, while the
        // visible plasma body was fifteen blocks overhead.
        AcademySounds.playSound(player.serverLevel(), player.getX(), player.getY(), player.getZ(),
                AcademySounds.VM_PLASMA_CANNON, SoundSource.PLAYERS, .5f, 1f);
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        State state = STATES.get(player.getUUID());
        PlasmaOrbEntity orb = resolve(player.serverLevel(), state);
        if (state == null || !state.dimension().equals(player.level().dimension())
                || orb == null || orb.isArmed()) return false;

        if (!data.isDevMode() && data.getCurrentOverload() < state.overloadFloor()) {
            data.setCurrentOverload(state.overloadFloor());
        }

        if (ticks < chargeTime(data)
                && !DynamicSkillRules.tryPay(data, getId(), lerpf(18, 25, data.getProficiency(getId())), 0)) {
            return false;
        }

        if (ticks == chargeTime(data)) {
            AcademySounds.playSound(player.serverLevel(), player.getX(), player.getY(), player.getZ(),
                    AcademySounds.VM_PLASMA_CANNON_T, SoundSource.PLAYERS, .5f, 1f);
        }
        return true;
    }

    @Override
    public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return onChargingTick(player, data, ticks) ? TickResult.CONTINUE : TickResult.ABORT_RESOURCE;
    }

    @Override
    public boolean canRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        State state = STATES.get(player.getUUID());
        return ticks >= chargeTime(data) && state != null
                && state.dimension().equals(player.level().dimension()) && resolve(player.serverLevel(), state) != null;
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (!canRelease(player, data, ticks)) return false;
        State state = STATES.remove(player.getUUID());
        PlasmaOrbEntity orb = resolve(player.serverLevel(), state);
        if (orb == null) return false;

        float exp = data.getProficiency(getId());
        Vec3 destination = lookingDestination(player, player.getEyePosition(), player.getLookAngle(), 100);
        if (!orb.arm(destination, DynamicSkillRules.damage(getId(), lerpf(80, 150, exp)), lerpf(12, 15, exp))) {
            orb.discard();
            return false;
        }
        DynamicSkillRules.addExp(player, data, getId(), .008f);
        return true;
    }

    @Override public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        tryRelease(player, data, ticks);
    }

    @Override
    public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {
        discard(player.serverLevel(), STATES.remove(player.getUUID()));
    }

    @Override public void execute(ServerPlayer player, PlayerAbilityData data) {}
    @Override public int getCooldownTicks(float proficiency) { return (int) lerpf(1000, 600, proficiency); }

    /** Mob path: the player's full charge time, with the body overhead from the first charging tick. */
    @Override public int mobChargeTicks(float proficiency) { return chargeTime(proficiency); }

    @Override
    public boolean mobChargeTick(MobCastContext context, int tick) {
        LivingEntity caster = context.caster();
        ServerLevel level = context.level();
        if (tick == 1) {
            discard(level, MOB_STATES.remove(caster.getUUID()));
            PlasmaOrbEntity orb = AcademyEntities.PLASMA_ORB.get().create(level);
            if (orb == null) return false;
            orb.configureCharging(caster, caster.position().add(0, 15, 0));
            if (!level.addFreshEntity(orb)) return false;
            MOB_STATES.put(caster.getUUID(), new State(0, orb.getUUID(), level.dimension()));
            AcademySounds.playSound(level, caster.getX(), caster.getY(), caster.getZ(),
                    AcademySounds.VM_PLASMA_CANNON, SoundSource.HOSTILE, .5f, 1f);
            return true;
        }
        State state = MOB_STATES.get(caster.getUUID());
        PlasmaOrbEntity orb = resolve(level, state);
        if (state == null || !state.dimension().equals(level.dimension()) || orb == null || orb.isArmed()) return false;
        if (tick == chargeTime(context.proficiency()))
            AcademySounds.playSound(level, caster.getX(), caster.getY(), caster.getZ(),
                    AcademySounds.VM_PLASMA_CANNON_T, SoundSource.HOSTILE, .5f, 1f);
        return true;
    }

    @Override
    public void mobChargeAborted(MobCastContext context) {
        discard(context.level(), MOB_STATES.remove(context.caster().getUUID()));
    }

    @Override
    public boolean mobCast(MobCastContext context) {
        LivingEntity caster = context.caster();
        State state = MOB_STATES.remove(caster.getUUID());
        PlasmaOrbEntity orb = resolve(context.level(), state);
        if (orb == null) return false;
        float exp = context.proficiency();
        Vec3 destination = lookingDestination(caster, context.eye(), context.look(), 100);
        orb.setMobBreaksBlocks(context.mayDestroyBlocks());
        if (!orb.arm(destination, context.damage(lerpf(80, 150, exp)), lerpf(12, 15, exp))) {
            orb.discard();
            return false;
        }
        return true;
    }

    @Override public int mobCooldownTicks(float proficiency) { return getCooldownTicks(proficiency); }

    private int chargeTime(PlayerAbilityData data) {
        return chargeTime(data.getProficiency(getId()));
    }

    private static int chargeTime(float proficiency) {
        return (int) lerpf(60, 30, proficiency);
    }

    private float overloadToKeep(PlayerAbilityData data) {
        return lerpf(500, 400, data.getProficiency(getId()));
    }

    private static PlasmaOrbEntity resolve(ServerLevel level, State state) {
        if (state == null) return null;
        ServerLevel original = level.getServer().getLevel(state.dimension());
        Entity entity = original == null ? null : original.getEntity(state.orbId());
        return entity instanceof PlasmaOrbEntity orb && orb.isAlive() ? orb : null;
    }

    private static void discard(ServerLevel level, State state) {
        PlasmaOrbEntity orb = resolve(level, state);
        if (orb != null) orb.discard();
    }

    /** Equivalent to Raytrace.getLookingPos(player, 100, EntitySelectors.living), along the given aim. */
    private static Vec3 lookingDestination(LivingEntity player, Vec3 eye, Vec3 look, double range) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 end = eye.add(look.scale(range));
        // Same clip as LoadedRaycast.pick(player, range) for a player's own eye and look.
        HitResult blockHit = LoadedRaycast.clip(level, new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double nearest = blockHit.getType() == HitResult.Type.MISS
                ? range : eye.distanceTo(blockHit.getLocation());
        Vec3 destination = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

        for (Entity entity : level.getEntities(player, new AABB(eye, end).inflate(1),
                entity -> (entity instanceof LivingEntity || entity instanceof EnderDragonPart)
                        && entity.isAlive() && entity.isPickable())) {
            var clipped = entity.getBoundingBox().inflate(.3).clip(eye, end);
            if (clipped.isPresent()) {
                double distance = eye.distanceTo(clipped.get());
                if (distance < nearest) {
                    nearest = distance;
                    // Raytrace.getLookingPos used the entity position plus
                    // 60% of its eye height after selecting the intersected
                    // entity; the box intercept itself was not the endpoint.
                    destination = entity.position().add(0, entity.getEyeHeight() * .6, 0);
                }
            }
        }
        return destination;
    }
}
