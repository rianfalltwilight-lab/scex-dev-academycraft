package com.mohistmc.academy.skill.ability.meltdowner;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.api.mob.MobCastContext;
import com.mohistmc.academy.api.mob.MobCastable;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.entity.MdBallEntity;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.passive.PassiveDamageHelper;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** 1.0.7 held five-ball missile context with zero-based spawn/attack cadence. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class ElectronMissileEffect implements ChargingSkillEffect, MobCastable {
    private static final float START_OVERLOAD = 200;
    private static final int MAX_ORBS = 5;

    private static final class State {
        final List<UUID> orbs = new ArrayList<>();
        final float overloadFloor;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        State(float overloadFloor, ServerPlayer player) {
            this.overloadFloor = overloadFloor; dimension = player.level().dimension();
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /**
     * Orbs of a mob caster's running missile context.  An entry exists only while the mob charge runs;
     * release and abort (death, unload, dimension change) remove it and discard the orbs.
     */
    private static final Map<UUID, List<MdBallEntity>> MOB_ORBS = new ConcurrentHashMap<>();

    @Override public String getId() { return "electron_missile"; }
    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }
    @Override public int getMinChargeTicks() { return 0; }
    @Override public int getMaxChargeTicks() { return 200; }
    @Override public int getMaxChargeTicks(PlayerAbilityData data) {
        return (int) lerpf(80, 200, data.getProficiency(getId()));
    }
    @Override public int getSessionTimeoutTicks(PlayerAbilityData data) {
        // Server state ticks begin at one; the old context's first update was
        // tick zero and terminated only after its zero-based time limit.
        return getMaxChargeTicks(data) + 2;
    }

    @Override
    public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.canPay(data, getId(), 0, START_OVERLOAD);
    }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
        if (!DynamicSkillRules.tryPay(data, getId(), 0, START_OVERLOAD)) return;
        STATES.put(player.getUUID(), new State(data.getCurrentOverload(), player));
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        State state = STATES.get(player.getUUID());
        if (state == null || !state.dimension.equals(player.level().dimension())) return false;
        float exp = data.getProficiency(getId());
        if (!DynamicSkillRules.tryPay(data, getId(), lerpf(12, 5, exp), 0)) return false;
        if (!data.isDevMode() && data.getCurrentOverload() < state.overloadFloor) {
            data.setCurrentOverload(state.overloadFloor);
        }

        int legacyTick = ticks - 1;
        // EMContextC received MSG_EFFECT_UPDATE on every successful upkeep
        // tick, including the final over-limit tick which terminated it.
        EffectHelper.meltdownBurst(player.serverLevel(), player.getX(), player.getY() + .4,
                player.getZ(), 1 + player.getRandom().nextInt(3), .7);
        if (legacyTick > getMaxChargeTicks(data)) return true;
        if (legacyTick % 10 == 0 && state.orbs.size() < MAX_ORBS) spawnOrb(player, state);
        if (legacyTick != 0 && legacyTick % 8 == 0 && !state.orbs.isEmpty()) {
            attackNearest(player, data, state, exp);
        }
        return true;
    }

    private static void spawnOrb(ServerPlayer player, State state) {
        MdBallEntity ball = AcademyEntities.MD_BALL.get().create(player.serverLevel());
        if (ball == null) return;
        ball.bind(player.getUUID(), state.orbs.size(), false);
        ball.setPos(player.position());
        if (player.serverLevel().addFreshEntity(ball)) state.orbs.add(ball.getUUID());
    }

    private void attackNearest(ServerPlayer player, PlayerAbilityData data, State state, float exp) {
        float cp = lerpf(60, 25, exp);
        float overload = lerpf(9, 4, exp);
        if (!DynamicSkillRules.canPay(data, getId(), cp, overload)) return;
        float range = lerpf(5, 13, exp);
        LivingEntity target = player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(range),
                        entity -> entity != player && entity.isAlive()
                                && entity.distanceToSqr(player) <= range * range)
                .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (target == null || !DynamicSkillRules.tryPay(data, getId(), cp, overload)) return;

        UUID id = state.orbs.remove(player.getRandom().nextInt(state.orbs.size()));
        Entity ball = player.serverLevel().getEntity(id);
        var from = ball == null ? player.getEyePosition() : ball.position();
        int frames = target.invulnerableTime;
        target.invulnerableTime = -1;
        PassiveDamageHelper.meltdownerAttack(player, data, target, getId(), lerpf(10, 18, exp));
        // A rejected hit must not leave the target open to everyone else's hits.
        if (target.invulnerableTime == -1) target.invulnerableTime = frames;
        EffectHelper.mdRaySmall(player.serverLevel(), from, target.getEyePosition());
        player.serverLevel().playSound(null, from.x, from.y, from.z,
                AcademySounds.MD_RAY_SMALL, SoundSource.PLAYERS, .5F, 1.0F);
        if (ball != null) ball.discard();
        if (!data.isDevMode()) DynamicSkillRules.addExp(player, data, getId(), .001F);
    }

    @Override public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return onChargingTick(player, data, ticks) ? TickResult.CONTINUE : TickResult.ABORT_RESOURCE;
    }

    @Override public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        State state = STATES.remove(player.getUUID());
        if (state == null) return false;
        clear(player, state);
        return true;
    }

    private static void clear(ServerPlayer player, State state) {
        var original = player.server.getLevel(state.dimension);
        for (UUID id : state.orbs) {
            Entity entity = original == null ? null : original.getEntity(id);
            if (entity != null) entity.discard();
        }
        state.orbs.clear();
    }

    @Override public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        tryRelease(player, data, ticks);
    }

    @Override public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {
        State state = STATES.remove(player.getUUID());
        if (state == null) return;
        clear(player, state);
        if (!data.isDevMode()) data.setCooldown(getId(), getCooldownTicks(data.getProficiency(getId())));
    }

    @Override public void execute(ServerPlayer player, PlayerAbilityData data) {}

    /** Mob cast: the player's whole held context (80..200 ticks); the charge ticks are the volley. */
    @Override public int mobChargeTicks(float proficiency) { return (int) lerpf(80, 200, proficiency); }

    @Override public int mobCooldownTicks(float proficiency) { return getCooldownTicks(proficiency); }

    /** Same zero-based cadence as the player: an orb every 10 ticks (max 5), a shot every 8. */
    @Override
    public boolean mobChargeTick(MobCastContext context, int ticks) {
        LivingEntity caster = context.caster();
        ServerLevel level = context.level();
        List<MdBallEntity> orbs = MOB_ORBS.computeIfAbsent(caster.getUUID(), id -> new ArrayList<>());
        EffectHelper.meltdownBurst(level, caster.getX(), caster.getY() + .4, caster.getZ(),
                1 + caster.getRandom().nextInt(3), .7);
        int legacyTick = ticks - 1;
        if (legacyTick % 10 == 0 && orbs.size() < MAX_ORBS) {
            MdBallEntity ball = AcademyEntities.MD_BALL.get().create(level);
            if (ball != null) {
                ball.bind(caster.getUUID(), orbs.size(), false);
                ball.setPos(caster.position());
                if (level.addFreshEntity(ball)) orbs.add(ball);
            }
        }
        if (legacyTick != 0 && legacyTick % 8 == 0 && !orbs.isEmpty()) {
            mobAttack(context, orbs);
        }
        return true;
    }

    /** Aims at the mob's target when it is in range, else the nearest living entity it may hurt. */
    private void mobAttack(MobCastContext context, List<MdBallEntity> orbs) {
        LivingEntity caster = context.caster();
        ServerLevel level = context.level();
        float exp = context.proficiency();
        float range = lerpf(5, 13, exp);
        LivingEntity target = context.target();
        if (target == null || target.distanceToSqr(caster) > range * range) {
            target = level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range),
                            entity -> entity != caster && entity.isAlive()
                                    && entity.distanceToSqr(caster) <= range * range
                                    && AcademyDamageHelper.allowsTarget(caster, entity))
                    .stream().min(Comparator.comparingDouble(caster::distanceToSqr)).orElse(null);
        }
        if (target == null) return;
        MdBallEntity ball = orbs.remove(caster.getRandom().nextInt(orbs.size()));
        var from = ball.isRemoved() ? caster.getEyePosition() : ball.position();
        if (AcademyDamageHelper.allowsTarget(caster, target)) {
            int frames = target.invulnerableTime;
            target.invulnerableTime = -1;
            AcademyDamageHelper.hurt(caster, target, com.mohistmc.academy.skill.SkillDamageSource.attack(caster),
                    DynamicSkillRules.damage(getId(), lerpf(10, 18, exp)));
            if (target.invulnerableTime == -1) target.invulnerableTime = frames;
        }
        EffectHelper.mdRaySmall(level, from, target.getEyePosition());
        level.playSound(null, from.x, from.y, from.z, AcademySounds.MD_RAY_SMALL, SoundSource.HOSTILE, .5F, 1.0F);
        ball.discard();
    }

    /** End of the volley: leftover orbs vanish, as on a player's release. */
    @Override
    public boolean mobCast(MobCastContext context) {
        mobChargeAborted(context);
        return true;
    }

    @Override
    public void mobChargeAborted(MobCastContext context) {
        List<MdBallEntity> orbs = MOB_ORBS.remove(context.caster().getUUID());
        if (orbs != null) orbs.forEach(Entity::discard);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        MOB_ORBS.clear();
    }

    @Override public int getCooldownTicks(float exp) {
        // Source calls clampi(700, 400, exp.toInt); LambdaLib's
        // clamp(min,max,value) therefore returns the lower bound, 700.
        return 700;
    }
}
