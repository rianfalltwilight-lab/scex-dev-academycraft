package com.mohistmc.academy.api.mob;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AcademyAttachments;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Wires {@link AbilityCastGoal} into mobs: a server-side mob that joins a level gets the goal once when
 * its type has an {@code academy:mob_skills} data-map entry or its {@code academy:mob_ability} attachment
 * lists skills.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class MobSkillAi {
    public static final int GOAL_PRIORITY = 2;

    private MobSkillAi() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof Mob mob && hasAnySkills(mob)) ensureAttached(mob);
    }

    /** Adds the goal unless the mob already has one; returns whether it was added. */
    public static boolean ensureAttached(Mob mob) {
        if (mob.level().isClientSide() || hasGoal(mob)) return false;
        mob.goalSelector.addGoal(GOAL_PRIORITY, new AbilityCastGoal(mob));
        return true;
    }

    public static boolean hasGoal(Mob mob) {
        return mob.goalSelector.getAvailableGoals().stream().anyMatch(w -> w.getGoal() instanceof AbilityCastGoal);
    }

    /** The data-map profile of the entity's type, or {@link MobSkills#DEFAULT}. */
    public static MobSkills profile(LivingEntity entity) {
        MobSkills skills = AcademyMobDataMaps.get(entity.getType());
        return skills != null ? skills : MobSkills.DEFAULT;
    }

    /** Whether the entity has anything to cast; does not create the attachment. */
    public static boolean hasAnySkills(LivingEntity entity) {
        if (entity.hasData(AcademyAttachments.MOB_ABILITY)
                && !entity.getData(AcademyAttachments.MOB_ABILITY).skills().isEmpty()) return true;
        MobSkills mapped = AcademyMobDataMaps.get(entity.getType());
        return mapped != null && !mapped.skills().isEmpty();
    }

    /**
     * Effective skills: data-map entries (proficiency replaced by the attachment's when the mob has its own),
     * then attachment-only skills with default weight and range.  First entry per skill id wins.
     */
    public static List<MobSkills.Entry> resolve(LivingEntity entity) {
        Map<String, Float> own = entity.hasData(AcademyAttachments.MOB_ABILITY)
                ? entity.getData(AcademyAttachments.MOB_ABILITY).skills() : Map.of();
        Map<String, MobSkills.Entry> out = new LinkedHashMap<>();
        for (MobSkills.Entry e : profile(entity).skills()) {
            if (out.containsKey(e.skill())) continue;
            Float p = own.get(e.skill());
            out.put(e.skill(), p == null ? e
                    : new MobSkills.Entry(e.skill(), p, e.weight(), e.minRange(), e.maxRange(), e.cooldownTicks()));
        }
        List<String> attachmentOnly = new ArrayList<>(own.keySet());
        attachmentOnly.sort(String::compareTo);
        for (String id : attachmentOnly) out.putIfAbsent(id, MobSkills.Entry.defaults(id, own.get(id)));
        return List.copyOf(out.values());
    }
}
