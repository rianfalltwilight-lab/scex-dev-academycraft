package com.mohistmc.academy.capability;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Battery-slot support for other mods' Forge-energy items, such as Singularity Iteration batteries and
 * electric tools.  1.0.7 registered an IC2 {@code EnergyItemManager} so IC2 electric items worked in
 * every battery slot; NeoForge's item energy capability is the 1.21.1 edge for that.
 *
 * <p>Transfers move whole IF only: the FE amount is rounded down to a multiple of {@code fePerIf} before
 * the real transfer, so no fraction of an IF is created or lost at the boundary.
 */
public final class ExternalItemEnergy {
    private ExternalItemEnergy() {}

    /** The item's FE storage, or null for AcademyCraft's own items and items without one. */
    public static @Nullable IEnergyStorage of(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() instanceof IEnergyItem) return null;
        return stack.getCapability(Capabilities.EnergyStorage.ITEM);
    }

    public static boolean supports(ItemStack stack) {
        return of(stack) != null;
    }

    public static int extract(ItemStack stack, int maxIf, boolean simulate) {
        return extract(of(stack), maxIf, simulate, ExternalEnergyConversion.fePerIf());
    }

    public static int receive(ItemStack stack, int maxIf, boolean simulate) {
        return receive(of(stack), maxIf, simulate, ExternalEnergyConversion.fePerIf());
    }

    public static boolean full(ItemStack stack) {
        IEnergyStorage storage = of(stack);
        return storage != null && storage.getEnergyStored() >= storage.getMaxEnergyStored();
    }

    /** Pulls up to {@code maxIf} whole IF out of {@code storage}; returns the IF moved. */
    static int extract(@Nullable IEnergyStorage storage, int maxIf, boolean simulate, int fePerIf) {
        if (storage == null || maxIf <= 0 || !storage.canExtract()) return 0;
        int want = feFor(maxIf, fePerIf);
        int whole = wholeFe(storage.extractEnergy(want, true), want, fePerIf);
        if (whole <= 0 || simulate) return whole / fePerIf;
        return Math.clamp(storage.extractEnergy(whole, false), 0, whole) / fePerIf;
    }

    /** Pushes up to {@code maxIf} whole IF into {@code storage}; returns the IF moved. */
    static int receive(@Nullable IEnergyStorage storage, int maxIf, boolean simulate, int fePerIf) {
        if (storage == null || maxIf <= 0 || !storage.canReceive()) return 0;
        int offer = feFor(maxIf, fePerIf);
        int whole = wholeFe(storage.receiveEnergy(offer, true), offer, fePerIf);
        if (whole <= 0 || simulate) return whole / fePerIf;
        return Math.clamp(storage.receiveEnergy(whole, false), 0, whole) / fePerIf;
    }

    private static int feFor(int imaginaryEnergy, int fePerIf) {
        // Cap at a whole number of IF so the simulated amount never ends in a fraction.
        long fe = (long) imaginaryEnergy * fePerIf;
        return (int) Math.min(fe, Integer.MAX_VALUE / fePerIf * (long) fePerIf);
    }

    private static int wholeFe(int simulatedFe, int requestedFe, int fePerIf) {
        int fe = Math.clamp(simulatedFe, 0, requestedFe);
        return fe - fe % fePerIf;
    }
}
