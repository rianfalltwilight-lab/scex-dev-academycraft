package com.mohistmc.academy.skill;

/** Which skills the skill tree (terminal app and developer) draws; shared so the server can test it. */
public final class SkillTreeVisibility {
    private SkillTreeVisibility() {}

    /**
     * SkillTree.scala: {@code LearningHelper.canBePotentiallyLearned} and {@code isEnabled}. A skill is
     * drawn when it is enabled and the player has reached its level, has learned it, or has learned its
     * parent (the first non-level prerequisite) or it has none.
     */
    public static boolean visible(PlayerAbilityData data, Skill skill) {
        if (!PlayerAbilityData.skillEnabled(skill.getId())) return false;
        if (data.getPlayerLevel() >= skill.getLevel() || data.hasLearnedSkill(skill.getId())) return true;
        String parent = skill.getPrerequisites().stream().map(Skill.Prerequisite::skillId)
                .filter(id -> !id.startsWith("any_level_")).findFirst().orElse(null);
        return parent == null || data.hasLearnedSkill(parent);
    }
}
