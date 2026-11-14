package com.mohistmc.academy.world.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;

/** Comparator output of a machine: its stored energy (through its FE face), 0..15 like a container. */
public final class MachineComparator {
    private MachineComparator() {}

    public static int signal(Level level, BlockPos pos) {
        var energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        if (energy == null) return 0;
        int max = energy.getMaxEnergyStored(), stored = energy.getEnergyStored();
        if (max <= 0 || stored <= 0) return 0;
        return 1 + (int) Math.floor(14.0 * Math.min(stored, max) / max);
    }
}
