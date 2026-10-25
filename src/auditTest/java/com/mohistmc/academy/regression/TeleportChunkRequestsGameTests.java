package com.mohistmc.academy.regression;

import com.mohistmc.academy.network.*;

import com.mohistmc.academy.gametest.recheck.RecheckPlayers;
import com.mohistmc.academy.skill.*;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.item.ExtraItemData;
import com.mohistmc.academy.world.item.HandheldTeleporterItem;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@GameTestHolder("academy_audit")
@PrefixGameTestTemplate(false)
public final class TeleportChunkRequestsGameTests {
    private static void tickPending(long now) {
        try {
            var method = TeleportChunkRequests.class.getDeclaredMethod("tickPending", long.class);
            method.setAccessible(true);
            method.invoke(null, now);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static LocationTeleportChunkPlan.Chunk cold(GameTestHelper h) {
        int x = (h.absolutePos(net.minecraft.core.BlockPos.ZERO).getX() >> 4) + 2000;
        int z = (h.absolutePos(net.minecraft.core.BlockPos.ZERO).getZ() >> 4) + 2000;
        for (int attempt = 0; attempt < 64; attempt++, x += 32)
            if (h.getLevel().getChunkSource().getChunkNow(x, z) == null) return new LocationTeleportChunkPlan.Chunk(x, z);
        throw new AssertionError("No cold fixture chunk within bounded search");
    }
    private static Vec3 destination(GameTestHelper h, LocationTeleportChunkPlan.Chunk chunk) {
        return new Vec3(chunk.x() * 16 + 8.5, h.getLevel().getMaxBuildHeight() - 16, chunk.z() * 16 + 8.5);
    }
    /** Inspect actual Minecraft tickets, so removing only our Java map cannot make cleanup pass. */
    private static int tickets(ServerLevel level, LocationTeleportChunkPlan.Chunk chunk) {
        try {
            Field managerField = ServerChunkCache.class.getDeclaredField("distanceManager"); managerField.setAccessible(true);
            DistanceManager manager = (DistanceManager)managerField.get(level.getChunkSource());
            Field ticketsField = DistanceManager.class.getDeclaredField("tickets"); ticketsField.setAccessible(true);
            var map = (it.unimi.dsi.fastutil.longs.Long2ObjectMap<?>)ticketsField.get(manager);
            Object entry = map.get(ChunkPos.asLong(chunk.x(), chunk.z()));
            if (!(entry instanceof Iterable<?> values)) return 0;
            int count = 0;
            for (Object value : values) if (value instanceof Ticket<?> ticket
                    && ticket.getType().toString().equals("academy_pending_teleport")) count++;
            return count;
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @GameTest(template = "empty", batch = "core_teleport_cold", timeoutTicks = 260)
    public static void coldSavedLocationLoadsWithoutBlockingThenPays(GameTestHelper h) {
        var session = RecheckPlayers.connect(h); var p = session.player();
        var chunk = cold(h); Vec3 dest = destination(h, chunk), origin = p.position();
        var d = p.getData(AcademyAttachments.PLAYER_ABILITY);
        d.setCurrentAbility(AbilityCategory.TELEPORTER); d.setPlayerLevel(5); d.learnSkill("location_teleport");
        d.setAbilityActive(true); d.setCurrentCp(d.getMaxCp()); d.setCurrentOverload(0);
        d.addTeleportLocation(new PlayerAbilityData.TeleportLocation("audit", Level.OVERWORLD.location().toString(), dest.x, dest.y, dest.z));
        float cp = d.getCurrentCp();
        try {
            LocationTeleportActionPacket.handle(new LocationTeleportActionPacket(LocationTeleportActionPacket.PERFORM, 0, ""), context(p));
            h.assertTrue(TeleportChunkRequests.isPending(p.getUUID()) && p.position().equals(origin)
                    && d.getCurrentCp() == cp && h.getLevel().getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null,
                    "Cold cast must return before loading, movement and resource payment");
            h.assertTrue(tickets(h.getLevel(), chunk) == 1, "Pending cast has no actual residency ticket");
        } catch (RuntimeException | Error failure) { session.close(); throw failure; }
        boolean[] completionObserved = {false};
        h.succeedWhen(() -> {
            // GameTestServer skips the normal inter-tick wait. Give asynchronous chunk work
            // a normal wall-clock budget without changing the production 200-tick deadline.
            if (TeleportChunkRequests.isPending(p.getUUID()))
                java.util.concurrent.locks.LockSupport.parkNanos(50_000_000L);
            h.assertTrue(!TeleportChunkRequests.isPending(p.getUUID()), "Cold destination still loading");
            if (!completionObserved[0]) {
                completionObserved[0] = true;
                com.mojang.logging.LogUtils.getLogger().info(
                        "COLD_TELEPORT_COMPLETE position={} destination={} cp={}/{} cooldown={} alive={} removed={} loaded={} tickets={} testTick={}",
                        p.position(), dest, d.getCurrentCp(), cp, d.isOnCooldown("location_teleport"),
                        p.isAlive(), p.isRemoved(), h.getLevel().getChunkSource().getChunkNow(chunk.x(), chunk.z()) != null,
                        tickets(h.getLevel(), chunk), h.getTick());
            }
            try {
                h.assertTrue(p.position().distanceToSqr(dest) < .01 && d.getCurrentCp() < cp && d.isOnCooldown("location_teleport"),
                        "Ready cast did not commit movement/payment/cooldown");
                h.assertTrue(tickets(h.getLevel(), chunk) == 0, "Successful cast leaked its residency ticket");
            } finally { session.close(); }
        });
    }

    private static ItemStack device(ServerPlayer p, Vec3 dest) {
        ItemStack stack = new ItemStack(AcademyItems.TELEPORTER_DEVICE.get());
        ((HandheldTeleporterItem)stack.getItem()).setEnergy(stack, 10_000);
        ExtraItemData.setTeleport(stack, Level.OVERWORLD.location(), dest.x, dest.y, dest.z);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack); p.getInventory().setItem(8, new ItemStack(Items.ENDER_PEARL, 2));
        return stack;
    }

    @GameTest(template = "empty", batch = "core_teleport_loaded")
    public static void handheldLoadedDestinationConsumesExactlyOnce(GameTestHelper h) {
        try (var session = RecheckPlayers.connect(h)) {
            var p = session.player(); p.setPos(p.position().add(0, 30, 0));
            Vec3 dest = p.position().add(2, 0, 0); ItemStack stack = device(p, dest);
            var item = (HandheldTeleporterItem)stack.getItem(); item.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
            h.assertTrue(p.position().distanceToSqr(dest) < .01 && item.getEnergyStored(stack) == 5000
                    && p.getInventory().getItem(8).getCount() == 1 && !TeleportChunkRequests.isPending(p.getUUID()),
                    "Loaded handheld teleport did not preserve exact movement/IF/pearl settlement");
            h.succeed();
        }
    }

    private static void cancelHandheld(GameTestHelper h, boolean changeItem) {
        try (var session = RecheckPlayers.connect(h)) {
            var p = session.player(); var chunk = cold(h); Vec3 origin = p.position();
            ItemStack stack = device(p, destination(h, chunk)); var item = (HandheldTeleporterItem)stack.getItem();
            item.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
            h.assertTrue(TeleportChunkRequests.isPending(p.getUUID()) && tickets(h.getLevel(), chunk) == 1,
                    "Cold handheld cast fixture did not create one request");
            if (changeItem) p.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
            else item.setEnergy(stack, 4999);
            tickPending(p.server.getTickCount());
            h.assertTrue(!TeleportChunkRequests.isPending(p.getUUID()) && tickets(h.getLevel(), chunk) == 0
                    && p.position().equals(origin) && p.getInventory().getItem(8).getCount() == 2,
                    "Changed hand/cost state committed or leaked a pending handheld cast");
            h.succeed();
        }
    }
    @GameTest(template = "empty", batch = "core_teleport_loaded") public static void handheldChangedStackCancels(GameTestHelper h) { cancelHandheld(h, true); }
    @GameTest(template = "empty", batch = "core_teleport_loaded") public static void handheldChangedEnergyCancels(GameTestHelper h) { cancelHandheld(h, false); }

    @GameTest(template = "empty", batch = "core_teleport_timeout")
    public static void ticketDeadlineAndLogoutReleaseActualTickets(GameTestHelper h) {
        var session = RecheckPlayers.connect(h); var p = session.player(); var chunk = cold(h);
        int[] committed = {0};
        try {
            h.assertTrue(TeleportChunkRequests.request(p, h.getLevel(), Set.of(chunk), () -> true, () -> committed[0]++),
                    "Timeout request fixture rejected");
            h.assertTrue(tickets(h.getLevel(), chunk) == 1, "Timeout fixture has no actual ticket");
            tickPending(p.server.getTickCount() + 200);
            h.assertTrue(committed[0] == 0 && !TeleportChunkRequests.isPending(p.getUUID()) && tickets(h.getLevel(), chunk) == 0,
                    "Expired request committed or retained a real ticket");
            h.assertTrue(TeleportChunkRequests.request(p, h.getLevel(), Set.of(chunk), () -> true, () -> committed[0]++),
                    "Released owner could not start a new request");
            session.close();
            h.assertTrue(committed[0] == 0 && !TeleportChunkRequests.isPending(p.getUUID()) && tickets(h.getLevel(), chunk) == 0,
                    "Production logout failed to remove request and actual ticket");
            h.succeed();
        } finally { session.close(); }
    }

    @GameTest(template = "empty", batch = "core_teleport_budget")
    public static void requestAndChunkBudgetsRejectBeforeAddingTickets(GameTestHelper h) {
        var sessions = new ArrayList<RecheckPlayers.Session>(); var chunk = cold(h); int[] committed = {0};
        try {
            var first = RecheckPlayers.connect(h); sessions.add(first);
            var tooMany = new HashSet<LocationTeleportChunkPlan.Chunk>();
            for (int i = 0; i < 17; i++) tooMany.add(new LocationTeleportChunkPlan.Chunk(chunk.x() + i, chunk.z()));
            h.assertTrue(!TeleportChunkRequests.request(first.player(), h.getLevel(), tooMany, () -> true, () -> committed[0]++)
                    && tickets(h.getLevel(), chunk) == 0, "Over-budget destination added tickets");
            for (int i = 0; i < 16; i++) {
                var session = i == 0 ? first : RecheckPlayers.connect(h); if (i != 0) sessions.add(session);
                h.assertTrue(TeleportChunkRequests.request(session.player(), h.getLevel(), Set.of(chunk), () -> true, () -> committed[0]++),
                        "Bounded accepted request fixture failed at " + i);
            }
            var extra = RecheckPlayers.connect(h); sessions.add(extra);
            h.assertTrue(!TeleportChunkRequests.request(extra.player(), h.getLevel(), Set.of(chunk), () -> true, () -> committed[0]++)
                    && !TeleportChunkRequests.request(first.player(), h.getLevel(), Set.of(chunk), () -> true, () -> committed[0]++)
                    && tickets(h.getLevel(), chunk) == 16, "Global/owner budget added excess residency tickets");
            sessions.forEach(RecheckPlayers.Session::close);
            h.assertTrue(committed[0] == 0 && tickets(h.getLevel(), chunk) == 0, "Budget test cleanup leaked tickets");
            h.succeed();
        } finally { sessions.forEach(RecheckPlayers.Session::close); }
    }

    private static IPayloadContext context(ServerPlayer p) {
        return (IPayloadContext)Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(), new Class<?>[]{IPayloadContext.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("player")) return p;
                    if (method.getName().equals("enqueueWork") && args[0] instanceof Runnable work) {
                        work.run(); return CompletableFuture.completedFuture(null);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
