package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.capability.IEnergyItem;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

/** A sided view of the machine's existing inventory, never a second store. */
final class MachineAutomationItems implements IItemHandler {
    private final AcademyContainerBlockEntity owner;
    private final int[] slots;
    private final BiPredicate<Integer, ItemStack> accepts;
    private final BiPredicate<Integer, ItemStack> extracts;
    private final BooleanSupplier valid;

    MachineAutomationItems(AcademyContainerBlockEntity owner, int[] slots,
            BiPredicate<Integer, ItemStack> accepts, BiPredicate<Integer, ItemStack> extracts,
            BooleanSupplier valid) {
        this.owner = owner;
        this.slots = slots;
        this.accepts = accepts;
        this.extracts = extracts;
        this.valid = valid;
    }

    static IItemHandler chargingSlot(AcademyContainerBlockEntity owner, Direction side, BooleanSupplier valid) {
        return new MachineAutomationItems(owner, new int[]{0},
                (slot, stack) -> side != Direction.DOWN && EnergyItemHelper.isEnergyItem(stack),
                (slot, stack) -> (side == null || side == Direction.DOWN) && fullyCharged(stack), valid);
    }

    static boolean fullyCharged(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IEnergyItem energy
                && energy.getEnergyStored(stack) >= energy.getMaxEnergyStored(stack);
    }

    private boolean available(int slot) {
        return slot >= 0 && slot < slots.length && !owner.isRemoved() && valid.getAsBoolean();
    }

    @Override public int getSlots() { return slots.length; }
    @Override public int getSlotLimit(int slot) { return 64; }

    @Override public @NotNull ItemStack getStackInSlot(int slot) {
        return available(slot) ? owner.getItems().get(slots[slot]) : ItemStack.EMPTY;
    }

    @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return available(slot) && !stack.isEmpty() && accepts.test(slots[slot], stack);
    }

    @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (!isItemValid(slot, stack)) return stack;
        ItemStack existing = owner.getItems().get(slots[slot]);
        if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, stack)) return stack;
        int moved = Math.min(stack.getCount(), Math.max(0,
                Math.min(getSlotLimit(slot), stack.getMaxStackSize()) - existing.getCount()));
        if (moved == 0) return stack;
        if (!simulate) {
            if (existing.isEmpty()) owner.getItems().set(slots[slot], stack.copyWithCount(moved));
            else existing.grow(moved);
            owner.markMachineChanged();
        }
        return stack.copyWithCount(stack.getCount() - moved);
    }

    @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!available(slot) || amount <= 0) return ItemStack.EMPTY;
        ItemStack existing = owner.getItems().get(slots[slot]);
        if (existing.isEmpty() || !extracts.test(slots[slot], existing)) return ItemStack.EMPTY;
        ItemStack extracted = existing.copyWithCount(Math.min(amount, existing.getCount()));
        if (!simulate) {
            existing.shrink(extracted.getCount());
            if (existing.isEmpty()) owner.getItems().set(slots[slot], ItemStack.EMPTY);
            owner.markMachineChanged();
        }
        return extracted;
    }
}
