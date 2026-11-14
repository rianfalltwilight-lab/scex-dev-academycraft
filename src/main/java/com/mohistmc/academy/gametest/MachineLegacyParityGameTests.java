package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyFluids;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.entity.ImagFusorBlockEntity;
import com.mohistmc.academy.world.item.DeveloperPortable;
import com.mohistmc.academy.world.item.EnergyUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Machine and energy-item behaviour restored from AcademyCraft 1.12.2. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MachineLegacyParityGameTests {
    private static final String EMPTY = "empty";
    private MachineLegacyParityGameTests() {}

    @GameTest(template = EMPTY)
    public static void energyItemsKeepLegacyPerCallBandwidth(GameTestHelper helper) {
        checkBandwidth(helper, AcademyItems.ENERGY_UNIT.get(), EnergyUnit.MAX_ENERGY, EnergyUnit.BANDWIDTH);
        checkBandwidth(helper, AcademyItems.DEVELOPER_PORTABLE.get(), DeveloperPortable.MAX_ENERGY,
                DeveloperPortable.BANDWIDTH);
        helper.assertTrue(EnergyUnit.BANDWIDTH == 20 && DeveloperPortable.BANDWIDTH == 50,
                "legacy ItemEnergyBase(10000, 20) / DeveloperType.PORTABLE bandwidth 50 changed");
        helper.succeed();
    }

    private static void checkBandwidth(GameTestHelper helper, Item item, int capacity, int bandwidth) {
        ItemStack stack = new ItemStack(item);
        EnergyItemHelper.setEnergy(stack, 0);
        helper.assertTrue(EnergyItemHelper.receiveEnergy(stack, 1000, true) == bandwidth
                        && EnergyItemHelper.getEnergy(stack) == 0, item + " simulated charge ignored bandwidth");
        helper.assertTrue(EnergyItemHelper.receiveEnergy(stack, 1000, false) == bandwidth
                        && EnergyItemHelper.getEnergy(stack) == bandwidth, item + " charge ignored bandwidth");
        // Fixture/creative fills bypass bandwidth like legacy charge(..., ignoreBandwidth=true).
        EnergyItemHelper.setEnergy(stack, capacity);
        helper.assertTrue(EnergyItemHelper.getEnergy(stack) == capacity, item + " direct fill was clamped");
        helper.assertTrue(EnergyItemHelper.extractEnergy(stack, 1000, false) == bandwidth
                        && EnergyItemHelper.getEnergy(stack) == capacity - bandwidth, item + " pull ignored bandwidth");
        var fe = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        helper.assertTrue(fe != null, item + " lost its FE capability");
        helper.assertTrue(fe.extractEnergy(1_000_000, false) == bandwidth * 4
                        && EnergyItemHelper.getEnergy(stack) == capacity - 2 * bandwidth,
                item + " FE extraction bypassed the IF bandwidth");
        helper.assertTrue(fe.receiveEnergy(1_000_000, false) == bandwidth * 4
                        && EnergyItemHelper.getEnergy(stack) == capacity - bandwidth,
                item + " FE receive bypassed the IF bandwidth");
    }

    @GameTest(template = EMPTY)
    public static void imagFusorPausesWhileOutputIsFull(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.getLevel().setBlock(pos, AcademyBlocks.IMAG_FUSOR.get().defaultBlockState(), 3);
        if (!(helper.getLevel().getBlockEntity(pos) instanceof ImagFusorBlockEntity fusor)) {
            helper.fail("imag fusor block entity missing"); return;
        }
        fusor.getItems().set(ImagFusorBlockEntity.INPUT_SLOT, new ItemStack(AcademyItems.CRYSTAL_LOW.get()));
        fusor.getFluidTank().fill(new FluidStack(AcademyFluids.PHASE_LIQUID.get(), 3000), FluidAction.EXECUTE);
        fusor.injectEnergy(2000);
        for (int i = 0; i < 10; i++) fusor.tick();
        helper.assertTrue(fusor.getProcessingTime() == 10, "fusor did not start crystal_low -> crystal_normal");

        fusor.getItems().set(ImagFusorBlockEntity.OUTPUT_SLOT, new ItemStack(AcademyItems.CRYSTAL_NORMAL.get(),
                new ItemStack(AcademyItems.CRYSTAL_NORMAL.get()).getMaxStackSize()));
        double energy = fusor.getEnergy();
        for (int i = 0; i < 5; i++) fusor.tick();
        helper.assertTrue(fusor.getProcessingTime() == 10, "full output reset progress instead of pausing");
        helper.assertTrue(fusor.getEnergy() == energy, "paused fusor drew energy");

        fusor.getItems().set(ImagFusorBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
        fusor.tick();
        helper.assertTrue(fusor.getProcessingTime() == 11, "fusor did not resume paused progress");

        fusor.getItems().set(ImagFusorBlockEntity.OUTPUT_SLOT, new ItemStack(Items.STONE));
        fusor.tick();
        helper.assertTrue(fusor.getProcessingTime() == 0, "foreign output item must abort like legacy updateWork");

        fusor.getItems().set(ImagFusorBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
        for (int i = 0; i < 3; i++) fusor.tick();
        fusor.getItems().set(ImagFusorBlockEntity.INPUT_SLOT, new ItemStack(Items.STONE));
        fusor.tick();
        helper.assertTrue(fusor.getProcessingTime() == 0, "changed input kept old progress");
        helper.succeed();
    }
}
