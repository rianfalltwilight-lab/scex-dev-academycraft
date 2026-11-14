package com.mohistmc.academy.skill.ability.teleporter;

import com.mohistmc.academy.AcademyCraft;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Forty-tick post-Flashing partial gravity cancel, as legacy GravityCancellor: while airborne and not
 * flying it added {@link #LIFT}=0.072 to vertical velocity each tick, so gravity (0.08) was only partly
 * offset and fall distance kept building.
 *
 * <p>Legacy did this on the client. Writing a player's velocity from the server would overwrite the
 * client's motion with stale server values, so the lift is applied as a transient {@code generic.gravity}
 * modifier instead: the attribute syncs to the client, which then moves itself.  Flashing is the only user.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
final class GravityCancelRuntime {
    static final double LIFT = 0.072;
    private static final ResourceLocation MODIFIER =
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "flashing_gravity_cancel");
    private static final Map<UUID, Long> ACTIVE = new ConcurrentHashMap<>();

    private GravityCancelRuntime() {}

    static void start(ServerPlayer player) {
        long until = player.serverLevel().getGameTime() + 40;
        ACTIVE.merge(player.getUUID(), until, Math::max);
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity != null && !gravity.hasModifier(MODIFIER)) {
            gravity.addTransientModifier(new AttributeModifier(MODIFIER, -LIFT,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    static boolean active(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    private static void stop(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity != null) gravity.removeModifier(MODIFIER);
    }

    @SubscribeEvent
    static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Long until = ACTIVE.get(player.getUUID());
        if (until == null) return;
        if (!player.isAlive() || com.mohistmc.academy.skill.AbilityInterferenceService.isInterfered(player)
                || player.serverLevel().getGameTime() >= until) stop(player);
    }

    @SubscribeEvent
    static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player);
    }

    @SubscribeEvent
    static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player);
    }

    @SubscribeEvent
    static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player);
    }

    @SubscribeEvent
    static void stopping(ServerStoppingEvent event) {
        for (UUID id : List.copyOf(ACTIVE.keySet())) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            if (player != null) stop(player);
        }
        ACTIVE.clear();
    }
}
