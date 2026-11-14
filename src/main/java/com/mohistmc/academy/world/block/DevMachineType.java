package com.mohistmc.academy.world.block;

public enum DevMachineType {
    PORTABLE(2, 30, "ac.developer.type.portable", 25, 750),
    NORMAL(3, 70, "ac.developer.type.normal", 20, 700),
    ADVANCED(Integer.MAX_VALUE, 100, "ac.developer.type.advanced", 15, 600);

    public final int maxLevel;
    public final int syncRate;
    /** Translation key of the short tier name (Portable / Normal / Advanced). */
    public final String translationKey;
    public final int stimulationTicks;
    public final int energyPerStimulation;

    DevMachineType(int maxLevel, int syncRate, String translationKey, int stimulationTicks, int energyPerStimulation) {
        this.maxLevel = maxLevel;
        this.syncRate = syncRate;
        this.translationKey = translationKey;
        this.stimulationTicks = stimulationTicks;
        this.energyPerStimulation = energyPerStimulation;
    }

    public net.minecraft.network.chat.Component displayName() {
        return net.minecraft.network.chat.Component.translatable(translationKey);
    }

    public int energyPerTick() { return energyPerStimulation / stimulationTicks; }

    /**
     * AcademyCraft 1.0.7 advanced a stimulation only after {@code tick > tps}.
     * That means the real debit window is TPS + 1 ticks, despite the old UI
     * estimating CPS from TPS. Keep the runtime quirk for behavioural parity.
     */
    public int developmentTicksPerStimulation() { return stimulationTicks + 1; }

    public int actualEnergyPerStimulation() {
        return energyPerTick() * developmentTicksPerStimulation();
    }

    public int applySyncRate(int baseCost) {
        return baseCost * 100 / syncRate;
    }

    public static DevMachineType fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            return NORMAL;
        }
        return values()[ordinal];
    }
}
