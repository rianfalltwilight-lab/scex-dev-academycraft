package com.mohistmc.academy.api.mob;

/**
 * Implemented by skill effects that mobs can cast. {@link #mobCast} performs the skill for a non-player
 * caster on the server thread and returns whether it went off (a cooldown is then applied).
 */
public interface MobCastable {
    boolean mobCast(MobCastContext context);

    /** Raw cooldown after a successful mob cast, before tuning multipliers and the configured floor. */
    int mobCooldownTicks(float proficiency);
}
