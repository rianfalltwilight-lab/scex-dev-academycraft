package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.api.mob.MobCastContext;
import com.mohistmc.academy.api.mob.MobCastable;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.DynamicOneShotSkillEffect;
import com.mohistmc.academy.world.effect.EffectHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** The non-charging cast creates a vacuum whose first and only pulse is five ticks later. */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class AeroSeparatorEffect implements DynamicOneShotSkillEffect, MobCastable {
    private record Pulse(ServerPlayer owner, ServerLevel level, float proficiency, long due) {}
    private static final java.util.Map<java.util.UUID, Pulse> PULSES = new java.util.HashMap<>();
    /** The player pulse's five-tick delay; mobs wait it out as a server-timed charge. */
    private static final int PULSE_DELAY = 5;
    @Override public String getId() { return "aero_separator"; }
    @Override public float rawCp(float p) { return lerpf(1200, 1800, p); }
    @Override public float rawOverload(float p) { return lerpf(480, 360, p); }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        AeroPassiveRuntime.terminateSustained(player, data);
        PULSES.put(player.getUUID(), new Pulse(player, player.serverLevel(),
                data.getProficiency(getId()), player.serverLevel().getGameTime() + 5));
        if (!data.isDevMode()) DynamicSkillRules.addExp(player, data, getId(),
                lerpf(0.002F, 0.001F, data.getProficiency(getId())));
    }

    /**
     * Mob path: the same delayed pulse around the caster, timed by the mob charge (which ends with
     * its caster). A mob never suffocates itself: {@code hurtSelf} is a player-only boundary, and a
     * self-hit would make the skill suicidal for low-health casters.
     */
    @Override public int mobChargeTicks(float p) { return PULSE_DELAY; }

    @Override
    public boolean mobCast(MobCastContext context) {
        LivingEntity caster = context.caster();
        if (!caster.isAlive() || caster.level() != context.level()) return false;
        detonate(caster, context.proficiency(), caster.position());
        return true;
    }

    @Override public int mobCooldownTicks(float p) { return getCooldownTicks(p); }

    static int detonate(ServerPlayer player, PlayerAbilityData data, Vec3 centre) {
        return detonate(player, data.getProficiency("aero_separator"), centre);
    }

    /** Players suffocate themselves too (legacy); a mob caster is left out of its own pulse. */
    private static int detonate(LivingEntity caster, float proficiency, Vec3 centre) {
        ServerLevel level = (ServerLevel) caster.level();
        float radius = AeroBehaviorMath.separatorRadius(proficiency);
        float damage = DynamicSkillRules.damage("aero_separator", AeroBehaviorMath.separatorDamage(proficiency));
        EffectHelper.glowBurst(level, centre.x, centre.y, centre.z,
                32, 0.18F, 0xAAEAFBFF, 12, 1.7);
        level.playSound(null, centre.x, centre.y, centre.z,
                SoundEvents.BREEZE_INHALE, caster.getSoundSource(), 1.5F, 0.45F);
        AABB area = new AABB(centre, centre).inflate(radius);
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, area,
                p -> p.isAlive() && AeroProjectileRules.clearsLikeLegacy(p))) projectile.discard();
        int affected = 0;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            boolean hurt = living == caster
                    ? caster instanceof ServerPlayer player
                    && AcademyDamageHelper.hurtSelf(player, living, com.mohistmc.academy.skill.SkillDamageSource.drowning(player, true), damage)
                    : AcademyDamageHelper.hurt(caster, living, com.mohistmc.academy.skill.SkillDamageSource.drowning(caster, false), damage);
            if (hurt) {
                living.setAirSupply(Math.min(living.getAirSupply(), 0));
                affected++;
            }
        }
        return affected;
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        for (Pulse pulse : java.util.List.copyOf(PULSES.values())) {
            ServerPlayer player = pulse.owner();
            var data = player.getData(com.mohistmc.academy.skill.AcademyAttachments.PLAYER_ABILITY);
            if (!player.isAlive() || player.isRemoved() || player.level() != pulse.level()
                    || !data.isAbilityActive()
                    || data.getCurrentAbility() != com.mohistmc.academy.skill.AbilityCategory.AEROHAND
                    || com.mohistmc.academy.skill.AbilityInterferenceService.isInterfered(player)) {
                PULSES.remove(player.getUUID(), pulse);
            } else if (pulse.level().getGameTime() >= pulse.due() && PULSES.remove(player.getUUID(), pulse)) {
                detonate(player, pulse.proficiency(), player.position());
            }
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        PULSES.remove(event.getEntity().getUUID());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void dimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        PULSES.remove(event.getEntity().getUUID());
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { PULSES.clear(); }

    @Override public int getCooldownTicks(float p) { return (int) lerpf(200, 120, Math.clamp(p, 0, 1)); }
}
