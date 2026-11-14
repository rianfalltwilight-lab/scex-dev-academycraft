package com.mohistmc.academy.world.block;

import com.mohistmc.academy.data.ProgressionData;
import com.mohistmc.academy.data.ProgressionData.DeveloperField;

/**
 * Developer tiers.  The constructor values are the AcademyCraft 1.0.7 numbers and serve as fallbacks;
 * datapack {@code academy_progression} {@code developer.<tier>} overrides them, so always read the
 * accessor methods.
 */
public enum DevMachineType {
    PORTABLE(2, 30, "ac.developer.type.portable", 25, 750),
    NORMAL(3, 70, "ac.developer.type.normal", 20, 700),
    ADVANCED(Integer.MAX_VALUE, 100, "ac.developer.type.advanced", 15, 600);

    private final int defaultMaxLevel;
    private final int defaultSyncRate;
    /** Translation key of the short tier name (Portable / Normal / Advanced). */
    public final String translationKey;
    private final int defaultStimulationTicks;
    private final int defaultEnergyPerStimulation;

    DevMachineType(int maxLevel, int syncRate, String translationKey, int stimulationTicks, int energyPerStimulation) {
        this.defaultMaxLevel = maxLevel;
        this.defaultSyncRate = syncRate;
        this.translationKey = translationKey;
        this.defaultStimulationTicks = stimulationTicks;
        this.defaultEnergyPerStimulation = energyPerStimulation;
    }

    /** Highest skill level this tier can teach. */
    public int maxLevel() {
        return ProgressionData.developer(this, DeveloperField.MAX_LEVEL, defaultMaxLevel);
    }

    /** Sync rate in percent (1..100), shown as the skill-tree bar. */
    public int syncRate() {
        return ProgressionData.developer(this, DeveloperField.SYNC_RATE, defaultSyncRate);
    }

    /** 1.0.7 "TPS": nominal ticks per stimulation (at least 1). */
    public int stimulationTicks() {
        return ProgressionData.developer(this, DeveloperField.TICKS_PER_STIMULATION, defaultStimulationTicks);
    }

    /** 1.0.7 "CPS": energy per nominal stimulation. */
    public int energyPerStimulation() {
        return ProgressionData.developer(this, DeveloperField.ENERGY_PER_STIMULATION, defaultEnergyPerStimulation);
    }

    public net.minecraft.network.chat.Component displayName() {
        return net.minecraft.network.chat.Component.translatable(translationKey);
    }

    public int energyPerTick() { return energyPerStimulation() / stimulationTicks(); }

    /**
     * AcademyCraft 1.0.7 advanced a stimulation only after {@code tick > tps}.
     * That means the real debit window is TPS + 1 ticks, despite the old UI
     * estimating CPS from TPS. Keep the runtime quirk for behavioural parity.
     */
    public int developmentTicksPerStimulation() { return stimulationTicks() + 1; }

    public int actualEnergyPerStimulation() {
        return energyPerTick() * developmentTicksPerStimulation();
    }

    public int applySyncRate(int baseCost) {
        return baseCost * 100 / syncRate();
    }

    public static DevMachineType fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            return NORMAL;
        }
        return values()[ordinal];
    }
}
