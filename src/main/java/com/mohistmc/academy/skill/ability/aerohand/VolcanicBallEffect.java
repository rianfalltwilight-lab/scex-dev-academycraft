package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.world.entity.AeroProjectileEntity;
import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** ExtraAcC projectile activation; flight, collision and damage live in the entity. */
public final class VolcanicBallEffect extends AeroProjectileEffect {
    @Override public String getId() { return "volcanic_ball"; }
    @Override protected AeroProjectileEntity.Kind kind() { return AeroProjectileEntity.Kind.VOLCANIC_BALL; }
    @Override public float rawCp(float p) { return lerpf(40, 100, p); }
    @Override public float rawOverload(float p) { return lerpf(40, 30, p); }
    @Override protected float activationExp(float p) { return lerpf(0.002F, 0.001F, p); }
    @Override public int getCooldownTicks(float p) { return (int) lerpf(20, 5, Math.clamp(p, 0, 1)); }
}