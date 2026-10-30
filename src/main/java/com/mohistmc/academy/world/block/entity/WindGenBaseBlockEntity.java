package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.capability.IFEnergyStorage;
import com.mohistmc.academy.energy.api.block.IWirelessGenerator;
import com.mohistmc.academy.world.AcademyBlockEntities;
import com.mohistmc.academy.world.block.WindGenBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.function.BooleanSupplier;

public class WindGenBaseBlockEntity extends AcademyContainerBlockEntity
        implements IFEnergyStorage, IWirelessGenerator {
    private boolean structureComplete;
    private boolean middleComplete;
    private boolean working;
    private double generationRate;
    private static final double MIN_GENERATION_RATE = 7.5;
    private static final double MAX_GENERATION_RATE = 15.0;
    private static final int MAX_STORAGE = 20000;
    private float storedEnergy = 0.0f;
    private final IItemHandler inputItems = MachineAutomationItems.chargingSlot(this, Direction.UP, () -> true);
    private final IItemHandler outputItems = MachineAutomationItems.chargingSlot(this, Direction.DOWN, () -> true);
    private final IItemHandler internalItems = MachineAutomationItems.chargingSlot(this, null, () -> true);

    public IItemHandler getHandlerForSide(Direction side) {
        return side == null ? internalItems : side == Direction.DOWN ? outputItems : inputItems;
    }

    public IItemHandler getProxyHandler(Direction side, BooleanSupplier validProxy) {
        return MachineAutomationItems.chargingSlot(this, side, validProxy);
    }

    public WindGenBaseBlockEntity(BlockPos p_155229_, BlockState p_155230_) {
        super(AcademyBlockEntities.WINDGEN_BASE.get(), p_155229_, p_155230_);
        setItems(net.minecraft.core.NonNullList.withSize(getContainerSize(), ItemStack.EMPTY));
    }

    public void tick(boolean structureComplete, boolean middleComplete, boolean working, int mainY) {
        // The base ticker is registered on both logical sides.  Generation,
        // inventory charging and block-state mutation are server-authoritative;
        // letting the client run this method caused duplicate local energy,
        // desynchronised fan animation and (with some loaders) a client-side
        // block update loop.  Keep the cached structure flags for rendering,
        // but never mutate energy or send updates from the client.
        if (level == null || level.isClientSide()) {
            this.structureComplete = structureComplete;
            this.middleComplete = middleComplete;
            this.working = working;
            this.generationRate = working ? generationRate(mainY) : 0;
            return;
        }
        boolean stateChanged = this.structureComplete != structureComplete
                || this.middleComplete != middleComplete || this.working != working;
        this.structureComplete = structureComplete;
        this.middleComplete = middleComplete;
        this.working = working;
        this.generationRate = working ? generationRate(mainY) : 0;

        // Keep the authoritative runtime state in the block state as well as
        // the BE cache.  Without this, the base always rendered the disabled
        // model even while it was generating power.
        if (level != null && !level.isClientSide()
                && getBlockState().hasProperty(WindGenBase.ENABLE)
                && getBlockState().getValue(WindGenBase.ENABLE) != structureComplete) {
            level.setBlock(worldPosition, getBlockState().setValue(WindGenBase.ENABLE, structureComplete), Block.UPDATE_CLIENTS);
        }

        float oldEnergy = storedEnergy;

        if (working) {
            if (Double.isFinite(generationRate)) {
                storedEnergy = Math.min(MAX_STORAGE, storedEnergy + (float) generationRate);
            }
        }

        // 无论结构是否有效，都尝试用存储池中的整数能量给能源单元充能
        int chargeAmount = Math.min((int) storedEnergy, (int) getBandwidth());
        int charged = chargeAmount > 0 ? chargeEnergyUnit(chargeAmount) : 0;
        storedEnergy -= charged;
        // A battery can change while generation keeps the buffer unchanged.
        if (oldEnergy != storedEnergy || charged > 0 || stateChanged) markMachineChanged();
        // Energy is synchronized by the open menu's ContainerData. Only the
        // world-visible turbine status needs a block-entity broadcast.
        if (stateChanged) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static double generationRate(int mainY) {
        double t = Math.clamp((mainY - 70.0) / 90.0, 0.0, 1.0);
        return MIN_GENERATION_RATE + (MAX_GENERATION_RATE - MIN_GENERATION_RATE) * t;
    }

    /**
     * 给槽位中的能源单元充能
     * @param amount 充能数量
     * @return 实际充能数量
     */
    private int chargeEnergyUnit(int amount) {
        ItemStack stack = getItems().getFirst();
        if (stack.isEmpty() || !EnergyItemHelper.isEnergyItem(stack)) return 0;
        return EnergyItemHelper.receiveEnergy(stack, amount, false);
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    public boolean isValidMiddle() {
        return middleComplete;
    }

    public boolean isValidMain() {
        return structureComplete;
    }

    public boolean isWorking() { return working; }
    public double getGenerationRate() { return generationRate; }

    // ==================== IWirelessGenerator ====================

    @Override
    public double getProvidedEnergy(double req) {
        if (!Double.isFinite(req) || req <= 0) return 0;
        double give = Math.min(Math.max(0, req), storedEnergy);
        storedEnergy -= (float) give;
        if (give > 0) markMachineChanged();
        return give;
    }

    @Override
    public double getBandwidth() {
        return 300; // 1.0.7 IFConstants.LATENCY_MK3
    }

    // ==================== IFEnergyStorage ====================

    @Override
    public int getEnergyStored() {
        return (int) storedEnergy;
    }

    @Override
    public int getMaxEnergyStored() {
        return MAX_STORAGE;
    }

    @Override
    public void setEnergy(int energy) {
        float bounded = Math.clamp(energy, 0, MAX_STORAGE);
        if (storedEnergy == bounded) return;
        storedEnergy = bounded;
        markMachineChanged();
    }

    @Override public int receiveEnergy(int amount, boolean simulate) {
        int accepted = Math.min(Math.max(0, amount), (int) (MAX_STORAGE - storedEnergy));
        if (!simulate && accepted > 0) { storedEnergy += accepted; markMachineChanged(); }
        return accepted;
    }

    @Override public int extractEnergy(int amount, boolean simulate) {
        int extracted = Math.min(Math.max(0, amount), (int) storedEnergy);
        if (!simulate && extracted > 0) { storedEnergy -= extracted; markMachineChanged(); }
        return extracted;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        tag.putFloat("storedEnergy", storedEnergy);
        tag.putBoolean("structureComplete", structureComplete);
        tag.putBoolean("middleComplete", middleComplete);
        tag.putBoolean("working", working);
        tag.putDouble("generationRate", generationRate);
        return tag;
    }

    @Override
    public void loadAdditional(CompoundTag p_331149_, HolderLookup.Provider p_333170_) {
        super.loadAdditional(p_331149_, p_333170_);
        if (p_331149_.contains("storedEnergy")) {
            this.storedEnergy = MachineStateSanitizer.clampFinite(p_331149_.getFloat("storedEnergy"), MAX_STORAGE);
        }
        structureComplete = p_331149_.getBoolean("structureComplete");
        middleComplete = p_331149_.getBoolean("middleComplete");
        working = p_331149_.getBoolean("working");
        double loadedRate = p_331149_.getDouble("generationRate");
        generationRate = Double.isFinite(loadedRate)
                ? Math.clamp(loadedRate, 0.0, MAX_GENERATION_RATE) : 0.0;
    }

    @Override
    public void saveAdditional(CompoundTag p_187471_, HolderLookup.Provider p_327783_) {
        super.saveAdditional(p_187471_, p_327783_);
        p_187471_.putFloat("storedEnergy", storedEnergy);
        p_187471_.putBoolean("structureComplete", structureComplete);
        p_187471_.putBoolean("middleComplete", middleComplete);
        p_187471_.putBoolean("working", working);
        p_187471_.putDouble("generationRate", generationRate);
    }
}
