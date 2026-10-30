package com.mohistmc.academy.regression;

import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.capability.IEnergyItem;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyFluids;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.entity.AcademyContainerBlockEntity;
import com.mohistmc.academy.world.block.entity.ImagFusorBlockEntity;
import com.mohistmc.academy.world.block.entity.PhaseGenBlockEntity;
import com.mohistmc.academy.world.block.entity.SolarGenBlockEntity;
import com.mohistmc.academy.world.block.entity.WindGenBaseBlockEntity;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder("academy_automation")
@PrefixGameTestTemplate(false)
public final class AutomationFluidGameTests {
    @GameTest(template = "empty", batch = "automation_inventory", timeoutTicks = 40)
    public static void generatorFacesSimulationAndChargedOnlyExtraction(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, AcademyBlocks.PHASE_GEN.get());
        var phase = (PhaseGenBlockEntity) h.getBlockEntity(pos);
        IItemHandler top = capability(h, pos, Direction.UP);
        IItemHandler side = capability(h, pos, Direction.EAST);
        IItemHandler bottom = capability(h, pos, Direction.DOWN);
        h.assertTrue(top.getSlots() == 1 && side.getSlots() == 1 && bottom.getSlots() == 2,
                "Phase generator exposes the wrong machine slots");
        ItemStack liquid = new ItemStack(AcademyItems.PHASE_BUCKET.get());
        h.assertTrue(top.insertItem(0, liquid, true).isEmpty() && phase.getItems().get(0).isEmpty()
                && liquid.getCount() == 1 && phase.getFluidAmount() == 0, "Simulated input mutated inventory or fluid");
        h.assertTrue(side.insertItem(0, liquid, false).getCount() == 1, "Side accepted a fluid container");
        h.assertTrue(top.insertItem(0, new ItemStack(Items.WATER_BUCKET), false).getCount() == 1,
                "Top accepted the wrong fluid");
        h.assertTrue(bottom.insertItem(0, new ItemStack(Items.BUCKET), false).getCount() == 1,
                "Bottom accepted input into the output slot");
        ItemStack battery = battery(-1);
        h.assertTrue(side.insertItem(0, battery, true).isEmpty() && phase.getItems().get(2).isEmpty(),
                "Simulated battery input mutated inventory");
        h.assertTrue(side.insertItem(0, battery, false).isEmpty(), "Side rejected an energy item");
        h.assertTrue(bottom.extractItem(1, 1, false).isEmpty(), "Unfinished battery escaped the charging slot");
        fillBattery(phase.getItems().get(2));
        h.assertTrue(!bottom.extractItem(1, 1, true).isEmpty() && !phase.getItems().get(2).isEmpty(),
                "Simulated extraction changed a charged battery");
        h.assertTrue(!bottom.extractItem(1, 1, false).isEmpty() && phase.getItems().get(2).isEmpty(),
                "Charged battery could not leave the bottom face");
        for (Block block : List.of(AcademyBlocks.SOLAR_GEN.get(), AcademyBlocks.WINDGEN_BASE.get())) {
            placeGenerator(h, pos, block);
            var machine = (AcademyContainerBlockEntity) h.getBlockEntity(pos);
            IItemHandler input = capability(h, pos, Direction.EAST);
            IItemHandler output = capability(h, pos, Direction.DOWN);
            ItemStack oversized = battery(0).copyWithCount(2);
            h.assertTrue(input.insertItem(0, oversized, true).getCount() == 1 && machine.getItems().getFirst().isEmpty(),
                    "Charging slot lost its one-item stack limit or simulate contract");
            input.insertItem(0, battery(-1), false);
            h.assertTrue(output.extractItem(0, 1, false).isEmpty() && input.extractItem(0, 1, false).isEmpty(),
                    "Generator exposed unfinished charging items");
            fillBattery(machine.getItems().getFirst());
            ItemStack charged = output.extractItem(0, 1, false);
            var fe = charged.getCapability(Capabilities.EnergyStorage.ITEM);
            h.assertTrue(fe != null && fe.getEnergyStored() == 4 * EnergyItemHelper.getEnergy(charged),
                    "Charged output changed 4 FE = 1 IF conversion");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "automation_inventory", timeoutTicks = 40)
    public static void bucketsAndLegacyMatterUnitsConserveBothMachineTanks(GameTestHelper h) {
        for (boolean generator : List.of(false, true)) {
            var machine = detached(h, generator);
            setInput(machine, new ItemStack(AcademyItems.PHASE_BUCKET.get()));
            tick(machine);
            h.assertTrue(tank(machine).getFluidAmount() == 1000 && input(machine).isEmpty()
                    && output(machine).is(Items.BUCKET), "Standard bucket was not drained and replaced exactly once");
            setOutput(machine, ItemStack.EMPTY);
            setInput(machine, new ItemStack(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get(), 2));
            tick(machine);
            h.assertTrue(tank(machine).getFluidAmount() == 2000 && input(machine).getCount() == 1
                    && output(machine).is(AcademyItems.MATTER_UNIT_NONE.get()) && output(machine).getCount() == 1,
                    "Legacy unit no longer transfers exactly 1000 mB per item");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "automation_inventory", timeoutTicks = 40)
    public static void blockedOutputsComponentsWrongFluidsAndInsufficientRoomDoNotConsume(GameTestHelper h) {
        for (boolean generator : List.of(false, true)) {
            var machine = detached(h, generator);
            for (Item container : List.of(AcademyItems.PHASE_BUCKET.get(), AcademyItems.MATTER_UNIT_PHASE_LIQUID.get())) {
                setInput(machine, new ItemStack(container));
                ItemStack returned = new ItemStack(container == AcademyItems.PHASE_BUCKET.get()
                        ? Items.BUCKET : AcademyItems.MATTER_UNIT_NONE.get());
                returned.set(DataComponents.CUSTOM_NAME, Component.literal("do not merge unlike components"));
                setOutput(machine, returned);
                tick(machine);
                h.assertTrue(input(machine).getCount() == 1 && tank(machine).isEmpty() && ItemStack.matches(output(machine), returned),
                        "Container transaction merged unlike components or consumed input behind blocked output");
                returned = new ItemStack(returned.getItem(), returned.getMaxStackSize());
                setOutput(machine, returned);
                tick(machine);
                h.assertTrue(input(machine).getCount() == 1 && tank(machine).isEmpty() && output(machine).getCount() == returned.getMaxStackSize(),
                        "Full output consumed fluid or input");
            }
            setOutput(machine, ItemStack.EMPTY);
            setInput(machine, new ItemStack(Items.WATER_BUCKET));
            tick(machine);
            h.assertTrue(tank(machine).isEmpty() && input(machine).is(Items.WATER_BUCKET), "Wrong fluid entered the phase tank");
            tank(machine).fill(phase(7500), FluidAction.EXECUTE);
            setInput(machine, new ItemStack(AcademyItems.PHASE_BUCKET.get()));
            tick(machine);
            h.assertTrue(tank(machine).getFluidAmount() == 7500 && input(machine).getCount() == 1 && output(machine).isEmpty(),
                    "Bucket was consumed when less than one bucket of space remained");
            tank(machine).fill(phase(500), FluidAction.EXECUTE);
            setInput(machine, new ItemStack(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get()));
            tick(machine);
            h.assertTrue(tank(machine).getFluidAmount() == 8000 && input(machine).getCount() == 1 && output(machine).isEmpty(),
                    "Full tank consumed a legacy container");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "automation_inventory", timeoutTicks = 40)
    public static void standardPartialConsumableAndReplacementContainersConserveFluid(GameTestHelper h) {
        for (boolean generator : List.of(false, true)) {
            var machine = detached(h, generator);
            tank(machine).fill(phase(7900), FluidAction.EXECUTE);
            ItemStack partial = fluidContainer(Items.PRISMARINE_SHARD, 500);
            partial.set(DataComponents.CUSTOM_NAME, Component.literal("partial container"));
            setInput(machine, partial);
            var preview = FluidUtil.tryEmptyContainer(partial, tank(machine), Integer.MAX_VALUE, null, false);
            h.assertTrue(preview.isSuccess() && fluidAmount(preview.getResult()) == 400
                    && fluidAmount(partial) == 500 && tank(machine).getFluidAmount() == 7900,
                    "Native container simulation modified source or destination");
            tick(machine);
            h.assertTrue(input(machine).isEmpty() && tank(machine).getFluidAmount() == 8000
                    && fluidAmount(output(machine)) == 400
                    && output(machine).getHoverName().getString().equals("partial container"),
                    "Partial transfer lost remaining fluid or container components");
            var disk = machine.saveWithFullMetadata(h.getLevel().registryAccess());
            var restored = (AcademyContainerBlockEntity) BlockEntity.loadStatic(machine.getBlockPos(), machine.getBlockState(),
                    disk, h.getLevel().registryAccess());
            h.assertTrue(restored != null && tank(restored).getFluidAmount() == 8000
                    && ItemStack.matches(output(restored), output(machine)), "Save/reload lost partial container or tank content");

            machine = detached(h, generator);
            setInput(machine, fluidContainer(Items.PRISMARINE_CRYSTALS, 500));
            tick(machine);
            h.assertTrue(tank(machine).getFluidAmount() == 500 && output(machine).is(Items.GLASS_BOTTLE),
                    "Machine ignored the capability's replacement container item");
            setInput(machine, fluidContainer(Items.NAUTILUS_SHELL, 750));
            setOutput(machine, new ItemStack(Items.STONE, 64));
            tick(machine);
            h.assertTrue(tank(machine).getFluidAmount() == 1250 && input(machine).isEmpty()
                    && output(machine).is(Items.STONE) && output(machine).getCount() == 64,
                    "Consumable fluid container did not support an empty returned stack");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "automation_inventory", timeoutTicks = 100)
    public static void naturalHoppersFeedAndDrainPhaseGeneratorContainers(GameTestHelper h) {
        hopperFluidCycle(h, AcademyBlocks.PHASE_GEN.get());
    }

    @GameTest(template = "empty", batch = "automation_inventory", timeoutTicks = 100)
    public static void naturalHoppersFeedAndDrainImagFusorContainers(GameTestHelper h) {
        hopperFluidCycle(h, AcademyBlocks.IMAG_FUSOR.get());
    }

    private static void hopperFluidCycle(GameTestHelper h, Block block) {
        BlockPos pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, block);
        h.setBlock(pos.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        h.setBlock(pos.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        var source = (HopperBlockEntity) h.getBlockEntity(pos.above());
        var drain = (HopperBlockEntity) h.getBlockEntity(pos.below());
        source.setItem(0, new ItemStack(AcademyItems.PHASE_BUCKET.get()));
        var machine = (AcademyContainerBlockEntity) h.getBlockEntity(pos);
        h.succeedWhen(() -> {
            h.assertTrue(source.isEmpty() && input(machine).isEmpty() && output(machine).isEmpty()
                    && drain.getItem(0).is(Items.BUCKET) && drain.getItem(0).getCount() == 1,
                    "Natural vanilla hopper ticks did not complete container input/return");
            double generated = machine instanceof PhaseGenBlockEntity phase ? phase.getStoredEnergy() * 2 : 0;
            h.assertTrue(tank(machine).getFluidAmount() + generated == 1000, "Hopper cycle lost or duplicated fluid/energy");
            LogUtils.getLogger().info("[academy_automation:hopper] machine={} natural_tick=true conserved_mB=1000", block);
        });
    }

    @GameTest(template = "empty", batch = "automation_inventory", timeoutTicks = 100)
    public static void naturalHoppersChargeSolarBatteryAndExtractWhenFull(GameTestHelper h) {
        hopperChargeCycle(h, false);
    }

    @GameTest(template = "empty", batch = "automation_inventory", timeoutTicks = 100)
    public static void naturalHoppersChargeThroughWindUpperProxy(GameTestHelper h) {
        hopperChargeCycle(h, true);
    }

    private static void hopperChargeCycle(GameTestHelper h, boolean wind) {
        BlockPos pos = new BlockPos(2, 2, 2);
        placeGenerator(h, pos, wind ? AcademyBlocks.WINDGEN_BASE.get() : AcademyBlocks.SOLAR_GEN.get());
        BlockPos inputPos = wind ? pos.above(2) : pos.above();
        h.setBlock(inputPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        h.setBlock(pos.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        var source = (HopperBlockEntity) h.getBlockEntity(inputPos);
        var drain = (HopperBlockEntity) h.getBlockEntity(pos.below());
        var machine = (AcademyContainerBlockEntity) h.getBlockEntity(pos);
        if (wind) ((WindGenBaseBlockEntity) machine).setEnergy(100);
        else ((SolarGenBlockEntity) machine).setEnergy(100);
        source.setItem(0, battery(-10));
        h.succeedWhen(() -> {
            ItemStack result = drain.getItem(0);
            h.assertTrue(source.isEmpty() && machine.getItems().getFirst().isEmpty() && result.is(AcademyItems.ENERGY_UNIT.get()),
                    "Natural hopper ticks failed charging input/output through the registered capability");
            var energy = result.getCapability(Capabilities.EnergyStorage.ITEM);
            h.assertTrue(energy != null && energy.getEnergyStored() == energy.getMaxEnergyStored()
                    && energy.getEnergyStored() == 4 * EnergyItemHelper.getEnergy(result),
                    "Hopper extracted an unfinished battery or altered FE/IF conversion");
            LogUtils.getLogger().info("[academy_automation:hopper] generator={} natural_tick=true battery_full=true", wind ? "wind_proxy" : "solar");
        });
    }

    @GameTest(template = "empty", batch = "automation_inventory", timeoutTicks = 40)
    public static void removedHandlersRejectAccessAndGeneratorContentsDropOnce(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 2, 2);
        for (Block block : List.of(AcademyBlocks.SOLAR_GEN.get(), AcademyBlocks.WINDGEN_BASE.get(),
                AcademyBlocks.PHASE_GEN.get(), AcademyBlocks.IMAG_FUSOR.get(), AcademyBlocks.METAL_FORMER.get())) {
            placeGenerator(h, pos, block);
            IItemHandler handler = capability(h, pos, Direction.UP);
            IItemHandler proxy = block == AcademyBlocks.WINDGEN_BASE.get() ? capability(h, pos.above(), Direction.UP) : null;
            var machine = (AcademyContainerBlockEntity) h.getBlockEntity(pos);
            boolean charging = block == AcademyBlocks.SOLAR_GEN.get() || block == AcademyBlocks.WINDGEN_BASE.get();
            if (charging) machine.getItems().set(0, battery(17));
            h.getLevel().destroyBlock(h.absolutePos(pos), false);
            h.assertTrue(handler.getStackInSlot(0).isEmpty() && handler.extractItem(0, 1, false).isEmpty()
                    && handler.insertItem(0, battery(0), false).getCount() == 1, "Removed machine's old handler remained usable");
            if (proxy != null) h.assertTrue(proxy.getStackInSlot(0).isEmpty()
                    && proxy.insertItem(0, battery(0), false).getCount() == 1, "Removed wind proxy retained its inventory capability");
            if (charging) {
                var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(pos)).inflate(1),
                        item -> item.getItem().is(AcademyItems.ENERGY_UNIT.get()));
                h.assertTrue(drops.stream().mapToInt(drop -> drop.getItem().getCount()).sum() == 1
                        && EnergyItemHelper.getEnergy(drops.getFirst().getItem()) == 17,
                        "Generator removal lost or duplicated inventory or its components");
                drops.forEach(ItemEntity::discard);
            }
        }
        h.succeed();
    }

    private static void placeGenerator(GameTestHelper h, BlockPos pos, Block block) {
        h.getLevel().setBlock(h.absolutePos(pos), block.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (block == AcademyBlocks.WINDGEN_BASE.get())
            h.getLevel().setBlock(h.absolutePos(pos.above()), AcademyBlocks.WIND_GEN_BASE_SUB.get().defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static IItemHandler capability(GameTestHelper h, BlockPos pos, Direction face) {
        IItemHandler handler = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(pos), face);
        h.assertTrue(handler != null, "Missing registered item capability at " + pos + " face " + face);
        return handler;
    }

    private static AcademyContainerBlockEntity detached(GameTestHelper h, boolean generator) {
        Block block = generator ? AcademyBlocks.PHASE_GEN.get() : AcademyBlocks.IMAG_FUSOR.get();
        var machine = (AcademyContainerBlockEntity) ((EntityBlock) block).newBlockEntity(h.absolutePos(new BlockPos(1, 2, 1)), block.defaultBlockState());
        machine.setLevel(h.getLevel());
        if (generator) {
            var tag = machine.saveWithFullMetadata(h.getLevel().registryAccess());
            tag.putFloat("storedEnergy", 6000); // Freeze generation so the container transaction is isolated.
            machine.loadAdditional(tag, h.getLevel().registryAccess());
        }
        return machine;
    }

    private static FluidTank tank(AcademyContainerBlockEntity machine) {
        return machine instanceof PhaseGenBlockEntity phase ? phase.getFluidTank() : ((ImagFusorBlockEntity) machine).getFluidTank();
    }
    private static int inputSlot(AcademyContainerBlockEntity machine) { return machine instanceof PhaseGenBlockEntity ? 0 : ImagFusorBlockEntity.FLUID_INPUT_SLOT; }
    private static int outputSlot(AcademyContainerBlockEntity machine) { return machine instanceof PhaseGenBlockEntity ? 1 : ImagFusorBlockEntity.EMPTY_UNIT_SLOT; }
    private static ItemStack input(AcademyContainerBlockEntity machine) { return machine.getItems().get(inputSlot(machine)); }
    private static ItemStack output(AcademyContainerBlockEntity machine) { return machine.getItems().get(outputSlot(machine)); }
    private static void setInput(AcademyContainerBlockEntity machine, ItemStack stack) { machine.getItems().set(inputSlot(machine), stack); }
    private static void setOutput(AcademyContainerBlockEntity machine, ItemStack stack) { machine.getItems().set(outputSlot(machine), stack); }
    private static void tick(AcademyContainerBlockEntity machine) {
        if (machine instanceof PhaseGenBlockEntity phase) phase.tick(); else ((ImagFusorBlockEntity) machine).tick();
    }
    private static FluidStack phase(int amount) { return new FluidStack(AcademyFluids.PHASE_LIQUID.get(), amount); }
    private static ItemStack fluidContainer(Item item, int amount) {
        ItemStack stack = new ItemStack(item);
        stack.set(AutomationFluidFixtures.FLUID, SimpleFluidContent.copyOf(phase(amount)));
        return stack;
    }
    private static int fluidAmount(ItemStack stack) { return FluidUtil.getFluidContained(stack).map(FluidStack::getAmount).orElse(0); }
    private static ItemStack battery(int energy) {
        ItemStack stack = new ItemStack(AcademyItems.ENERGY_UNIT.get());
        int capacity = ((IEnergyItem) stack.getItem()).getMaxEnergyStored(stack);
        EnergyItemHelper.setEnergy(stack, energy < 0 ? capacity + energy : energy);
        return stack;
    }
    private static void fillBattery(ItemStack stack) {
        EnergyItemHelper.setEnergy(stack, ((IEnergyItem) stack.getItem()).getMaxEnergyStored(stack));
    }
}
