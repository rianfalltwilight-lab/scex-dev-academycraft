package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.capability.ExternalEnergyConversion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Exact fixed-point bridge buffer. One stored integer is one FE (one quarter
 * IF), so repeated RF/IF round trips cannot create or discard fractional IF.
 */
public abstract class EnergyBridgeBlockEntity extends AcademyContainerBlockEntity {
    // Legacy 1.0.7 values; live numbers come from machines.energyBridge and machines.fePerIf.
    public static final int MAX_IF = 2000;
    public static final int MAX_FE = MAX_IF * ExternalEnergyConversion.FE_PER_IF;
    public static final double BANDWIDTH_IF = 100.0;

    /** Configured FE buffer: {@code bufferIf * fePerIf}, saturating at Integer.MAX_VALUE. */
    public static int maxFe() {
        long fe = (long) com.mohistmc.academy.config.MachineConfig.BRIDGE_BUFFER_IF.get()
                * ExternalEnergyConversion.fePerIf();
        return (int) Math.min(Integer.MAX_VALUE, fe);
    }

    public static double bandwidthIf() {
        return com.mohistmc.academy.config.MachineConfig.BRIDGE_BANDWIDTH.get();
    }

    private int storedFe;

    protected EnergyBridgeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override public int getContainerSize() { return 0; }

    public final int getStoredFe() { return storedFe; }
    public final double getStoredIf() {
        return storedFe / (double) ExternalEnergyConversion.fePerIf();
    }

    public final int receiveExternalFe(int amount, boolean simulate) {
        int accepted = Math.min(Math.max(0, amount), Math.max(0, maxFe() - storedFe));
        if (!simulate && accepted > 0) setStoredFe(storedFe + accepted);
        return accepted;
    }

    public final int extractExternalFe(int amount, boolean simulate) {
        int extracted = Math.min(Math.max(0, amount), storedFe);
        if (!simulate && extracted > 0) setStoredFe(storedFe - extracted);
        return extracted;
    }

    protected final void setStoredFe(int amount) {
        int bounded = Math.clamp(amount, 0, maxFe());
        if (bounded == storedFe) return;
        storedFe = bounded;
        markMachineChanged();
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        storedFe = Math.clamp(tag.getInt("bridgeStoredFe"), 0, maxFe());
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("bridgeStoredFe", storedFe);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        tag.putInt("bridgeStoredFe", storedFe);
        return tag;
    }
}
