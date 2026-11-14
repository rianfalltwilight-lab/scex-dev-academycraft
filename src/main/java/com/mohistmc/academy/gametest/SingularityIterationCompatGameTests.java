package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.capability.ExternalEnergyConversion;
import com.mohistmc.academy.skill.ConductorArcs;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import com.mohistmc.academy.world.block.entity.EnergyBridgeOutputBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Singularity Iteration (mio_icif) integration, the 1.21.1 successor of 1.0.7's IC2 support.  Run with
 * {@code ./gradlew runGameTestServer -PsiJar=/path/to/mio_icif-<version>.jar}; without the mod each test
 * only checks that the integration stays dormant.
 */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SingularityIterationCompatGameTests {
    private static final String EMPTY = "empty";
    private static final String BATCH = "si_compat";
    private static final String SI = "mio_icif";
    private SingularityIterationCompatGameTests() {}

    private static boolean loaded() { return ModList.get().isLoaded(SI); }

    private static ResourceLocation si(String path) { return ResourceLocation.fromNamespaceAndPath(SI, path); }

    private static ItemStack siBattery() {
        return new ItemStack(BuiltInRegistries.ITEM.get(si("normal/item_chargebat_lev0")));
    }

    /** An SI battery (new ones start full) set to exactly {@code fe} through its own FE view. */
    private static ItemStack siBatteryAt(int fe) {
        ItemStack stack = siBattery();
        IEnergyStorage storage = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        for (int guard = 0; guard < 10_000 && storage.getEnergyStored() > fe; guard++)
            if (storage.extractEnergy(storage.getEnergyStored() - fe, false) <= 0) break;
        for (int guard = 0; guard < 10_000 && storage.getEnergyStored() < fe; guard++)
            if (storage.receiveEnergy(fe - storage.getEnergyStored(), false) <= 0) break;
        return stack;
    }

    private static Block siBlock(String path) { return BuiltInRegistries.BLOCK.get(si(path)); }

    private static void check(GameTestHelper helper, boolean ok, String message) {
        if (!ok) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void siRecipesLoadOnlyWithTheMod(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        for (String id : new String[]{"si_rf_input", "si_rf_output"}) {
            boolean present = recipes.byKey(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, id)).isPresent();
            check(helper, present == loaded(), id + " present=" + present + " but mio_icif loaded=" + loaded());
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void siBatteryFillsAndDrainsThroughANode(GameTestHelper helper) {
        if (!loaded()) { helper.succeed(); return; }
        int ratio = ExternalEnergyConversion.fePerIf();
        ItemStack battery = siBatteryAt(4000);
        IEnergyStorage fe = battery.getCapability(Capabilities.EnergyStorage.ITEM);
        check(helper, fe != null && EnergyItemHelper.isEnergyItem(battery), "SI battery is not a battery-slot item");
        check(helper, !EnergyItemHelper.isNativeEnergyItem(battery), "SI battery taken for an IF item");
        check(helper, fe.getEnergyStored() == 4000, "fixture battery holds " + fe.getEnergyStored() + " FE");

        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(pos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        if (!(helper.getLevel().getBlockEntity(pos) instanceof BaseNodeBlockEntity node)) {
            helper.fail("node fixture missing"); return;
        }
        node.setEnergy(0);
        node.getItems().set(0, battery);
        node.serverTick();
        // Basic node bandwidth 150 IF; the battery's own rate (128 EU = 512 FE) is the tighter bound.
        int pulled = (int) node.getEnergy();
        check(helper, pulled > 0 && pulled <= 150, "node pulled " + pulled + " IF from the SI battery");
        check(helper, fe.getEnergyStored() == 4000 - pulled * ratio,
                "battery lost " + (4000 - fe.getEnergyStored()) + " FE for " + pulled + " IF");

        node.getItems().set(0, ItemStack.EMPTY);
        ItemStack empty = siBatteryAt(0);
        node.getItems().set(1, empty);
        node.setEnergy(1000);
        node.serverTick();
        IEnergyStorage out = empty.getCapability(Capabilities.EnergyStorage.ITEM);
        int pushed = 1000 - (int) node.getEnergy();
        check(helper, out.getEnergyStored() == 0 || pushed > 0, "empty-battery fixture");
        check(helper, pushed > 0 && out.getEnergyStored() == pushed * ratio,
                "node pushed " + pushed + " IF, battery holds " + out.getEnergyStored() + " FE");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void stackedSiBatteriesMoveNothing(GameTestHelper helper) {
        if (!loaded()) { helper.succeed(); return; }
        ItemStack stack = siBattery();
        stack.setCount(2);
        check(helper, EnergyItemHelper.receiveEnergy(stack, 100, false) == 0, "a stack of two SI batteries took energy");
        check(helper, EnergyItemHelper.extractEnergy(stack, 100, false) == 0, "a stack of two SI batteries gave energy");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void abilityArcsFindNearbySiCables(GameTestHelper helper) {
        if (!loaded()) {
            check(helper, ConductorArcs.conductors(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 3))).isEmpty(),
                    "arc conductors found without mio_icif");
            helper.succeed(); return;
        }
        Block cable = siBlock("wiring/cable/block_cable");
        check(helper, cable != Blocks.AIR && cable.defaultBlockState().is(ConductorArcs.ARC_CONDUCTORS),
                "SI cable is not in academy:arc_conductors");
        BlockPos centre = helper.absolutePos(new BlockPos(3, 2, 3));
        for (int i = 0; i < 3; i++) helper.getLevel().setBlock(centre.offset(i, -1, 0), cable.defaultBlockState(), 3);
        helper.getLevel().setBlock(centre.offset(0, 0, 6), cable.defaultBlockState(), 3);
        int found = ConductorArcs.conductors(helper.getLevel(), centre).size();
        check(helper, found == 3, "expected 3 cables within 5 blocks, found " + found);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void rfOutputFeedsAnSiGridThroughACable(GameTestHelper helper) {
        if (!loaded()) { helper.succeed(); return; }
        BlockPos bridgePos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos cablePos = bridgePos.east();
        BlockPos boxPos = cablePos.east();
        var level = helper.getLevel();
        level.setBlock(bridgePos, AcademyBlocks.RF_OUTPUT.get().defaultBlockState(), 3);
        level.setBlock(cablePos, siBlock("wiring/cable/block_cable").defaultBlockState(), 3);
        level.setBlock(boxPos, siBlock("wiring/block_bat_box").defaultBlockState(), 3);
        if (!(level.getBlockEntity(bridgePos) instanceof EnergyBridgeOutputBlockEntity bridge)) {
            helper.fail("rf_output fixture missing"); return;
        }
        bridge.injectEnergy(2000);
        int start = bridge.getStoredFe();
        check(helper, start > 0, "rf_output fixture holds no FE");
        helper.succeedWhen(() -> {
            IEnergyStorage box = level.getCapability(Capabilities.EnergyStorage.BLOCK, boxPos, null);
            check(helper, box != null, "SI batbox exposes no FE view");
            check(helper, box.getEnergyStored() > 0 && bridge.getStoredFe() < start,
                    "SI grid has not drawn from rf_output (bridge " + bridge.getStoredFe() + "/" + start
                            + " FE, batbox " + box.getEnergyStored() + " FE)");
        });
    }
}
