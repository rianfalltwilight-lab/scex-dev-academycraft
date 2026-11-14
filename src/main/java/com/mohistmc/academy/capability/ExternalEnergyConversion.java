package com.mohistmc.academy.capability;

/**
 * CoFH/Forge-energy conversion boundary. AcademyCraft 1.0.7 used {@code 1 IF = 4 RF/FE};
 * the live ratio is {@code machines.fePerIf} in the server config.
 */
public final class ExternalEnergyConversion {
    /** Legacy default ratio; read {@link #fePerIf()} for the configured value. */
    public static final int FE_PER_IF = 4;

    private ExternalEnergyConversion() {}

    /** Configured FE per IF (server config {@code machines.fePerIf}, default {@value #FE_PER_IF}). */
    public static int fePerIf() {
        return Math.max(1, com.mohistmc.academy.config.MachineConfig.FE_PER_IF.get());
    }

    public static int wholeIfFromFe(int fe) {
        return Math.max(0, fe) / fePerIf();
    }

    /** "stored/max IF (stored/max FE)", so packs with FE mods (and IE's own "IF") read the same number. */
    public static String describe(int stored, int max) {
        return stored + "/" + max + " IF (" + ifToFe(stored) + "/" + ifToFe(max) + " FE)";
    }

    public static int ifToFe(int imaginaryEnergy) {
        long converted = (long) Math.max(0, imaginaryEnergy) * fePerIf();
        return (int) Math.min(Integer.MAX_VALUE, converted);
    }
}
