package com.mohistmc.academy.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config section [mobs]: rules for mobs that cast AcademyCraft skills. */
public final class MobCastConfig {
    private MobCastConfig() {}

    private static ModConfigSpec.BooleanValue destroyBlocks;
    private static ModConfigSpec.BooleanValue sameTypeFriendly;
    private static ModConfigSpec.IntValue castsPerTick;
    private static ModConfigSpec.IntValue minCooldown;
    private static ModConfigSpec.BooleanValue movementSkills;

    static void define(ModConfigSpec.Builder builder) {
        builder.push("mobs");
        destroyBlocks = builder
                .comment("Whether skills cast by mobs may destroy blocks. Even when true, the mobGriefing gamerule, "
                        + "skill.destroyBlocks and each skill's destroy_blocks tuning must also allow it.")
                .define("destroyBlocks", false);
        sameTypeFriendly = builder
                .comment("Whether a mob's skills spare other mobs of the same entity type.")
                .define("sameTypeFriendly", true);
        castsPerTick = builder
                .comment("Most mob skill casts processed per dimension per tick; further casts are refused.")
                .defineInRange("castsPerTick", 20, 1, 1000);
        minCooldown = builder
                .comment("Lowest cooldown in ticks applied after any mob cast.")
                .defineInRange("minCooldownTicks", 10, 0, 72000);
        movementSkills = builder
                .comment("Whether mob skills may move or teleport the caster itself (dashes, blinks). Entity types "
                        + "in the academy:immobile_casters tag are never moved.")
                .define("movementSkills", true);
        builder.pop();
    }

    public static boolean destroyBlocks() { return read(destroyBlocks, false); }
    public static boolean sameTypeFriendly() { return read(sameTypeFriendly, true); }
    public static int castsPerTick() { return read(castsPerTick, 20); }
    public static int minCooldown() { return read(minCooldown, 10); }
    public static boolean movementSkills() { return read(movementSkills, true); }

    private static <T> T read(ModConfigSpec.ConfigValue<T> value, T fallback) {
        if (value == null) return fallback;
        try { return value.get(); } catch (IllegalStateException unloaded) { return fallback; }
    }
}
