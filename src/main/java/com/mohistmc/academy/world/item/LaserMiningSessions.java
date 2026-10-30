package com.mohistmc.academy.world.item;

import com.mohistmc.academy.AcademyCraft;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Main-thread action state: one current mining target per player, never serialized to a stack. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class LaserMiningSessions {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private LaserMiningSessions() {}

    static float advance(ServerPlayer player, ItemStack stack, int slot, BlockPos pos, BlockState state) {
        long tick = player.level().getGameTime();
        Session previous = SESSIONS.get(player.getUUID());
        float progress = previous != null && previous.matches(player, stack, slot)
                && previous.pos.equals(pos) && previous.state == state
                && tick >= previous.tick && tick <= previous.tick + 1 ? previous.progress + 0.2F : 0.2F;
        SESSIONS.put(player.getUUID(), new Session(stack, slot, player.level().dimension(), pos.immutable(), state, progress, tick));
        return progress;
    }

    static void clear(ServerPlayer player) { SESSIONS.remove(player.getUUID()); }

    static void clear(ServerPlayer player, ItemStack stack) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.stack == stack) clear(player);
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && (!player.isAlive()
                || !session.matches(player, player.getMainHandItem(), player.getInventory().selected)
                || !ExtraItemData.isActive(session.stack)
                || player.level().getGameTime() > session.tick + 1)) clear(player);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        SESSIONS.remove(event.getOriginal().getUUID());
    }

    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SESSIONS.clear(); }

    private record Session(ItemStack stack, int slot, ResourceKey<Level> dimension, BlockPos pos,
                           BlockState state, float progress, long tick) {
        boolean matches(ServerPlayer player, ItemStack held, int selectedSlot) {
            return stack == held && slot == selectedSlot && dimension.equals(player.level().dimension());
        }
    }
}
