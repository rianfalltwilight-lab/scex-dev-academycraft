package com.mohistmc.academy.skill.ability.electromaster;

import com.mohistmc.academy.skill.ability.LoadedRaycast;
import com.mohistmc.academy.skill.AcademyDamageHelper;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.api.mob.MobCastContext;
import com.mohistmc.academy.api.mob.MobCastable;

import com.mohistmc.academy.world.effect.EffectHelper;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import com.mohistmc.academy.world.effect.VisualLightning;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class ThunderClapEffect implements ChargingSkillEffect, MobCastable {

    private static final int MIN_TICKS = 40;
    private static final int MAX_TICKS = 60;

    @Override
    public String getId() {
        return "thunder_clap";
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

    @Override public boolean releasesOnKeyUp() { return false; }

    @Override
    public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.canPay(data, getId(), 0,
                lerpf(390, 252, data.getProficiency(getId())));
    }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
        float exp = data.getProficiency(getId());
        float overload = lerpf(390, 252, exp);
        if (!DynamicSkillRules.tryPay(data,getId(),0,overload)) return;

        ServerLevel level = player.serverLevel();
        EffectHelper.arcSpark(level, player.getX(), player.getY() + 1, player.getZ(), 20, 0.5);
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float exp = data.getProficiency(getId());
        float consumption = lerpf(18, 25, exp);

        if (ElectromasterRules.shouldConsumeThunderClapTick(ticks)
                && !DynamicSkillRules.tryPay(data,getId(),consumption,0)) return false;
        return true;
    }

    @Override
    public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float required = ElectromasterRules.shouldConsumeThunderClapTick(ticks)
                ? lerpf(18, 25, data.getProficiency(getId())) : 0;
        boolean enough = DynamicSkillRules.canPay(data,getId(),required,0);
        boolean continuing = onChargingTick(player, data, ticks);
        return !enough ? (ticks >= MIN_TICKS ? TickResult.RELEASE : TickResult.ABORT_RESOURCE)
                : (continuing ? TickResult.CONTINUE : TickResult.RELEASE);
    }

    @Override
    public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        ServerLevel level = player.serverLevel();
        float exp = data.getProficiency(getId());

        Vec3 targetPos = getTargetPos(player);

        strike(player, level, exp, ticks, targetPos);

        DynamicSkillRules.addExp(player,data, getId(), 0.003f);
    }

    /**
     * Mob cast: a full 60-tick charge, then the strike lands on the target (or the 40-block look end).
     * The strike is visual lightning only, so it never sets fire or breaks blocks.
     */
    @Override
    public boolean mobCast(MobCastContext context) {
        Vec3 targetPos;
        if (context.target() != null) {
            targetPos = context.target().position();
        } else {
            Vec3 end = context.eye().add(context.look().scale(40.0));
            BlockHitResult hit = LoadedRaycast.clip(context.level(), new ClipContext(context.eye(), end,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context.caster()));
            targetPos = hit.getType() != HitResult.Type.MISS ? hit.getLocation() : end;
        }
        strike(context.caster(), context.level(), context.proficiency(), context.chargeTicks(), targetPos);
        return true;
    }

    @Override public int mobChargeTicks(float proficiency) { return MAX_TICKS; }

    @Override
    public boolean mobChargeTick(MobCastContext context, int tick) {
        if (tick == 1) {
            LivingEntity caster = context.caster();
            EffectHelper.arcSpark(context.level(), caster.getX(), caster.getY() + 1, caster.getZ(), 20, 0.5);
        }
        return true;
    }

    @Override public int mobCooldownTicks(float proficiency) { return getCooldownTicks(proficiency, MAX_TICKS); }

    /** The released strike, shared by players and mob casters. */
    private void strike(LivingEntity caster, ServerLevel level, float exp, int ticks, Vec3 targetPos) {
        VisualLightning.send(level, targetPos);

        float damage = getDamage(exp, ticks);
        float range = getRange(exp);

        List<Entity> entities = level.getEntities(caster,
                new AABB(targetPos.x - range, targetPos.y - range, targetPos.z - range,
                        targetPos.x + range, targetPos.y + range, targetPos.z + range),
                Entity::isAlive);

        for (Entity e : entities) {
            if (e != caster) {
                double dist = targetPos.distanceTo(e.position());
                if (dist <= range) {
                    float faded = ElectromasterRules.thunderClapDamageAtDistance(damage, dist, range);
                    AcademyDamageHelper.hurt(caster,e,com.mohistmc.academy.skill.SkillDamageSource.attack(caster),
                            DynamicSkillRules.damage(getId(),faded));
                }
            }
        }
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
        // 蓄力技能通过 Charging 接口执行，此处留空
    }


    private Vec3 getTargetPos(ServerPlayer player) {
        BlockHitResult hit = (BlockHitResult) LoadedRaycast.pick(player, 40.0);
        if (hit.getType() != HitResult.Type.MISS) return hit.getLocation();
        Vec3 eye = player.getEyePosition(0);
        Vec3 look = player.getLookAngle().scale(40.0);
        return eye.add(look);
    }

    private float lerpf(float a, float b, float x) {
        return a + (b - a) * x;
    }

    private float getDamage(float exp, int ticks) {
        return lerpf(36, 72, exp) * ElectromasterRules.thunderClapChargeFactor(ticks);
    }

    private float getRange(float exp) {
        return lerpf(15, 30, exp);
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return (int) (40 * lerpf(10, 6, proficiency));
    }

    @Override
    public int getCooldownTicks(float proficiency, int chargedTicks) {
        return (int) (Math.max(MIN_TICKS, Math.min(MAX_TICKS, chargedTicks)) * lerpf(10, 6, proficiency));
    }
}

