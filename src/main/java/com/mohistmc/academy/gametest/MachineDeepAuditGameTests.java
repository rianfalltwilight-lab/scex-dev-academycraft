package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.crafting.MetalFormerRecipes.Mode;
import com.mohistmc.academy.crafting.MetalFormingRecipe;
import com.mohistmc.academy.crafting.MetalFormingRecipeInput;
import com.mohistmc.academy.energy.impl.VBlocks;
import com.mohistmc.academy.energy.impl.WiWorldData;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyFluids;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.DevMachineBase;
import com.mohistmc.academy.world.block.entity.*;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real machine, capability, persistence and chunk-broadcast-queue regressions. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MachineDeepAuditGameTests {
    private MachineDeepAuditGameTests() {}

    @GameTest(template = "empty")
    public static void shadedSolarChargesWithinBandwidthAndPersistsFraction(GameTestHelper h) {
        var solar = place(h, 2, AcademyBlocks.SOLAR_GEN.get(), SolarGenBlockEntity.class);
        var level = h.getLevel();
        level.setBlock(solar.getBlockPos().above(), Blocks.STONE.defaultBlockState(), 3);
        // Sky light is propagated asynchronously; establish the covered fixture
        // before testing the synchronous energy transaction in a single tick.
        h.startSequence().thenWaitUntil(() -> h.assertTrue(!level.canSeeSky(solar.getBlockPos().above()),
                "roof sky-light update has not reached the covered panel")).thenExecute(() -> {
            // The 100 IF/t solar bandwidth is capped by the energy unit's legacy 20 IF/call.
            solar.setEnergy(420);
            ItemStack battery = battery(0);
            solar.getItems().set(0, battery);
            solar.tick(level, solar.getBlockPos(), solar.getBlockState());
            check(solar.getStatus() == SolarGenBlockEntity.SolarStatus.STOPPED,
                    "covered panel still advertises generation");
            check(solar.getEnergyStored() == 400 && EnergyItemHelper.getEnergy(battery) == 20,
                    "covered stored energy must charge exactly the 20 IF energy-unit bandwidth");
            solar.getItems().set(0, ItemStack.EMPTY);
            solar.getProvidedEnergy(0.4);
            CompoundTag saved = solar.saveWithoutMetadata(level.registryAccess());
            var restored = new SolarGenBlockEntity(solar.getBlockPos(), solar.getBlockState());
            restored.loadWithComponents(saved, level.registryAccess());
            check(near(restored.getStoredEnergy(), 399.6), "integer compatibility NBT erased solar fraction");
            check(restored.extractEnergy(1, true) == 1 && near(restored.getStoredEnergy(), 399.6), "simulation changed fraction");
            check(restored.extractEnergy(1, false) == 1 && near(restored.getStoredEnergy(), 398.6), "extraction erased fraction");
            check(restored.receiveEnergy(1, false) == 1 && near(restored.getStoredEnergy(), 399.6), "receive erased fraction");
            check(restored.receiveEnergy(-1, false) == 0 && restored.extractEnergy(-1, false) == 0, "negative energy changed store");
        }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void windBatteryChangePersistsWhenBufferDoesNotChange(GameTestHelper h) {
        var wind = place(h, 2, AcademyBlocks.WINDGEN_BASE.get(), WindGenBaseBlockEntity.class);
        wind.tick(true, true, true, 160);
        wind.setEnergy(0);
        ItemStack battery = battery(0);
        wind.getItems().set(0, battery);
        var chunk = h.getLevel().getChunkAt(wind.getBlockPos());
        chunk.setUnsaved(false);
        wind.tick(true, true, true, 160);
        check(wind.getEnergyStored() == 0 && EnergyItemHelper.getEnergy(battery) == 15,
                "wind generation/charging conservation failed");
        check(chunk.isUnsaved(), "net-zero buffer concealed changed battery from persistence");
        var restored = new WindGenBaseBlockEntity(wind.getBlockPos(), wind.getBlockState());
        restored.loadWithComponents(wind.saveWithoutMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        check(EnergyItemHelper.getEnergy(restored.getItems().getFirst()) == 15, "charged battery lost in NBT round trip");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fluidCapabilityMarksOnlyExecutedChangesAndRoundTrips(GameTestHelper h) {
        var phase = place(h, 2, AcademyBlocks.PHASE_GEN.get(), PhaseGenBlockEntity.class);
        var level = h.getLevel();
        var fluid = level.getCapability(Capabilities.FluidHandler.BLOCK, phase.getBlockPos(), Direction.UP);
        check(fluid != null, "registered phase tank capability missing");
        var chunk = level.getChunkAt(phase.getBlockPos());
        chunk.setUnsaved(false);
        FluidStack liquid = new FluidStack(AcademyFluids.PHASE_LIQUID.get(), 1000);
        check(fluid.fill(liquid, FluidAction.SIMULATE) == 1000 && phase.getFluidAmount() == 0 && !chunk.isUnsaved(),
                "simulated tank fill mutated persistence or amount");
        check(fluid.fill(liquid, FluidAction.EXECUTE) == 1000 && chunk.isUnsaved(), "external tank fill was not persisted");
        chunk.setUnsaved(false);
        check(fluid.drain(100, FluidAction.SIMULATE).getAmount() == 100 && !chunk.isUnsaved(), "simulated drain dirtied chunk");
        check(fluid.drain(100, FluidAction.EXECUTE).getAmount() == 100 && chunk.isUnsaved(), "external tank drain was not persisted");
        var restored = new PhaseGenBlockEntity(phase.getBlockPos(), phase.getBlockState());
        restored.loadWithComponents(phase.saveWithoutMetadata(level.registryAccess()), level.registryAccess());
        check(restored.getFluidAmount() == 900, "tank save/load lost the final external mutation");
        check(fluid.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 100), FluidAction.EXECUTE) == 0,
                "phase tank accepted wrong fluid");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fusorBatteryDoesNotLoseFractionalHeadroom(GameTestHelper h) {
        var fusor = place(h, 2, AcademyBlocks.IMAG_FUSOR.get(), ImagFusorBlockEntity.class);
        ItemStack battery = battery(100);
        fusor.getItems().set(ImagFusorBlockEntity.ENERGY_INPUT_SLOT, battery);
        fusor.injectEnergy(1999.75);
        fusor.tick();
        check(EnergyItemHelper.getEnergy(battery) == 100 && near(fusor.getRequiredEnergy(), .25),
                "fractional room consumed a whole battery unit");
        fusor.pullEnergy(1);
        fusor.tick();
        check(EnergyItemHelper.getEnergy(battery) == 99 && near(fusor.getRequiredEnergy(), .25),
                "available whole unit failed to transfer conservatively");
        var fluid = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, fusor.getBlockPos(), Direction.UP);
        var chunk = h.getLevel().getChunkAt(fusor.getBlockPos());
        chunk.setUnsaved(false);
        check(fluid != null && fluid.fill(new FluidStack(AcademyFluids.PHASE_LIQUID.get(), 250), FluidAction.EXECUTE) == 250
                && chunk.isUnsaved(), "fusor external tank mutation did not dirty save");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void metalFormerAutomationConservesOversizedAndNegativeStacks(GameTestHelper h) {
        var metal = place(h, 2, AcademyBlocks.METAL_FORMER.get(), MetalFomerBlockEntity.class);
        var handler = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, metal.getBlockPos(), Direction.UP);
        check(handler != null, "metal former item capability missing");
        ItemStack oversized = new ItemStack(Items.IRON_INGOT, 80);
        check(handler.insertItem(0, oversized, true).getCount() == 16 && metal.getItems().get(0).isEmpty(),
                "oversized simulation changed slot or wrong remainder");
        check(handler.insertItem(0, oversized, false).getCount() == 16 && metal.getItems().get(0).getCount() == 64,
                "empty input slot swallowed more than its stack limit");
        var all = metal.getHandlerForSide(null);
        check(all.extractItem(0, -1, false).isEmpty() && metal.getItems().get(0).getCount() == 64,
                "negative extraction grew inventory");
        check(all.extractItem(0, 4, true).getCount() == 4 && metal.getItems().get(0).getCount() == 64,
                "extraction simulation consumed inventory");
        check(all.extractItem(0, 4, false).getCount() == 4 && metal.getItems().get(0).getCount() == 60,
                "valid extraction failed positive control");
        check(all.getStackInSlot(-1).isEmpty() && all.extractItem(99, 1, false).isEmpty(), "invalid automation slot accepted");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void metalRecipeCountAndOutputComponentsAreRespected(GameTestHelper h) throws Exception {
        var recipe = new MetalFormingRecipe(Mode.PLATE, Ingredient.of(Items.IRON_INGOT), 2, new ItemStack(Items.IRON_NUGGET));
        check(!recipe.matches(new MetalFormingRecipeInput(new ItemStack(Items.IRON_INGOT), Mode.PLATE), h.getLevel()),
                "recipe accepted fewer than required inputs");
        check(recipe.matches(new MetalFormingRecipeInput(new ItemStack(Items.IRON_INGOT, 2), Mode.PLATE), h.getLevel()),
                "recipe rejected exact count positive control");
        var metal = place(h, 2, AcademyBlocks.METAL_FORMER.get(), MetalFomerBlockEntity.class);
        metal.getItems().set(0, new ItemStack(Items.IRON_INGOT, 2));
        ItemStack renamed = new ItemStack(Items.IRON_NUGGET);
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("separate output"));
        metal.getItems().set(1, renamed);
        var current = MetalFomerBlockEntity.class.getDeclaredField("current");
        current.setAccessible(true);
        current.set(metal, recipe);
        check(metal.isActionBlocked(), "machine would merge incompatible output components");
        metal.getItems().set(1, new ItemStack(Items.IRON_NUGGET));
        check(!metal.isActionBlocked(), "identical output positive control blocked");
        // A cached job no longer present in RecipeManager must be discarded before debit.
        metal.injectEnergy(100);
        metal.serverTick();
        check(metal.getCurrentRecipe() == null && near(metal.getEnergy(), 100) && metal.getItems().get(0).getCount() == 2,
                "removed recipe kept executing or consuming energy");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bridgeCacheInvalidatesReplacementAndClearsOnUnload(GameTestHelper h) throws Exception {
        var output = place(h, 2, AcademyBlocks.RF_OUTPUT.get(), EnergyBridgeOutputBlockEntity.class);
        BlockPos target = output.getBlockPos().east();
        var level = h.getLevel();
        level.setBlock(target, AcademyBlocks.RF_INPUT.get().defaultBlockState(), 3);
        var first = (EnergyBridgeInputBlockEntity) level.getBlockEntity(target);
        output.receiveExternalFe(40, false);
        output.serverTick();
        check(first.getStoredFe() == 40 && output.getStoredFe() == 0, "cached neighbor transfer positive control failed");
        level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
        output.receiveExternalFe(40, false);
        output.serverTick();
        check(output.getStoredFe() == 40 && first.getStoredFe() == 40, "removed neighbor received energy through stale cache");
        level.setBlock(target, AcademyBlocks.RF_INPUT.get().defaultBlockState(), 3);
        var second = (EnergyBridgeInputBlockEntity) level.getBlockEntity(target);
        output.serverTick();
        check(second != first && second.getStoredFe() == 40 && output.getStoredFe() == 0, "replacement did not invalidate cached null");
        output.onChunkUnloaded();
        var field = EnergyBridgeOutputBlockEntity.class.getDeclaredField("neighbors");
        field.setAccessible(true);
        check(((Map<?, ?>) field.get(output)).isEmpty(), "unload retained neighbor cache owners");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void developerProxyInvalidatesCachedNullAndRejectsStaleViews(GameTestHelper h) {
        var main = place(h, 2, AcademyBlocks.DEV_NORMAL.get(), DevNormalBlockEntity.class);
        var block = (DevMachineBase) main.getBlockState().getBlock();
        var offset = block.getRotatedSubBlocks(main.getBlockState().getValue(DevMachineBase.FACING).getOpposite()).getFirst();
        BlockPos subPos = main.getBlockPos().offset(offset.dx(), offset.dy(), offset.dz());
        h.getLevel().setBlock(subPos, AcademyBlocks.DEV_NORMAL_SUB.get().defaultBlockState(), 3);
        var sub = (DevNormalSubBlockEntity) h.getLevel().getBlockEntity(subPos);
        var cache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, h.getLevel(), subPos, Direction.UP);
        check(cache.getCapability() == null, "unconfigured proxy exposed energy");
        UUID id = UUID.randomUUID();
        main.setStructureId(id);
        sub.setStructureId(id);
        sub.setMainPos(main.getBlockPos());
        var view = cache.getCapability();
        check(view != null && view.receiveEnergy(40, false) == 40 && main.getEnergyStored() == 10,
                "proxy identity completion failed to invalidate cached null");
        sub.setStructureId(UUID.randomUUID());
        check(view.receiveEnergy(40, false) == 0 && main.getEnergyStored() == 10 && cache.getCapability() == null,
                "stale proxy view still modified old main");
        sub.setStructureId(id);
        check(cache.getCapability() != null, "restored valid proxy remained unavailable");
        h.getLevel().destroyBlock(main.getBlockPos(), false);
        check(view.receiveEnergy(40, false) == 0, "removed developer accepted energy");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void wirelessQueriesNeverLoadOfflineChunks(GameTestHelper h) {
        BlockPos remote = new BlockPos(20_000_000, 100, 20_000_000);
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", remote.getX()); tag.putInt("y", remote.getY()); tag.putInt("z", remote.getZ());
        check(!h.getLevel().isLoaded(remote), "remote negative fixture unexpectedly loaded");
        check(new VBlocks.VWMatrix(tag).get(h.getLevel()) == null
                && new VBlocks.VNNode(tag).get(h.getLevel()) == null
                && new VBlocks.VNGenerator(tag).get(h.getLevel()) == null
                && new VBlocks.VNReceiver(tag).get(h.getLevel()) == null
                && !h.getLevel().isLoaded(remote), "wireless registry query loaded an offline chunk");
        var solar = place(h, 2, AcademyBlocks.SOLAR_GEN.get(), SolarGenBlockEntity.class);
        check(new VBlocks.VNGenerator(solar).get(h.getLevel()) == solar, "loaded wireless endpoint positive control failed");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void wirelessChunkLifecycleSuspendsAndResumesRealTransfers(GameTestHelper h) {
        var node = place(h, 2, AcademyBlocks.NODE_BASIC.get(), NodeBasicBlockEntity.class);
        var solar = place(h, 3, AcademyBlocks.SOLAR_GEN.get(), SolarGenBlockEntity.class);
        var level = h.getLevel();
        check(WirelessSystem.linkGenerator(level, node, solar, false, ""), "real wireless link fixture failed");
        var data = WiWorldData.get(level);
        var chunk = level.getChunkAt(node.getBlockPos());
        solar.setEnergy(300);
        data.tick();
        check(node.getEnergy() > 0 && solar.getEnergyStored() < 300, "loaded transfer positive control failed");
        double beforeNode = node.getEnergy();
        int beforeSolar = solar.getEnergyStored();
        data.setDirty(false);
        WirelessSystem.onChunkUnload(new ChunkEvent.Unload(chunk));
        try {
            data.tick();
            check(node.getEnergy() == beforeNode && solar.getEnergyStored() == beforeSolar,
                    "unloaded owner remained in active transfer set");
            check(!new WiWorldData().isDirty(), "unchanged wireless graph is always dirty");
        } finally { WirelessSystem.onChunkLoad(new ChunkEvent.Load(chunk, false)); }
        data.tick();
        check(node.getEnergy() > beforeNode && solar.getEnergyStored() < beforeSolar, "reloaded owner failed to resume per-tick transfer");
        data.setDirty(false);
        check(WirelessSystem.unlinkUser(level, solar) && data.isDirty(), "topology mutation was not marked for save");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void energyMutationsStayOutOfFullBlockBroadcastQueue(GameTestHelper h) throws Exception {
        var dev = place(h, 2, AcademyBlocks.DEV_NORMAL.get(), DevNormalBlockEntity.class);
        var level = h.getLevel();
        var chunk = level.getChunkAt(dev.getBlockPos());
        var holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(chunk.getPos().toLong());
        check(holder != null && holder.getTickingChunk() != null, "packet budget needs a ticking chunk");
        holder.broadcastChanges(chunk);
        var changed = net.minecraft.server.level.ChunkHolder.class.getDeclaredField("changedBlocksPerSection");
        changed.setAccessible(true);
        level.sendBlockUpdated(dev.getBlockPos(), dev.getBlockState(), dev.getBlockState(), Block.UPDATE_CLIENTS);
        check(queued(changed, holder, dev.getBlockPos(), chunk.getSectionIndex(dev.getBlockPos().getY())),
                "broadcast-queue observation positive control failed");
        holder.broadcastChanges(chunk);
        chunk.setUnsaved(false);
        for (int i = 1; i <= 200; i++) dev.setEnergy(i);
        check(chunk.isUnsaved(), "real energy mutation was not marked for persistence");
        check(!queued(changed, holder, dev.getBlockPos(), chunk.getSectionIndex(dev.getBlockPos().getY())),
                "200 energy mutations queued full block-entity broadcasts");
        var restored = new DevNormalBlockEntity(dev.getBlockPos(), dev.getBlockState());
        restored.loadWithComponents(dev.saveWithoutMetadata(level.registryAccess()), level.registryAccess());
        check(restored.getEnergyStored() == 200, "coalesced dirty state lost final energy on save");
        chunk.setUnsaved(false);
        dev.setEnergy(200);
        check(!chunk.isUnsaved(), "no-op energy assignment dirtied the chunk");
        chunk.setUnsaved(true); // Preserve test placement for the normal server cleanup/save path.
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void persistedWirelessIndexRebuildsWithDormantOwners(GameTestHelper h) throws Exception {
        var node = place(h, 2, AcademyBlocks.NODE_BASIC.get(), NodeBasicBlockEntity.class);
        var solar = place(h, 3, AcademyBlocks.SOLAR_GEN.get(), SolarGenBlockEntity.class);
        check(WirelessSystem.linkGenerator(h.getLevel(), node, solar, false, ""), "saved graph fixture link failed");
        var live = WiWorldData.get(h.getLevel());
        CompoundTag saved = live.save(new CompoundTag(), h.getLevel().registryAccess());
        var restored = new WiWorldData();
        var load = WiWorldData.class.getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        load.invoke(restored, saved, h.getLevel().registryAccess());
        var bind = WiWorldData.class.getDeclaredMethod("bindLevel", net.minecraft.server.level.ServerLevel.class);
        bind.setAccessible(true);
        bind.invoke(restored, h.getLevel());
        check(restored.getNodeConnection(solar) != null && restored.getNodeConnection(solar).getNode() == node,
                "saved topology did not restore owner and generator lookup");
        solar.setEnergy(300);
        double before = node.getEnergy();
        restored.tick();
        check(node.getEnergy() > before && solar.getEnergyStored() < 300, "restored active index failed to transfer");
        // An offline owner is indexed without resolving its BE or creating a chunk.
        CompoundTag position = new CompoundTag();
        position.putInt("x", 20_000_000); position.putInt("y", 100); position.putInt("z", 20_000_000);
        CompoundTag dormant = new CompoundTag();
        dormant.put("node", position);
        dormant.put("receivers", new net.minecraft.nbt.ListTag());
        dormant.put("generators", new net.minecraft.nbt.ListTag());
        var list = new net.minecraft.nbt.ListTag(); list.add(dormant);
        CompoundTag nodes = new CompoundTag(); nodes.put("list", list);
        CompoundTag offlineSave = new CompoundTag(); offlineSave.put("node", nodes);
        var offline = new WiWorldData();
        load.invoke(offline, offlineSave, h.getLevel().registryAccess());
        bind.invoke(offline, h.getLevel());
        var active = WiWorldData.class.getDeclaredField("activeNodes"); active.setAccessible(true);
        check(((java.util.Set<?>) active.get(offline)).isEmpty(), "offline owner entered the per-tick work set");
        offline.tick();
        check(!h.getLevel().isLoaded(new BlockPos(20_000_000, 100, 20_000_000)), "restoring offline graph loaded its chunk");
        h.succeed();
    }

    private static boolean queued(java.lang.reflect.Field field, Object holder, BlockPos pos, int section) throws Exception {
        var sections = (it.unimi.dsi.fastutil.shorts.ShortSet[]) field.get(holder);
        return sections[section] != null && sections[section].contains(SectionPos.sectionRelativePos(pos));
    }

    private static <T extends BlockEntity> T place(GameTestHelper h, int x, Block block, Class<T> type) {
        BlockPos pos = h.absolutePos(new BlockPos(x, 2, 2));
        h.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        return type.cast(h.getLevel().getBlockEntity(pos));
    }

    private static ItemStack battery(int energy) {
        ItemStack stack = new ItemStack(AcademyItems.ENERGY_UNIT.get());
        EnergyItemHelper.setEnergy(stack, energy); // fixture fill, not a bandwidth-limited charge
        return stack;
    }

    private static boolean near(double a, double b) { return Math.abs(a - b) < .0001; }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
