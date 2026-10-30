package com.mohistmc.academy.regression;

import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.gametest.recheck.RecheckPlayers;
import com.mohistmc.academy.network.*;
import com.mohistmc.academy.skill.*;
import com.mohistmc.academy.skill.ability.LoadedRaycast;
import com.mohistmc.academy.skill.ability.meltdowner.MineRayBasicEffect;
import com.mohistmc.academy.skill.passive.VecDefenseRuntime;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.DevMachineType;
import com.mohistmc.academy.world.item.DeveloperPortable;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Real accepted-damage, multi-tick mining and forged-payload regression controls. */
@GameTestHolder("academy_audit")
@PrefixGameTestTemplate(false)
public final class CoreDeepAuditGameTests {
    private static PlayerAbilityData ability(ServerPlayer p, AbilityCategory category, String skill) {
        var data = p.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(category); data.setPlayerLevel(5); data.learnSkill(skill);
        data.setProficiency(skill, 1); data.setAbilityActive(true);
        data.setCurrentCp(data.getMaxCp()); data.setCurrentOverload(0);
        return data;
    }

    private static void damageBypass(GameTestHelper h, boolean kill) {
        var session = RecheckPlayers.connect(h);
        var p = session.player();
        // Login protection must expire; the test never replaces ServerPlayer.hurt or its damage source.
        h.runAfterDelay(65, () -> {
            try {
                var data = ability(p, AbilityCategory.VECMANIP, "vec_reflection");
                VecDefenseRuntime.start(p.getUUID(), VecDefenseRuntime.Mode.REFLECTION);
                p.setHealth(20); p.invulnerableTime = 0;
                float cp = data.getCurrentCp();
                h.assertTrue(!p.hurt(p.damageSources().generic(), 4) && p.getHealth() == 20
                        && data.getCurrentCp() < cp, "Positive control: ordinary damage must still reflect and pay");
                p.invulnerableTime = 0;
                var before = data.captureDynamicResources();
                int[] commits = {0};
                h.assertTrue(TeleportChunkRequests.request(p, h.getLevel(),
                        java.util.Set.of(new LocationTeleportChunkPlan.Chunk(100_000 + (kill ? 1 : 2), 100_000)),
                        () -> true, () -> commits[0]++), "Pending teleport death fixture rejected");
                h.assertTrue(p.hurt(kill ? p.damageSources().genericKill() : p.damageSources().fellOutOfWorld(), 1000)
                        && !p.isAlive(), "Kill/void must bypass a live, maxed reflection context");
                h.assertTrue(data.captureDynamicResources().equals(before), "Bypassed damage charged ability resources");
                h.assertTrue(!VecDefenseRuntime.active(p.getUUID(), VecDefenseRuntime.Mode.REFLECTION),
                        "Real death left the reflection context active");
                h.assertTrue(!TeleportChunkRequests.isPending(p.getUUID()) && commits[0] == 0,
                        "Real death failed to cancel asynchronous teleport");
                h.succeed();
            } finally { VecDefenseRuntime.stop(p.getUUID()); session.close(); }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 110)
    public static void genericKillBypassesActiveReflection(GameTestHelper h) { damageBypass(h, true); }
    @GameTest(template = "empty", timeoutTicks = 110)
    public static void voidBypassesActiveReflection(GameTestHelper h) { damageBypass(h, false); }

    private static BlockPos miningTarget(GameTestHelper h, ServerPlayer p) {
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4, 24, 3))));
        p.setYRot(0); p.setXRot(0);
        return BlockPos.containing(p.getEyePosition().add(0, 0, 3));
    }
    private static int drops(ServerPlayer p, Item item) {
        return p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(8),
                e -> e.getItem().is(item)).stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    @GameTest(template = "empty")
    public static void miningProgressCannotBreakReplacementBedrock(GameTestHelper h) {
        try (var session = RecheckPlayers.connect(h)) {
            var p = session.player(); var data = ability(p, AbilityCategory.MELTDOWNER, "mine_ray_basic");
            BlockPos target = miningTarget(h, p);
            h.getLevel().setBlock(target, Blocks.STONE.defaultBlockState(), 3);
            var effect = new MineRayBasicEffect();
            effect.onChargingStart(p, data);
            try {
                h.assertTrue(effect.onChargingTick(p, data, 1), "Mining start fixture failed");
                h.getLevel().setBlock(target, Blocks.BEDROCK.defaultBlockState(), 3);
                for (int tick = 2; tick < 16; tick++) effect.onChargingTick(p, data, tick);
                h.assertTrue(h.getLevel().getBlockState(target).is(Blocks.BEDROCK),
                        "Cached mining progress destroyed replacement bedrock");
                h.assertTrue(drops(p, Items.BEDROCK) == 0 && drops(p, Items.COBBLESTONE) == 0,
                        "Replacement target emitted old or forbidden drops");
            } finally { effect.onChargingRelease(p, data, 16); }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void miningRechecksProtectionAtCommit(GameTestHelper h) {
        try (var session = RecheckPlayers.connect(h)) {
            var p = session.player(); var data = ability(p, AbilityCategory.MELTDOWNER, "mine_ray_basic");
            BlockPos target = miningTarget(h, p);
            h.getLevel().setBlock(target, Blocks.STONE.defaultBlockState(), 3);
            int[] seen = {0}; boolean[] protectedNow = {false};
            Consumer<BlockEvent.BreakEvent> protection = event -> {
                if (event.getPlayer() == p && event.getPos().equals(target)) {
                    seen[0]++; if (protectedNow[0]) event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(BlockEvent.BreakEvent.class, protection);
            var effect = new MineRayBasicEffect(); effect.onChargingStart(p, data);
            try {
                effect.onChargingTick(p, data, 1);
                h.assertTrue(seen[0] == 1, "Initial protection preflight did not run");
                protectedNow[0] = true;
                for (int tick = 2; tick < 12; tick++) effect.onChargingTick(p, data, tick);
                h.assertTrue(seen[0] >= 2 && h.getLevel().getBlockState(target).is(Blocks.STONE)
                        && drops(p, Items.COBBLESTONE) == 0, "Late protection was ignored at mining commit");
            } finally {
                effect.onChargingRelease(p, data, 12); NeoForge.EVENT_BUS.unregister(protection);
            }
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void miningAllowedChestDropsInventoryExactlyOnce(GameTestHelper h) {
        try (var session = RecheckPlayers.connect(h)) {
            var p = session.player(); var data = ability(p, AbilityCategory.MELTDOWNER, "mine_ray_basic");
            BlockPos target = miningTarget(h, p);
            h.getLevel().setBlock(target, Blocks.CHEST.defaultBlockState(), 3);
            var chest = (ChestBlockEntity) h.getLevel().getBlockEntity(target);
            chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
            var effect = new MineRayBasicEffect(); effect.onChargingStart(p, data);
            try {
                for (int tick = 1; tick < 14; tick++) effect.onChargingTick(p, data, tick);
                h.assertTrue(h.getLevel().getBlockState(target).isAir(), "Allowed mining failed to remove chest");
                h.assertTrue(drops(p, Items.CHEST) == 1 && drops(p, Items.DIAMOND) == 7,
                        "Mining duplicated or lost the chest/inventory");
            } finally { effect.onChargingRelease(p, data, 14); }
            h.succeed();
        }
    }

    private static void rayBoundary(GameTestHelper h, int direction) {
        try (var session = RecheckPlayers.connect(h)) {
            var p = session.player(); var level = h.getLevel();
            int startX = p.blockPosition().getX() >> 4, z = p.blockPosition().getZ() >> 4;
            int absent = startX + direction;
            while (Math.abs(absent - startX) < 128 && level.getChunkSource().getChunkNow(absent, z) != null) absent += direction;
            h.assertTrue(Math.abs(absent - startX) < 128, "No cold chunk found within bounded test search");
            Vec3 start = new Vec3(startX * 16 + 8.5, level.getMaxBuildHeight() - 16, z * 16 + 8.5);
            Vec3 end = new Vec3(absent * 16 + 8.5, start.y, start.z);
            var hit = LoadedRaycast.clip(level, new ClipContext(start, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, p));
            h.assertTrue(hit.getType() == HitResult.Type.BLOCK && !LoadedRaycast.isLoaded(level, hit.getBlockPos()),
                    "Ray did not terminate at the unavailable chunk boundary");
            h.assertTrue(level.getChunkSource().getChunkNow(absent, z) == null, "Ray synchronously loaded cold terrain");
            double nearEdge = direction > 0 ? absent * 16 - 2.5 : (absent + 1) * 16 + 2.5;
            p.setPos(nearEdge, start.y - p.getEyeHeight(), start.z);
            p.setYRot(direction > 0 ? -90 : 90); p.setXRot(0);
            var shiftData = ability(p, AbilityCategory.TELEPORTER, "shift_tp");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS, 2));
            var before = shiftData.captureDynamicResources();
            var shift = new com.mohistmc.academy.skill.ability.teleporter.ShiftTpEffect();
            h.assertTrue(!shift.tryRelease(p, shiftData, 1) && shiftData.captureDynamicResources().equals(before)
                    && p.getMainHandItem().getCount() == 2 && level.getChunkSource().getChunkNow(absent, z) == null,
                    "Shift placement context loaded a cold hit cell or charged a rejected cast");
            BlockPos blast = BlockPos.containing(nearEdge, start.y, start.z);
            level.setBlock(blast, Blocks.GLASS.defaultBlockState(), 3);
            var orb = new com.mohistmc.academy.entity.PlasmaOrbEntity(
                    com.mohistmc.academy.world.AcademyEntities.PLASMA_ORB.get(), level);
            orb.configureCharging(p, Vec3.atCenterOf(blast));
            h.assertTrue(level.addFreshEntity(orb) && orb.arm(Vec3.atCenterOf(blast), 0, 3), "Cold-edge plasma fixture rejected");
            orb.tick();
            h.assertTrue(orb.isRemoved() && level.getBlockState(blast).is(Blocks.GLASS)
                    && level.getChunkSource().getChunkNow(absent, z) == null,
                    "Cold-edge plasma invoked terrain explosion or synchronously loaded its blast margin");
            level.removeBlock(blast, false);
            BlockPos wall = BlockPos.containing(start.add(direction * 2, 0, 0));
            level.setBlock(wall, Blocks.GLASS_PANE.defaultBlockState(), 3);
            var control = LoadedRaycast.clip(level, new ClipContext(start, start.add(direction * 4, 0, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            h.assertTrue(control.getType() == HitResult.Type.BLOCK && control.getBlockPos().equals(wall),
                    "Loaded thin collision shape positive control failed");
            level.removeBlock(wall, false); h.succeed();
        }
    }
    @GameTest(template = "empty") public static void rayStopsBeforeColdPositiveChunk(GameTestHelper h) { rayBoundary(h, 1); }
    @GameTest(template = "empty") public static void rayStopsBeforeColdNegativeChunk(GameTestHelper h) { rayBoundary(h, -1); }

    @GameTest(template = "empty")
    public static void forgedDeveloperFeedbackCannotLoadChunksAndValidInductionStillPays(GameTestHelper h) {
        try (var session = RecheckPlayers.connect(h)) {
            var p = session.player(); var data = p.getData(AcademyAttachments.PLAYER_ABILITY);
            BlockPos far = new BlockPos(20_000_000, 100, -20_000_000);
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null,
                    "Forged position must start unloaded");
            var before = data.toSyncTag().copy();
            for (int i = 0; i < 100; i++) LearnSkillPacket.handle(new LearnSkillPacket("arc_gen",
                    DevMachineType.NORMAL.ordinal(), Optional.of(far), UUID.randomUUID()), context(p));
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null
                    && before.equals(data.toSyncTag()), "Rejected learning packet loaded terrain or mutated ability data");
            h.assertTrue(!PayloadRateLimiter.allow(p.getUUID(), "learn_skill", h.getLevel().getGameTime(), 20, 4),
                    "Malformed developer requests did not exhaust their bounded request budget");
            PayloadRateLimiter.forget(p.getUUID());
            ItemStack portable = new ItemStack(AcademyItems.DEVELOPER_PORTABLE.get());
            ((DeveloperPortable)portable.getItem()).setEnergy(portable, 10_000);
            p.setItemInHand(InteractionHand.MAIN_HAND, portable);
            UUID nonce = DevLearningSessionManager.issue(p, DevMachineType.PORTABLE, Optional.empty());
            LearnSkillPacket.handle(new LearnSkillPacket(LearnSkillPacket.INDUCTION_ACTION,
                    DevMachineType.PORTABLE.ordinal(), Optional.empty(), nonce), context(p));
            int ticks = DevMachineType.PORTABLE.developmentTicksPerStimulation() * DevLearningSessionManager.INDUCTION_STIMULATIONS;
            for (int i = 0; i < ticks; i++) DevLearningSessionManager.tick(p);
            h.assertTrue(data.hasAbility() && data.getPlayerLevel() == 1
                    && EnergyItemHelper.getEnergy(portable) == 10_000 - ticks * DevMachineType.PORTABLE.energyPerTick(),
                    "Valid developer request no longer completes through the real energy ledger");
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void staleLearnedSkillCannotBypassCurrentLevel(GameTestHelper h) {
        try (var session = RecheckPlayers.connect(h)) {
            var p = session.player(); var d = ability(p, AbilityCategory.VECMANIP, "vec_reflection");
            d.setSlot(0, 0, "vec_reflection");
            d.setPlayerLevel(1);
            var before = d.captureDynamicResources(); UUID token = SkillInputSessionManager.sessionId(p);
            SkillKeyDownPacket.handle(new SkillKeyDownPacket(0, new SkillInputToken(token, 1)), context(p));
            h.assertTrue(SkillChargingManager.getState(p.getUUID()) == null && d.captureDynamicResources().equals(before),
                    "Stale learned high-level skill bypassed the current player level");
            d.setPlayerLevel(5);
            SkillKeyDownPacket.handle(new SkillKeyDownPacket(0, new SkillInputToken(token, 2)), context(p));
            var state = SkillChargingManager.getState(p.getUUID());
            h.assertTrue(state != null && VecDefenseRuntime.active(p.getUUID(), VecDefenseRuntime.Mode.REFLECTION),
                    "Correct-level key-down positive control did not start its paid context");
            ChargingAckPacket.handle(new ChargingAckPacket(state.epoch, state.generation), context(p));
            d.setPlayerLevel(1);
            SkillEventHandler.onPlayerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
            h.assertTrue(SkillChargingManager.getState(p.getUUID()) == null
                    && !VecDefenseRuntime.active(p.getUUID(), VecDefenseRuntime.Mode.REFLECTION),
                    "Level change did not abort the already active context");
            h.succeed();
        }
    }

    static IPayloadContext context(ServerPlayer p) {
        return (IPayloadContext)Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
                new Class<?>[]{IPayloadContext.class}, (proxy, method, args) -> {
                    if (method.getName().equals("player")) return p;
                    if (method.getName().equals("enqueueWork")) {
                        if (args[0] instanceof Runnable work) { work.run(); return CompletableFuture.completedFuture(null); }
                        if (args[0] instanceof java.util.function.Supplier<?> work) return CompletableFuture.completedFuture(work.get());
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
