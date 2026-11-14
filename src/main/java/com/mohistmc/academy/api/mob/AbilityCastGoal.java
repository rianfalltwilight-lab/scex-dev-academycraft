package com.mohistmc.academy.api.mob;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AcademyAttachments;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

/**
 * Casts the mob's skills at its current target.  Holds no goal flags, so melee, ranged and movement goals
 * keep running; it only steers the look control while the target is near.  Every {@code cast_interval}
 * ticks it picks a weighted random skill that is supported, enabled, off cooldown and whose range band
 * contains the target distance (and, by default, only with line of sight), then casts it through
 * {@link AcademyMobAbilities#cast}.
 */
public class AbilityCastGoal extends Goal {
    /** Delay before retrying when nothing could be cast. */
    static final int RETRY_TICKS = 10;

    private final Mob mob;
    private long nextAttempt = Long.MIN_VALUE;
    private @Nullable AcademyMobAbilities.Result lastResult;
    private @Nullable String lastSkill;

    public AbilityCastGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && mob.isAlive() && !mob.isNoAi() && MobSkillAi.hasAnySkills(mob);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        long now = mob.level().getGameTime();
        int interval = MobSkillAi.profile(mob).castInterval();
        // Stagger the first cast so a crowd that spots the player at once does not fire in the same tick.
        nextAttempt = Math.max(nextAttempt, now + 1 + mob.getRandom().nextInt(Math.max(1, interval / 2)));
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return;
        MobSkills profile = MobSkillAi.profile(mob);
        List<MobSkills.Entry> skills = MobSkillAi.resolve(mob);
        if (skills.isEmpty()) return;
        double distance = Math.sqrt(mob.distanceToSqr(target));
        double reach = skills.stream().mapToDouble(MobSkills.Entry::maxRange).max().orElse(0);
        if (distance <= reach) mob.getLookControl().setLookAt(target, 30F, 30F);

        long now = mob.level().getGameTime();
        if (now < nextAttempt) return;
        if (profile.needsLineOfSight() && !mob.getSensing().hasLineOfSight(target)) {
            nextAttempt = now + RETRY_TICKS;
            return;
        }
        MobAbilityData data = mob.hasData(AcademyAttachments.MOB_ABILITY)
                ? mob.getData(AcademyAttachments.MOB_ABILITY) : null;
        List<MobSkills.Entry> usable = new ArrayList<>();
        for (MobSkills.Entry e : skills) {
            if (!e.inRange(distance)) continue;
            if (data != null && data.isOnCooldown(e.skill(), now)) continue;
            if (!AcademyMobAbilities.isSupported(e.skill()) || !DynamicSkillRules.enabled(e.skill())) continue;
            usable.add(e);
        }
        MobSkills.Entry pick = WeightedSkillPick.pick(usable, MobSkills.Entry::weight, mob.getRandom().nextDouble());
        if (pick == null) {
            nextAttempt = now + Math.min(RETRY_TICKS, profile.castInterval());
            return;
        }
        mob.getLookControl().setLookAt(target, 30F, 30F);
        AcademyMobAbilities.Result result = AcademyMobAbilities.cast(mob, pick.skill(),
                AcademyMobAbilities.CastOptions.at(target).withProficiency(pick.proficiency()));
        lastSkill = pick.skill();
        lastResult = result;
        switch (result) {
            case CAST -> {
                MobAbilityData after = AcademyMobAbilities.data(mob);
                if (pick.cooldownTicks() > after.cooldownRemaining(pick.skill(), now))
                    after.setCooldown(pick.skill(), now, pick.cooldownTicks());
                nextAttempt = now + profile.castInterval();
            }
            case BUSY -> nextAttempt = now + 1 + mob.getRandom().nextInt(5);
            default -> nextAttempt = now + Math.min(RETRY_TICKS, profile.castInterval());
        }
    }

    public @Nullable AcademyMobAbilities.Result lastResult() { return lastResult; }

    public @Nullable String lastSkill() { return lastSkill; }
}
