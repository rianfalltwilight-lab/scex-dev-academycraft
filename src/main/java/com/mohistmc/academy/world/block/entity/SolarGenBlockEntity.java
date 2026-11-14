package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.capability.IFEnergyStorage;
import com.mohistmc.academy.config.MachineConfig;
import com.mohistmc.academy.energy.api.block.IWirelessGenerator;
import com.mohistmc.academy.world.AcademyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 太阳能发电机 —— 实现 IWirelessGenerator 接口以向 IF 能源网络供电。
 * @author Mgazul
 */
public class SolarGenBlockEntity extends AcademyContainerBlockEntity
        implements IFEnergyStorage, IWirelessGenerator {

    // Legacy 1.0.7 values; live numbers come from machines.solarGen in the server config.
    public static final float STRONG_RATE = 3.0f;
    public static final float WEAK_RATE = 0.6f;
    public static final int MAX_STORAGE = 1000;

    private static int maxStorage() { return MachineConfig.SOLAR_BUFFER.get(); }

    private float storedEnergy = 0.0f;
    private final IItemHandler inputItems = MachineAutomationItems.chargingSlot(this, Direction.UP, () -> true);
    private final IItemHandler outputItems = MachineAutomationItems.chargingSlot(this, Direction.DOWN, () -> true);
    private final IItemHandler internalItems = MachineAutomationItems.chargingSlot(this, null, () -> true);

    public IItemHandler getHandlerForSide(Direction side) {
        return side == null ? internalItems : side == Direction.DOWN ? outputItems : inputItems;
    }

    public SolarGenBlockEntity(BlockPos pos, BlockState state) {
        super(AcademyBlockEntities.SOLAR_GEN.get(), pos, state);
        setItems(NonNullList.withSize(getContainerSize(), ItemStack.EMPTY));
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) return;
        float rate = switch (getStatus()) {
            case STRONG -> (float) MachineConfig.SOLAR_DAY_RATE.get();
            case WEAK -> (float) MachineConfig.SOLAR_RAIN_RATE.get();
            case STOPPED -> 0.0f;
        };

        float oldEnergy = storedEnergy;
        int maxStorage = maxStorage();
        if (storedEnergy > maxStorage) storedEnergy = maxStorage;
        if (rate > 0.0f) {
            storedEnergy = Math.min(maxStorage, storedEnergy + rate);
        }

        int chargeAmount = Math.min((int) storedEnergy, (int) getBandwidth());
        int charged = chargeAmount > 0 ? chargeEnergyUnit(chargeAmount) : 0;
        storedEnergy -= charged;
        if (oldEnergy != storedEnergy || charged > 0) markMachineChanged();
    }

    private int chargeEnergyUnit(int amount) {
        ItemStack stack = getItems().getFirst();
        if (stack.isEmpty() || !EnergyItemHelper.isEnergyItem(stack)) return 0;
        return EnergyItemHelper.receiveEnergy(stack, amount, false);
    }

    // ==================== IFEnergyStorage ====================

    @Override
    public int getEnergyStored() { return (int) storedEnergy; }

    @Override
    public int getMaxEnergyStored() { return maxStorage(); }

    @Override
    public void setEnergy(int energy) {
        float bounded = Math.clamp(energy, 0, maxStorage());
        if (storedEnergy == bounded) return;
        this.storedEnergy = bounded;
        markMachineChanged();
    }

    @Override public int receiveEnergy(int amount, boolean simulate) {
        int accepted = Math.min(Math.max(0, amount), Math.max(0, (int) (maxStorage() - storedEnergy)));
        if (!simulate && accepted > 0) { storedEnergy += accepted; markMachineChanged(); }
        return accepted;
    }

    @Override public int extractEnergy(int amount, boolean simulate) {
        int extracted = Math.min(Math.max(0, amount), (int) storedEnergy);
        if (!simulate && extracted > 0) { storedEnergy -= extracted; markMachineChanged(); }
        return extracted;
    }

    public float getStoredEnergy() { return storedEnergy; }

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
        return MachineConfig.SOLAR_BANDWIDTH.get(); // 1.0.7 IFConstants.LATENCY_MK2 = 100
    }

    // ==================== NBT ====================

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("storedEnergy")) {
            this.storedEnergy = MachineStateSanitizer.clampFinite(tag.getFloat("storedEnergy"), maxStorage());
        }
        // The fractional value is authoritative; accept the old integer key
        // only when loading a save without it.
        else if (tag.contains("if_energy")) storedEnergy = Math.clamp(tag.getInt("if_energy"), 0, maxStorage());
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putFloat("storedEnergy", storedEnergy);
        serializeEnergy(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        tag.putFloat("storedEnergy", storedEnergy);
        return tag;
    }

    // ==================== Solar State ====================

    public enum SolarStatus { STRONG, STOPPED, WEAK }

    public SolarStatus getStatus() {
        if (level == null) return SolarStatus.STOPPED;
        long time = level.getDayTime() % 24000;
        if (time < 0 || time > 12500 || !level.canSeeSky(worldPosition.above())) return SolarStatus.STOPPED;
        if (level.isRaining() || level.isThundering()) return SolarStatus.WEAK;
        return SolarStatus.STRONG;
    }
}
