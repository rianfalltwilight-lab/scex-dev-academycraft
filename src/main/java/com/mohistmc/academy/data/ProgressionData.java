package com.mohistmc.academy.data;

import com.mohistmc.academy.config.BalanceProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.world.block.DevMachineType;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * {@code data/<namespace>/academy_progression/*.json}: CP/overload curves, level-up and developer numbers.
 *
 * <p>Every field is optional; the built-in defaults are the AcademyCraft 1.0.7 values the code used before
 * this file existed.  Files merge in id order, later files overriding individual fields of earlier ones.
 * A malformed field is logged and ignored, keeping the value it had.  Server and client read the same
 * accessors (the files are synced), so skill-tree estimates match what the server charges.
 * See docs/MODPACK-PROGRESSION.md.
 */
public final class ProgressionData extends SyncedJsonData {
    public static final ProgressionData INSTANCE = new ProgressionData();

    /** Upper bounds that keep {@code energy * stimulations} inside an int. */
    public static final int MAX_STIMULATIONS = 1000;
    public static final int MAX_ENERGY_PER_STIMULATION = 1_000_000;
    public static final int MAX_TICKS_PER_STIMULATION = 72_000;

    private static final Values DEFAULTS = new Builder().build();
    /** Built values per balance profile: built-in defaults, then the profile files, then the pack files. */
    private static volatile Map<BalanceProfile, Values> perProfile = Map.of();
    private static volatile Map<ResourceLocation, JsonElement> packFiles = Map.of();
    private static volatile Map<BalanceProfile, Map<ResourceLocation, JsonElement>> profileFiles = Map.of();

    private ProgressionData() { super("progression", "academy_progression"); }

    private static Values values() {
        Values v = perProfile.get(BalanceProfile.current());
        return v == null ? DEFAULTS : v;
    }

    @Override
    protected void accept(Map<ResourceLocation, JsonElement> files) {
        packFiles = Map.copyOf(files);
        rebuild();
    }

    /** {@link BalanceProfileData} hands over the {@code <profile>/progression/} files. */
    static void acceptProfileFiles(Map<BalanceProfile, Map<ResourceLocation, JsonElement>> files) {
        profileFiles = Map.copyOf(files);
        rebuild();
    }

    private static synchronized void rebuild() {
        Map<BalanceProfile, Values> out = new java.util.EnumMap<>(BalanceProfile.class);
        for (BalanceProfile profile : BalanceProfile.values()) {
            Builder builder = new Builder();
            apply(builder, profileFiles.getOrDefault(profile, Map.of()));
            apply(builder, packFiles);
            out.put(profile, builder.build());
        }
        perProfile = Map.copyOf(out);
    }

    private static void apply(Builder builder, Map<ResourceLocation, JsonElement> files) {
        new TreeMap<>(files).forEach((id, json) -> {
            if (json == null || !json.isJsonObject()) {
                LOGGER.warn("Progression file {} is not a JSON object; ignored", id);
                return;
            }
            builder.apply(id, json.getAsJsonObject());
        });
    }

    /** Test hook: install these files as if a datapack reload delivered them (merged in id order). */
    public static void applyForTest(Map<ResourceLocation, JsonElement> files) {
        INSTANCE.accept(new TreeMap<>(files));
    }

    /** Test hook: back to the built-in defaults (the profile layer stays). */
    public static void resetForTest() {
        INSTANCE.accept(Map.of());
    }

    // ---- rules (balance profile knobs) -----------------------------------------------------------

    /** Multiplies the config {@code cpRecoverSpeed}. */
    public static float cpRecoveryMultiplier() { return values().cpRecoveryMultiplier; }
    /** Multiplies the config {@code overloadRecoverSpeed}. */
    public static float overloadRecoveryMultiplier() { return values().overloadRecoveryMultiplier; }
    /** Multiplies the config {@code maxCpIncreaseRate} and {@code maxOverloadIncreaseRate}. */
    public static float usageGrowthMultiplier() { return values().usageGrowthMultiplier; }
    /** Multiplies the config {@code proficiencyIncreaseRate}. */
    public static float proficiencyGainMultiplier() { return values().proficiencyGainMultiplier; }
    /** 1.0.7 cleared usage growth on every level change; true keeps it (still capped by the new level). */
    public static boolean keepUsageGrowthOnLevelUp() { return values().keepUsageGrowthOnLevelUp; }
    /** The overload lock lifts at or below this fraction of max overload (1.0.7: 0, i.e. fully drained). */
    public static float overloadUnlockFraction() { return values().overloadUnlockFraction; }
    /** CP cost at full proficiency is {@code 1 - discount} of the cost at zero (linear in proficiency). */
    public static float proficiencyCpDiscount() { return values().proficiencyCpDiscount; }
    /** Prerequisite proficiency requirements above this are lowered to it (1.0.7: 1, no cap). */
    public static float prerequisiteProficiencyCap() { return values().prerequisiteProficiencyCap; }
    /** A CP potion refills up to this fraction of max CP (1.0.7: 0.5). */
    public static float cpPotionRestoreFraction() { return values().cpPotionRestoreFraction; }
    /** ... but at most this much CP (1.0.7: 2000). */
    public static float cpPotionMaxRestore() { return values().cpPotionMaxRestore; }

    // ---- resources -------------------------------------------------------------------------------

    private static int level(int value) { return Math.clamp(value, 0, 5); }
    public static float initialCp(int level) { return values().cpInitial[level(level)]; }
    public static float maxAddedCp(int level) { return values().cpMaxAdded[level(level)]; }
    public static float initialOverload(int level) { return values().overloadInitial[level(level)]; }
    public static float maxAddedOverload(int level) { return values().overloadMaxAdded[level(level)]; }

    public static float brainCourseCp() { return values().brainCourseCp; }
    public static float brainCourseAdvancedCp() { return values().brainCourseAdvancedCp; }
    public static float brainCourseAdvancedOverload() { return values().brainCourseAdvancedOverload; }
    public static float mindCourseRecovery() { return values().mindCourseRecovery; }

    // ---- level-up gauge --------------------------------------------------------------------------

    /** Gauge experience needed per controllable skill of the current level before the next level-up. */
    public static float thresholdPerSkill(int level) { return values().thresholdPerSkill[level(level)]; }

    // ---- developer machines ----------------------------------------------------------------------

    public enum DeveloperField { MAX_LEVEL, SYNC_RATE, TICKS_PER_STIMULATION, ENERGY_PER_STIMULATION }

    /** The datapack value of one developer field, or {@code fallback} (the enum's built-in value). */
    public static int developer(DevMachineType type, DeveloperField field, int fallback) {
        Integer[] row = values().developer.get(type);
        Integer value = row == null ? null : row[field.ordinal()];
        return value == null ? fallback : value;
    }

    // ---- stimulation counts ----------------------------------------------------------------------

    private static int stimulations(float raw) {
        if (!Float.isFinite(raw)) return MAX_STIMULATIONS;
        return Math.clamp((int) raw, 1, MAX_STIMULATIONS);
    }

    /** 1.0.7 DevelopActionSkill: {@code (int)(3 + level * level * 0.5)}. */
    public static int learnStimulations(int skillLevel) {
        Values v = values();
        return stimulations(v.learnBase + skillLevel * skillLevel * v.learnPerLevelSquared);
    }

    /** 1.0.7 DevelopActionLevel: {@code 5 * (currentLevel + 1)}. */
    public static int levelUpStimulations(int currentLevel) {
        return stimulations((float) values().levelUpPerLevel * (currentLevel + 1));
    }

    /** 1.0.7 DevelopActionReset: {@code currentLevel * 10}. */
    public static int resetStimulations(int currentLevel) {
        return stimulations((float) values().resetPerLevel * currentLevel);
    }

    /** 1.0.7 DevelopActionLevel at level zero: five stimulations. */
    public static int inductionStimulations() { return stimulations(values().induction); }

    // ---- induction -------------------------------------------------------------------------------

    /** Weight of a category in an induction without a factor (unlisted categories weigh 1). */
    public static double inductionWeight(AbilityCategory category) {
        Double weight = values().categoryWeights.get(category.id());
        return weight == null ? 1.0 : weight;
    }

    /**
     * Picks the category of an induction without a factor.  Equal weights keep the legacy single
     * {@code nextInt(size)} draw; all-zero weights fall back to uniform.
     */
    public static AbilityCategory pickInductionCategory(List<AbilityCategory> categories, RandomSource random) {
        if (categories.isEmpty()) return null;
        double[] weights = new double[categories.size()];
        double total = 0;
        boolean equal = true;
        for (int i = 0; i < weights.length; i++) {
            weights[i] = inductionWeight(categories.get(i));
            total += weights[i];
            if (weights[i] != weights[0]) equal = false;
        }
        if (equal || !(total > 0) || !Double.isFinite(total)) {
            return categories.get(random.nextInt(categories.size()));
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] <= 0) continue;
            roll -= weights[i];
            if (roll < 0) return categories.get(i);
        }
        for (int i = weights.length - 1; i >= 0; i--) if (weights[i] > 0) return categories.get(i);
        return categories.get(random.nextInt(categories.size()));
    }

    // ---- storage ---------------------------------------------------------------------------------

    private record Values(float[] cpInitial, float[] cpMaxAdded, float[] overloadInitial, float[] overloadMaxAdded,
                          float brainCourseCp, float brainCourseAdvancedCp, float brainCourseAdvancedOverload,
                          float mindCourseRecovery, float[] thresholdPerSkill,
                          Map<DevMachineType, Integer[]> developer,
                          float learnBase, float learnPerLevelSquared, int levelUpPerLevel, int resetPerLevel,
                          int induction, Map<String, Double> categoryWeights,
                          float cpRecoveryMultiplier, float overloadRecoveryMultiplier, float usageGrowthMultiplier,
                          float proficiencyGainMultiplier, boolean keepUsageGrowthOnLevelUp, float overloadUnlockFraction,
                          float proficiencyCpDiscount, float prerequisiteProficiencyCap,
                          float cpPotionRestoreFraction, float cpPotionMaxRestore) {}

    /** Holds the built-in defaults; each file overrides the fields it names. */
    private static final class Builder {
        float[] cpInitial = {1800, 1800, 2800, 4000, 5800, 8000};
        float[] cpMaxAdded = {0, 900, 1000, 1500, 1700, 12000};
        float[] overloadInitial = {100, 100, 150, 240, 350, 500};
        float[] overloadMaxAdded = {0, 40, 70, 80, 100, 500};
        float brainCourseCp = 1000;
        float brainCourseAdvancedCp = 1500;
        float brainCourseAdvancedOverload = 100;
        float mindCourseRecovery = 1.2f;
        float[] thresholdPerSkill = {0.666f, 0.666f, 0.666f, 0.666f, 1.333f, 0.666f};
        final Map<DevMachineType, Integer[]> developer = new EnumMap<>(DevMachineType.class);
        float learnBase = 3;
        float learnPerLevelSquared = .5f;
        int levelUpPerLevel = 5;
        int resetPerLevel = 10;
        int induction = 5;
        final Map<String, Double> categoryWeights = new LinkedHashMap<>();
        float cpRecoveryMultiplier = 1;
        float overloadRecoveryMultiplier = 1;
        float usageGrowthMultiplier = 1;
        float proficiencyGainMultiplier = 1;
        boolean keepUsageGrowthOnLevelUp = false;
        float overloadUnlockFraction = 0;
        float proficiencyCpDiscount = 0;
        float prerequisiteProficiencyCap = 1;
        float cpPotionRestoreFraction = .5f;
        float cpPotionMaxRestore = 2000;

        private ResourceLocation file;

        Values build() {
            Map<DevMachineType, Integer[]> dev = new EnumMap<>(DevMachineType.class);
            developer.forEach((type, row) -> dev.put(type, row.clone()));
            return new Values(cpInitial.clone(), cpMaxAdded.clone(), overloadInitial.clone(),
                    overloadMaxAdded.clone(), brainCourseCp, brainCourseAdvancedCp, brainCourseAdvancedOverload,
                    mindCourseRecovery, thresholdPerSkill.clone(), dev, learnBase,
                    learnPerLevelSquared, levelUpPerLevel, resetPerLevel, induction,
                    Map.copyOf(categoryWeights), cpRecoveryMultiplier, overloadRecoveryMultiplier,
                    usageGrowthMultiplier, proficiencyGainMultiplier, keepUsageGrowthOnLevelUp,
                    overloadUnlockFraction, proficiencyCpDiscount, prerequisiteProficiencyCap,
                    cpPotionRestoreFraction, cpPotionMaxRestore);
        }

        void apply(ResourceLocation id, JsonObject root) {
            file = id;
            JsonObject cp = object(root, "", "cp");
            if (cp != null) {
                cpInitial = levels(cp, "cp.", "initial", cpInitial);
                cpMaxAdded = levels(cp, "cp.", "max_added", cpMaxAdded);
            }
            JsonObject overload = object(root, "", "overload");
            if (overload != null) {
                overloadInitial = levels(overload, "overload.", "initial", overloadInitial);
                overloadMaxAdded = levels(overload, "overload.", "max_added", overloadMaxAdded);
            }
            JsonObject courses = object(root, "", "courses");
            if (courses != null) {
                brainCourseCp = number(courses, "courses.", "brain_course_cp", brainCourseCp, 1e9f);
                brainCourseAdvancedCp = number(courses, "courses.", "brain_course_advanced_cp",
                        brainCourseAdvancedCp, 1e9f);
                brainCourseAdvancedOverload = number(courses, "courses.", "brain_course_advanced_overload",
                        brainCourseAdvancedOverload, 1e9f);
                mindCourseRecovery = number(courses, "courses.", "mind_course_recovery", mindCourseRecovery, 1000);
            }
            JsonObject levelUp = object(root, "", "level_up");
            if (levelUp != null) {
                thresholdPerSkill = levels(levelUp, "level_up.", "threshold_per_skill", thresholdPerSkill);
            }
            JsonObject dev = object(root, "", "developer");
            if (dev != null) {
                for (DevMachineType type : DevMachineType.values()) {
                    String key = type.name().toLowerCase(Locale.ROOT);
                    JsonObject row = object(dev, "developer.", key);
                    if (row != null) developer(type, "developer." + key + ".", row);
                }
                for (String key : dev.keySet()) {
                    if (Arrays.stream(DevMachineType.values()).noneMatch(t -> t.name().equalsIgnoreCase(key))) {
                        warn("developer." + key, "is not a developer type (portable, normal, advanced)");
                    }
                }
            }
            JsonObject stim = object(root, "", "stimulations");
            if (stim != null) {
                learnBase = number(stim, "stimulations.", "learn_base", learnBase, MAX_STIMULATIONS);
                learnPerLevelSquared = number(stim, "stimulations.", "learn_per_level_squared",
                        learnPerLevelSquared, MAX_STIMULATIONS);
                levelUpPerLevel = integer(stim, "stimulations.", "level_up_per_level", levelUpPerLevel,
                        1, MAX_STIMULATIONS);
                resetPerLevel = integer(stim, "stimulations.", "reset_per_level", resetPerLevel,
                        1, MAX_STIMULATIONS);
                induction = integer(stim, "stimulations.", "induction", induction, 1, MAX_STIMULATIONS);
            }
            JsonObject rules = object(root, "", "rules");
            if (rules != null) {
                cpRecoveryMultiplier = number(rules, "rules.", "cp_recovery_multiplier", cpRecoveryMultiplier, 100);
                overloadRecoveryMultiplier = number(rules, "rules.", "overload_recovery_multiplier",
                        overloadRecoveryMultiplier, 100);
                usageGrowthMultiplier = number(rules, "rules.", "usage_growth_multiplier", usageGrowthMultiplier, 1000);
                proficiencyGainMultiplier = number(rules, "rules.", "proficiency_gain_multiplier",
                        proficiencyGainMultiplier, 100);
                keepUsageGrowthOnLevelUp = bool(rules, "rules.", "keep_usage_growth_on_level_up",
                        keepUsageGrowthOnLevelUp);
                overloadUnlockFraction = number(rules, "rules.", "overload_unlock_fraction", overloadUnlockFraction, 1);
                proficiencyCpDiscount = number(rules, "rules.", "proficiency_cp_discount", proficiencyCpDiscount, .9f);
                prerequisiteProficiencyCap = number(rules, "rules.", "prerequisite_proficiency_cap",
                        prerequisiteProficiencyCap, 1);
            }
            JsonObject potion = object(root, "", "cp_potion");
            if (potion != null) {
                cpPotionRestoreFraction = number(potion, "cp_potion.", "restore_fraction", cpPotionRestoreFraction, 1);
                cpPotionMaxRestore = number(potion, "cp_potion.", "max_restore", cpPotionMaxRestore, 1e9f);
            }
            JsonObject induct = object(root, "", "induction");
            if (induct != null) {
                JsonObject weights = object(induct, "induction.", "category_weights");
                if (weights != null) weights(weights);
            }
        }

        private void developer(DevMachineType type, String path, JsonObject row) {
            Integer[] current = developer.computeIfAbsent(type, t -> new Integer[DeveloperField.values().length]);
            Integer v;
            if ((v = optionalInt(row, path, "max_level", 0, Integer.MAX_VALUE)) != null)
                current[DeveloperField.MAX_LEVEL.ordinal()] = v;
            if ((v = optionalInt(row, path, "sync_rate", 1, 100)) != null)
                current[DeveloperField.SYNC_RATE.ordinal()] = v;
            if ((v = optionalInt(row, path, "ticks_per_stimulation", 1, MAX_TICKS_PER_STIMULATION)) != null)
                current[DeveloperField.TICKS_PER_STIMULATION.ordinal()] = v;
            if ((v = optionalInt(row, path, "energy_per_stimulation", 0, MAX_ENERGY_PER_STIMULATION)) != null)
                current[DeveloperField.ENERGY_PER_STIMULATION.ordinal()] = v;
        }

        private void weights(JsonObject weights) {
            for (Map.Entry<String, JsonElement> entry : weights.entrySet()) {
                String path = "induction.category_weights." + entry.getKey();
                Double value = finiteNumber(entry.getValue());
                if (value == null || value < 0) { warn(path, "must be a finite number >= 0"); continue; }
                if (AbilityCategory.fromId(entry.getKey()) == null) {
                    LOGGER.warn("Progression file {}: {} names a category that is not registered", file, path);
                }
                categoryWeights.put(entry.getKey(), Math.min(value, 1e6));
            }
            boolean anyPositive = AbilityCategory.all().stream()
                    .anyMatch(c -> categoryWeights.getOrDefault(c.id(), 1.0) > 0);
            if (!anyPositive) {
                LOGGER.warn("Progression file {}: every induction category weight is 0; "
                        + "an induction without a factor falls back to a uniform pick", file);
            }
        }

        // -- field helpers --

        private JsonObject object(JsonObject parent, String prefix, String key) {
            if (!parent.has(key)) return null;
            JsonElement element = parent.get(key);
            if (element.isJsonObject()) return element.getAsJsonObject();
            warn(prefix + key, "must be a JSON object");
            return null;
        }

        private float[] levels(JsonObject parent, String prefix, String key, float[] fallback) {
            if (!parent.has(key)) return fallback;
            JsonElement element = parent.get(key);
            if (!element.isJsonArray() || element.getAsJsonArray().size() != 6) {
                warn(prefix + key, "must be an array of six numbers (levels 0..5)");
                return fallback;
            }
            JsonArray array = element.getAsJsonArray();
            float[] out = new float[6];
            for (int i = 0; i < 6; i++) {
                Double value = finiteNumber(array.get(i));
                if (value == null || value < 0 || value > 1e9) {
                    warn(prefix + key, "entry " + i + " must be a finite number in [0, 1e9]");
                    return fallback;
                }
                out[i] = value.floatValue();
            }
            return out;
        }

        private float number(JsonObject parent, String prefix, String key, float fallback, float max) {
            if (!parent.has(key)) return fallback;
            Double value = finiteNumber(parent.get(key));
            if (value == null || value < 0) {
                warn(prefix + key, "must be a finite number >= 0");
                return fallback;
            }
            if (value > max) {
                LOGGER.warn("Progression file {}: {} clamped to {}", file, prefix + key, max);
                return max;
            }
            return value.floatValue();
        }

        private int integer(JsonObject parent, String prefix, String key, int fallback, int min, int max) {
            Integer value = optionalInt(parent, prefix, key, min, max);
            return value == null ? fallback : value;
        }

        private Integer optionalInt(JsonObject parent, String prefix, String key, int min, int max) {
            if (!parent.has(key)) return null;
            Double value = finiteNumber(parent.get(key));
            if (value == null || value < 0) {
                warn(prefix + key, "must be a finite number >= 0");
                return null;
            }
            int clamped = (int) Math.clamp(Math.floor(value), min, max);
            if (clamped != value) {
                LOGGER.warn("Progression file {}: {} = {} used as {}", file, prefix + key, value, clamped);
            }
            return clamped;
        }

        private boolean bool(JsonObject parent, String prefix, String key, boolean fallback) {
            if (!parent.has(key)) return fallback;
            JsonElement e = parent.get(key);
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
            warn(prefix + key, "must be true or false");
            return fallback;
        }

        private static Double finiteNumber(JsonElement element) {
            if (element == null || !element.isJsonPrimitive()) return null;
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (!primitive.isNumber()) return null;
            double value = primitive.getAsDouble();
            return Double.isFinite(value) ? value : null;
        }

        private void warn(String path, String problem) {
            LOGGER.warn("Progression file {}: {} {}; keeping the previous value", file, path, problem);
        }
    }
}
