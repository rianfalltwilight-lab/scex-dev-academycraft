package com.mohistmc.academy.config;

import java.util.List;

/** Pure, reload-safe interpretation of the 1.0.7 ability tuning matrix. */
public final class LegacyAbilityRules {
    private LegacyAbilityRules() {}
    public static final Snapshot DEFAULTS = new Snapshot(15, 1, 32, 1, .0025f, .0058f, 1);
    public record Snapshot(int cpDelay, float cpSpeed, int overloadDelay, float overloadSpeed,
                           float maxCpGrowth, float maxOverloadGrowth, float proficiencyGrowth) {}
    public record SkillTuning(boolean enabled, float damage, float cp, float overload, float exp,
                              boolean destroyBlocks) {}

    public static SkillTuning parseSkill(String skillId, List<? extends String> entries) {
        boolean enabled=true, destroy=true; float damage=1, cp=1, overload=1, exp=1;
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
                }
            } catch (RuntimeException ignored) { }
        }
        return new SkillTuning(enabled,damage,cp,overload,exp,destroy);
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
