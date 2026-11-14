package com.mohistmc.academy.skill;

import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.world.AcademyAttributes;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * How ability damage is scaled, in one place.  Effects already apply the per-skill and global config
 * multipliers through {@link DynamicSkillRules#damage}; {@link AcademyDamageHelper} then applies the
 * attacker's {@code academy:skill_damage} and the target's {@code academy:skill_resistance}.
 */
public final class SkillDamageMath {
    private SkillDamageMath() {}

    /** Every factor of one hit, for {@code /academy debug damage} and tests. */
    public record Breakdown(float raw, float skillMultiplier, float globalMultiplier,
                            float attackerMultiplier, float targetMultiplier, float result) {}

    public static float attackerMultiplier(@Nullable Entity attacker) {
        if (!(attacker instanceof LivingEntity living)) return 1F;
        var instance = living.getAttribute(AcademyAttributes.SKILL_DAMAGE);
        return instance == null ? 1F : (float) Math.max(0, instance.getValue());
    }

    public static float targetMultiplier(@Nullable Entity target) {
        if (!(target instanceof LivingEntity living)) return 1F;
        var instance = living.getAttribute(AcademyAttributes.SKILL_RESISTANCE);
        return instance == null ? 1F : (float) Math.clamp(1 - instance.getValue(), 0, 2);
    }

    /** Attribute stage only: the amount a hit carries after the config stage. */
    public static float applyAttributes(float configScaled, @Nullable Entity attacker, @Nullable Entity target) {
        return configScaled * attackerMultiplier(attacker) * targetMultiplier(target);
    }

    public static Breakdown breakdown(String skillId, float raw, @Nullable Entity attacker, @Nullable Entity target) {
        float skill = DynamicSkillRules.tuning(skillId).damage();
        float global = (float) ACConfig.Server.damageMul();
        float configScaled = DynamicSkillRules.damage(skillId, raw);
        float attackerMul = attackerMultiplier(attacker);
        float targetMul = targetMultiplier(target);
        return new Breakdown(raw, skill, global, attackerMul, targetMul, configScaled * attackerMul * targetMul);
    }
}
