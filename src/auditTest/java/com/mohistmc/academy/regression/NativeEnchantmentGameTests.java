package com.mohistmc.academy.regression;

import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.world.AcademyItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real 1.21.1 menus, loaded vanilla enchantment tags and ordinary survival players. */
@GameTestHolder("academy_native_compat")
@PrefixGameTestTemplate(false)
public final class NativeEnchantmentGameTests {
    private record ArmorCase(Item item, EquipmentSlot slot,
                             ResourceKey<Enchantment> allowed, ResourceKey<Enchantment> wrongPart) {}

    private static ArmorCase[] resonance() {
        return new ArmorCase[]{
                new ArmorCase(AcademyItems.RESO_HELMET.get(), EquipmentSlot.HEAD,
                        Enchantments.RESPIRATION, Enchantments.SWIFT_SNEAK),
                new ArmorCase(AcademyItems.RESO_CHESTPLATE.get(), EquipmentSlot.CHEST,
                        Enchantments.THORNS, Enchantments.RESPIRATION),
                new ArmorCase(AcademyItems.RESO_LEGGINGS.get(), EquipmentSlot.LEGS,
                        Enchantments.SWIFT_SNEAK, Enchantments.FEATHER_FALLING),
                new ArmorCase(AcademyItems.RESO_BOOTS.get(), EquipmentSlot.FEET,
                        Enchantments.FEATHER_FALLING, Enchantments.RESPIRATION)
        };
    }

    @GameTest(template = "empty")
    public static void resonanceArmorUsesRealEnchantingTable(GameTestHelper h) {
        var player = FollowupGameTests.player(h, AbilityCategory.TELEKINESIS);
        try {
            for (ArmorCase armor : resonance()) {
                var menu = enchantingMenu(h, player);
                try {
                    ItemStack original = new ItemStack(armor.item());
                    h.assertTrue(original.isEnchantable() && !original.isDamageableItem(),
                            "Resonance armour must be enchantable without introducing durability");
                    menu.getSlot(0).set(original);
                    menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 16));
                    h.assertTrue(menu.costs[2] == 30 && menu.enchantClue[2] >= 0,
                            "Real table did not offer a level-30 enchantment for " + armor.item());
                    int beforeLevels = player.experienceLevel;
                    h.assertTrue(menu.clickMenuButton(player, 2), "Real enchanting action was rejected");
                    ItemStack result = menu.getSlot(0).getItem();
                    h.assertTrue(result.is(armor.item()) && result.getCount() == 1 && result.isEnchanted(),
                            "Real table action did not enchant the original armour");
                    h.assertTrue(player.experienceLevel == beforeLevels - 3
                                    && menu.getSlot(1).getItem().getCount() == 13,
                            "Survival table action did not consume exactly three levels and lapis");
                    h.assertTrue(result.getEnchantments().keySet().stream().allMatch(result::isPrimaryItemFor),
                            "Table applied an enchantment outside this armour part's primary-item tags");
                    h.assertTrue(!result.has(DataComponents.MAX_DAMAGE) && !result.isDamageableItem(),
                            "Enchanting introduced durability");
                } finally { close(player, menu); }
            }
            h.succeed();
        } finally { player.server.getPlayerList().remove(player); }
    }

    @GameTest(template = "empty")
    public static void resonanceAnvilAcceptsCorrectPartsAndRejectsWrongParts(GameTestHelper h) {
        var player = FollowupGameTests.player(h, AbilityCategory.TELEKINESIS);
        try {
            for (ArmorCase armor : resonance()) {
                applyBook(h, player, armor.item(), armor.allowed(), true);
                applyBook(h, player, armor.item(), armor.wrongPart(), false);
                applyBook(h, player, armor.item(), Enchantments.SHARPNESS, false);
            }
            h.succeed();
        } finally { player.server.getPlayerList().remove(player); }
    }

    @GameTest(template = "empty")
    public static void enchantedResonanceRoundTripsAndNeverWears(GameTestHelper h) {
        var player = FollowupGameTests.player(h, AbilityCategory.TELEKINESIS);
        // Ordinary ServerPlayer login grants 60 ticks of damage immunity. Wait for
        // its real tick countdown so generic damage reaches the native armour path.
        h.runAfterDelay(65, () -> {
          try {
            Holder<Enchantment> protection = enchantment(h, Enchantments.PROTECTION);
            for (ArmorCase armor : resonance()) {
                ItemStack enchanted = applyBook(h, player, armor.item(), Enchantments.PROTECTION, true);
                ItemStack loaded = ItemStack.parse(h.getLevel().registryAccess(),
                        enchanted.save(h.getLevel().registryAccess())).orElseThrow();
                h.assertTrue(ItemStack.isSameItemSameComponents(enchanted, loaded)
                                && loaded.getEnchantmentLevel(protection) == 1,
                        "Enchanted armour components did not survive native ItemStack save/load");
                h.assertTrue(!loaded.has(DataComponents.MAX_DAMAGE) && !loaded.isDamageableItem(),
                        "Save/load introduced a durability component");
                player.setItemSlot(armor.slot(), loaded);
                loaded.hurtAndBreak(10_000, player, armor.slot());
                h.assertTrue(loaded.getCount() == 1 && loaded.getDamageValue() == 0
                                && loaded.getEnchantmentLevel(protection) == 1,
                        "Native durability damage wore or destroyed enchanted resonance armour");
            }
            float beforeHealth = player.getHealth();
            h.assertTrue(player.hurt(player.damageSources().generic(), 8)
                            && player.getHealth() < beforeHealth,
                    "Survival armour-wear check did not deliver real damage");
            for (ArmorCase armor : resonance()) {
                ItemStack worn = player.getItemBySlot(armor.slot());
                h.assertTrue(worn.is(armor.item()) && worn.getCount() == 1 && worn.getDamageValue() == 0
                                && worn.getEnchantmentLevel(protection) == 1,
                        "Actual incoming damage wore resonance armour or lost its enchantment");
            }
            h.succeed();
          } finally { player.server.getPlayerList().remove(player); }
        });
    }

    @GameTest(template = "empty")
    public static void paperAndImaginaryArmorStayBlockedInNativeMenus(GameTestHelper h) {
        var player = FollowupGameTests.player(h, AbilityCategory.TELEKINESIS);
        Item[] blocked = {AcademyItems.PAPER_HELMET.get(), AcademyItems.PAPER_CHESTPLATE.get(),
                AcademyItems.PAPER_LEGGINGS.get(), AcademyItems.PAPER_BOOTS.get(),
                AcademyItems.IMAG_HELMET.get(), AcademyItems.IMAG_CHESTPLATE.get(),
                AcademyItems.IMAG_LEGGINGS.get(), AcademyItems.IMAG_BOOTS.get()};
        try {
            for (Item item : blocked) {
                player.setGameMode(GameType.SURVIVAL);
                var menu = enchantingMenu(h, player);
                try {
                    menu.getSlot(0).set(new ItemStack(item));
                    menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 16));
                    for (int option = 0; option < 3; option++) {
                        h.assertTrue(menu.costs[option] == 0 && menu.enchantClue[option] == -1
                                        && !menu.clickMenuButton(player, option),
                                "Special armour unexpectedly accepted a table option: " + item);
                    }
                    h.assertTrue(!menu.getSlot(0).getItem().isEnchanted()
                                    && menu.getSlot(1).getItem().getCount() == 16
                                    && player.experienceLevel == 100,
                            "Rejected table action mutated special armour or spent resources");
                } finally { close(player, menu); }
                applyBook(h, player, item, Enchantments.PROTECTION, false);
                // Vanilla creative mode bypasses supported-item tags. The item's explicit
                // book veto must still preserve the add-on's prohibition in this path.
                player.setGameMode(GameType.CREATIVE);
                applyBook(h, player, item, Enchantments.PROTECTION, false);
            }
            h.succeed();
        } finally { player.server.getPlayerList().remove(player); }
    }

    private static Holder<Enchantment> enchantment(GameTestHelper h, ResourceKey<Enchantment> key) {
        return h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
    }

    private static ItemStack applyBook(GameTestHelper h, ServerPlayer player, Item item,
                                       ResourceKey<Enchantment> key, boolean allowed) {
        BlockPos pos = station(h, player);
        h.getLevel().setBlock(pos, Blocks.ANVIL.defaultBlockState(), 3);
        var menu = new AnvilMenu(81, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), pos));
        player.containerMenu = menu;
        Holder<Enchantment> enchantment = enchantment(h, key);
        var book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.updateEnchantments(book, values -> values.set(enchantment, 1));
        try {
            menu.getSlot(0).set(new ItemStack(item));
            menu.getSlot(1).set(book);
            menu.createResult();
            int cost = menu.getCost();
            int beforeLevels = player.experienceLevel;
            if (allowed) {
                h.assertTrue(!menu.getSlot(2).getItem().isEmpty() && cost > 0,
                        "Native anvil rejected supported enchantment " + key.location() + " on " + item);
                menu.clicked(2, 0, ClickType.PICKUP, player);
                ItemStack result = menu.getCarried().copy();
                h.assertTrue(result.is(item) && result.getCount() == 1
                                && result.getEnchantmentLevel(enchantment) == 1,
                        "Taking the anvil result did not apply its enchantment");
                h.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty()
                                && player.experienceLevel == beforeLevels - cost,
                        "Real survival anvil action did not consume exactly its inputs and level cost");
                return result;
            }
            h.assertTrue(menu.getSlot(2).getItem().isEmpty(),
                    "Native anvil accepted forbidden enchantment " + key.location() + " on " + item);
            menu.clicked(2, 0, ClickType.PICKUP, player);
            h.assertTrue(menu.getCarried().isEmpty() && menu.getSlot(0).getItem().getCount() == 1
                            && menu.getSlot(1).getItem().getCount() == 1 && player.experienceLevel == beforeLevels,
                    "Rejected anvil action consumed an input or awarded an output");
            return ItemStack.EMPTY;
        } finally { close(player, menu); }
    }

    private static BlockPos station(GameTestHelper h, ServerPlayer player) {
        BlockPos pos = h.absolutePos(new BlockPos(4, 2, 4));
        player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        player.experienceLevel = 100;
        return pos;
    }

    private static EnchantmentMenu enchantingMenu(GameTestHelper h, ServerPlayer player) {
        BlockPos pos = station(h, player);
        for (BlockPos air : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 1, 1)))
            h.getLevel().setBlock(air, Blocks.AIR.defaultBlockState(), 3);
        for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS)
            h.getLevel().setBlock(pos.offset(offset), Blocks.BOOKSHELF.defaultBlockState(), 3);
        h.getLevel().setBlock(pos, Blocks.ENCHANTING_TABLE.defaultBlockState(), 3);
        var menu = new EnchantmentMenu(82, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), pos));
        player.containerMenu = menu;
        return menu;
    }

    private static void close(ServerPlayer player, AbstractContainerMenu menu) {
        menu.setCarried(ItemStack.EMPTY);
        menu.getSlot(0).set(ItemStack.EMPTY);
        menu.getSlot(1).set(ItemStack.EMPTY);
        menu.removed(player);
        player.containerMenu = player.inventoryMenu;
    }
}
