package com.mohistmc.academy.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config section {@code [machines]}: energy, buffer and processing numbers of the
 * IF machines. Every default equals the value that was hard-coded before (AcademyCraft
 * 1.0.7 parity). Values are read at use time, so a config reload applies to placed
 * machines without re-placing them. Before the config is loaded (unit tests, early client
 * code) every accessor returns its legacy default.
 */
public final class MachineConfig {

    private MachineConfig() {}

    /** A double setting with its legacy default. */
    public static final class Num {
        private final String key;
        private final double fallback;
        private final double min;
        private final double max;
        private final String comment;
        private ModConfigSpec.DoubleValue value;
        private Scale scale = Scale.NONE;

        Num(String key, double fallback, double min, double max, String comment) {
            this.key = key;
            this.fallback = fallback;
            this.min = min;
            this.max = max;
            this.comment = comment;
        }

        void define(ModConfigSpec.Builder builder) {
            value = builder.comment(comment).defineInRange(key, fallback, min, max);
        }

        Num scaled(Scale category) { this.scale = category; return this; }

        /** The configured value times the balance profile's multiplier for its category. */
        public double get() {
            double v = configured() * com.mohistmc.academy.data.ProgressionData.machineScale(scale);
            return Double.isFinite(v) ? v : configured();
        }

        /** The configured value alone. */
        public double configured() {
            if (value == null) return fallback;
            try {
                double read = value.get();
                return Double.isFinite(read) ? Math.clamp(read, min, max) : fallback;
            } catch (IllegalStateException unloaded) {
                return fallback;
            }
        }

        public double legacyDefault() { return fallback; }

        /** In-memory change only (tests); never saved here. */
        public void set(double wanted) { if (value != null) value.set(wanted); }
    }

    /** An int setting with its legacy default. */
    public static final class Int {
        private final String key;
        private final int fallback;
        private final int min;
        private final int max;
        private final String comment;
        private ModConfigSpec.IntValue value;
        private Scale scale = Scale.NONE;

        Int(String key, int fallback, int min, int max, String comment) {
            this.key = key;
            this.fallback = fallback;
            this.min = min;
            this.max = max;
            this.comment = comment;
        }

        void define(ModConfigSpec.Builder builder) {
            value = builder.comment(comment).defineInRange(key, fallback, min, max);
        }

        Int scaled(Scale category) { this.scale = category; return this; }

        /** The configured value times the balance profile's multiplier for its category (saturating). */
        public int get() {
            int configured = configured();
            float multiplier = com.mohistmc.academy.data.ProgressionData.machineScale(scale);
            if (multiplier == 1f) return configured;
            double v = (double) configured * multiplier;
            return Double.isFinite(v) ? (int) Math.clamp(Math.round(v), 0, Integer.MAX_VALUE) : configured;
        }

        /** The configured value alone. */
        public int configured() {
            if (value == null) return fallback;
            try {
                return Math.clamp(value.get(), min, max);
            } catch (IllegalStateException unloaded) {
                return fallback;
            }
        }

        public int legacyDefault() { return fallback; }

        /** In-memory change only (tests); never saved here. */
        public void set(int wanted) { if (value != null) value.set(wanted); }
    }

    /** A fixed legacy number times the balance profile's multiplier for {@code scale} (saturating). */
    public static int scale(int legacy, Scale scale) {
        float multiplier = com.mohistmc.academy.data.ProgressionData.machineScale(scale);
        if (multiplier == 1f) return legacy;
        double v = (double) legacy * multiplier;
        return Double.isFinite(v) ? (int) Math.clamp(Math.round(v), 0, Integer.MAX_VALUE) : legacy;
    }

    /** Which balance-profile multiplier ({@code machines.*_multiplier}) a value takes. */
    public enum Scale { NONE, STORAGE, BANDWIDTH, GENERATION, RANGE, ITEM }

    /** One wireless node tier. */
    public static final class NodeTier {
        public final Int maxEnergy;
        public final Int bandwidth;
        public final Num range;
        public final Int maxConnections;

        NodeTier(int maxEnergy, int bandwidth, double range, int maxConnections) {
            this.maxEnergy = new Int("maxEnergy", maxEnergy, 1, 1_000_000_000, "IF buffer of the node.")
                    .scaled(Scale.STORAGE);
            this.bandwidth = new Int("bandwidth", bandwidth, 0, 1_000_000,
                    "IF per tick moved between the node and its connected machines / battery slots.")
                    .scaled(Scale.BANDWIDTH);
            this.range = new Num("range", range, 0.0, 256.0, "Connection range in blocks.").scaled(Scale.RANGE);
            this.maxConnections = new Int("maxConnections", maxConnections, 0, 1024,
                    "Maximum number of generators plus receivers linked to this node.");
        }

        void define(ModConfigSpec.Builder builder, String name) {
            builder.push(name);
            maxEnergy.define(builder);
            bandwidth.define(builder);
            range.define(builder);
            maxConnections.define(builder);
            builder.pop();
        }
    }

    // ==================== Values ====================

    public static final Int FE_PER_IF = new Int("fePerIf", 4, 1, 1_000,
            "FE (RF) per 1 IF at every Forge-energy boundary (energy bridges, FE item/block views).");
    public static final Int WIRELESS_NETWORK_BUFFER = new Int("networkBuffer", 2000, 0, 1_000_000_000,
            "Shared IF buffer of one wireless matrix network.")
            .scaled(Scale.STORAGE);

    public static final NodeTier NODE_BASIC = new NodeTier(15000, 150, 9.0, 5);
    public static final NodeTier NODE_STANDARD = new NodeTier(50000, 300, 12.0, 10);
    public static final NodeTier NODE_ADVANCED = new NodeTier(200000, 900, 19.0, 20);

    public static final Num MATRIX_CAPACITY_PER_LEVEL = new Num("capacityPerLevel", 8.0, 0.0, 1024.0,
            "Matrix node capacity = capacityPerLevel * L (L = core level 1..3), rounded down.");
    public static final Num MATRIX_BANDWIDTH_PER_LEVEL_SQUARED = new Num("bandwidthPerLevelSquared", 60.0, 0.0, 1_000_000.0,
            "Matrix bandwidth (IF/t) = bandwidthPerLevelSquared * L^2.")
            .scaled(Scale.BANDWIDTH);
    public static final Num MATRIX_RANGE_PER_SQRT_LEVEL = new Num("rangePerSqrtLevel", 24.0, 0.0, 256.0,
            "Matrix range (blocks) = rangePerSqrtLevel * sqrt(L), capped at 256.");

    public static final Int SOLAR_BUFFER = new Int("buffer", 1000, 1, 1_000_000_000, "IF buffer.")
            .scaled(Scale.STORAGE);
    public static final Num SOLAR_DAY_RATE = new Num("dayRate", 3.0, 0.0, 1_000_000.0, "IF per tick in clear daylight.")
            .scaled(Scale.GENERATION);
    public static final Num SOLAR_RAIN_RATE = new Num("rainRate", 0.6, 0.0, 1_000_000.0, "IF per tick in daylight while raining.")
            .scaled(Scale.GENERATION);
    public static final Num SOLAR_BANDWIDTH = new Num("bandwidth", 100.0, 0.0, 1_000_000.0, "IF per tick output.")
            .scaled(Scale.BANDWIDTH);

    public static final Int WIND_BUFFER = new Int("buffer", 20000, 1, 1_000_000_000, "IF buffer of the base.")
            .scaled(Scale.STORAGE);
    public static final Num WIND_MIN_RATE = new Num("minRate", 7.5, 0.0, 1_000_000.0, "IF per tick at or below minHeight.")
            .scaled(Scale.GENERATION);
    public static final Num WIND_MAX_RATE = new Num("maxRate", 15.0, 0.0, 1_000_000.0, "IF per tick at or above maxHeight.")
            .scaled(Scale.GENERATION);
    public static final Int WIND_MIN_HEIGHT = new Int("minHeight", 70, -2048, 4096, "Y of the turbine head where minRate applies.");
    public static final Int WIND_MAX_HEIGHT = new Int("maxHeight", 160, -2048, 4096,
            "Y of the turbine head where maxRate applies (linear in between).");
    public static final Num WIND_BANDWIDTH = new Num("bandwidth", 300.0, 0.0, 1_000_000.0, "IF per tick output.")
            .scaled(Scale.BANDWIDTH);

    public static final Int PHASE_BUFFER = new Int("buffer", 6000, 1, 1_000_000_000, "IF buffer.")
            .scaled(Scale.STORAGE);
    public static final Num PHASE_ENERGY_PER_MB = new Num("energyPerMb", 0.5, 0.0, 1_000_000.0, "IF generated per mB of phase liquid.")
            .scaled(Scale.GENERATION);
    public static final Int PHASE_MAX_DRAIN = new Int("maxDrainPerTick", 100, 0, 1_000_000, "Most phase liquid (mB) burned per tick.");
    public static final Num PHASE_BANDWIDTH = new Num("bandwidth", 50.0, 0.0, 1_000_000.0, "IF per tick output.")
            .scaled(Scale.BANDWIDTH);

    public static final Int CAT_BUFFER = new Int("buffer", 2000, 1, 1_000_000_000, "IF buffer.")
            .scaled(Scale.STORAGE);
    public static final Num CAT_GENERATION = new Num("generation", 500.0, 0.0, 1_000_000.0, "IF generated per tick.")
            .scaled(Scale.GENERATION);
    public static final Num CAT_BANDWIDTH = new Num("bandwidth", 200.0, 0.0, 1_000_000.0, "IF per tick output.")
            .scaled(Scale.BANDWIDTH);

    public static final Num FUSOR_BUFFER = new Num("buffer", 2000.0, 1.0, 1_000_000_000.0, "IF buffer.")
            .scaled(Scale.STORAGE);
    public static final Num FUSOR_BANDWIDTH = new Num("bandwidth", 50.0, 0.0, 1_000_000.0, "IF per tick input.")
            .scaled(Scale.BANDWIDTH);
    public static final Int FUSOR_PROCESS_TIME = new Int("processTime", 120, 1, 72_000,
            "Ticks per craft for recipes without their own \"time\".");
    public static final Num FUSOR_ENERGY_PER_TICK = new Num("energyPerTick", 12.0, 0.0, 1_000_000.0,
            "IF per tick while crafting, for recipes without their own \"energy\".");

    public static final Num FORMER_BUFFER = new Num("buffer", 3000.0, 1.0, 1_000_000_000.0, "IF buffer.")
            .scaled(Scale.STORAGE);
    public static final Num FORMER_BANDWIDTH = new Num("bandwidth", 50.0, 0.0, 1_000_000.0, "IF per tick input.")
            .scaled(Scale.BANDWIDTH);
    public static final Int FORMER_PROCESS_TIME = new Int("processTime", 60, 1, 72_000,
            "Ticks per craft for recipes without their own \"time\".");
    public static final Num FORMER_ENERGY_PER_TICK = new Num("energyPerTick", 13.3, 0.0, 1_000_000.0,
            "IF per tick while crafting, for recipes without their own \"energy\".");

    public static final Int BRIDGE_BUFFER_IF = new Int("bufferIf", 2000, 1, 100_000_000,
            "IF buffer of each RF/FE bridge (stored as bufferIf * fePerIf FE).")
            .scaled(Scale.STORAGE);
    public static final Num BRIDGE_BANDWIDTH = new Num("bandwidth", 100.0, 0.0, 1_000_000.0, "IF per tick to/from the wireless node.")
            .scaled(Scale.BANDWIDTH);

    public static final Int INTERFERER_BUFFER = new Int("buffer", 10_000, 1, 1_000_000_000, "IF buffer.")
            .scaled(Scale.STORAGE);
    public static final Int INTERFERER_BANDWIDTH = new Int("bandwidth", 50, 0, 1_000_000, "IF per tick drawn from the battery slot.")
            .scaled(Scale.BANDWIDTH);
    public static final Num INTERFERER_COST_PER_RANGE_SQUARED = new Num("costPerRangeSquared", 1.0, 0.0, 10_000.0,
            "IF paid every 10 ticks = ceil(costPerRangeSquared * range^2).");

    // Must stay after the fields: loading this class first initializes ACConfig.Server,
    // whose static block calls define(...) on the objects created above.
    static {
        ACConfig.Server.init();
    }

    static void define(ModConfigSpec.Builder builder) {
        builder.comment("Machine and wireless energy numbers. Defaults equal AcademyCraft 1.0.7.").push("machines");
        FE_PER_IF.define(builder);

        builder.push("wireless");
        WIRELESS_NETWORK_BUFFER.define(builder);
        builder.pop();

        builder.push("node");
        NODE_BASIC.define(builder, "basic");
        NODE_STANDARD.define(builder, "standard");
        NODE_ADVANCED.define(builder, "advanced");
        builder.pop();

        builder.push("matrix");
        MATRIX_CAPACITY_PER_LEVEL.define(builder);
        MATRIX_BANDWIDTH_PER_LEVEL_SQUARED.define(builder);
        MATRIX_RANGE_PER_SQRT_LEVEL.define(builder);
        builder.pop();

        builder.push("solarGen");
        SOLAR_BUFFER.define(builder);
        SOLAR_DAY_RATE.define(builder);
        SOLAR_RAIN_RATE.define(builder);
        SOLAR_BANDWIDTH.define(builder);
        builder.pop();

        builder.push("windGen");
        WIND_BUFFER.define(builder);
        WIND_MIN_RATE.define(builder);
        WIND_MAX_RATE.define(builder);
        WIND_MIN_HEIGHT.define(builder);
        WIND_MAX_HEIGHT.define(builder);
        WIND_BANDWIDTH.define(builder);
        builder.pop();

        builder.push("phaseGen");
        PHASE_BUFFER.define(builder);
        PHASE_ENERGY_PER_MB.define(builder);
        PHASE_MAX_DRAIN.define(builder);
        PHASE_BANDWIDTH.define(builder);
        builder.pop();

        builder.push("catEngine");
        CAT_BUFFER.define(builder);
        CAT_GENERATION.define(builder);
        CAT_BANDWIDTH.define(builder);
        builder.pop();

        builder.push("imagFusor");
        FUSOR_BUFFER.define(builder);
        FUSOR_BANDWIDTH.define(builder);
        FUSOR_PROCESS_TIME.define(builder);
        FUSOR_ENERGY_PER_TICK.define(builder);
        builder.pop();

        builder.push("metalFormer");
        FORMER_BUFFER.define(builder);
        FORMER_BANDWIDTH.define(builder);
        FORMER_PROCESS_TIME.define(builder);
        FORMER_ENERGY_PER_TICK.define(builder);
        builder.pop();

        builder.push("energyBridge");
        BRIDGE_BUFFER_IF.define(builder);
        BRIDGE_BANDWIDTH.define(builder);
        builder.pop();

        builder.push("abilityInterferer");
        INTERFERER_BUFFER.define(builder);
        INTERFERER_BANDWIDTH.define(builder);
        INTERFERER_COST_PER_RANGE_SQUARED.define(builder);
        builder.pop();

        builder.pop();
    }

    // ==================== Derived helpers ====================

    /** Wind turbine IF/t for a head at {@code mainY}; linear between the configured heights. */
    public static double windRate(int mainY) {
        double low = WIND_MIN_RATE.get();
        double high = WIND_MAX_RATE.get();
        int minY = WIND_MIN_HEIGHT.get();
        int maxY = WIND_MAX_HEIGHT.get();
        if (maxY <= minY) return mainY >= maxY ? high : low;
        double t = Math.clamp((mainY - (double) minY) / (maxY - minY), 0.0, 1.0);
        return low + (high - low) * t;
    }

    /** Ability Interferer pulse cost for an already clamped legacy cost of range^2. */
    public static int interfererCost(int legacyRangeSquaredCost) {
        double scaled = Math.ceil(INTERFERER_COST_PER_RANGE_SQUARED.get() * legacyRangeSquaredCost - 1.0e-9);
        return (int) Math.clamp(scaled, 0, Integer.MAX_VALUE);
    }
}
