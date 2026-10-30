package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Bounded ticket residency; generation advances in normal server ticks without a blocking chunk read. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class TeleportChunkRequests {
    private static final int MAX_REQUESTS = 16, MAX_CHUNKS = 16, TIMEOUT_TICKS = 200;
    private static final TicketType<UUID> TICKET =
            TicketType.create("academy_pending_teleport", Comparator.<UUID>naturalOrder());
    private record Request(ServerPlayer owner, ServerLevel origin, ServerLevel target, UUID ticket,
                           List<ChunkPos> chunks, long deadline, BooleanSupplier valid, Runnable ready) {}
    private static final Map<UUID, Request> PENDING = new HashMap<>();
    private TeleportChunkRequests() {}

    /** The caller revalidates gameplay state in valid/ready. A second request never replaces a live cast. */
    public static boolean request(ServerPlayer player, ServerLevel target,
            Set<LocationTeleportChunkPlan.Chunk> chunks, BooleanSupplier valid, Runnable ready) {
        if (!SkillInputSessionManager.isCurrentPlayer(player) || chunks.isEmpty() || chunks.size() > MAX_CHUNKS
                || PENDING.containsKey(player.getUUID()) || PENDING.size() >= MAX_REQUESTS || !valid.getAsBoolean()) return false;
        List<ChunkPos> positions = chunks.stream().map(c -> new ChunkPos(c.x(), c.z())).toList();
        Request request = new Request(player, player.serverLevel(), target, UUID.randomUUID(), positions,
                player.server.getTickCount() + TIMEOUT_TICKS, valid, ready);
        PENDING.put(player.getUUID(), request);
        try {
            for (ChunkPos chunk : positions) target.getChunkSource().addRegionTicket(TICKET, chunk, 1, request.ticket);
            // Keep the already-loaded use path synchronous, without ever waiting for missing terrain.
            advance(request, player.server.getTickCount());
            return true;
        } catch (RuntimeException | Error failure) {
            cancel(player.getUUID());
            throw failure;
        }
    }

    public static boolean isPending(UUID player) { return PENDING.containsKey(player); }

    private static void release(Request request) {
        for (ChunkPos chunk : request.chunks)
            request.target.getChunkSource().removeRegionTicket(TICKET, chunk, 1, request.ticket);
    }

    public static void cancel(UUID player) {
        Request request = PENDING.remove(player);
        if (request != null) release(request);
    }

    private static void advance(Request request, long now) {
        if (PENDING.get(request.owner.getUUID()) != request) return;
        if (!SkillInputSessionManager.isCurrentPlayer(request.owner) || request.owner.serverLevel() != request.origin
                || now >= request.deadline || !request.valid.getAsBoolean()) {
            cancel(request.owner.getUUID());
            return;
        }
        for (ChunkPos chunk : request.chunks)
            if (request.target.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) return;
        // Remove before commit: a successful cross-dimensional move fires lifecycle callbacks.
        PENDING.remove(request.owner.getUUID(), request);
        try { request.ready.run(); }
        finally { release(request); }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        tickPending(event.getServer().getTickCount());
    }
    // Explicit clock seam keeps timeout tests deterministic while retaining real world/ticket state.
    static void tickPending(long now) {
        for (Request request : List.copyOf(PENDING.values())) advance(request, now);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { cancel(event.getEntity().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { cancel(event.getEntity().getUUID()); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { cancel(event.getEntity().getUUID()); }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        for (Request request : List.copyOf(PENDING.values()))
            if (request.origin == event.getLevel() || request.target == event.getLevel()) cancel(request.owner.getUUID());
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (UUID player : List.copyOf(PENDING.keySet())) cancel(player);
    }
}
