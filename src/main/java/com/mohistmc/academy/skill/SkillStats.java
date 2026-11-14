package com.mohistmc.academy.skill;

import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.config.DynamicSkillRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Real numbers for skill tooltips, at the player's current proficiency and at full proficiency: CP and
 * overload after tuning, the cp_cost attribute and the profile discount; cooldown after tuning and the
 * cooldown attribute; and, for the main damaging skills, damage after tuning, level scaling and the
 * skill_damage attribute (before the target's resistance, armour and boss bonus).
 */
public final class SkillStats {
    private SkillStats() {}

    private record Damage(float min, float max, boolean perHit) {
        float at(float proficiency) { return min + (max - min) * Math.clamp(proficiency, 0, 1); }
    }

    /** Raw 1.0.7 damage formulas (lerp from proficiency 0 to 1), copied from the skill effects. */
    private static final Map<String, Damage> DAMAGE = Map.ofEntries(
            Map.entry("arc_gen", new Damage(5, 9, false)),
            Map.entry("thunder_bolt", new Damage(10, 25, false)),
            Map.entry("railgun", new Damage(60, 110, false)),
            Map.entry("scatter_bomb", new Damage(5, 9, true)),
            Map.entry("electron_missile", new Damage(10, 18, true)),
            Map.entry("meltdowner", new Damage(18, 50, false)),
            Map.entry("plasma_cannon", new Damage(80, 150, false)),
            Map.entry("blood_retro", new Damage(30, 60, false)),
            Map.entry("dir_blast", new Damage(10, 25, false)),
            Map.entry("dir_shock", new Damage(7, 15, false)),
            Map.entry("ground_shock", new Damage(4, 6, false)),
            Map.entry("threatening_teleport", new Damage(3, 6, true)));

    public static List<Component> lines(Player player, PlayerAbilityData data, Skill skill) {
        List<Component> out = new ArrayList<>();
        String id = skill.getId();
        float proficiency = data.getProficiency(id);
        var tuning = ACConfig.Server.skill(id);
        if (skill.getBaseCpCost() > 0 || skill.getBaseOverload() > 0) {
            float cp = skill.getBaseCpCost() * tuning.cp() * data.cpCostFactor(id);
            float overload = skill.getBaseOverload() * tuning.overload();
            out.add(Component.translatable("ac.skill_slot.cp", (int) Math.ceil(cp)).withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("  "))
                    .append(Component.translatable("ac.skill_slot.overload", (int) Math.ceil(overload))
                            .withStyle(ChatFormatting.RED)));
        }
        SkillEffect effect = skill.getEffect();
        if (effect != null && skill.getType() != SkillType.PASSIVE) {
            float now = cooldownSeconds(data, id, effect.getCooldownTicks(proficiency));
            float best = cooldownSeconds(data, id, effect.getCooldownTicks(1));
            if (now > 0 || best > 0) out.add(withMax("ac.skill_stats.cooldown", seconds(now), seconds(best))
                    .withStyle(ChatFormatting.GRAY));
        }
        Damage damage = DAMAGE.get(id);
        if (damage != null) {
            float now = shownDamage(player, id, damage.at(proficiency));
            float best = shownDamage(player, id, damage.at(1));
            out.add(withMax(damage.perHit ? "ac.skill_stats.damage_per_hit" : "ac.skill_stats.damage",
                    whole(now), whole(best)).withStyle(ChatFormatting.GOLD));
        }
        return out;
    }

    private static net.minecraft.network.chat.MutableComponent withMax(String key, String now, String best) {
        var line = Component.translatable(key, now);
        if (!now.equals(best)) line.append(Component.translatable("ac.skill_stats.at_max", best));
        return line;
    }

    private static float cooldownSeconds(PlayerAbilityData data, String id, int ticks) {
        if (ticks <= 0) return 0;
        return DynamicSkillRules.cooldown(id, ticks) * data.getCooldownScale() / 20f;
    }

    private static float shownDamage(Player player, String id, float raw) {
        float tuned = DynamicSkillRules.damage(id, raw);
        return CombatRules.offense(player, tuned) * SkillDamageMath.attackerMultiplier(player);
    }

    private static String seconds(float value) {
        return String.format(Locale.ROOT, value < 10 ? "%.1f" : "%.0f", value);
    }

    private static String whole(float value) {
        return String.format(Locale.ROOT, value < 10 ? "%.1f" : "%.0f", value);
    }
}
