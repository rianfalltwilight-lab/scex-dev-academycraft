package com.mohistmc.academy.skill.ability.aerohand;

import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.ThrownTrident;

/**
 * Which projectiles the aerohand barriers delete.
 *
 * <p>ExtraAcC cleared only 1.12 {@code IProjectile}s: arrows, thrown items, llama spit and its
 * own flying entities. 1.21 widened {@link Projectile} to entities that were never in that set,
 * and deleting a trident destroys the player's item outright, so those are left alone.
 */
public final class AeroProjectileRules {
    private AeroProjectileRules() {}

    public static boolean clearsLikeLegacy(Projectile projectile) {
        return !(projectile instanceof ThrownTrident
                || projectile instanceof AbstractHurtingProjectile
                || projectile instanceof FishingHook
                || projectile instanceof ShulkerBullet
                || projectile instanceof FireworkRocketEntity);
    }
}
