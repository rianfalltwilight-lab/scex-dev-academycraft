package com.mohistmc.academy.api.mob;

/**
 * Implemented by skill effects that mobs can cast. {@link #mobCast} performs the skill for a non-player
 * caster on the server thread and returns whether it went off (a cooldown is then applied).
 */
public interface MobCastable {
    boolean mobCast(MobCastContext context);

    /** Raw cooldown after a successful mob cast, before tuning multipliers and the configured floor. */
    int mobCooldownTicks(float proficiency);

    /**
     * Ticks a mob charges before {@link #mobCast} runs (0 = instant).  While charging, the context is
     * re-aimed every tick and passed to {@link #mobChargeTick}; at the end {@code mobCast} receives a
     * context whose {@code chargeTicks()} is the charge reached.
     */
    default int mobChargeTicks(float proficiency) { return 0; }

    /** Called each charging tick (1-based); return false to abort the cast without a cooldown. */
    default boolean mobChargeTick(MobCastContext context, int tick) { return true; }

    /** Called when a charge ends without release (caster died, unloaded, aborted). */
    default void mobChargeAborted(MobCastContext context) {}
}
