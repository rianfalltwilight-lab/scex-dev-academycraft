package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.config.MachineConfig;
import com.mohistmc.academy.energy.api.block.IWirelessGenerator;
import com.mohistmc.academy.world.AcademyBlockEntities;
import com.mohistmc.academy.world.AcademyFluids;
import com.mohistmc.academy.capability.EnergyItemHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 虚相位能量发生机 —— 消耗 PhaseLiquid 单元产生 IF 能量,实现 IWirelessGenerator 向无线能源网络供电。
 * @author Mgazul
 */
public class PhaseGenBlockEntity extends AcademyContainerBlockEntity implements IWirelessGenerator {

    public static final int TANK_SIZE = 8000;
    public static final int PER_UNIT = 1000;
    // Legacy 1.0.7 values; live numbers come from machines.phaseGen in the server config.
    public static final int CONSUME_PER_TICK = 100;
    public static final double GEN_PER_MB = 0.5;
    public static final double MAX_BANDWIDTH = 50; // 100 mB * .5 IF/mB
    public static final int MAX_STORAGE = 6000;

    private static int maxStorage() { return MachineConfig.PHASE_BUFFER.get(); }

    private float storedEnergy = 0;
    private final IItemHandler topItems = itemView(new int[]{0}, true, false);
    private final IItemHandler sideItems = itemView(new int[]{2}, true, false);
    private final IItemHandler bottomItems = itemView(new int[]{1, 2}, false, true);
    private final IItemHandler internalItems = itemView(new int[]{0, 1, 2}, true, true);
    private final FluidTank tank = new FluidTank(TANK_SIZE,
            fs -> fs.getFluid() == AcademyFluids.PHASE_LIQUID.get()) {
        @Override protected void onContentsChanged() { markMachineChanged(); }
    };

    public PhaseGenBlockEntity(BlockPos pos, BlockState state) {
        super(AcademyBlockEntities.PHASE_GEN.get(), pos, state);
        setItems(NonNullList.withSize(getContainerSize(), ItemStack.EMPTY));
    }

    @Override
    public int getContainerSize() {
        return 3; // liquid input, empty container output, energy item
    }

    // ==================== Tick Logic ====================

    public void tick() {
        if (level == null || level.isClientSide) return;

        boolean changed = false;

        // TileGeneratorBase in 1.0.7 fills its 6000 IF buffer every server
        // tick, before the machine handles containers and charges its output
        // item.  Draining the tank only when NodeConn asked for energy left an
        // unbound generator permanently at 0 IF and made its battery slot a
        // no-op.
        int maxStorage = maxStorage();
        if (storedEnergy > maxStorage) {
            storedEnergy = maxStorage;
            changed = true;
        }
        double perMb = MachineConfig.PHASE_ENERGY_PER_MB.get();
        double required = Math.max(0.0, maxStorage - storedEnergy);
        int maxDrain = perMb <= 0 ? 0
                : (int) Math.min(MachineConfig.PHASE_MAX_DRAIN.get(), Math.floor(required / perMb));
        if (maxDrain > 0) {
            FluidStack drained = tank.drain(maxDrain,
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty()) {
                storedEnergy = Math.min(maxStorage,
                        storedEnergy + (float) (drained.getAmount() * perMb));
                changed = true;
            }
        }

        if (PhaseFluidContainers.emptyOne(getItems(), 0, 1, tank)) changed = true;
        ItemStack energyItem = getItems().get(2);
        if (EnergyItemHelper.isEnergyItem(energyItem) && storedEnergy >= 1) {
            int charged = EnergyItemHelper.receiveEnergy(energyItem, Math.min((int) storedEnergy, (int) getBandwidth()), false);
            storedEnergy -= charged;
            if (charged > 0) changed = true;
        }
        if (changed) markMachineChanged();
    }

    // ==================== IWirelessGenerator ====================

    @Override
    public double getProvidedEnergy(double req) {
        if (!Double.isFinite(req) || req <= 0) return 0;
        // Wireless transport pulls from the already generated buffer.  The
        // NodeConn also applies this generator's bandwidth, but keep the bound
        // here for safe direct callers.
        double give = Math.min(Math.min(req, getBandwidth()), storedEnergy);
        storedEnergy -= (float) give;
        if (give > 0) markMachineChanged();
        return give;
    }

    @Override
    public double getBandwidth() {
        return MachineConfig.PHASE_BANDWIDTH.get();
    }

    // ==================== Accessors ====================

    public float getStoredEnergy() { return storedEnergy; }
    public int getMaxEnergy() { return maxStorage(); }
    public int getFluidAmount() { return tank.getFluidAmount(); }
    public int getTankSize() { return TANK_SIZE; }
    /** Compatibility for older menu/tests; the bar now represents tank fill. */
    public int getProgress() { return tank.getFluidAmount(); }
    public int getProcessTicks() { return TANK_SIZE; }
    public FluidTank getFluidTank() { return tank; }

    public IItemHandler getHandlerForSide(Direction side) {
        if (side == null) return internalItems;
        return side == Direction.UP ? topItems : side == Direction.DOWN ? bottomItems : sideItems;
    }

    private IItemHandler itemView(int[] slots, boolean insert, boolean extract) {
        return new MachineAutomationItems(this, slots,
                (slot, stack) -> insert && (slot == 0 ? PhaseFluidContainers.isInput(stack)
                        : slot == 2 && EnergyItemHelper.isEnergyItem(stack)),
                (slot, stack) -> extract && (slot == 1 || slot == 2 && MachineAutomationItems.fullyCharged(stack)),
                () -> true);
    }

    /** 是否正在工作中 */
    public boolean isWorking() {
        return tank.getFluidAmount() > 0 && storedEnergy < maxStorage();
    }

    // ==================== NBT ====================

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        storedEnergy = MachineStateSanitizer.clampFinite(tag.getFloat("storedEnergy"), maxStorage());
        if (tag.contains("tank")) {
            tank.readFromNBT(provider, tag.getCompound("tank"));
            FluidStack loaded = tank.getFluid();
            if (!loaded.isEmpty() && !tank.isFluidValid(loaded)) tank.setFluid(FluidStack.EMPTY);
            else if (loaded.getAmount() > TANK_SIZE) tank.setFluid(loaded.copyWithAmount(TANK_SIZE));
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putFloat("storedEnergy", storedEnergy);
        tag.put("tank", tank.writeToNBT(provider, new CompoundTag()));
    }
}
