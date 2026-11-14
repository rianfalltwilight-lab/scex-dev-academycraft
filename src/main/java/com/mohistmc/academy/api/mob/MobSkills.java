package com.mohistmc.academy.api.mob;

import com.mohistmc.academy.AcademyCraft;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * Value of the {@code academy:mob_skills} entity-type data map
 * ({@code data/<ns>/data_maps/entity_type/mob_skills.json}): which skills every mob of that type casts
 * through {@link AbilityCastGoal}, and how often.
 */
public record MobSkills(List<Entry> skills, int castInterval, boolean needsLineOfSight, float damageScale) {
    public static final int DEFAULT_CAST_INTERVAL = 40;
    public static final float DEFAULT_PROFICIENCY = 0.5F;
    public static final double DEFAULT_MAX_RANGE = 16.0;
    public static final int MAX_TICKS = 72000;

    /** {@code "academy:thunder_bolt"} or {@code "thunder_bolt"}; stored bare, encoded namespaced. */
    public static final Codec<String> SKILL_ID = Codec.STRING.comapFlatMap(raw -> {
        String id = AcademyMobAbilities.normalize(raw);
        if (id.isEmpty() || id.indexOf(':') >= 0) return DataResult.error(() -> "Not an AcademyCraft skill id: " + raw);
        return DataResult.success(id);
    }, id -> AcademyCraft.MODID + ":" + id);

    private static final Codec<Float> WEIGHT = Codec.FLOAT.validate(w -> Float.isFinite(w) && w > 0
            ? DataResult.success(w) : DataResult.error(() -> "weight must be > 0: " + w));

    /** One castable skill with its proficiency, pick weight, usable distance band and extra cooldown. */
    public record Entry(String skill, float proficiency, float weight, double minRange, double maxRange,
                        int cooldownTicks) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.<Entry>create(i -> i.group(
                SKILL_ID.fieldOf("skill").forGetter(Entry::skill),
                Codec.floatRange(0F, 1F).optionalFieldOf("proficiency", DEFAULT_PROFICIENCY).forGetter(Entry::proficiency),
                WEIGHT.optionalFieldOf("weight", 1F).forGetter(Entry::weight),
                Codec.doubleRange(0, 256).optionalFieldOf("min_range", 0.0).forGetter(Entry::minRange),
                Codec.doubleRange(0, 256).optionalFieldOf("max_range", DEFAULT_MAX_RANGE).forGetter(Entry::maxRange),
                Codec.intRange(0, MAX_TICKS).optionalFieldOf("cooldown_ticks", 0).forGetter(Entry::cooldownTicks)
        ).apply(i, Entry::new)).validate(e -> e.maxRange() >= e.minRange() ? DataResult.success(e)
                : DataResult.error(() -> "max_range < min_range for " + e.skill()));

        /** Defaults used for a skill listed only in the mob's own {@code academy:mob_ability} attachment. */
        public static Entry defaults(String skill, float proficiency) {
            return new Entry(skill, proficiency, 1F, 0.0, DEFAULT_MAX_RANGE, 0);
        }

        public boolean inRange(double distance) {
            return distance >= minRange && distance <= maxRange;
        }
    }

    public static final Codec<MobSkills> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().optionalFieldOf("skills", List.of()).forGetter(MobSkills::skills),
            Codec.intRange(1, MAX_TICKS).optionalFieldOf("cast_interval", DEFAULT_CAST_INTERVAL).forGetter(MobSkills::castInterval),
            Codec.BOOL.optionalFieldOf("needs_line_of_sight", true).forGetter(MobSkills::needsLineOfSight),
            Codec.floatRange(0F, 1000F).optionalFieldOf("damage_scale", 1F).forGetter(MobSkills::damageScale)
    ).apply(i, MobSkills::new));

    /** Used for mobs that only have attachment skills. */
    public static final MobSkills DEFAULT = new MobSkills(List.of(), DEFAULT_CAST_INTERVAL, true, 1F);

    public MobSkills {
        skills = List.copyOf(skills);
    }

    /** Without a damage scale (1). */
    public MobSkills(List<Entry> skills, int castInterval, boolean needsLineOfSight) {
        this(skills, castInterval, needsLineOfSight, 1F);
    }
}
