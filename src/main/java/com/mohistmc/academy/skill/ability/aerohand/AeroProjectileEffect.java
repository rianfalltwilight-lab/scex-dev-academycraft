package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.DynamicOneShotSkillEffect;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.entity.AeroProjectileEntity;
import net.minecraft.server.level.ServerPlayer;

/** Payment and entity admission share a single successful activation. */
abstract class AeroProjectileEffect implements DynamicOneShotSkillEffect {
    protected abstract AeroProjectileEntity.Kind kind();
    protected abstract float activationExp(float proficiency);

    @Override public boolean executeAndReport(ServerPlayer player, PlayerAbilityData data) {
        float p = data.getProficiency(getId());
        var before = data.captureDynamicResources();
        if (!DynamicSkillRules.tryPay(data, getId(), rawCp(p), rawOverload(p))) return false;
        var paid = data.captureDynamicResources();
        var type = switch (kind()) {
            case VOLCANIC_BALL -> AcademyEntities.AERO_VOLCANIC_BALL.get();
            case AIR_BLADE -> AcademyEntities.AERO_AIR_BLADE.get();
            case BOMBER_LANCE -> AcademyEntities.AERO_BOMBER_LANCE.get();
        };
        var level = player.serverLevel();
        var projectile = new AeroProjectileEntity(type, level, kind());
        projectile.prepare(player, p);
        if (!level.addFreshEntity(projectile) || !projectile.isAlive()
                || !player.isAlive() || player.level() != level) {
            projectile.discard();
            data.rollbackDynamicPayment(before, paid);
            return false;
        }
        projectile.arm();
        if (!data.isDevMode()) DynamicSkillRules.addExp(player, data, getId(), activationExp(p));
        return true;
    }

    @Override public void execute(ServerPlayer player, PlayerAbilityData data) { executeAndReport(player, data); }
}
