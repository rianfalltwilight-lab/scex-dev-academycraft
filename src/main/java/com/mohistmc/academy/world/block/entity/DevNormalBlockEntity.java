package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.capability.IFEnergyStorage;
import com.mohistmc.academy.energy.api.block.IWirelessReceiver;
import com.mohistmc.academy.world.AcademyBlockEntities;
import com.mohistmc.academy.world.block.IDevStructure;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;

public class DevNormalBlockEntity extends AcademyContainerBlockEntity implements IFEnergyStorage, IDevStructure, IWirelessReceiver {
    public static final int MAX_ENERGY = 50_000;
    public static int maxEnergy() { return com.mohistmc.academy.config.MachineConfig.scale(MAX_ENERGY, com.mohistmc.academy.config.MachineConfig.Scale.STORAGE); }
    private static final int MAX_BANDWIDTH = 100;
    public static int maxBandwidth() { return com.mohistmc.academy.config.MachineConfig.scale(MAX_BANDWIDTH, com.mohistmc.academy.config.MachineConfig.Scale.BANDWIDTH); }
    private int energy = 0;
    private UUID structureId;

    public DevNormalBlockEntity(BlockPos pos, BlockState state) {
        super(AcademyBlockEntities.DEV_NORMAL.get(), pos, state);
    }

    @Override public int getContainerSize() { return 0; }

    @Override
    public UUID getStructureId() {
        return structureId;
    }

    @Override
    public void setStructureId(UUID structureId) {
        this.structureId = structureId;
        setChanged();
    }

    @Override
    public int getEnergyStored() {
        return energy;
    }

    @Override
    public int getMaxEnergyStored() {
        return maxEnergy();
    }

    @Override
    public void setEnergy(int energy) {
        int bounded = Math.clamp(energy, 0, maxEnergy());
        if (this.energy == bounded) return;
        this.energy = bounded;
        markMachineChanged(); // Open menus already use incremental ContainerData.
    }

    @Override public double getRequiredEnergy() {
        return energy < maxEnergy() ? Math.min(maxBandwidth(), maxEnergy() - energy) : 0;
    }

    @Override public double injectEnergy(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return amount;
        int accepted = (int) Math.floor(Math.min(amount, maxEnergy() - energy));
        if (accepted > 0) setEnergy(energy + accepted);
        return amount - accepted;
    }

    @Override public double pullEnergy(double amount) { return 0; }

    @Override public double getBandwidth() { return maxBandwidth(); }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("energy")) {
            this.energy = Math.clamp(tag.getInt("energy"), 0, maxEnergy());
        }
        if (tag.contains("structureId")) {
            this.structureId = com.mohistmc.academy.world.block.IDevStructure
                    .parseStructureId(tag.getString("structureId"));
        }
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider); tag.putInt("energy", energy); return tag;
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("energy", energy);
        if (structureId != null) {
            tag.putString("structureId", structureId.toString());
        }
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }
}
