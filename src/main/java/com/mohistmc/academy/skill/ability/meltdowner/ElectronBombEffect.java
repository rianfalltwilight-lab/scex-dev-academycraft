package com.mohistmc.academy.skill.ability.meltdowner;

import com.mohistmc.academy.skill.ability.LoadedRaycast;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.api.mob.MobCastContext;
import com.mohistmc.academy.api.mob.MobCastable;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.entity.MdBallEntity;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.SkillDamageSource;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import com.mohistmc.academy.skill.passive.PassiveDamageHelper;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/** Delayed server-owned port of the 1.0.7 Electron Bomb context. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class ElectronBombEffect implements SkillEffect, MobCastable {
    private static final double RANGE = 15;

    private static final class Ball {
        final UUID owner;
        /** Non-null for a mob caster; the entry lives at most 20 ticks and leaves with its owner. */
        final @Nullable LivingEntity mob;
        /** The mob's aim target, re-aimed at fire time like the player's recomputed look. */
        final @Nullable LivingEntity target;
        final MdBallEntity visual;
        final float proficiency;
        int remaining;
        boolean fired;

        Ball(LivingEntity caster, @Nullable LivingEntity target, int life, float proficiency, MdBallEntity visual) {
            owner = caster.getUUID();
            mob = caster instanceof ServerPlayer ? null : caster;
            this.target = target;
            remaining = life;
            this.proficiency = proficiency;
            this.visual = visual;
        }
    }

    private static final List<Ball> BALLS = new ArrayList<>();

    @Override public String getId() { return "electron_bomb"; }
    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }

    private float cp(PlayerAbilityData data) { return lerpf(35, 80, data.getProficiency(getId())); }
    private float overload(PlayerAbilityData data) { return lerpf(16, 13, data.getProficiency(getId())); }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.canPay(data, getId(), cp(data), overload(data));
    }

    @Override public void execute(ServerPlayer player, PlayerAbilityData data) { executeAndReport(player, data); }

    public boolean executeAndReport(ServerPlayer player, PlayerAbilityData data) {
        if (!canActivate(player, data)) return false;
        float proficiency = data.getProficiency(getId());
        MdBallEntity visual = spawnBall(player, player.serverLevel(), proficiency);
        if (visual == null) return false;
        if (!DynamicSkillRules.tryPay(data, getId(), cp(data), overload(data))) {
            visual.discard();
            return false;
        }
        DynamicSkillRules.addExp(player, data, getId(), .005f);
        BALLS.add(new Ball(player, null, visual.lifetime(), proficiency, visual));
        return true;
    }

    /** Mob cast: same ball and delayed ray, no CP or exp; damage goes through AcademyDamageHelper. */
    @Override
    public boolean mobCast(MobCastContext context) {
        MdBallEntity visual = spawnBall(context.caster(), context.level(), context.proficiency());
        if (visual == null) return false;
        BALLS.add(new Ball(context.caster(), context.target(), visual.lifetime(), context.proficiency(), visual));
        return true;
    }

    @Override public int mobCooldownTicks(float proficiency) { return getCooldownTicks(proficiency); }

    private static @Nullable MdBallEntity spawnBall(LivingEntity caster, ServerLevel level, float proficiency) {
        int life = proficiency >= .8f ? 5 : 20;
        MdBallEntity visual = new MdBallEntity(AcademyEntities.MD_BALL.get(), level)
                .bind(caster.getUUID(), 0, false, life);
        return level.addFreshEntity(visual) ? visual : null;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        Iterator<Ball> iterator = BALLS.iterator();
        while (iterator.hasNext()) {
            Ball ball = iterator.next();
            LivingEntity caster = ball.mob != null ? ball.mob
                    : event.getServer().getPlayerList().getPlayer(ball.owner);
            if (caster == null || !caster.isAlive() || caster.level() != ball.visual.level()
                    || (ball.mob != null && ball.mob.isRemoved())
                    || (caster instanceof ServerPlayer player && AbilityInterferenceService.isInterfered(player))) {
                ball.visual.discard();
                iterator.remove();
                continue;
            }
            if (ball.visual.isRemoved()) {
                iterator.remove();
                continue;
            }

            ball.remaining--;
            // EntityMdBall invoked its callback at life - 2, then remained for
            // the final two ticks. The five-tick mastery variant fires at 3.
            if (!ball.fired && ball.remaining == 2) {
                fire(caster, ball);
                ball.fired = true;
            }
            if (ball.remaining <= 0) {
                ball.visual.discard();
                iterator.remove();
            }
        }
    }

    private static void fire(LivingEntity caster, Ball ball) {
        ServerLevel level = (ServerLevel) ball.visual.level();
        Vec3 from = ball.visual.position();
        Vec3 destination = lookingDestination(caster, level, aim(caster, ball.target), RANGE);
        Entity hit = firstEntity(caster, level, from, destination);
        if (hit != null) {
            if (caster instanceof ServerPlayer player) {
                PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
                PassiveDamageHelper.meltdownerAttack(player, data, hit, "electron_bomb",
                        lerpf(6, 12, ball.proficiency));
            } else {
                AcademyDamageHelper.hurt(caster, hit, SkillDamageSource.attack(caster),
                        DynamicSkillRules.damage("electron_bomb", lerpf(6, 12, ball.proficiency)));
            }
        }

        // EBContextC recomputed the player's looking destination and did not
        // shorten the client ray to the server-side ball obstruction.
        EffectHelper.mdRaySmall(level, from, destination, 14);
        level.playSound(null, from.x, from.y, from.z, AcademySounds.MD_RAY_SMALL,
                caster instanceof ServerPlayer ? SoundSource.PLAYERS : SoundSource.HOSTILE, .5f, 1f);
    }

    /** A player's current look; a mob re-aims at its living target (else its own look). */
    private static Vec3 aim(LivingEntity caster, @Nullable LivingEntity target) {
        if (!(caster instanceof ServerPlayer) && target != null && target.isAlive() && !target.isRemoved()
                && target.level() == caster.level()) {
            Vec3 toward = target.getEyePosition().subtract(caster.getEyePosition());
            if (toward.lengthSqr() > 1.0e-8) return toward;
        }
        return caster.getLookAngle();
    }

    static Vec3 lookingDestination(LivingEntity caster, ServerLevel level, Vec3 look, double range) {
        Vec3 from = caster.getEyePosition();
        Vec3 intended = from.add(look.normalize().scale(range));
        HitResult block = LoadedRaycast.clip(level, new ClipContext(from, intended,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double best = block.getType() == HitResult.Type.MISS
                ? from.distanceToSqr(intended) : from.distanceToSqr(block.getLocation());
        Entity nearest = null;
        for (Entity candidate : level.getEntities(caster,
                new AABB(from, intended).inflate(1),
                candidate -> candidate != caster && candidate.isAlive() && candidate.isPickable())) {
            var intercept = candidate.getBoundingBox().inflate(.3).clip(from, intended);
            if (intercept.isEmpty()) continue;
            double distance = from.distanceToSqr(intercept.get());
            if (distance <= best) {
                best = distance;
                nearest = candidate;
            }
        }
        if (nearest != null) {
            return nearest.position().add(0, nearest.getEyeHeight() * .6, 0);
        }
        return block.getType() == HitResult.Type.MISS ? intended : block.getLocation();
    }

    static Entity firstEntity(LivingEntity caster, ServerLevel level, Vec3 from, Vec3 destination) {
        HitResult block = LoadedRaycast.clip(level, new ClipContext(from, destination,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double best = block.getType() == HitResult.Type.MISS
                ? from.distanceToSqr(destination) : from.distanceToSqr(block.getLocation());
        Entity nearest = null;
        for (Entity candidate : level.getEntities(caster,
                new AABB(from, destination).inflate(1), candidate -> candidate != caster
                        && !(candidate instanceof MdBallEntity)
                        && candidate.isAlive() && candidate.isPickable())) {
            var intercept = candidate.getBoundingBox().inflate(.3).clip(from, destination);
            if (intercept.isEmpty()) continue;
            double distance = from.distanceToSqr(intercept.get());
            if (distance <= best) {
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { BALLS.clear(); }

    @Override public int getCooldownTicks(float proficiency) { return (int) lerpf(20, 10, proficiency); }
}
