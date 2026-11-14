package com.mohistmc.academy.skill.ability.meltdowner;

import com.mohistmc.academy.skill.ability.LoadedRaycast;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

import com.mohistmc.academy.api.mob.MobCastContext;
import com.mohistmc.academy.api.mob.MobCastable;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Final 1.12.2 mark-and-release Jet Engine with a server-authoritative travel phase. */
public final class JetEngineEffect implements ChargingSkillEffect, MobCastable {
    private static final double RANGE = 12;

    @Override public String getId() { return "jet_engine"; }
    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }
    @Override public int getMinChargeTicks() { return 1; }
    @Override public int getMaxChargeTicks() { return 1; }
    @Override public int getSessionTimeoutTicks(PlayerAbilityData data) { return Integer.MAX_VALUE; }
    @Override public TickResult getSessionTimeoutResult(
            ServerPlayer player, PlayerAbilityData data, int ticks) {
        return TickResult.ABORT_RESOURCE;
    }
    @Override public boolean canRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return ticks >= 1;
    }

    private float holdCpRequirement(PlayerAbilityData data) {
        return lerpf(170, 140, data.getProficiency(getId()));
    }

    @Override
    public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.enabled(getId()) && (data.isDevMode()
                || data.getCurrentCp() >= DynamicSkillRules.playerCp(data, getId(), holdCpRequirement(data)));
    }

    @Override public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {}

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return data.isDevMode()
                || data.getCurrentCp() >= DynamicSkillRules.playerCp(data, getId(), holdCpRequirement(data));
    }

    @Override
    public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return onChargingTick(player, data, ticks) ? TickResult.CONTINUE : TickResult.ABORT_RESOURCE;
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float exp = data.getProficiency(getId());
        // Context.consume in final 1.12.2 took overload first and CP second.
        // Jet Engine's historically swapped variable names therefore debit
        // 60..50 CP and 170..140 overload on a successful release.
        float cpDebit = lerpf(60, 50, exp);
        float overloadDebit = lerpf(170, 140, exp);
        if (!DynamicSkillRules.enabled(getId())
                || !DynamicSkillRules.tryPay(data, getId(), cpDebit, overloadDebit)) return false;

        Vec3 destination = legacyDestination(player);
        DynamicSkillRules.addExp(player, data, getId(), .004F);
        JetEngineRuntime.start(player, destination, lerpf(7, 20, exp));
        return true;
    }

    /**
     * Commit 769f45c1 removed the old +1.65 Y adjustment.  The final target is
     * exactly Raytrace.getLookingPos(player, 12, EntitySelectors.nothing).
     */
    static Vec3 legacyDestination(ServerPlayer player) {
        return legacyDestination(player, player.serverLevel(), player.getEyePosition(), player.getLookAngle());
    }

    static Vec3 legacyDestination(LivingEntity caster, ServerLevel level, Vec3 from, Vec3 look) {
        Vec3 intended = from.add(look.normalize().scale(RANGE));
        BlockHitResult hit = LoadedRaycast.clip(level, new ClipContext(from, intended,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : intended;
    }

    /**
     * Mob cast (instant, like the player's one-tick mark): the same 12-block look destination toward
     * the target, but the mob stops there instead of overshooting, and only when the destination is
     * safe (inside the border and build height, loaded, no suffocation, ground within reach below).
     * Needs {@link MobCastContext#mayMoveCaster()}; otherwise the cast fails without a cooldown.
     */
    @Override
    public boolean mobCast(MobCastContext context) {
        if (!context.mayMoveCaster()) return false;
        LivingEntity caster = context.caster();
        Vec3 destination = JetEngineRuntime.safeMobDestination(caster, context.level(),
                legacyDestination(caster, context.level(), context.eye(), context.look()));
        if (destination == null) return false;
        JetEngineRuntime.startMob(caster, destination, lerpf(7, 20, context.proficiency()));
        return true;
    }

    @Override public int mobCooldownTicks(float proficiency) { return getCooldownTicks(proficiency); }

    @Override public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        tryRelease(player, data, ticks);
    }
    @Override public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {}
    @Override public void execute(ServerPlayer player, PlayerAbilityData data) {}
    @Override public int getCooldownTicks(float proficiency) {
        return (int) lerpf(60, 30, proficiency);
    }
}
