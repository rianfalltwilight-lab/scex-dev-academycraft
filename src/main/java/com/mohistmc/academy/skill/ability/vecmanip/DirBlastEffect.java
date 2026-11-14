package com.mohistmc.academy.skill.ability.vecmanip;

import com.mohistmc.academy.skill.ability.LoadedRaycast;
import com.mohistmc.academy.skill.AcademyDamageHelper;

import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.api.mob.MobCastContext;
import com.mohistmc.academy.api.mob.MobCastable;
import com.mohistmc.academy.world.effect.EffectHelper;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.AcademySounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** 定向爆破 —— 蓄力后向目标位置释放爆破冲击，伤害敌人并破坏方块 */
public class DirBlastEffect implements ChargingSkillEffect, MobCastable {

    // 1.0.7 accepts only ticker > 6 && ticker < 50.
    private static final int MIN_TICKS = 7;
    private static final int MAX_TICKS = 49;
    private static final int MAX_TOLERANT_TICKS = 200;
    /** A mob releases inside the legacy 7..49 window. */
    private static final int MOB_CHARGE_TICKS = 15;
    private static final double RANGE = 4.0;
    private static final double AOE_RANGE = 3.0;

    @Override
    public String getId() {
        return "dir_blast";
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
        float cp = lerpf(160, 200, data.getProficiency(getId()));
        float overload = lerpf(50, 30, data.getProficiency(getId()));
        return ChargingSkillEffect.super.canRelease(player, data, ticks)
                && DynamicSkillRules.canPay(data, getId(), cp, overload);
    }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
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
        if (ticks < MIN_TICKS || ticks > MAX_TICKS) return;

        float exp = data.getProficiency(getId());
        float cp = lerpf(160, 200, exp);
        float overload = lerpf(50, 30, exp);

        if (!DynamicSkillRules.tryPay(data,getId(),cp,overload)) return;

        ServerLevel level = player.serverLevel();
        boolean effective = blast(player, level, exp, player.getEyePosition(), player.getLookAngle(),
                player.getYHeadRot(), player.getXRot(), DynamicSkillRules.destroysBlocks(level, getId()));
        DynamicSkillRules.addExp(player,data, getId(), effective ? 0.0025f : 0.0012f);
    }

    /** Mob path: a fixed charge inside the legacy window, then the blast along the aim. */
    @Override public int mobChargeTicks(float proficiency) { return MOB_CHARGE_TICKS; }

    @Override
    public boolean mobCast(MobCastContext context) {
        Vec3 look = context.look();
        float yaw = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (Math.asin(Mth.clamp(-look.y, -1, 1)) * Mth.RAD_TO_DEG);
        blast(context.caster(), context.level(), context.proficiency(), context.eye(), look, yaw, pitch,
                context.mayDestroyBlocks());
        return true;
    }

    @Override public int mobCooldownTicks(float proficiency) { return getCooldownTicks(proficiency); }

    /**
     * The blast after payment, shared by players and mob casters; returns whether an entity was caught.
     * A player's block breaking keeps its interaction check and BreakEvent; a mob's is decided by
     * {@code destroyBlocks} (its cast-time griefing rules).
     */
    private boolean blast(LivingEntity caster, ServerLevel level, float exp, Vec3 eyePos, Vec3 lookDir,
                          float yaw, float pitch, boolean destroyBlocks) {
        float damage = lerpf(10, 25, exp);
        float breakProb = lerpf(0.5f, 0.8f, exp);
        float dropRate = lerpf(0.4f, 0.9f, exp);

        float breakHardness;
        if (exp < 0.25f) breakHardness = 2.9f;
        else if (exp < 0.5f) breakHardness = 25f;
        else breakHardness = 55f;

        Vec3 rayEnd=eyePos.add(lookDir.scale(RANGE));
        var blockHit=LoadedRaycast.clip(level, new ClipContext(eyePos,rayEnd,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,caster));
        Vec3 position = blockHit.getType()==HitResult.Type.MISS?rayEnd:blockHit.getLocation();
        double closestTarget=eyePos.distanceToSqr(position);

        // Raytrace.traceLiving(..., EntitySelectors.living) in 1.0.7 only
        // accepted LivingEntity/dragon-part targets and stopped at the first
        // collidable block.  A projectile in front of the player must not move
        // the centre of the blast.
        var entities = level.getEntities(caster,
                caster.getBoundingBox().inflate(RANGE),
                e -> e.isAlive() && e.isPickable()
                        && (e instanceof LivingEntity || e instanceof EnderDragonPart));
        for (Entity e : entities) {
            var result = e.getBoundingBox().clip(eyePos, eyePos.add(lookDir.scale(RANGE)));
            if (result.isPresent() && eyePos.distanceToSqr(result.get()) < closestTarget) {
                position = e.getEyePosition();
                closestTarget=eyePos.distanceToSqr(result.get());
            }
        }

        Vec3 finalPos = position;
        AABB aoeArea = new AABB(
                finalPos.x - AOE_RANGE, finalPos.y - AOE_RANGE, finalPos.z - AOE_RANGE,
                finalPos.x + AOE_RANGE, finalPos.y + AOE_RANGE, finalPos.z + AOE_RANGE
        );
        boolean effective = false;
        boolean player = caster instanceof Player;

        for (Entity e : level.getEntities(caster, aoeArea,
                candidate -> candidate != caster && candidate.isAlive()
                        && candidate.position().distanceToSqr(finalPos) <= AOE_RANGE * AOE_RANGE)) {
                // A mob neither hurts nor throws its allies, its owner or its own kind.
                if (!player && !AcademyDamageHelper.allowsTarget(caster, e)) continue;
                AcademyDamageHelper.hurt(caster,e,com.mohistmc.academy.skill.SkillDamageSource.attack(caster), DynamicSkillRules.damage(getId(),damage));
                Vec3 delta = caster.getEyePosition().subtract(e.getEyePosition()).normalize();
                delta = new Vec3(delta.x, -0.4, delta.z).normalize();
                // BlastwaveContext.knockback first lifted the entity by 0.1,
                // replaced its velocity, then added the common 0.24 impulse.
                e.setPos(e.getX(), e.getY() + .1, e.getZ());
                Vec3 outward = e.position().subtract(caster.position()).normalize().scale(0.24);
                e.setDeltaMovement(delta.scale(-1.2).add(outward));
                e.hurtMarked = true;
                effective = true;
        }

        int cx = (int) Math.round(finalPos.x);
        int cy = (int) Math.round(finalPos.y);
        int cz = (int) Math.round(finalPos.z);

        // Scala's (x - 3) until (x + 3) is [-3, +2], not [-3, +3].
        if (destroyBlocks) for (int dx = -3; dx < 3; dx++) {
            for (int dy = -3; dy < 3; dy++) {
                for (int dz = -3; dz < 3; dz++) {
                    if (dx * dx + dy * dy + dz * dz > 6) continue;
                    if ((dx != 0 || dy != 0 || dz != 0) && level.random.nextFloat() >= breakProb) continue;

                    BlockPos pos = new BlockPos(cx + dx, cy + dy, cz + dz);
                    if (!LoadedRaycast.isLoaded(level, pos) || !caster.mayInteract(level, pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    float hardness = state.getDestroySpeed(level, pos);

                    if (hardness >= 0 && hardness <= breakHardness && !state.isAir()) {
                        if (caster instanceof Player breaker) {
                            BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(level, pos, state, breaker);
                            NeoForge.EVENT_BUS.post(breakEvent);
                            if (breakEvent.isCanceled() || level.getBlockState(pos) != state) continue;
                        }
                        // Mobs get the same vanilla drop roll and nothing more.
                        level.destroyBlock(pos, level.random.nextFloat() < dropRate, caster);
                    }
                }
            }
        }

        float effectYaw=yaw-20+level.random.nextFloat()*40;
        float effectPitch=pitch-10+level.random.nextFloat()*20;
        EffectHelper.waveRings(level,caster.getEyePosition().lerp(finalPos,.7),
                Vec3.directionFromRotation(effectPitch,effectYaw),2,1);

        AcademySounds.playSound(level, finalPos.x, finalPos.y, finalPos.z,
                AcademySounds.VM_DIRECTED_BLAST, player ? SoundSource.PLAYERS : SoundSource.HOSTILE, 0.5f, 1.0f);
        return effective;
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
}
