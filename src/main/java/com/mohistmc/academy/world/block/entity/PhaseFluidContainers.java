package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.world.AcademyFluids;
import com.mohistmc.academy.world.AcademyItems;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Shared container transaction for the two phase-liquid machines. */
public final class PhaseFluidContainers {
    private PhaseFluidContainers() {}

    public static boolean isInput(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get())
                || FluidUtil.getFluidContained(stack)
                .filter(fluid -> fluid.getFluid() == AcademyFluids.PHASE_LIQUID.get()).isPresent());
    }

    static boolean emptyOne(List<ItemStack> items, int inputSlot, int outputSlot, FluidTank tank) {
        ItemStack input = items.get(inputSlot);
        if (input.isEmpty()) return false;
        ItemStack returned;
        if (input.is(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get())) {
            returned = new ItemStack(AcademyItems.MATTER_UNIT_NONE.get());
            FluidStack unit = new FluidStack(AcademyFluids.PHASE_LIQUID.get(), 1000);
            if (!canReturn(items.get(outputSlot), returned) || tank.fill(unit, FluidAction.SIMULATE) != 1000)
                return false;
            tank.fill(unit, FluidAction.EXECUTE);
        } else {
            // FluidUtil operates on a one-item copy and returns the capability's
            // replacement stack, including any fluid left in a partial container.
            var preview = FluidUtil.tryEmptyContainer(input, tank, Integer.MAX_VALUE, null, false);
            if (!preview.isSuccess() || !canReturn(items.get(outputSlot), preview.getResult())) return false;
            var transfer = FluidUtil.tryEmptyContainer(input, tank, Integer.MAX_VALUE, null, true);
            if (!transfer.isSuccess()) return false;
            returned = transfer.getResult();
        }
        input.shrink(1);
        if (input.isEmpty()) items.set(inputSlot, ItemStack.EMPTY);
        if (!returned.isEmpty()) {
            ItemStack output = items.get(outputSlot);
            if (output.isEmpty()) items.set(outputSlot, returned);
            else output.grow(returned.getCount());
        }
        return true;
    }

    private static boolean canReturn(ItemStack output, ItemStack returned) {
        if (returned.isEmpty()) return true;
        int limit = Math.min(64, returned.getMaxStackSize());
        return returned.getCount() <= limit && (output.isEmpty()
                || ItemStack.isSameItemSameComponents(output, returned)
                && output.getCount() <= limit - returned.getCount());
    }
}
