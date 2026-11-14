package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.world.entity.AeroProjectileEntity;
import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** ExtraAcC projectile activation; flight, collision and damage live in the entity. */
public final class BomberLanceEffect extends AeroProjectileEffect {
    @Override public String getId() { return "bomber_lance"; }
    @Override protected AeroProjectileEntity.Kind kind() { return AeroProjectileEntity.Kind.BOMBER_LANCE; }
    @Override public float rawCp(float p) { return lerpf(600, 900, p); }
    @Override public float rawOverload(float p) { return lerpf(240, 160, p); }
    @Override protected float activationExp(float p) { return lerpf(0.005F, 0.01F, p); }
    @Override public int getCooldownTicks(float p) { return (int) lerpf(80, 60, Math.clamp(p, 0, 1)); }
}