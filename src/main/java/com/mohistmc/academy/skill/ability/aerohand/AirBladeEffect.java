package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.world.entity.AeroProjectileEntity;
import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** ExtraAcC projectile activation; flight, collision and damage live in the entity. */
public final class AirBladeEffect extends AeroProjectileEffect {
    @Override public String getId() { return "air_blade"; }
    @Override protected AeroProjectileEntity.Kind kind() { return AeroProjectileEntity.Kind.AIR_BLADE; }
    @Override public float rawCp(float p) { return lerpf(100, 150, p); }
    @Override public float rawOverload(float p) { return lerpf(60, 40, p); }
    @Override protected float activationExp(float p) { return lerpf(0.002F, 0.004F, p); }
    @Override public int getCooldownTicks(float p) { return Math.round(lerpf(40, 30, Math.clamp(p, 0, 1))); }
}