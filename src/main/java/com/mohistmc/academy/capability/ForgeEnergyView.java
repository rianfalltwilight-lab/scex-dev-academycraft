package com.mohistmc.academy.capability;

import net.neoforged.neoforge.energy.IEnergyStorage;

/** NeoForge/Jade-facing view over the mod's authoritative IF storage. */
public final class ForgeEnergyView implements IEnergyStorage {
    private final IFEnergyStorage delegate;
    private final java.util.function.BooleanSupplier validity;
    public ForgeEnergyView(IFEnergyStorage delegate) { this(delegate, () -> true); }
    public ForgeEnergyView(IFEnergyStorage delegate, java.util.function.BooleanSupplier validity) {
        this.delegate = delegate;
        this.validity = validity;
    }
    private boolean valid() {
        return (!(delegate instanceof net.minecraft.world.level.block.entity.BlockEntity be) || !be.isRemoved())
                && validity.getAsBoolean();
    }
    @Override public int receiveEnergy(int amount, boolean simulate) {
        if (!valid()) return 0;
        int acceptedIf = delegate.receiveEnergy(ExternalEnergyConversion.wholeIfFromFe(amount), simulate);
        return ExternalEnergyConversion.ifToFe(acceptedIf);
    }
    @Override public int extractEnergy(int amount, boolean simulate) {
        if (!valid()) return 0;
        int extractedIf = delegate.extractEnergy(ExternalEnergyConversion.wholeIfFromFe(amount), simulate);
        return ExternalEnergyConversion.ifToFe(extractedIf);
    }
    @Override public int getEnergyStored() {
        return valid() ? ExternalEnergyConversion.ifToFe(delegate.getEnergyStored()) : 0;
    }
    @Override public int getMaxEnergyStored() {
        return valid() ? ExternalEnergyConversion.ifToFe(delegate.getMaxEnergyStored()) : 0;
    }
    @Override public boolean canExtract() { return valid(); }
    @Override public boolean canReceive() { return valid(); }
}
