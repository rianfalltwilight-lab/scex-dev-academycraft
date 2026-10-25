package com.mohistmc.academy.regression;

import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.capability.IEnergyItem;
import com.mohistmc.academy.gametest.recheck.RecheckPlayers;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.item.AcademyDataComponents;
import com.mohistmc.academy.world.item.ExtraItemData;
import com.mohistmc.academy.world.item.LaserGunItem;
import com.mohistmc.academy.world.item.LaserMiningSessions;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native ItemStack codecs, actual FE capabilities and live laser world actions. */
@GameTestHolder("academy_data_sync")
@PrefixGameTestTemplate(false)
public final class NativeItemDataGameTests {
    private static final List<String> DEVICES = List.of("energy_unit", "developer_portable", "ray_twister",
            "energy_unit_group", "electricalibur", "avalon", "lasor_gun", "air_jet", "teleporter", "drop_item_magnet");
    private static final List<String> ARMOR = List.of("imag_helmet", "imag_chestplate", "imag_leggings", "imag_boots");

    @GameTest(template = "empty", batch = "native_item_data")
    public static void legacyDamageEmptyHalfFullMigratesThroughNativeStackCodec(GameTestHelper h) {
        for (String id : DEVICES) verifyLegacyEnergy(h, id, false);
        // Imaginary armour also accepted the older durability encoding before its CUSTOM_DATA format.
        for (String id : ARMOR) verifyLegacyEnergy(h, id, false);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "native_item_data")
    public static void legacyArmorNbtEmptyHalfFullMigratesWithoutLosingForeignData(GameTestHelper h) {
        for (String id : ARMOR) verifyLegacyEnergy(h, id, true);
        h.succeed();
    }

    private static void verifyLegacyEnergy(GameTestHelper h, String id, boolean nbtEnergy) {
        ItemStack fresh = item(id);
        h.assertTrue(fresh.getItem() instanceof IEnergyItem, "Not an IF item: " + id);
        int capacity = ((IEnergyItem) fresh.getItem()).getMaxEnergyStored(fresh);
        h.assertTrue(EnergyItemHelper.getEnergy(fresh) == 0, "Fresh stack started charged: " + id);
        // 0.0.30's default empty charge was omitted from the saved component patch.
        CompoundTag omittedDefault = legacy(id, capacity, null);
        omittedDefault.getCompound("components").remove("minecraft:damage");
        assertEnergy(h, ItemStack.parseOptional(h.getLevel().registryAccess(), omittedDefault), 0,
                id + " omitted legacy default");
        for (int amount : new int[] {0, capacity / 2, capacity}) {
            CompoundTag saved = legacy(id, capacity - amount, nbtEnergy ? amount : null);
            ItemStack loaded = ItemStack.parseOptional(h.getLevel().registryAccess(), saved);
            assertEnergy(h, loaded, amount, id + " legacy decode");
            CompoundTag persisted = (CompoundTag) loaded.save(h.getLevel().registryAccess());
            ItemStack reloaded = ItemStack.parseOptional(h.getLevel().registryAccess(), persisted);
            assertEnergy(h, reloaded, amount, id + " saved reload");
            var fe = reloaded.getCapability(Capabilities.EnergyStorage.ITEM);
            h.assertTrue(fe != null && fe.getEnergyStored() == amount * 4 && fe.getMaxEnergyStored() == capacity * 4,
                    "4 FE = 1 IF capability mismatch: " + id);
            CompoundTag before = (CompoundTag) reloaded.save(h.getLevel().registryAccess());
            fe.receiveEnergy(80, true);
            fe.extractEnergy(80, true);
            h.assertTrue(before.equals(reloaded.save(h.getLevel().registryAccess())), "simulate mutated components: " + id);
            h.assertTrue(reloaded.getBarWidth() == Math.round(13F * amount / capacity)
                    && reloaded.isBarVisible() == (amount < capacity), "Charge bar changed: " + id);
        }
    }

    @GameTest(template = "empty", batch = "native_item_data")
    public static void nativeEnergyWinsOverBothLegacyStoresAndRemovesOnlyOwnedKeys(GameTestHelper h) {
        CompoundTag saved = legacy("energy_unit", 123, 456);
        saved.getCompound("components").putInt("academy:energy", 789);
        ItemStack loaded = ItemStack.parseOptional(h.getLevel().registryAccess(), saved);
        assertEnergy(h, loaded, 789, "Typed precedence");
        CompoundTag data = loaded.get(DataComponents.CUSTOM_DATA).copyTag();
        h.assertTrue(!data.contains("academy_extra_energy") && data.getInt("othermod:unchanged") == 73,
                "Migration removed foreign data or kept obsolete charge");
        // The old armour CUSTOM_DATA value takes precedence over its even older DAMAGE fallback.
        loaded = ItemStack.parseOptional(h.getLevel().registryAccess(), legacy(ARMOR.getFirst(), 100, 456));
        assertEnergy(h, loaded, 456, "Legacy precedence");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "native_item_data")
    public static void energyAndBindingSurviveNativeWireCopySplitAndSave(GameTestHelper h) {
        ItemStack stack = item("teleporter");
        EnergyItemHelper.setEnergy(stack, 6754);
        ExtraItemData.setTeleport(stack, Level.OVERWORLD.location(), 100.25, 72.5, -300.75);
        var target = ExtraItemData.teleport(stack);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, stack);
            ItemStack client = ItemStack.STREAM_CODEC.decode(buffer);
            h.assertTrue(EnergyItemHelper.getEnergy(client) == 6754 && target.equals(ExtraItemData.teleport(client)),
                    "Native stack network codec lost typed item values");
            h.assertTrue(ItemStack.isSameItemSameComponents(stack, client), "Native stack components disagree after wire roundtrip");
        } finally { buffer.release(); }
        ItemStack copy = stack.copy();
        ItemStack split = copy.split(1);
        h.assertTrue(copy.isEmpty() && EnergyItemHelper.getEnergy(split) == 6754
                && target.equals(ExtraItemData.teleport(split)), "Copy/split lost energy or bound coordinates");
        EnergyItemHelper.setEnergy(split, 100);
        ExtraItemData.clearTeleport(split);
        h.assertTrue(EnergyItemHelper.getEnergy(stack) == 6754 && target.equals(ExtraItemData.teleport(stack)),
                "Copied components shared mutable state");
        ItemStack reload = ItemStack.parseOptional(h.getLevel().registryAccess(), (CompoundTag) stack.save(h.getLevel().registryAccess()));
        h.assertTrue(EnergyItemHelper.getEnergy(reload) == 6754 && target.equals(ExtraItemData.teleport(reload)),
                "Native save/reload lost binding or charge");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "native_item_data")
    public static void legacyBindingMigratesAndClearCannotResurrectIt(GameTestHelper h) {
        CompoundTag saved = legacy("teleporter", 5432, null);
        CompoundTag data = saved.getCompound("components").getCompound("minecraft:custom_data");
        data.putBoolean("academy_extra_teleport_set", true);
        data.putString("academy_extra_teleport_dimension", "minecraft:overworld");
        data.putDouble("academy_extra_teleport_x", 12.5);
        data.putDouble("academy_extra_teleport_y", 72);
        data.putDouble("academy_extra_teleport_z", -8.25);
        ItemStack stack = ItemStack.parseOptional(h.getLevel().registryAccess(), saved);
        h.assertTrue(new ExtraItemData.TeleportTarget(Level.OVERWORLD.location(), 12.5, 72, -8.25)
                .equals(stack.get(AcademyDataComponents.TELEPORT_TARGET.get())), "Legacy binding did not migrate");
        h.assertTrue(stack.get(DataComponents.CUSTOM_DATA).copyTag().getInt("othermod:unchanged") == 73,
                "Binding migration lost foreign NBT");
        ExtraItemData.clearTeleport(stack);
        ItemStack loaded = ItemStack.parseOptional(h.getLevel().registryAccess(), (CompoundTag) stack.save(h.getLevel().registryAccess()));
        h.assertTrue(ExtraItemData.teleport(loaded) == null, "Cleared binding resurrected through legacy fallback");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "native_item_data")
    public static void laserSavedAndCopiedStacksCannotCarryMiningProgress(GameTestHelper h) {
        try (var fixture = RecheckPlayers.connect(h)) {
            ServerPlayer player = fixture.player();
            ItemStack stack = laser(player);
            BlockPos target = ore(h, player);
            mine(player, stack, 14);
            h.assertTrue(h.getLevel().getBlockState(target).is(Blocks.IRON_ORE), "Prime broke the ore too early");
            CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            h.assertTrue(!custom.contains("academy_extra_harvest_pos") && !custom.contains("academy_extra_harvest_progress")
                    && !custom.contains("academy_extra_harvest_state"), "Mining wrote transient state to the item");
            ItemStack replacement = stack.copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, replacement);
            mine(player, replacement, 1);
            h.assertTrue(h.getLevel().getBlockState(target).is(Blocks.IRON_ORE), "Copied laser inherited mining progress");
            // Native persistence is another new stack identity, even with identical components.
            replacement = ItemStack.parseOptional(h.getLevel().registryAccess(), (CompoundTag) replacement.save(h.getLevel().registryAccess()));
            player.setItemInHand(InteractionHand.MAIN_HAND, replacement);
            mine(player, replacement, 14);
            h.assertTrue(h.getLevel().getBlockState(target).is(Blocks.IRON_ORE), "Reloaded laser inherited mining progress");
            mine(player, replacement, 1);
            h.assertTrue(h.getLevel().getBlockState(target).isAir(), "Fresh continuous mining no longer harvests ore");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "native_item_data")
    public static void laserTransferredToAnotherPlayerStartsFresh(GameTestHelper h) {
        try (var first = RecheckPlayers.connect(h); var second = RecheckPlayers.connect(h)) {
            ItemStack stack = laser(first.player());
            BlockPos target = ore(h, first.player());
            mine(first.player(), stack, 14);
            first.player().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            second.player().setItemInHand(InteractionHand.MAIN_HAND, stack);
            mine(second.player(), stack, 1);
            h.assertTrue(h.getLevel().getBlockState(target).is(Blocks.IRON_ORE), "Transferred laser inherited another player's progress");
            mine(second.player(), stack, 14);
            h.assertTrue(h.getLevel().getBlockState(target).isAir(), "New owner's fresh mining failed");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "native_item_data")
    public static void laserHotbarChangeCannotResumePreviousProgress(GameTestHelper h) {
        try (var fixture = RecheckPlayers.connect(h)) {
            ServerPlayer player = fixture.player();
            ItemStack stack = laser(player);
            BlockPos target = ore(h, player);
            mine(player, stack, 14);
            int selected = player.getInventory().selected;
            player.getInventory().selected = (selected + 1) % 9;
            LaserMiningSessions.tick(new PlayerTickEvent.Post(player));
            player.getInventory().selected = selected;
            mine(player, stack, 1);
            h.assertTrue(h.getLevel().getBlockState(target).is(Blocks.IRON_ORE), "Hotbar roundtrip inherited mining progress");
            mine(player, stack, 14);
            h.assertTrue(h.getLevel().getBlockState(target).isAir(), "Resumed fresh mining failed");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "native_item_data")
    public static void laserDimensionRoundtripCannotResumePreviousProgress(GameTestHelper h) {
        try (var fixture = RecheckPlayers.connect(h)) {
            ServerPlayer player = fixture.player();
            ItemStack stack = laser(player);
            BlockPos target = ore(h, player);
            mine(player, stack, 14);
            var origin = player.position();
            var other = player.server.getLevel(Level.NETHER);
            h.assertTrue(other != null && player.teleportTo(other, origin.x, origin.y, origin.z, Set.of(), 0, 0),
                    "Real dimension transition failed");
            h.assertTrue(player.teleportTo(h.getLevel(), origin.x, origin.y, origin.z, Set.of(), 0, 0),
                    "Return dimension transition failed");
            mine(player, stack, 1);
            h.assertTrue(h.getLevel().getBlockState(target).is(Blocks.IRON_ORE), "Dimension roundtrip inherited mining progress");
            mine(player, stack, 14);
            h.assertTrue(h.getLevel().getBlockState(target).isAir(), "Fresh mining after dimension transition failed");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "native_item_data")
    public static void legacyHarvestProgressIsDiscardedWithoutDroppingForeignNbt(GameTestHelper h) {
        CompoundTag saved = legacy("lasor_gun", 1000, null);
        CompoundTag data = saved.getCompound("components").getCompound("minecraft:custom_data");
        data.putLong("academy_extra_harvest_pos", BlockPos.ZERO.asLong());
        data.putFloat("academy_extra_harvest_progress", 1000);
        data.putString("academy_extra_harvest_state", "old-state");
        ItemStack stack = ItemStack.parseOptional(h.getLevel().registryAccess(), saved);
        CompoundTag actual = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        h.assertTrue(actual.getInt("othermod:unchanged") == 73 && actual.getAllKeys().stream()
                .noneMatch(key -> key.startsWith("academy_extra_harvest_")), "Legacy action state survived migration or foreign NBT was dropped");
        h.succeed();
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("academy", id)));
    }

    private static CompoundTag legacy(String id, int damage, Integer nbtEnergy) {
        CompoundTag saved = new CompoundTag();
        saved.putString("id", "academy:" + id);
        saved.putInt("count", 1);
        CompoundTag components = new CompoundTag();
        components.putInt("minecraft:damage", damage);
        CompoundTag data = new CompoundTag();
        data.putInt("othermod:unchanged", 73);
        if (nbtEnergy != null) data.putInt("academy_extra_energy", nbtEnergy);
        components.put("minecraft:custom_data", data);
        saved.put("components", components);
        return saved;
    }

    private static void assertEnergy(GameTestHelper h, ItemStack stack, int expected, String label) {
        h.assertTrue(!stack.isEmpty() && EnergyItemHelper.getEnergy(stack) == expected
                && stack.getOrDefault(AcademyDataComponents.ENERGY.get(), -1) == expected, label + " energy changed");
        h.assertTrue(!stack.has(DataComponents.DAMAGE) && !stack.has(DataComponents.MAX_DAMAGE)
                && !stack.isDamageableItem(), label + " kept fake equipment wear");
        h.assertTrue(stack.get(DataComponents.CUSTOM_DATA).copyTag().getInt("othermod:unchanged") == 73,
                label + " removed foreign CUSTOM_DATA");
    }

    private static ItemStack laser(ServerPlayer player) {
        ItemStack stack = new ItemStack(AcademyItems.LASOR_GUN.get());
        EnergyItemHelper.setEnergy(stack, 1000);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.use(player.level(), player, InteractionHand.MAIN_HAND);
        return stack;
    }

    private static BlockPos ore(GameTestHelper h, ServerPlayer player) {
        BlockPos target = player.blockPosition().south(3).above();
        h.getLevel().setBlock(target, Blocks.IRON_ORE.defaultBlockState(), 3);
        return target;
    }

    private static void mine(ServerPlayer player, ItemStack stack, int ticks) {
        for (int tick = 0; tick < ticks; tick++)
            ((LaserGunItem) stack.getItem()).inventoryTick(stack, player.level(), player, player.getInventory().selected, true);
    }
}
