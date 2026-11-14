package com.mohistmc.academy.skill;

import com.mohistmc.academy.data.ProgressionData;

/**
 * AcademyCraft 1.0.7 ability-level progress formula.  The per-skill factor (0.666, 1.333 at level 4)
 * comes from {@link ProgressionData#thresholdPerSkill}, datapack {@code level_up.threshold_per_skill}.
 */
public final class LegacyLevelProgress {
    private LegacyLevelProgress() {}

    public static float threshold(int level, int controllableSkillsAtLevel) {
        if (controllableSkillsAtLevel <= 0) return 0.0f;
        return controllableSkillsAtLevel * ProgressionData.thresholdPerSkill(level);
    }

    public static float fraction(float accumulatedExp, float threshold) {
        if (threshold <= 0.0f) return 1.0f;
        if (!Float.isFinite(accumulatedExp)) return 0.0f;
        return Math.clamp(accumulatedExp / threshold, 0.0f, 1.0f);
    }

    public static boolean canLevelUp(boolean hasAbility, int level, float accumulatedExp, float threshold) {
        return hasAbility && level >= 1 && level < 5 && fraction(accumulatedExp, threshold) >= 1.0f;
    }
}
