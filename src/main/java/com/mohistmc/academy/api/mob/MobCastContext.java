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
                             @Nullable LivingEntity target, Vec3 eye, Vec3 look) {

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
