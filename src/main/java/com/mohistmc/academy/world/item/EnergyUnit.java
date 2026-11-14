package com.mohistmc.academy.world.item;

import com.mohistmc.academy.capability.IEnergyItem;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class EnergyUnit extends AcademyItem implements IEnergyItem {

    public static final int MAX_ENERGY = 10000;
    /** Legacy 1.12.2 per-call IF transfer limit: ItemEnergyBase(10000, 20). */
    public static final int BANDWIDTH = 20;

    public EnergyUnit() {
        super(new Properties().stacksTo(1).setNoRepair());
    }

    @Override
    public int getEnergyStored(ItemStack stack) {
        return ExtraItemData.energy(stack, MAX_ENERGY);
    }

    @Override
    public int getMaxEnergyStored(ItemStack stack) {
        return MAX_ENERGY;
    }

    @Override
    public void setEnergy(ItemStack stack, int energy) {
        ExtraItemData.setEnergy(stack, energy, MAX_ENERGY);
    }

    @Override
    public int extractEnergy(ItemStack stack, int maxExtract, boolean simulate) {
        int energy = getEnergyStored(stack);
        int extracted = Math.min(energy, Math.min(Math.max(0, maxExtract), BANDWIDTH));
        if (!simulate && extracted > 0) {
            setEnergy(stack, energy - extracted);
        }
        return extracted;
    }

    @Override
    public int receiveEnergy(ItemStack stack, int maxReceive, boolean simulate) {
        int energy = getEnergyStored(stack);
        int maxEnergy = getMaxEnergyStored(stack);
        int received = Math.min(maxEnergy - energy, Math.min(Math.max(0, maxReceive), BANDWIDTH));
        if (!simulate && received > 0) {
            setEnergy(stack, energy + received);
        }
        return received;
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, Item.TooltipContext p_333372_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_333372_, p_41423_, p_41424_);
        int energy = getEnergyStored(p_41421_);
        p_41423_.add(Component.literal(energy + "/" + MAX_ENERGY + " IF"));
    }
}
