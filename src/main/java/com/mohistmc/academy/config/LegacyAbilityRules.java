package com.mohistmc.academy.config;

import com.mohistmc.academy.data.SkillTuningData;
import java.util.List;

/** Pure, reload-safe interpretation of the 1.0.7 ability tuning matrix. */
public final class LegacyAbilityRules {
    private LegacyAbilityRules() {}
    public static final Snapshot DEFAULTS = new Snapshot(15, 1, 32, 1, .0025f, .0058f, 1);
    public record Snapshot(int cpDelay, float cpSpeed, int overloadDelay, float overloadSpeed,
                           float maxCpGrowth, float maxOverloadGrowth, float proficiencyGrowth) {}
    public record SkillTuning(boolean enabled, float damage, float cp, float overload, float exp,
                              boolean destroyBlocks, float cooldown) {}

    /**
     * Effective tuning: the server config {@code skill.overrides} lines merged with the datapack
     * {@code academy_skills/<id>.json} override (multipliers multiply, a skill disabled in either is disabled).
     */
    public static SkillTuning parseSkill(String skillId, List<? extends String> entries) {
        return merge(parseConfig(skillId, entries), SkillTuningData.override(skillId));
    }

    public static SkillTuning merge(SkillTuning config, SkillTuningData.SkillOverride data) {
        if (data == null) return config;
        return new SkillTuning(config.enabled() && (data.enabled() == null || data.enabled()),
                product(config.damage(), data.damage()), product(config.cp(), data.cp()),
                product(config.overload(), data.overload()), product(config.exp(), data.exp()),
                config.destroyBlocks() && (data.destroyBlocks() == null || data.destroyBlocks()),
                product(config.cooldown(), data.cooldown()));
    }
    private static float product(float a, float b) { float v=a*b; return Float.isFinite(v)&&v>=0?v:1; }

    /** The balance profile's rate multipliers over the config snapshot. */
    public static Snapshot withProfile(Snapshot config, float cpRecovery, float overloadRecovery, float usageGrowth,
                                       float proficiencyGain) {
        if (cpRecovery == 1f && overloadRecovery == 1f && usageGrowth == 1f && proficiencyGain == 1f) return config;
        return new Snapshot(config.cpDelay(), config.cpSpeed() * cpRecovery, config.overloadDelay(),
                config.overloadSpeed() * overloadRecovery, config.maxCpGrowth() * usageGrowth,
                config.maxOverloadGrowth() * usageGrowth, config.proficiencyGrowth() * proficiencyGain);
    }

    /** Applies the global {@code skill.cpCostMultiplier} and {@code skill.cooldownMultiplier}. */
    public static SkillTuning withGlobal(SkillTuning tuning, float cpCost, float cooldown) {
        if (cpCost == 1f && cooldown == 1f) return tuning;
        return new SkillTuning(tuning.enabled(), tuning.damage(), product(tuning.cp(), cpCost), tuning.overload(),
                tuning.exp(), tuning.destroyBlocks(), product(tuning.cooldown(), cooldown));
    }

    /** The config lines alone ({@code <id>.<key>=<value>}). */
    public static SkillTuning parseConfig(String skillId, List<? extends String> entries) {
        boolean enabled=true, destroy=true; float damage=1, cp=1, overload=1, exp=1, cooldown=1;
        String prefix=skillId+".";
        for (String raw : entries) {
            if (raw == null || !raw.startsWith(prefix)) continue;
            int eq=raw.indexOf('=',prefix.length()); if(eq<0) continue;
            String key=raw.substring(prefix.length(),eq).strip(), value=raw.substring(eq+1).strip();
            try {
                switch(key) {
                    case "enabled" -> enabled=Boolean.parseBoolean(value);
                    case "destroy_blocks" -> destroy=Boolean.parseBoolean(value);
                    case "damage" -> damage=positive(value,damage);
                    case "cp" -> cp=positive(value,cp);
                    case "overload" -> overload=positive(value,overload);
                    case "exp" -> exp=positive(value,exp);
                    case "cooldown" -> cooldown=positive(value,cooldown);
                }
            } catch (RuntimeException ignored) { }
        }
        return new SkillTuning(enabled,damage,cp,overload,exp,destroy,cooldown);
    }
    private static float positive(String text,float fallback){float v=Float.parseFloat(text);return Float.isFinite(v)&&v>=0?v:fallback;}
    /**
     * CPData.getCPRecoverSpeed: {@code speed * 0.0003 * maxCP * lerp(1, 2, curCP / maxCP)}, where 1.0.7's
     * {@code maxCP} field is the RAW maximum (level base + course bonuses) without usage growth
     * ({@code getMaxCP()} adds {@code addMaxCP}). Callers pass that raw maximum; the current value may
     * exceed it by the usage growth, and lerpf is unclamped, so the ratio is only floored at 0.
     */
    public static float cpRecovery(float current, float rawMax, float personalRate, Snapshot rules) {
        if (!Float.isFinite(current) || !Float.isFinite(rawMax) || rawMax <= 0 || !Float.isFinite(personalRate)) return 0;
        return rules.cpSpeed() * .0003f * rawMax * personalRate * (1f + Math.max(0, current / rawMax));
    }
    /**
     * CPData.getOverloadRecoverSpeed: {@code speed * max(0.002 * maxO, 0.007 * maxO * lerp(1, 0.5, curO / maxO / 2))}
     * with the RAW maximum overload (no usage growth), as for {@link #cpRecovery}.
     */
    public static float overloadRecovery(float current, float rawMax, Snapshot rules) {
        if (!Float.isFinite(current) || !Float.isFinite(rawMax) || rawMax <= 0) return 0;
        return rules.overloadSpeed() * Math.max(.002f * rawMax,
                .007f * rawMax * (1f - .25f * Math.max(0, current / rawMax)));
    }
}
