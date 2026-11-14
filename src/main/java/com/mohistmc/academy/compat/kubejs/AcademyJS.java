package com.mohistmc.academy.compat.kubejs;

import com.mohistmc.academy.api.event.AbilityEvents;
import com.mohistmc.academy.api.mob.AcademyMobAbilities;
import com.mohistmc.academy.api.mob.MobAbilityData;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillRegistry;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code Academy} script binding.  Plain static Java (no KubeJS types), so it is also usable from
 * other mods.  Player mutators run on the server and resync the player; ids accept "x" or "academy:x".
 */
public final class AcademyJS {
    private AcademyJS() {}

    // ---------------------------------------------------------------- players

    public static @Nullable PlayerAbilityData data(Player player) {
        return player == null ? null : player.getData(AcademyAttachments.PLAYER_ABILITY);
    }

    public static @Nullable String getCategory(Player player) {
        PlayerAbilityData d = data(player);
        return d == null || !d.hasAbility() ? null : d.getCurrentAbility().id();
    }

    /** Same rules as /academy category: keeps the level, clears learned skills. Null or "" clears the ability. */
    public static boolean setCategory(Player player, @Nullable String category) {
        PlayerAbilityData d = data(player);
        if (d == null) return false;
        AbilityCategory target = category == null || category.isBlank() ? null : AbilityCategory.fromId(strip(category));
        if (category != null && !category.isBlank() && target == null) return false;
        AbilityCategory old = d.getCurrentAbility();
        int oldLevel = d.getPlayerLevel();
        if (!d.changeCategory(target)) return false;
        NeoForge.EVENT_BUS.post(new AbilityEvents.CategoryChanged(player, old, target));
        if (oldLevel != d.getPlayerLevel())
            NeoForge.EVENT_BUS.post(new AbilityEvents.LevelChanged(player, oldLevel, d.getPlayerLevel()));
        sync(player, d);
        return true;
    }

    public static int getLevel(Player player) { PlayerAbilityData d = data(player); return d == null ? 0 : d.getPlayerLevel(); }

    public static boolean setLevel(Player player, int level) {
        PlayerAbilityData d = data(player);
        if (d == null || !d.hasAbility() || level < 1 || level > 5) return false;
        int old = d.getPlayerLevel();
        d.setPlayerLevel(level);
        if (old != level) NeoForge.EVENT_BUS.post(new AbilityEvents.LevelChanged(player, old, level));
        sync(player, d);
        return true;
    }

    public static float getCP(Player player) { PlayerAbilityData d = data(player); return d == null ? 0 : d.getCurrentCp(); }
    public static float getMaxCP(Player player) { PlayerAbilityData d = data(player); return d == null ? 0 : d.getMaxCp(); }
    public static void setCP(Player player, float cp) { PlayerAbilityData d = data(player); if (d != null) { d.setCurrentCp(cp); sync(player, d); } }
    public static void addCP(Player player, float cp) { PlayerAbilityData d = data(player); if (d != null) { d.setCurrentCp(d.getCurrentCp() + cp); sync(player, d); } }

    public static float getOverload(Player player) { PlayerAbilityData d = data(player); return d == null ? 0 : d.getCurrentOverload(); }
    public static float getMaxOverload(Player player) { PlayerAbilityData d = data(player); return d == null ? 0 : d.getMaxOverload(); }
    public static void setOverload(Player player, float overload) { PlayerAbilityData d = data(player); if (d != null) { d.setCurrentOverload(overload); sync(player, d); } }
    public static boolean isOverloaded(Player player) { PlayerAbilityData d = data(player); return d != null && d.isOverloadLocked(); }

    public static boolean isActive(Player player) { PlayerAbilityData d = data(player); return d != null && d.isAbilityActive(); }

    public static boolean setActive(Player player, boolean active) {
        PlayerAbilityData d = data(player);
        if (d == null || !d.hasAbility() || d.isAbilityActive() == active) return false;
        d.setAbilityActive(active);
        NeoForge.EVENT_BUS.post(active ? new AbilityEvents.Activate(player) : new AbilityEvents.Deactivate(player));
        sync(player, d);
        return true;
    }

    public static boolean hasLearned(Player player, String skill) { PlayerAbilityData d = data(player); return d != null && d.hasLearnedSkill(strip(skill)); }

    /** Learns a skill of the player's current category, skipping prerequisites (like /academy learn). */
    public static boolean learn(Player player, String skill) {
        PlayerAbilityData d = data(player);
        Skill s = SkillRegistry.getSkill(strip(skill));
        if (d == null || s == null || !d.hasAbility() || s.getCategory() != d.getCurrentAbility()
                || d.hasLearnedSkill(s.getId())) return false;
        d.learnSkill(s.getId());
        NeoForge.EVENT_BUS.post(new AbilityEvents.SkillLearned(player, s));
        sync(player, d);
        return true;
    }

    public static boolean unlearn(Player player, String skill) {
        PlayerAbilityData d = data(player);
        if (d == null || !d.unlearnSkill(strip(skill))) return false;
        sync(player, d);
        return true;
    }

    public static List<String> learnedSkills(Player player) {
        PlayerAbilityData d = data(player);
        return d == null ? List.of() : d.getLearnedSkills().stream().sorted().toList();
    }

    public static float getProficiency(Player player, String skill) { PlayerAbilityData d = data(player); return d == null ? 0 : d.getProficiency(strip(skill)); }

    public static void setProficiency(Player player, String skill, float value) {
        PlayerAbilityData d = data(player);
        if (d == null || !d.hasLearnedSkill(strip(skill))) return;
        d.setProficiency(strip(skill), Math.clamp(value, 0F, 1F));
        sync(player, d);
    }

    public static boolean clearCooldown(Player player, String skill) {
        PlayerAbilityData d = data(player);
        if (d == null || !d.clearCooldown(strip(skill))) return false;
        sync(player, d);
        return true;
    }

    public static int clearCooldowns(Player player) {
        PlayerAbilityData d = data(player);
        if (d == null) return 0;
        int n = d.clearCooldowns();
        sync(player, d);
        return n;
    }

    // ------------------------------------------------------------------- mobs

    /** Casts with the mob's stored proficiency at its current target; returns the Result name. */
    public static String cast(LivingEntity caster, String skill) {
        LivingEntity target = caster instanceof net.minecraft.world.entity.Mob mob ? mob.getTarget() : null;
        return AcademyMobAbilities.cast(caster, skill, AcademyMobAbilities.CastOptions.at(target)).name();
    }

    /** Casts at the given target (may be null) with an explicit proficiency (0..1). */
    public static String cast(LivingEntity caster, String skill, float proficiency, @Nullable LivingEntity target) {
        return AcademyMobAbilities.cast(caster, skill,
                AcademyMobAbilities.CastOptions.at(target).withProficiency(proficiency)).name();
    }

    public static void setMobSkill(LivingEntity mob, String skill, float proficiency) {
        if (mob instanceof Player) return;
        AcademyMobAbilities.data(mob).setSkill(strip(skill), proficiency);
        if (mob instanceof net.minecraft.world.entity.Mob pathfinder)
            com.mohistmc.academy.api.mob.MobSkillAi.ensureAttached(pathfinder);
    }

    public static boolean removeMobSkill(LivingEntity mob, String skill) {
        return !(mob instanceof Player) && AcademyMobAbilities.data(mob).removeSkill(strip(skill));
    }

    public static Map<String, Float> mobSkills(LivingEntity mob) {
        if (mob instanceof Player || !mob.hasData(AcademyAttachments.MOB_ABILITY)) return Map.of();
        return new TreeMap<>(AcademyMobAbilities.data(mob).skills());
    }

    public static List<String> supportedMobSkills() { return AcademyMobAbilities.supportedSkills(); }

    // ------------------------------------------------------------------ misc

    public static List<String> categories() { return AbilityCategory.all().stream().map(AbilityCategory::id).toList(); }

    public static List<String> skills(String category) {
        AbilityCategory c = AbilityCategory.fromId(strip(category));
        return c == null ? List.of() : SkillRegistry.getSkillsByCategory(c).stream().map(Skill::getId).toList();
    }

    private static void sync(Player player, PlayerAbilityData data) {
        if (player instanceof ServerPlayer serverPlayer) data.syncTo(serverPlayer);
    }

    static String strip(String id) {
        return id != null && id.startsWith("academy:") ? id.substring(8) : id;
    }
}
