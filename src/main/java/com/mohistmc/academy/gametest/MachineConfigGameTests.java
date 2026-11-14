package com.mohistmc.academy.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.capability.ExternalEnergyConversion;
import com.mohistmc.academy.config.MachineConfig;
import com.mohistmc.academy.crafting.AcademyRecipeSerializers;
import com.mohistmc.academy.crafting.ImagFusorRecipe;
import com.mohistmc.academy.crafting.MetalFormingRecipe;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyFluids;
import com.mohistmc.academy.world.block.entity.AbilityInterfererBlockEntity;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import com.mohistmc.academy.world.block.entity.CatEngineBlockEntity;
import com.mohistmc.academy.world.block.entity.EnergyBridgeBlockEntity;
import com.mohistmc.academy.world.block.entity.EnergyBridgeInputBlockEntity;
import com.mohistmc.academy.world.block.entity.ImagFusorBlockEntity;
import com.mohistmc.academy.world.block.entity.MatrixBlockEntity;
import com.mohistmc.academy.world.block.entity.MetalFomerBlockEntity;
import com.mohistmc.academy.world.block.entity.PhaseGenBlockEntity;
import com.mohistmc.academy.world.block.entity.SolarGenBlockEntity;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server config section {@code [machines]} and the optional recipe {@code time}/{@code energy}
 * fields. Every test runs synchronously inside one call and restores any config value or
 * recipe-manager change in {@code finally}; the dedicated batch keeps them away from the
 * legacy machine tests.
 */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MachineConfigGameTests {
    private static final String EMPTY = "empty";
    private static final String BATCH = "machine_config";
    private MachineConfigGameTests() {}

    @GameTest(template = EMPTY, batch = BATCH)
    public static void machineConfigDefaultsEqualLegacyValues(GameTestHelper h) {
        check(ExternalEnergyConversion.fePerIf() == 4, "fePerIf default is not 4");
        check(MachineConfig.WIRELESS_NETWORK_BUFFER.get() == 2000, "network buffer default is not 2000");

        node(h, 1, AcademyBlocks.NODE_BASIC.get(), 15000, 150, 9.0, 5);
        node(h, 3, AcademyBlocks.NODE_STANDARD.get(), 50000, 300, 12.0, 10);
        node(h, 5, AcademyBlocks.NODE_ADVANCED.get(), 200000, 900, 19.0, 20);

        int[] capacity = {8, 16, 24};
        double[] bandwidth = {60, 240, 540};
        for (int level = 1; level <= 3; level++) {
            check(MatrixBlockEntity.capacityFor(level) == capacity[level - 1], "matrix capacity 8*L changed");
            check(MatrixBlockEntity.bandwidthFor(level) == bandwidth[level - 1], "matrix bandwidth 60*L^2 changed");
            check(near(MatrixBlockEntity.rangeFor(level), 24.0 * Math.sqrt(level)), "matrix range 24*sqrt(L) changed");
        }

        var solar = place(h, 7, AcademyBlocks.SOLAR_GEN.get(), SolarGenBlockEntity.class);
        check(solar.getMaxEnergyStored() == 1000 && solar.getBandwidth() == 100
                        && MachineConfig.SOLAR_DAY_RATE.get() == 3.0 && MachineConfig.SOLAR_RAIN_RATE.get() == 0.6,
                "solar generator defaults changed");

        check(MachineConfig.WIND_BUFFER.get() == 20000 && MachineConfig.WIND_BANDWIDTH.get() == 300
                        && near(MachineConfig.windRate(70), 7.5) && near(MachineConfig.windRate(0), 7.5)
                        && near(MachineConfig.windRate(115), 11.25) && near(MachineConfig.windRate(160), 15.0)
                        && near(MachineConfig.windRate(300), 15.0),
                "wind generator defaults changed");

        var phase = place(h, 9, AcademyBlocks.PHASE_GEN.get(), PhaseGenBlockEntity.class);
        check(phase.getMaxEnergy() == 6000 && phase.getBandwidth() == 50
                        && MachineConfig.PHASE_ENERGY_PER_MB.get() == 0.5 && MachineConfig.PHASE_MAX_DRAIN.get() == 100,
                "phase generator defaults changed");

        var cat = place(h, 11, AcademyBlocks.CAT_ENGINE.get(), CatEngineBlockEntity.class);
        check(cat.getMaxStorage() == 2000 && cat.getBandwidth() == 200 && MachineConfig.CAT_GENERATION.get() == 500,
                "cat engine defaults changed");

        var fusor = place(h, 13, AcademyBlocks.IMAG_FUSOR.get(), ImagFusorBlockEntity.class);
        check(fusor.getMaxEnergy() == 2000 && fusor.getBandwidth() == 50 && fusor.getProcessingDuration() == 120
                        && MachineConfig.FUSOR_ENERGY_PER_TICK.get() == 12.0,
                "imag fusor defaults changed");

        var former = place(h, 15, AcademyBlocks.METAL_FORMER.get(), MetalFomerBlockEntity.class);
        check(former.getMaxEnergy() == 3000 && former.getBandwidth() == 50
                        && MachineConfig.FORMER_PROCESS_TIME.get() == 60
                        && MachineConfig.FORMER_ENERGY_PER_TICK.get() == 13.3,
                "metal former defaults changed");

        var bridge = place(h, 17, AcademyBlocks.RF_INPUT.get(), EnergyBridgeInputBlockEntity.class);
        check(EnergyBridgeBlockEntity.maxFe() == EnergyBridgeBlockEntity.MAX_FE && EnergyBridgeBlockEntity.maxFe() == 8000
                        && bridge.getBandwidth() == 100 && bridge.externalEnergy().getMaxEnergyStored() == 8000,
                "energy bridge defaults changed");

        var interferer = place(h, 19, AcademyBlocks.ABILITY_INTERFERER.get(), AbilityInterfererBlockEntity.class);
        check(interferer.getMaxEnergyStored() == 10_000 && interferer.getBandwidth() == 50
                        && MachineConfig.interfererCost(100) == 100 && MachineConfig.interfererCost(10_000) == 10_000,
                "ability interferer defaults changed");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void imagFusorRecipeTimeAndEnergyOverrideConfig(GameTestHelper h) {
        ImagFusorRecipe recipe = decodeFusor(h, """
                {"type":"academy:imag_fusor","input":{"item":"minecraft:sculk_vein"},"phaseLiquid":100,
                 "output":{"id":"minecraft:stick"},"time":7,"energy":3}""");
        check(recipe.time().orElse(-1) == 7 && recipe.energy().orElse(-1.0) == 3.0, "time/energy not decoded");
        ImagFusorRecipe synced = roundTrip(h, AcademyRecipeSerializers.IMAG_FUSOR.get().streamCodec(), recipe);
        check(synced.time().orElse(-1) == 7 && synced.energy().orElse(-1.0) == 3.0, "time/energy lost in recipe sync");

        withRecipe(h, "config_test_fusor_timed", recipe, () -> {
            var fusor = fusor(h, 2);
            for (int i = 0; i < 6; i++) fusor.tick();
            check(fusor.getProcessingTime() == 6 && fusor.getItems().get(ImagFusorBlockEntity.OUTPUT_SLOT).isEmpty()
                            && fusor.getProcessingDuration() == 7,
                    "timed fusor recipe finished early or reported the config duration");
            fusor.tick();
            check(fusor.getItems().get(ImagFusorBlockEntity.OUTPUT_SLOT).is(Items.STICK)
                            && near(fusor.getEnergy(), 2000 - 7 * 3),
                    "timed fusor recipe did not finish after exactly 7 ticks at 3 IF/t");
        });
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void metalFormerRecipeTimeAndEnergyOverrideConfig(GameTestHelper h) {
        MetalFormingRecipe recipe = decodeFormer(h, """
                {"type":"academy:metal_forming","mode":"plate","input":{"item":"minecraft:sculk_vein"},
                 "output":{"id":"minecraft:stick"},"time":5,"energy":2}""");
        check(recipe.getTime().orElse(-1) == 5 && recipe.getEnergy().orElse(-1.0) == 2.0, "time/energy not decoded");
        MetalFormingRecipe synced = roundTrip(h, AcademyRecipeSerializers.METAL_FORMING.get().streamCodec(), recipe);
        check(synced.getTime().orElse(-1) == 5 && synced.getEnergy().orElse(-1.0) == 2.0,
                "time/energy lost in recipe sync");

        withRecipe(h, "config_test_former_timed", recipe, () -> {
            var former = former(h, 2);
            // Legacy search delay: the job is selected on the fifth idle tick.
            for (int i = 0; i < 5; i++) former.serverTick();
            check(former.getCurrentRecipe() != null, "timed former recipe was not selected");
            for (int i = 0; i < 4; i++) former.serverTick();
            check(former.getItems().get(MetalFomerBlockEntity.SLOT_OUT).isEmpty(), "timed former recipe finished early");
            former.serverTick();
            check(former.getItems().get(MetalFomerBlockEntity.SLOT_OUT).is(Items.STICK)
                            && near(former.getEnergy(), 3000 - 5 * 2),
                    "timed former recipe did not finish after exactly 5 ticks at 2 IF/t");
        });
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void recipesWithoutFieldsUseMachineConfig(GameTestHelper h) {
        ImagFusorRecipe fusorRecipe = decodeFusor(h, """
                {"type":"academy:imag_fusor","input":{"item":"minecraft:sculk_vein"},"phaseLiquid":100,
                 "output":{"id":"minecraft:stick"}}""");
        MetalFormingRecipe formerRecipe = decodeFormer(h, """
                {"type":"academy:metal_forming","mode":"plate","input":{"item":"minecraft:sculk_vein"},
                 "output":{"id":"minecraft:stick"}}""");
        check(fusorRecipe.time().isEmpty() && fusorRecipe.energy().isEmpty()
                        && formerRecipe.getTime().isEmpty() && formerRecipe.getEnergy().isEmpty(),
                "absent fields decoded as values");
        check(fusorRecipe.processTime() == 120 && fusorRecipe.energyPerTick() == 12.0
                        && formerRecipe.processTime() == 60 && formerRecipe.energyPerTick() == 13.3,
                "absent fields do not fall back to the legacy machine values");

        int fusorTime = MachineConfig.FUSOR_PROCESS_TIME.get();
        double fusorEnergy = MachineConfig.FUSOR_ENERGY_PER_TICK.get();
        int formerTime = MachineConfig.FORMER_PROCESS_TIME.get();
        double formerEnergy = MachineConfig.FORMER_ENERGY_PER_TICK.get();
        try {
            MachineConfig.FUSOR_PROCESS_TIME.set(4);
            MachineConfig.FUSOR_ENERGY_PER_TICK.set(5.0);
            MachineConfig.FORMER_PROCESS_TIME.set(3);
            MachineConfig.FORMER_ENERGY_PER_TICK.set(4.0);

            withRecipe(h, "config_test_fusor_default", fusorRecipe, () -> {
                var fusor = fusor(h, 2);
                for (int i = 0; i < 3; i++) fusor.tick();
                check(fusor.getItems().get(ImagFusorBlockEntity.OUTPUT_SLOT).isEmpty()
                        && fusor.getProcessingDuration() == 4, "config fusor time not applied");
                fusor.tick();
                check(fusor.getItems().get(ImagFusorBlockEntity.OUTPUT_SLOT).is(Items.STICK)
                        && near(fusor.getEnergy(), 2000 - 4 * 5), "config fusor time/energy not applied");
            });
            withRecipe(h, "config_test_former_default", formerRecipe, () -> {
                var former = former(h, 4);
                for (int i = 0; i < 5 + 2; i++) former.serverTick();
                check(former.getItems().get(MetalFomerBlockEntity.SLOT_OUT).isEmpty(), "config former time not applied");
                former.serverTick();
                check(former.getItems().get(MetalFomerBlockEntity.SLOT_OUT).is(Items.STICK)
                        && near(former.getEnergy(), 3000 - 3 * 4), "config former time/energy not applied");
            });
        } finally {
            MachineConfig.FUSOR_PROCESS_TIME.set(fusorTime);
            MachineConfig.FUSOR_ENERGY_PER_TICK.set(fusorEnergy);
            MachineConfig.FORMER_PROCESS_TIME.set(formerTime);
            MachineConfig.FORMER_ENERGY_PER_TICK.set(formerEnergy);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void loweredCapacityClampsStoredEnergy(GameTestHelper h) {
        var node = place(h, 2, AcademyBlocks.NODE_BASIC.get(), BaseNodeBlockEntity.class);
        var solar = place(h, 4, AcademyBlocks.SOLAR_GEN.get(), SolarGenBlockEntity.class);
        node.setEnergy(15000);
        solar.setEnergy(1000);
        check(node.getEnergy() == 15000 && solar.getEnergyStored() == 1000, "fixture fill failed");
        int nodeMax = MachineConfig.NODE_BASIC.maxEnergy.get();
        int solarMax = MachineConfig.SOLAR_BUFFER.get();
        try {
            MachineConfig.NODE_BASIC.maxEnergy.set(1000);
            MachineConfig.SOLAR_BUFFER.set(100);
            check(node.getMaxEnergy() == 1000 && solar.getMaxEnergyStored() == 100, "capacity not read at use time");
            node.serverTick();
            solar.tick(h.getLevel(), solar.getBlockPos(), solar.getBlockState());
            check(node.getEnergy() == 1000 && solar.getEnergyStored() <= 100,
                    "stored energy was not clamped to the lowered capacity");
        } finally {
            MachineConfig.NODE_BASIC.maxEnergy.set(nodeMax);
            MachineConfig.SOLAR_BUFFER.set(solarMax);
        }
        h.succeed();
    }

    // ==================== helpers ====================

    private static void node(GameTestHelper h, int x, Block block, double max, double bandwidth, double range,
                             int connections) {
        var node = place(h, x, block, BaseNodeBlockEntity.class);
        check(node.getMaxEnergy() == max && node.getBandwidth() == bandwidth && node.getRange() == range
                        && node.getCapacity() == connections,
                block + " node defaults changed: " + node.getMaxEnergy() + "/" + node.getBandwidth()
                        + "/" + node.getRange() + "/" + node.getCapacity());
    }

    private static ImagFusorBlockEntity fusor(GameTestHelper h, int x) {
        var fusor = place(h, x, AcademyBlocks.IMAG_FUSOR.get(), ImagFusorBlockEntity.class);
        fusor.getItems().set(ImagFusorBlockEntity.INPUT_SLOT, new ItemStack(Items.SCULK_VEIN, 4));
        fusor.getFluidTank().fill(new FluidStack(AcademyFluids.PHASE_LIQUID.get(), 1000), FluidAction.EXECUTE);
        fusor.injectEnergy(2000);
        return fusor;
    }

    private static MetalFomerBlockEntity former(GameTestHelper h, int x) {
        var former = place(h, x, AcademyBlocks.METAL_FORMER.get(), MetalFomerBlockEntity.class);
        former.getItems().set(MetalFomerBlockEntity.SLOT_IN, new ItemStack(Items.SCULK_VEIN, 4));
        former.injectEnergy(3000);
        return former;
    }

    private static <T> T place(GameTestHelper h, int x, Block block, Class<T> type) {
        BlockPos pos = h.absolutePos(new BlockPos(x, 2, 2));
        h.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        var entity = h.getLevel().getBlockEntity(pos);
        if (!type.isInstance(entity)) throw new GameTestAssertException(block + " block entity missing");
        return type.cast(entity);
    }

    private static void withRecipe(GameTestHelper h, String path, Recipe<?> recipe, Runnable body) {
        RecipeManager manager = h.getLevel().getRecipeManager();
        List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
        List<RecipeHolder<?>> merged = new ArrayList<>(original);
        merged.add(new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, path), recipe));
        manager.replaceRecipes(merged);
        try {
            body.run();
        } finally {
            manager.replaceRecipes(original);
        }
    }

    private static ImagFusorRecipe decodeFusor(GameTestHelper h, String json) {
        return AcademyRecipeSerializers.IMAG_FUSOR.get().codec().codec()
                .parse(RegistryOps.create(JsonOps.INSTANCE, h.getLevel().registryAccess()), parse(json))
                .getOrThrow(GameTestAssertException::new);
    }

    private static MetalFormingRecipe decodeFormer(GameTestHelper h, String json) {
        return AcademyRecipeSerializers.METAL_FORMING.get().codec().codec()
                .parse(RegistryOps.create(JsonOps.INSTANCE, h.getLevel().registryAccess()), parse(json))
                .getOrThrow(GameTestAssertException::new);
    }

    private static JsonObject parse(String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        object.remove("type");
        return object;
    }

    private static <T> T roundTrip(GameTestHelper h,
                                   net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, T> codec,
                                   T value) {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            codec.encode(buf, value);
            return codec.decode(buf);
        } finally {
            buf.release();
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1.0e-6;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }
}
