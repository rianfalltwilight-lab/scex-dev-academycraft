package com.mohistmc.academy.capability;

import net.minecraft.world.item.ItemStack;

/**
 * 能量物品的工具类
 */
public final class EnergyItemHelper {

    private EnergyItemHelper() {}

    /**
     * Whether a battery slot accepts the stack: AcademyCraft's own energy items, or another mod's item
     * with Forge energy ({@link ExternalItemEnergy}), e.g. Singularity Iteration batteries.
     */
    public static boolean isEnergyItem(ItemStack stack) {
        return isNativeEnergyItem(stack) || ExternalItemEnergy.supports(stack);
    }

    /** AcademyCraft's own IF items only. */
    public static boolean isNativeEnergyItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof IEnergyItem;
    }

    public static int getEnergy(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        if (stack.getItem() instanceof IEnergyItem energyItem) {
            return energyItem.getEnergyStored(stack);
        }
        return 0;
    }

    public static void setEnergy(ItemStack stack, int energy) {
        if (stack == null || stack.isEmpty()) return;
        if (stack.getItem() instanceof IEnergyItem energyItem) {
            energyItem.setEnergy(stack, energy);
        }
    }

    public static int extractEnergy(ItemStack stack, int maxExtract, boolean simulate) {
        if (stack == null || stack.isEmpty()) return 0;
        if (stack.getItem() instanceof IEnergyItem energyItem) {
            return energyItem.extractEnergy(stack, maxExtract, simulate);
        }
        return ExternalItemEnergy.extract(stack, maxExtract, simulate);
    }

    public static int receiveEnergy(ItemStack stack, int maxReceive, boolean simulate) {
        if (stack == null || stack.isEmpty()) return 0;
        if (stack.getItem() instanceof IEnergyItem energyItem) {
            return energyItem.receiveEnergy(stack, maxReceive, simulate);
        }
        return ExternalItemEnergy.receive(stack, maxReceive, simulate);
    }
}
