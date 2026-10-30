package com.mohistmc.academy.regression;

import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.aerohand.AeroPassiveRuntime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;

/** Server mutations and actual client packet delivery; never writes client flight attributes. */
final class NativeFlightClientChecks {
    private static final ResourceLocation OWN = ResourceLocation.parse("academy:aerohand_flying");
    private static final ResourceLocation EXTERNAL = ResourceLocation.parse("academy_audit:client_external_flight");
    private static int phase, waitTicks;
    private static boolean complete;
    private static CompletableFuture<Void> operation;
    private static Throwable failure;
    // Read and restored only on the integrated server thread.
    private static Snapshot original;

    private NativeFlightClientChecks() {}

    static boolean tick(Minecraft mc, List<String> evidence) {
        if (complete) return true;
        if (failure != null) {
            if (!operation.isDone() && ++waitTicks <= 200) return false;
            if (operation.isDone()) {
                try { operation.getNow(null); }
                catch (Throwable cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            }
            throw new IllegalStateException("native client flight sync failed", failure);
        }
        try {
            if (operation == null) {
                int requested = phase;
                operation = server(mc, player -> {
                    if (requested == 0) grant(player);
                    else if (requested == 1) retainExternal(player);
                    else if (requested == 2) {
                        require(player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT).removeModifier(EXTERNAL),
                                "external flight fixture was absent before revocation");
                        // Do not send a custom packet or force flying=false: native player ticking must do it.
                    } else restore(player);
                });
                waitTicks = 0;
            }
            if (++waitTicks > 200) throw new IllegalStateException("flight sync timeout phase=" + phase + " " + clientState(mc));
            if (!operation.isDone()) return false;
            operation.getNow(null);
            if (phase == 3) { complete = true; return true; }

            var attribute = mc.player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
            require(attribute != null, "real client player has no flight attribute");
            boolean own = attribute.hasModifier(OWN), external = attribute.hasModifier(EXTERNAL);
            boolean mayFly = mc.player.mayFly(), flying = mc.player.getAbilities().flying;
            boolean expected = switch (phase) {
                case 0 -> own && !external && mayFly && flying;
                case 1 -> !own && external && mayFly && flying;
                case 2 -> !own && !external && !mayFly && !flying;
                default -> false;
            };
            if (!expected || mc.player.getAbilities().mayfly || mc.gameMode.getPlayerMode() != GameType.SURVIVAL) return false;
            evidence.add("PASS native flight server -> real client phase=" + phase + ": " + clientState(mc)
                    + (phase == 0 ? "; actual Aero toggle grant" : phase == 1 ? "; own grant revoked, external grant retained"
                    : "; last external grant removed, native tick revoked flying"));
            phase++;
            operation = null;
            return false;
        } catch (Throwable problem) {
            failure = problem;
            operation = server(mc, NativeFlightClientChecks::restore);
            waitTicks = 0;
            return false;
        }
    }

    private static void grant(ServerPlayer player) {
        original = new Snapshot(player.getData(AcademyAttachments.PLAYER_ABILITY),
                player.gameMode.getGameModeForPlayer(), player.position(), player.getYRot(), player.getXRot(),
                player.getAbilities().flying);
        player.setGameMode(GameType.SURVIVAL);
        var attribute = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        require(attribute != null && !attribute.hasModifier(OWN) && !attribute.hasModifier(EXTERNAL)
                && !player.mayFly() && !player.getAbilities().mayfly, "fresh survival fixture already has flight permission");
        var data = new PlayerAbilityData();
        data.setCurrentAbility(AbilityCategory.AEROHAND);
        data.setPlayerLevel(5);
        data.learnSkill("flying");
        data.setAbilityActive(true);
        data.setCurrentCp(data.getMaxCp());
        data.setCurrentOverload(0);
        player.setData(AcademyAttachments.PLAYER_ABILITY, data);
        // Stay clear of on-ground flight cancellation while observing the real network round trip.
        player.teleportTo(player.getX(), player.getY() + 12, player.getZ());
        player.setDeltaMovement(Vec3.ZERO);
        require(AeroPassiveRuntime.toggleFlying(player, data) && AeroPassiveRuntime.isFlyingActive(player),
                "real Aero toggle failed to grant paid flight");
        require(attribute.hasModifier(OWN) && player.mayFly() && !player.getAbilities().mayfly,
                "server grant bypassed the native flight attribute");
    }

    private static void retainExternal(ServerPlayer player) {
        var attribute = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        require(AeroPassiveRuntime.isFlyingActive(player) && attribute.hasModifier(OWN), "own flight expired before composition check");
        attribute.addTransientModifier(new AttributeModifier(EXTERNAL, 1, AttributeModifier.Operation.ADD_VALUE));
        require(AeroPassiveRuntime.toggleFlying(player, player.getData(AcademyAttachments.PLAYER_ABILITY)), "real Aero toggle failed to stop");
        require(!attribute.hasModifier(OWN) && attribute.hasModifier(EXTERNAL)
                && player.mayFly() && player.getAbilities().flying, "stopping Aero revoked another source's flight");
    }

    private static void restore(ServerPlayer player) {
        if (original == null) return;
        AeroPassiveRuntime.terminateSustained(player, player.getData(AcademyAttachments.PLAYER_ABILITY));
        player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT).removeModifier(EXTERNAL);
        player.setData(AcademyAttachments.PLAYER_ABILITY, original.data);
        player.setGameMode(original.mode);
        player.teleportTo(original.position.x, original.position.y, original.position.z);
        player.setYRot(original.yaw);
        player.setXRot(original.pitch);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.getAbilities().flying = original.flying && player.mayFly();
        player.onUpdateAbilities();
        original = null;
    }

    private static CompletableFuture<Void> server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        require(server != null, "native flight check needs the existing integrated server");
        var id = mc.player.getUUID();
        return server.submit(() -> {
            var player = server.getPlayerList().getPlayer(id);
            require(player != null, "integrated server player disappeared");
            action.accept(player);
        });
    }

    private static String clientState(Minecraft mc) {
        var attribute = mc.player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        return "own=" + (attribute != null && attribute.hasModifier(OWN))
                + ", external=" + (attribute != null && attribute.hasModifier(EXTERNAL))
                + ", mayFly=" + mc.player.mayFly() + ", flying=" + mc.player.getAbilities().flying
                + ", vanillaMayfly=" + mc.player.getAbilities().mayfly;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    private record Snapshot(PlayerAbilityData data, GameType mode, Vec3 position, float yaw, float pitch, boolean flying) {}
}
