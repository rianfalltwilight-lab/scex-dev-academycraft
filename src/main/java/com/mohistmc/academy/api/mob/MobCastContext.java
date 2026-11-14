package com.mohistmc.academy.api.mob;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.config.MobCastConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Everything a mob-cast skill needs.  {@code look} already aims at {@code target} when one is given,
 * otherwise it is the caster's own look direction.  Mob casts pay no CP and grant no proficiency.
 */
public record MobCastContext(LivingEntity caster, ServerLevel level, String skillId, float proficiency,
                             @Nullable LivingEntity target, Vec3 eye, Vec3 look, int chargeTicks) {

    /** An instant cast (no charge). */
    public MobCastContext(LivingEntity caster, ServerLevel level, String skillId, float proficiency,
                          @Nullable LivingEntity target, Vec3 eye, Vec3 look) {
        this(caster, level, skillId, proficiency, target, eye, look, 0);
    }

    /** Entity types listed here are never moved or teleported by their own skills (e.g. bosses). */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> IMMOBILE_CASTERS =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy", "immobile_casters"));

    /** Whether a skill may move or teleport its caster ({@code mobs.movementSkills}, minus immobile_casters). */
    public boolean mayMoveCaster() {
        return MobCastConfig.movementSkills() && !caster.getType().is(IMMOBILE_CASTERS);
    }

    /** The same cast re-aimed from the caster's current eye at the target, after {@code ticks} of charge. */
    public MobCastContext at(int ticks) {
        Vec3 newEye = caster.getEyePosition();
        Vec3 newLook = target != null && target.isAlive()
                ? target.getEyePosition().subtract(newEye).normalize() : caster.getLookAngle();
        if (newLook.lengthSqr() < 1.0e-8) newLook = caster.getLookAngle();
        return new MobCastContext(caster, level, skillId, proficiency,
                target != null && target.isAlive() ? target : null, newEye, newLook, ticks);
    }

    /**
     * Whether this cast may destroy blocks: the skill's own rules ({@code skill.destroyBlocks}, the
     * dimension allowlist, the skill's destroy_blocks tuning), {@code mobs.destroyBlocks}, and the
     * mobGriefing gamerule for this caster.
     */
    public boolean mayDestroyBlocks() {
        return MobCastConfig.destroyBlocks() && DynamicSkillRules.destroysBlocks(level, skillId)
                && EventHooks.canEntityGrief(level, caster);
    }

    /** Config-scaled damage for this skill (the attribute stage runs later in AcademyDamageHelper). */
    public float damage(float raw) {
        return DynamicSkillRules.damage(skillId, raw);
    }
}
