package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.world.block.DevMachineBase;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Returns a player to the developer skill tree after they close the developer's wireless page.
 *
 * <p>The reopen waits for the next player tick and only happens when no other menu has been opened
 * in the meantime (opening a chest also closes the wireless page), the player is still within reach
 * and may interact with the machine. A new session nonce is issued, exactly as on a right-click.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class DevNetworkReturn {
    private record Pending(BlockPos pos, int containerId, boolean closed) {}
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private DevNetworkReturn() {}

    static void expect(ServerPlayer player, BlockPos pos, int containerId) {
        PENDING.put(player.getUUID(), new Pending(pos.immutable(), containerId, false));
    }

    public static void forget(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) return;
        if (event.getContainer().containerId != pending.containerId()) return;
        PENDING.put(player.getUUID(), new Pending(pending.pos(), pending.containerId(), true));
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null || !pending.closed()) return;
        PENDING.remove(player.getUUID());
        BlockPos pos = pending.pos();
        if (!player.isAlive() || player.containerMenu != player.inventoryMenu
                || !com.mohistmc.academy.skill.ability.LoadedRaycast.isLoaded(player.serverLevel(), pos)
                || player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64
                || !player.serverLevel().mayInteract(player, pos)) return;
        DevMachineBase.openDeveloper(player, pos, false);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }
}
