package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.api.mob.MobCastContext;
import com.mohistmc.academy.api.mob.MobCastable;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.DynamicOneShotSkillEffect;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.entity.AeroProjectileEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Payment and entity admission share a single successful activation. */
abstract class AeroProjectileEffect implements DynamicOneShotSkillEffect, MobCastable {
    protected abstract AeroProjectileEntity.Kind kind();
    protected abstract float activationExp(float proficiency);

    @Override public boolean executeAndReport(ServerPlayer player, PlayerAbilityData data) {
        float p = data.getProficiency(getId());
        var before = data.captureDynamicResources();
        if (!DynamicSkillRules.tryPay(data, getId(), rawCp(p), rawOverload(p))) return false;
        var paid = data.captureDynamicResources();
        if (!launch(player, player.serverLevel(), p, player.getEyePosition(), player.getLookAngle(), false)) {
            data.rollbackDynamicPayment(before, paid);
            return false;
        }
        if (!data.isDevMode()) DynamicSkillRules.addExp(player, data, getId(), activationExp(p));
        return true;
    }

    @Override public void execute(ServerPlayer player, PlayerAbilityData data) { executeAndReport(player, data); }

    /** Mob path: no payment or experience; block harvesting only when the cast may grief. */
    @Override public boolean mobCast(MobCastContext context) {
        return launch(context.caster(), context.level(), context.proficiency(), context.eye(), context.look(),
                context.mayDestroyBlocks());
    }

    @Override public int mobCooldownTicks(float proficiency) { return getCooldownTicks(proficiency); }

    /** Spawns and arms the projectile after payment; {@code mobBreaksBlocks} is ignored for players. */
    private boolean launch(LivingEntity caster, ServerLevel level, float p, Vec3 eye, Vec3 look, boolean mobBreaksBlocks) {
        var type = switch (kind()) {
            case VOLCANIC_BALL -> AcademyEntities.AERO_VOLCANIC_BALL.get();
            case AIR_BLADE -> AcademyEntities.AERO_AIR_BLADE.get();
            case BOMBER_LANCE -> AcademyEntities.AERO_BOMBER_LANCE.get();
        };
        var projectile = new AeroProjectileEntity(type, level, kind());
        projectile.prepare(caster, p, eye, look);
        projectile.setMobBreaksBlocks(mobBreaksBlocks);
        if (!level.addFreshEntity(projectile) || !projectile.isAlive()
                || !caster.isAlive() || caster.level() != level) {
            projectile.discard();
            return false;
        }
        projectile.arm();
        return true;
    }
}
