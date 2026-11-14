package com.mohistmc.academy.config;

/**
 * Server balance profile ({@code balance.profile}).  {@link #MODERN} layers the built-in
 * {@code academy_balance/modern/} data over the 1.0.7 numbers; {@link #LEGACY} keeps the 1.0.7 numbers.
 * Datapack overrides ({@code academy_skills/}, {@code academy_progression/}) apply on top of either.
 */
public enum BalanceProfile {
    MODERN("modern"),
    LEGACY("legacy");

    private final String id;

    BalanceProfile(String id) { this.id = id; }

    /** The directory name under {@code academy_balance/}. */
    public String id() { return id; }

    public static BalanceProfile byId(String id) {
        for (BalanceProfile profile : values()) if (profile.id.equals(id)) return profile;
        return null;
    }

    /** The configured profile; {@link #MODERN} before the server config loads (also on clients without one). */
    public static BalanceProfile current() {
        try {
            return ACConfig.Server.BALANCE_PROFILE.get();
        } catch (IllegalStateException | NullPointerException unloaded) {
            return MODERN;
        }
    }
}
