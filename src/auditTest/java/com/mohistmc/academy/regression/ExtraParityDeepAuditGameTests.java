package com.mohistmc.academy.regression;

import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.ability.telekinesis.PsychoSlamEffect;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.entity.ExtraPaperPlaneEntity;
import com.mohistmc.academy.world.entity.MagManipBlockEntity;
import com.mohistmc.academy.world.entity.MagManipTransactionData;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("academy_audit")
@PrefixGameTestTemplate(false)
public final class ExtraParityDeepAuditGameTests {
    private static ServerPlayer player(GameTestHelper h, AbilityCategory category) {
        var player = FollowupGameTests.player(h, category);
        player.setNoGravity(true);
        return player;
    }
    private static void close(ServerPlayer p) { p.server.getPlayerList().remove(p); }

    private static void carrierLanding(GameTestHelper h, int mode) {
        var p = player(h, AbilityCategory.ELECTROMASTER);
        var level = h.getLevel();
        var source = p.blockPosition().west(3);
        var landing = p.blockPosition().east(3);
        level.setBlock(source, AcademyBlocks.MAG_MANIP_ESCROW.get().defaultBlockState(), 3);
        level.setBlock(landing.below(), Blocks.BEDROCK.defaultBlockState(), 3);
        var carrier = new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(), level);
        carrier.setPos(Vec3.atBottomCenterOf(landing).add(0, .25, 0));
        carrier.initialize(p, Blocks.IRON_BLOCK.defaultBlockState(), source, null, null, 8);
        h.assertTrue(carrier.reserveForSpawn() && carrier.commitSpawnReservation() && level.addFreshEntity(carrier), "Carrier setup failed");
        int[] events = {0};
        Consumer<BlockEvent.EntityPlaceEvent> callback = event -> {
            if (event.getEntity() != p) return;
            events[0]++;
            if (mode == 0) carrier.recoverMaterial();
            if (mode == 1) event.setCanceled(true);
            if (mode == 2) level.setBlock(event.getPos(), Blocks.BEDROCK.defaultBlockState(), 3);
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, callback);
        try {
            carrier.throwFrom(p, carrier.position().add(0, -2, 0), 1);
            carrier.tick();
            h.assertTrue(events[0] > 0, "Fixture did not fire real placement event");
            var ledger = MagManipTransactionData.get(level);
            if (mode == 0) {
                h.assertTrue(carrier.isRemoved() && ledger.inspect(carrier.transactionId()) == null, "Successful landing did not settle");
                h.assertTrue(level.getBlockState(landing).is(Blocks.IRON_BLOCK), "Reentrant callback lost destination");
                h.assertTrue(level.getBlockState(source).isAir(), "Reentrant recovery duplicated source material");
            } else {
                h.assertTrue(carrier.isAlive() && ledger.inspect(carrier.transactionId()) != null, "Denied/replaced placement lost the material receipt");
                h.assertTrue(!level.getBlockState(landing).is(Blocks.IRON_BLOCK), "Denied placement leaked an iron block");
                if (mode == 2) h.assertTrue(level.getBlockState(landing).is(Blocks.BEDROCK), "Rollback overwrote listener replacement");
            }
        } finally { NeoForge.EVENT_BUS.unregister(callback); }
        try {
            if (carrier.isAlive()) {
                // The replacement listener also replaced the source's attempted recovery.
                level.setBlock(source, AcademyBlocks.MAG_MANIP_ESCROW.get().defaultBlockState(), 3);
                carrier.recoverMaterial();
                h.assertTrue(carrier.isRemoved() && level.getBlockState(source).is(Blocks.IRON_BLOCK), "Material could not recover after listener removal");
                h.assertTrue(MagManipTransactionData.get(level).inspect(carrier.transactionId()) == null, "Recovered receipt remained live");
            }
            h.succeed();
        } finally { carrier.discard(); close(p); }
    }
    @GameTest(template="empty") public static void landingCallbackCannotRecoverMaterialTwice(GameTestHelper h) { carrierLanding(h, 0); }
    @GameTest(template="empty") public static void canceledLandingRetainsMaterialForRetry(GameTestHelper h) { carrierLanding(h, 1); }
    @GameTest(template="empty") public static void replacedLandingDoesNotSettleOrOverwriteCallback(GameTestHelper h) { carrierLanding(h, 2); }

    @GameTest(template="empty") public static void extraNumericSkillCommandsMatchUpstreamOrder(GameTestHelper h) {
        var p = player(h, AbilityCategory.AEROHAND);
        var data = p.getData(AcademyAttachments.PLAYER_ABILITY);
        try {
            var commands = p.server.getCommands().getDispatcher();
            var source = p.createCommandSourceStack().withPermission(4).withSuppressedOutput();
            commands.execute("aim learn #7", source);
            h.assertTrue(data.getLearnedSkills().contains("bomber_lance") && !data.getLearnedSkills().contains("brain_course"), "Aero #7 did not select original bomber lance");
            commands.execute("aim learn #10", source);
            h.assertTrue(data.getLearnedSkills().contains("aero_separator") && !data.getLearnedSkills().contains("storm_core"), "Aero #10 did not select original separator");
            data.setCurrentAbility(AbilityCategory.TELEKINESIS);
            commands.execute("aim learn #7", source);
            h.assertTrue(data.getLearnedSkills().contains("psycho_slam") && !data.getLearnedSkills().contains("brain_course"), "Telekinesis #7 did not select original slam");
            commands.execute("aim learn #11", source);
            h.assertTrue(data.getLearnedSkills().contains("brain_course"), "Telekinesis generic courses were not appended");
            h.succeed();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) { throw new AssertionError(exception); }
        finally { close(p); }
    }

    @GameTest(template="empty") public static void specialArmorRejectsBooksInRealAnvil(GameTestHelper h) {
        var p = player(h, AbilityCategory.TELEKINESIS);
        // Creative normally bypasses enchantment item tags; the explicit book veto must still win.
        p.setGameMode(GameType.CREATIVE);
        var pos = p.blockPosition().below();
        h.getLevel().setBlock(pos, Blocks.ANVIL.defaultBlockState(), 3);
        var protection = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PROTECTION);
        var book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.updateEnchantments(book, values -> values.set(protection, 1));
        try {
            for (var item : new net.minecraft.world.item.Item[]{Items.IRON_CHESTPLATE,
                    AcademyItems.PAPER_CHESTPLATE.get(), AcademyItems.IMAG_CHESTPLATE.get()}) {
                var menu = new AnvilMenu(81, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), pos));
                try {
                    menu.getSlot(0).set(new ItemStack(item)); menu.getSlot(1).set(book.copy()); menu.createResult();
                    var output = menu.getSlot(2).getItem();
                    if (item == Items.IRON_CHESTPLATE)
                        h.assertTrue(!output.isEmpty() && output.getEnchantmentLevel(protection) == 1, "Positive control did not produce enchanted armor");
                    else h.assertTrue(output.isEmpty(), "Special armor accepted an enchantment book");
                    h.assertTrue(menu.getSlot(0).getItem().getCount() == 1 && menu.getSlot(1).getItem().getCount() == 1, "Preview consumed an input");
                } finally { menu.getSlot(0).set(ItemStack.EMPTY); menu.getSlot(1).set(ItemStack.EMPTY); menu.removed(p); }
            }
            h.succeed();
        } finally { close(p); }
    }

    private static void paperFlight(GameTestHelper h, boolean denied) {
        var p = player(h, AbilityCategory.TELEKINESIS);
        var plane = new ExtraPaperPlaneEntity(AcademyEntities.PAPER_PLANE.get(), h.getLevel());
        plane.launch(p, false); plane.arm();
        var fragile = BlockPos.containing(plane.position()).south();
        h.getLevel().setBlock(fragile, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SHOVEL));
        // Fire through two blocks in one tick to prove the second hard wall is still traced.
        h.getLevel().setBlock(fragile.south(), Blocks.BEDROCK.defaultBlockState(), 3);
        plane.setDeltaMovement(0, 0, 3);
        h.getLevel().addFreshEntity(plane);
        int[] breaks = {0};
        Consumer<BlockEvent.BreakEvent> event = e -> { if (e.getPlayer() == p && e.getPos().equals(fragile)) { breaks[0]++; if (denied) e.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(BlockEvent.BreakEvent.class, event);
        try {
            plane.tick();
            h.assertTrue(breaks[0] == 1, "Plane never attempted the original .2-hardness harvest");
            h.assertTrue(denied ? h.getLevel().getBlockState(fragile).is(Blocks.SNOW_BLOCK) : h.getLevel().getBlockState(fragile).isAir(), "Plane fragile-block permission mismatch");
            h.assertTrue(h.getLevel().getBlockState(fragile.south()).is(Blocks.BEDROCK) && plane.isRemoved(), "Plane skipped the resistant second wall");
            var returns = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(5), e -> e.getItem().is(AcademyItems.PAPER_PLANE.get()));
            h.assertTrue(returns.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1, "Paper glider was lost/duplicated at impact");
            returns.forEach(ItemEntity::discard); h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(event); plane.discard(); close(p); }
    }
    @GameTest(template="empty") public static void paperPlaneHarvestsFragileBlockThenStopsAtWall(GameTestHelper h) { paperFlight(h, false); }
    @GameTest(template="empty") public static void paperPlaneHonorsFragileBlockProtection(GameTestHelper h) { paperFlight(h, true); }

    @GameTest(template="empty") public static void paperPlaneKeepsOriginalDimensionsAndLongFlight(GameTestHelper h) {
        var p = player(h, AbilityCategory.TELEKINESIS);
        var plane = new ExtraPaperPlaneEntity(AcademyEntities.PAPER_PLANE.get(), h.getLevel());
        plane.launch(p, false); plane.arm(); h.getLevel().addFreshEntity(plane); plane.tickCount = 1201;
        try {
            var start = plane.position(); plane.tick();
            h.assertTrue(plane.isAlive() && plane.position().distanceToSqr(start) > .01, "Legacy glider timed out after one minute");
            h.assertTrue(plane.getBbWidth() == .5F && plane.getBbHeight() == .5F, "Legacy glider dimensions changed");
            h.succeed();
        } finally { plane.discard(); close(p); }
    }

    @GameTest(template="empty",timeoutTicks=45) public static void slamDamagesAtFirstTickAndThirtiethTick(GameTestHelper h) {
        var p = player(h, AbilityCategory.TELEKINESIS);
        var data = p.getData(AcademyAttachments.PLAYER_ABILITY); data.learnSkill("psycho_slam");
        var victim = EntityType.COW.create(h.getLevel()); victim.setNoAi(true); victim.setNoGravity(true);
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); victim.setHealth(200);
        victim.setPos(p.position().add(0, .7, 3)); h.getLevel().addFreshEntity(victim);
        h.assertTrue(new PsychoSlamEffect().executeAndReport(p, data), "Slam cast failed");
        h.assertTrue(victim.getHealth() == 200, "Slam dealt damage before the first entity update");
        h.runAfterDelay(3, () -> h.assertTrue(victim.getHealth() == 185, "First slam stage was missing or repeated"));
        h.runAfterDelay(28, () -> h.assertTrue(victim.getHealth() == 185, "Second slam stage happened before tick 30"));
        h.runAfterDelay(33, () -> {
            try { h.assertTrue(victim.getHealth() == 155, "Slam did not apply the original doubled second hit"); h.succeed(); }
            finally { victim.discard(); close(p); }
        });
    }

    @GameTest(template="empty") public static void cpPotionStacksToSixtyFourAndReturnsOneBottle(GameTestHelper h) {
        var p = player(h, AbilityCategory.TELEKINESIS);
        var data = p.getData(AcademyAttachments.PLAYER_ABILITY);
        p.getInventory().clearContent(); p.getInventory().selected = 0;
        try {
            h.assertTrue(p.getInventory().add(new ItemStack(AcademyItems.CP_POTION.get(), 32))
                    && p.getInventory().add(new ItemStack(AcademyItems.CP_POTION.get(), 32)), "Potion insertion failed");
            var stack = p.getMainHandItem();
            h.assertTrue(stack.getCount() == 64, "Original 64-potion stack split during real inventory insertion");
            data.setCurrentCp(0);
            stack.getItem().finishUsingItem(stack, h.getLevel(), p);
            h.assertTrue(stack.getCount() == 63 && p.getInventory().countItem(Items.GLASS_BOTTLE) == 1, "Potion finish lost/duplicated potion or bottle");
            h.assertTrue(data.getCurrentCp() == Math.min(2000, data.getMaxCp() / 2), "Potion recovery did not retain its original half-maximum cap");
            data.setCurrentCp(data.getMaxCp() / 2);
            h.assertTrue(stack.use(h.getLevel(), p, net.minecraft.world.InteractionHand.MAIN_HAND).getResult()
                    == net.minecraft.world.InteractionResult.FAIL, "Potion could be used at the half-maximum cap");
            h.succeed();
        } finally { close(p); }
    }

    @GameTest(template="empty") public static void canceledCarrierResumeReleasesOnlyTransientClaim(GameTestHelper h) {
        var p = player(h, AbilityCategory.ELECTROMASTER);
        var source = p.blockPosition().west(3);
        h.getLevel().setBlock(source, AcademyBlocks.MAG_MANIP_ESCROW.get().defaultBlockState(), 3);
        var first = new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(), h.getLevel());
        first.setPos(p.position().add(0, 2, 0)); first.initialize(p, Blocks.IRON_BLOCK.defaultBlockState(), source, null, null, 8);
        h.assertTrue(first.reserveForSpawn() && first.commitSpawnReservation() && h.getLevel().addFreshEntity(first), "Resume fixture failed to establish live transaction");
        first.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        int[] admissions = {0};
        Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> veto = e -> {
            if (e.getEntity() instanceof MagManipBlockEntity carrier && carrier.getUUID().equals(first.getUUID())) {
                admissions[0]++; e.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class, veto);
        try {
            MagManipBlockEntity.resumeLoadedTransaction(p);
            h.assertTrue(admissions[0] == 1, "Resume did not reach real entity admission callback");
            h.assertTrue(MagManipBlockEntity.findOwned(p) == null && h.getLevel().getEntity(first.getUUID()) == null,
                    "Vetoed resume left a live unspawned owner claim");
            h.assertTrue(MagManipTransactionData.get(h.getLevel()).inspect(first.transactionId()) != null,
                    "Veto erased the durable material receipt");
        } finally { NeoForge.EVENT_BUS.unregister(veto); }
        try {
            MagManipBlockEntity.resumeLoadedTransaction(p);
            var resumed = MagManipBlockEntity.findOwned(p);
            h.assertTrue(resumed != null && h.getLevel().getEntity(first.getUUID()) == resumed,
                    "Authorized retry did not admit the exact durable carrier");
            resumed.recoverMaterial();
            h.assertTrue(resumed.isRemoved() && h.getLevel().getBlockState(source).is(Blocks.IRON_BLOCK)
                    && MagManipTransactionData.get(h.getLevel()).inspect(first.transactionId()) == null,
                    "Resumed material did not settle exactly once");
            h.succeed();
        } finally { close(p); }
    }

    private static void carrierMovement(GameTestHelper h, boolean coldBoundary) {
        var p = player(h, AbilityCategory.ELECTROMASTER);
        var level = h.getLevel(); var source = p.blockPosition().west(3);
        level.setBlock(source, AcademyBlocks.MAG_MANIP_ESCROW.get().defaultBlockState(), 3);
        int chunkX = p.blockPosition().getX() >> 4, chunkZ = p.blockPosition().getZ() >> 4;
        int absent = chunkX + 1;
        while (absent - chunkX < 128 && level.getChunkSource().getChunkNow(absent, chunkZ) != null) absent++;
        h.assertTrue(absent - chunkX < 128, "No unloaded chunk within the bounded search");
        var carrier = new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(), level);
        carrier.setPos(coldBoundary ? new Vec3(absent * 16 - 2.5, level.getMaxBuildHeight() - 16, chunkZ * 16 + 8.5)
                : p.position().add(3, 3, 0));
        carrier.initialize(p, Blocks.IRON_BLOCK.defaultBlockState(), source, null, null, 8);
        h.assertTrue(carrier.reserveForSpawn() && carrier.commitSpawnReservation() && level.addFreshEntity(carrier), "Movement fixture failed to admit carrier");
        try {
            Vec3 before = carrier.position();
            carrier.throwFrom(p, before.add(4, 0, 0), coldBoundary ? 4 : 1);
            carrier.tick();
            if (coldBoundary) {
                h.assertTrue(level.getChunkSource().getChunkNow(absent, chunkZ) == null, "Carrier move synchronously loaded the adjacent chunk");
                h.assertTrue(carrier.isRemoved() && level.getBlockState(source).is(Blocks.IRON_BLOCK)
                        && MagManipTransactionData.get(level).inspect(carrier.transactionId()) == null,
                        "Cold-boundary stop lost its material or left an unowned receipt");
            } else {
                h.assertTrue(carrier.isAlive() && carrier.getX() > before.x + .9, "Loaded movement was disabled by boundary protection");
                h.assertTrue(level.getBlockState(source).is(AcademyBlocks.MAG_MANIP_ESCROW.get()), "Flight prematurely restored material");
                carrier.recoverMaterial();
                h.assertTrue(carrier.isRemoved() && level.getBlockState(source).is(Blocks.IRON_BLOCK), "Loaded flight lost material on recovery");
            }
            h.succeed();
        } finally { carrier.discard(); close(p); }
    }
    @GameTest(template="empty") public static void thrownCarrierStopsBeforeUnloadedChunk(GameTestHelper h) { carrierMovement(h, true); }
    @GameTest(template="empty") public static void thrownCarrierStillMovesThroughLoadedSpace(GameTestHelper h) { carrierMovement(h, false); }
}
