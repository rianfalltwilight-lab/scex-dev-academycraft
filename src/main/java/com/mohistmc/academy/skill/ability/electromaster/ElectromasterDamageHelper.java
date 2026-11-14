package com.mohistmc.academy.skill.ability.electromaster;

import com.mohistmc.academy.advancement.LegacyAdvancementBridge;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;

/** Shared observable behavior of AcademyCraft 1.0.7's package-private EMDamageHelper. */
final class ElectromasterDamageHelper {
    private ElectromasterDamageHelper() {}

    /**
     * Player or mob caster.  A mob only powers creepers it may hit, and only players earn the
     * charged-creeper advancement.
     */
    static boolean attack(LivingEntity attacker, Entity target, DamageSource source, float damage) {
        boolean hurt = AcademyDamageHelper.hurt(attacker, target, source, damage);
        if (!(attacker.level() instanceof ServerLevel level)) return hurt;
        if (target instanceof Creeper creeper
                && (attacker instanceof ServerPlayer || AcademyDamageHelper.allowsTarget(attacker, target))
                && level.random.nextFloat() < .3f) {
            powerWithoutLightningDamage(level, creeper);
            if (attacker instanceof ServerPlayer player) LegacyAdvancementBridge.electromasterChargedCreeper(player);
        }
        return hurt;
    }

    /** Creeper.thunderHit is the public powered-state bridge; neutralize its unrelated fire/damage. */
    private static void powerWithoutLightningDamage(ServerLevel level, Creeper creeper) {
        var bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        int fireTicks = creeper.getRemainingFireTicks();
        bolt.setVisualOnly(true);
        bolt.setDamage(0);
        creeper.thunderHit(level, bolt);
        creeper.setRemainingFireTicks(fireTicks);
    }
}
