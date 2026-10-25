package com.mohistmc.academy.regression;

import com.mohistmc.academy.crafting.AcademyRecipeTypes;
import com.mohistmc.academy.crafting.ImagFusorRecipe;
import com.mohistmc.academy.crafting.ImagFusorRecipeInput;
import com.mohistmc.academy.crafting.MetalFormerRecipes.Mode;
import com.mohistmc.academy.crafting.MetalFormingRecipe;
import com.mohistmc.academy.crafting.MetalFormingRecipeInput;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyFluids;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.entity.ImagFusorBlockEntity;
import com.mohistmc.academy.world.block.entity.MetalFomerBlockEntity;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Production machine ticks, native recipe lookup and a real server datapack reload. */
@GameTestHolder("academy_automation")
@PrefixGameTestTemplate(false)
public final class RecipeCacheReloadGameTests {
    private RecipeCacheReloadGameTests() {}

    @GameTest(template = "empty", batch = "automation_recipe_checks")
    public static void nativeRecipeCacheRechecksModeAndCountWithoutRescanningCandidates(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        CountingRecipe incise = new CountingRecipe(Mode.INCISE, 2, Items.GOLD_NUGGET);
        CountingRecipe plate = new CountingRecipe(Mode.PLATE, 2, Items.PAPER);
        try {
            manager.replaceRecipes(List.of(holder("first_incise", incise), holder("second_plate", plate)));
            var machine = former(h);
            machine.getItems().set(0, new ItemStack(Items.IRON_INGOT, 4));
            tick(machine, 65);
            h.assertTrue(machine.getItems().get(1).is(Items.PAPER) && machine.getItems().get(1).getCount() == 1,
                    "First production job did not select its mode/count recipe");
            h.assertTrue(incise.matchesCalls == 1 && plate.matchesCalls == 1,
                    "Fixture did not establish a scan past the rejected mode candidate");
            tick(machine, 65);
            h.assertTrue(machine.getItems().get(0).isEmpty() && machine.getItems().get(1).getCount() == 2,
                    "Cached production job changed exact consumption/output");
            h.assertTrue(incise.matchesCalls == 1 && plate.matchesCalls == 2,
                    "Warm lookup rescanned rejected candidates or skipped the selected recipe's matches check");
            LogUtils.getLogger().info("[academy_automation:recipes] production_searches=2 cold_candidate_matches=2 warm_candidate_matches=1 rejected_candidate_total=1; no MSPT inference");

            machine.getItems().set(0, new ItemStack(Items.IRON_INGOT));
            double energy = machine.getEnergy();
            tick(machine, 5);
            h.assertTrue(machine.getCurrentRecipe() == null && machine.getEnergy() == energy,
                    "Cached recipe accepted an insufficient input count or charged for a failed match");
            machine.getItems().set(0, new ItemStack(Items.IRON_INGOT, 2));
            machine.getItems().set(1, ItemStack.EMPTY);
            machine.injectEnergy(3000);
            machine.cycleMode(1);
            tick(machine, 65);
            h.assertTrue(machine.getMode() == Mode.INCISE && machine.getItems().get(1).is(Items.GOLD_NUGGET)
                            && machine.getItems().get(0).isEmpty(),
                    "Last recipe ID masked a changed machine mode");
        } finally {
            manager.replaceRecipes(original);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "automation_recipe_checks")
    public static void fusorRecipeChangesReplacementAndRemovalCannotInheritProgress(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        try {
            var machine = fusor(h);
            machine.getItems().set(0, new ItemStack(Items.IRON_INGOT));
            tick(machine, 119);
            h.assertTrue(machine.getProcessingTime() == 119, "Initial fusor job did not reach its real boundary");
            machine.getItems().set(0, new ItemStack(Items.SAND));
            machine.injectEnergy(2000);
            tick(machine, 1);
            h.assertTrue(machine.getProcessingTime() == 1 && machine.getItems().get(1).isEmpty()
                            && machine.getItems().get(0).getCount() == 1 && machine.getFluidAmount() == 8000,
                    "Changing to another valid recipe inherited the old job's final tick");
            tick(machine, 119);
            h.assertTrue(machine.getItems().get(1).is(AcademyItems.IMAG_SILICON_INGOT.get())
                            && machine.getFluidAmount() == 7000 && machine.getItems().get(0).isEmpty(),
                    "New fusor job did not complete using its own full duration and costs");

            var id = ResourceLocation.fromNamespaceAndPath("academy_automation", "replacement");
            manager.replaceRecipes(List.of(new RecipeHolder<>(id,
                    new ImagFusorRecipe(Ingredient.of(Items.IRON_INGOT), 2, 500, new ItemStack(Items.GOLD_INGOT)))));
            machine = fusor(h);
            machine.getItems().set(0, new ItemStack(Items.IRON_INGOT, 3));
            tick(machine, 119);
            manager.replaceRecipes(List.of(new RecipeHolder<>(id,
                    new ImagFusorRecipe(Ingredient.of(Items.IRON_INGOT), 3, 750, new ItemStack(Items.DIAMOND)))));
            machine.injectEnergy(2000);
            tick(machine, 1);
            h.assertTrue(machine.getProcessingTime() == 1 && machine.getItems().get(1).isEmpty(),
                    "Replacing the same ID retained the previous recipe object/progress");
            tick(machine, 119);
            h.assertTrue(machine.getItems().get(1).is(Items.DIAMOND) && machine.getItems().get(0).isEmpty()
                            && machine.getFluidAmount() == 7250,
                    "Replaced recipe did not use the new exact input, fluid and output");

            machine.getItems().set(0, new ItemStack(Items.IRON_INGOT, 3));
            machine.getItems().set(1, ItemStack.EMPTY);
            machine.injectEnergy(2000);
            tick(machine, 119);
            manager.replaceRecipes(List.of());
            double before = machine.getEnergy();
            tick(machine, 1);
            h.assertTrue(machine.getProcessingTime() == 0 && machine.getCurrentRecipePhaseLiquid() == 0
                            && machine.getItems().get(0).getCount() == 3 && machine.getItems().get(1).isEmpty()
                            && machine.getFluidAmount() == 7250 && machine.getEnergy() == before,
                    "Removed cached recipe consumed resources or produced stale output");
        } finally {
            manager.replaceRecipes(original);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "automation_recipe_reload", timeoutTicks = 4000)
    public static void realDatapackReloadUpdatesMaterialTagsAndInvalidatesActiveRecipes(GameTestHelper h) {
        try {
            var probe = new ReloadProbe(h);
            h.testInfo.addListener(probe);
            probe.start();
            h.onEachTick(probe::pollReload);
        } catch (Exception e) {
            throw new AssertionError("Could not start native datapack reload probe", e);
        }
    }

    private static final class CountingRecipe extends MetalFormingRecipe {
        int matchesCalls;
        CountingRecipe(Mode mode, int count, Item output) {
            super(mode, Ingredient.of(Items.IRON_INGOT), count, new ItemStack(output));
        }
        @Override public boolean matches(MetalFormingRecipeInput input, Level level) {
            matchesCalls++;
            return super.matches(input, level);
        }
    }

    private static RecipeHolder<MetalFormingRecipe> holder(String name, MetalFormingRecipe recipe) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("academy_automation", name), recipe);
    }

    // These machines are attached to the actual server level but are not placed
    // in its ticker list, so production ticks and debit boundaries are exact.
    private static MetalFomerBlockEntity former(GameTestHelper h) {
        var machine = new MetalFomerBlockEntity(h.absolutePos(new BlockPos(3, 2, 3)), AcademyBlocks.METAL_FORMER.get().defaultBlockState());
        machine.setLevel(h.getLevel()); machine.injectEnergy(3000);
        return machine;
    }

    private static ImagFusorBlockEntity fusor(GameTestHelper h) {
        var machine = new ImagFusorBlockEntity(h.absolutePos(new BlockPos(4, 2, 3)), AcademyBlocks.IMAG_FUSOR.get().defaultBlockState());
        machine.setLevel(h.getLevel()); machine.injectEnergy(2000);
        machine.getFluidTank().setFluid(new FluidStack(AcademyFluids.PHASE_LIQUID.get(), 8000));
        return machine;
    }

    private static void tick(MetalFomerBlockEntity machine, int count) { for (int i = 0; i < count; i++) machine.serverTick(); }
    private static void tick(ImagFusorBlockEntity machine, int count) { for (int i = 0; i < count; i++) machine.tick(); }

    private static final class ReloadProbe implements GameTestListener {
        private final GameTestHelper h;
        private final MinecraftServer server;
        private final List<String> originalPacks;
        private final Path pack;
        private final String packId;
        private final MetalFomerBlockEntity former;
        private final ImagFusorBlockEntity fusor;
        private CompletableFuture<Void> pending;
        private Throwable failure;
        private int stage;
        private boolean done;
        private boolean restoring;

        ReloadProbe(GameTestHelper h) {
            this.h = h; server = h.getLevel().getServer();
            originalPacks = List.copyOf(server.getPackRepository().getSelectedIds());
            String name = "academy_automation_" + UUID.randomUUID();
            Path root = server.getWorldPath(LevelResource.DATAPACK_DIR).toAbsolutePath().normalize();
            pack = root.resolve(name).normalize();
            if (!pack.startsWith(root) || pack.equals(root)) throw new IllegalStateException("Probe pack escaped datapacks directory");
            packId = "file/" + name;
            former = former(h); fusor = fusor(h);
        }

        void start() {
            try {
                assertMaterials(false, false);
                former.getItems().set(0, new ItemStack(Items.IRON_INGOT, 3));
                tick(former, 35);
                fusor.getItems().set(0, new ItemStack(Items.IRON_INGOT, 3));
                tick(fusor, 119);
                h.assertTrue(former.getWorkProgress() == 0.5 && fusor.getProcessingTime() == 119,
                        "Real reload fixture did not begin with two active jobs");
                write("pack.mcmeta", "{\"pack\":{\"pack_format\":48,\"description\":\"isolated native machine reload probe\"}}");
                writeTags(false);
                write("data/academy/recipe/form_reinforced_iron_plate.json", """
                        {"type":"academy:metal_forming","mode":"plate","input":{"tag":"academy:machine_inputs/iron_ingots"},"count":2,"output":{"id":"minecraft:gold_nugget","count":5}}
                        """);
                writeFusor(false);
                server.getPackRepository().reload();
                h.assertTrue(server.getPackRepository().getPack(packId) != null, "Native pack repository did not discover probe pack");
                pending = reload(probePacks());
            } catch (Throwable error) {
                restore(error);
            }
        }

        void pollReload() {
            if (done || pending == null || !pending.isDone()) return;
            try {
                pending.getNow(null);
                if (restoring) {
                    deletePack();
                    done = true;
                    if (failure != null) throw new AssertionError("Native recipe/tag reload probe failed", failure);
                    assertMaterials(false, false);
                    h.assertTrue(h.getLevel().getRecipeManager().getAllRecipesFor(AcademyRecipeTypes.METAL_FORMING.get()).size() == 28
                                    && h.getLevel().getRecipeManager().getAllRecipesFor(AcademyRecipeTypes.IMAG_FUSING.get()).size() == 7,
                            "Original machine recipe inventory was not restored");
                    LogUtils.getLogger().info("[academy_automation:recipes] native_reload append_tags=pass replace_tags=pass same_id_replacement=pass conditional_removal=pass original_packs_restored=true");
                    h.succeed();
                    return;
                }
                if (h.getTick() > 3000) throw new AssertionError("Native reload exceeded the probe's cleanup deadline");
                if (stage++ == 0) {
                    assertMaterials(true, false);
                    double formerEnergy = former.getEnergy();
                    tick(former, 1);
                    h.assertTrue(former.getCurrentRecipe() == null && former.getWorkProgress() == 0
                                    && former.getEnergy() == formerEnergy && former.getItems().get(1).isEmpty(),
                            "Actual reload retained or charged Metal Former's previous job");
                    tick(former, 64);
                    h.assertTrue(former.getItems().get(0).getCount() == 1 && former.getItems().get(1).is(Items.GOLD_NUGGET)
                                    && former.getItems().get(1).getCount() == 5,
                            "Actual reload did not apply Metal Former's replacement count/output");
                    fusor.injectEnergy(2000);
                    tick(fusor, 1);
                    h.assertTrue(fusor.getProcessingTime() == 1 && fusor.getItems().get(1).isEmpty(),
                            "Actual reload carried old fusor progress into replacement output");
                    tick(fusor, 119);
                    h.assertTrue(fusor.getItems().get(0).getCount() == 1 && fusor.getItems().get(1).is(Items.EMERALD)
                                    && fusor.getFluidAmount() == 7750,
                            "Actual reload did not apply fusor's replacement count/fluid/output");
                    // Warm the replacement recipe before removing it in the next reload.
                    fusor.getItems().set(0, new ItemStack(Items.GOLD_INGOT, 2));
                    fusor.getItems().set(1, ItemStack.EMPTY); fusor.injectEnergy(2000);
                    tick(fusor, 119);
                    writeTags(true); writeFusor(true);
                    pending = reload(probePacks());
                } else {
                    assertMaterials(true, true);
                    double before = fusor.getEnergy();
                    tick(fusor, 1);
                    h.assertTrue(fusor.getProcessingTime() == 0 && fusor.getCurrentRecipePhaseLiquid() == 0
                                    && fusor.getItems().get(0).getCount() == 2 && fusor.getItems().get(1).isEmpty()
                                    && fusor.getFluidAmount() == 7750 && fusor.getEnergy() == before,
                            "Conditionally removed cached recipe consumed resources after real reload");
                    h.assertTrue(h.getLevel().getRecipeManager().byKey(ResourceLocation.parse("academy:extra_fuse_constraint_ingot")).isEmpty(),
                            "False datapack condition did not remove the old machine recipe ID");
                    restore(null);
                }
            } catch (Throwable error) {
                if (done || restoring) throw new AssertionError("Native reload cleanup/validation failed", error);
                restore(error);
            }
        }

        private void assertMaterials(boolean added, boolean replaced) {
            var level = h.getLevel();
            h.assertTrue(MetalFomerBlockEntity.isSlotValid(level, 0, new ItemStack(Items.IRON_INGOT)) == !replaced,
                    "Iron input tag did not retain/remove the default material as requested");
            h.assertTrue(MetalFomerBlockEntity.isSlotValid(level, 0, new ItemStack(Items.GOLD_INGOT)) == added,
                    "Metal Former input filter did not observe current iron-material tag");
            h.assertTrue(fusor.getHandlerForSide(Direction.UP).isItemValid(0, new ItemStack(Items.RED_SAND)) == added,
                    "Fusor automation filter did not observe the reloaded silicon-material tag");
            h.assertTrue(level.getRecipeManager().getRecipeFor(AcademyRecipeTypes.IMAG_FUSING.get(),
                            new ImagFusorRecipeInput(new ItemStack(Items.SAND)), level).isPresent() == !replaced,
                    "Silicon recipe retained/removed the default sand incorrectly");
            h.assertTrue(level.getRecipeManager().getRecipeFor(AcademyRecipeTypes.IMAG_FUSING.get(),
                            new ImagFusorRecipeInput(new ItemStack(Items.RED_SAND)), level).isPresent() == added,
                    "Silicon recipe did not match the material added through a real datapack tag");
        }

        private void writeTags(boolean replace) throws IOException {
            write("data/academy/tags/item/machine_inputs/iron_ingots.json",
                    "{\"replace\":" + replace + ",\"values\":[\"minecraft:gold_ingot\"]}");
            write("data/academy/tags/item/machine_inputs/silicon_feedstock.json",
                    "{\"replace\":" + replace + ",\"values\":[\"minecraft:red_sand\"]}");
        }

        private void writeFusor(boolean removed) throws IOException {
            write("data/academy/recipe/extra_fuse_constraint_ingot.json", "{"
                    + (removed ? "\"neoforge:conditions\":[{\"type\":\"neoforge:false\"}]," : "")
                    + "\"type\":\"academy:imag_fusor\",\"input\":{\"tag\":\"academy:machine_inputs/iron_ingots\"},"
                    + "\"inputCount\":2,\"phaseLiquid\":250,\"output\":{\"id\":\"minecraft:emerald\",\"count\":1}}");
        }

        private void write(String name, String content) throws IOException {
            Path file = pack.resolve(name); Files.createDirectories(file.getParent()); Files.writeString(file, content);
        }

        private List<String> probePacks() {
            var selected = new ArrayList<>(originalPacks); selected.add(packId); return selected;
        }

        private CompletableFuture<Void> reload(List<String> selected) {
            // Initiate off-thread: MinecraftServer's same-thread overload uses
            // managedBlock. All mutations still run on its own executor; assert
            // only from a subsequent GameTest server tick once it completes.
            return CompletableFuture.supplyAsync(() -> server.reloadResources(selected)).thenCompose(Function.identity());
        }

        private void restore(Throwable error) {
            failure = error; restoring = true; pending = reload(originalPacks);
        }

        private void deletePack() throws IOException {
            if (Files.exists(pack)) {
                try (var files = Files.walk(pack)) {
                    for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
                }
            }
            server.getPackRepository().reload();
        }

        @Override public void testStructureLoaded(GameTestInfo info) {}
        @Override public void testPassed(GameTestInfo info, GameTestRunner runner) {}
        @Override public void testFailed(GameTestInfo info, GameTestRunner runner) {
            if (done) return;
            done = true;
            // Emergency timeout/setup path: sequence restoration after any
            // outstanding reload rather than racing two global reloads.
            var previous = pending == null ? CompletableFuture.<Void>completedFuture(null) : pending;
            previous.handle((value, error) -> null).thenCompose(value -> reload(originalPacks))
                    .whenComplete((value, error) -> server.execute(() -> {
                        try { deletePack(); }
                        catch (IOException cleanup) { LogUtils.getLogger().error("Failed to remove isolated recipe probe pack {}", pack, cleanup); }
                    }));
        }
        @Override public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) {}
    }
}
