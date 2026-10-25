package com.mohistmc.academy.regression;

import com.mohistmc.academy.gametest.recheck.RecheckPlayers;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillChargingManager;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mohistmc.academy.skill.ability.aerohand.AeroPassiveRuntime;
import com.mohistmc.academy.skill.ability.vecmanip.StormWingEffect;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real attribute composition and production player lifecycle boundaries. */
@GameTestHolder("academy_native_compat")
@PrefixGameTestTemplate(false)
public final class NativeFlightGameTests {
    private static final ResourceLocation OWN = ResourceLocation.parse("academy:aerohand_flying");
    private static final ResourceLocation EXTERNAL = ResourceLocation.parse("academy_audit:external_flight");
    private static final ResourceLocation DENY = ResourceLocation.parse("academy_audit:flight_denied");

    @GameTest(template = "empty", batch = "native_flight")
    public static void survivalFlightIsTransientAndRevokesOnDeactivation(GameTestHelper h) {
        try (var fixture = connect(h)) {
            var player = fixture.player();
            var data = ability(player);
            h.assertTrue(!player.mayFly(), "Fresh survival fixture already has flight");
            start(h, player, data);
            var attribute = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
            h.assertTrue(attribute.hasModifier(OWN) && player.mayFly() && player.getAbilities().flying,
                    "Ability did not grant effective survival flight through its attribute");
            h.assertTrue(!player.getAbilities().mayfly, "Ability overwrote the vanilla game-mode permission");
            h.assertTrue(attribute.save().getList("Modifiers", 10).isEmpty(),
                    "Temporary ability flight would be persisted as a permanent attribute modifier");
            AeroPassiveRuntime.playerTick(new PlayerTickEvent.Post(player));
            AeroPassiveRuntime.playerTick(new PlayerTickEvent.Post(player));
            h.assertTrue(attribute.getModifiers().size() == 1, "Repeated ticks stacked duplicate flight grants");
            data.setAbilityActive(false);
            AeroPassiveRuntime.playerTick(new PlayerTickEvent.Post(player));
            h.assertTrue(!attribute.hasModifier(OWN) && !player.mayFly() && !player.getAbilities().flying
                            && !AeroPassiveRuntime.isFlyingActive(player),
                    "Deactivation retained the ability's permission, motion state, or paid context");
            h.succeed();
        }
    }

    @GameTest(template = "empty", batch = "native_flight")
    public static void externalGrantsBeforeAndDuringAbilityRemainIndependent(GameTestHelper h) {
        try (var fixture = connect(h)) {
            var player = fixture.player();
            var data = ability(player);
            external(player);
            start(h, player, data);
            stop(h, player, data);
            assertExternalFlight(h, player, "Grant present before activation");

            start(h, player, data);
            var attribute = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
            attribute.removeModifier(EXTERNAL);
            h.assertTrue(attribute.hasModifier(OWN) && player.mayFly(),
                    "Removing another source also revoked the active skill's own permission");
            external(player);
            stop(h, player, data);
            assertExternalFlight(h, player, "Grant added during activation");
            h.succeed();
        }
    }

    @GameTest(template = "empty", batch = "native_flight")
    public static void creativeAndSpectatorTransitionsKeepGameModePermission(GameTestHelper h) {
        try (var fixture = connect(h)) {
            var player = fixture.player();
            var data = ability(player);
            for (var mode : new GameType[] {GameType.CREATIVE, GameType.SPECTATOR}) {
                start(h, player, data);
                h.assertTrue(player.setGameMode(mode), "Game-mode fixture did not transition to " + mode);
                stop(h, player, data);
                h.assertTrue(player.mayFly() && player.getAbilities().flying
                                && !player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT).hasModifier(OWN),
                        "Stopping a survival-started skill revoked " + mode + " flight");

                start(h, player, data);
                h.assertTrue(player.setGameMode(GameType.SURVIVAL), "Return to survival failed");
                h.assertTrue(player.mayFly() && player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT).hasModifier(OWN),
                        "Game-mode transition lost the active skill's independent grant");
                // Survival mode itself stops current flight. Exercise subsequent player flight.
                player.getAbilities().flying = true;
                stop(h, player, data);
                h.assertTrue(!player.mayFly() && !player.getAbilities().flying,
                        "Game-mode-started skill leaked a permission after returning to survival");
            }
            h.succeed();
        }
    }

    @GameTest(template = "empty", batch = "native_flight")
    public static void nativeFlightDenialIsNotBypassedWithMayfly(GameTestHelper h) {
        try (var fixture = connect(h)) {
            var player = fixture.player();
            var data = ability(player);
            var attribute = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
            attribute.addTransientModifier(new AttributeModifier(DENY, -1,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            start(h, player, data);
            h.assertTrue(attribute.hasModifier(OWN) && !player.mayFly() && !player.getAbilities().flying,
                    "Ability bypassed the native disabling modifier through a direct abilities write");
            stop(h, player, data);
            h.assertTrue(attribute.hasModifier(DENY) && !attribute.hasModifier(OWN),
                    "Ability cleanup removed another source's denial modifier");
            h.succeed();
        }
    }

    @GameTest(template = "empty", batch = "native_flight")
    public static void actualDimensionChangeRemovesOnlyAbilityFlight(GameTestHelper h) {
        try (var fixture = connect(h)) {
            var player = fixture.player();
            var data = ability(player);
            external(player);
            start(h, player, data);
            var destination = player.server.getLevel(Level.NETHER);
            h.assertTrue(destination != null, "Nether fixture missing");
            player.teleportTo(destination, player.getX(), 200, player.getZ(), Set.of(), 0, 0);
            h.assertTrue(player.serverLevel() == destination && !AeroPassiveRuntime.isFlyingActive(player),
                    "Actual dimension transfer did not cancel the paid flight context");
            assertExternalFlight(h, player, "Actual dimension transfer");
            h.succeed();
        }
    }

    @GameTest(template = "empty", batch = "native_flight")
    public static void actualLogoutRemovesOnlyAbilityFlightBeforeSave(GameTestHelper h) {
        try (var fixture = connect(h)) {
            var player = fixture.player();
            var data = ability(player);
            external(player);
            start(h, player, data);
            player.server.getPlayerList().remove(player);
            h.assertTrue(player.server.getPlayerList().getPlayer(player.getUUID()) == null
                            && !AeroPassiveRuntime.isFlyingActive(player),
                    "Actual logout retained the player or paid context");
            assertExternalFlight(h, player, "Actual logout");
            h.succeed();
        }
    }

    @GameTest(template = "empty", batch = "native_flight")
    public static void stormWingDimensionCleanupKeepsLaterNativeFlightGrant(GameTestHelper h) {
        try (var fixture = connect(h)) {
            var player = fixture.player();
            var data = ability(player);
            data.setCurrentAbility(AbilityCategory.VECMANIP);
            data.learnSkill("storm_wing");
            data.setProficiency("storm_wing", 1);
            var wing = (StormWingEffect) SkillRegistry.getSkill("storm_wing").getEffect();
            try {
                wing.onChargingStart(player, data);
                SkillChargingManager.startCharging(player.getUUID(), 0, "storm_wing", 1,
                        player.serverLevel().getGameTime());
                h.assertTrue(player.mayFly() && !player.getAbilities().flying,
                        "StormWing must grant its legacy permission without starting vanilla flight");
                external(player);
                player.getAbilities().flying = true;
                var destination = player.server.getLevel(Level.NETHER);
                h.assertTrue(destination != null, "Nether fixture missing");
                player.teleportTo(destination, player.getX(), 200, player.getZ(), Set.of(), 0, 0);
                h.assertTrue(player.serverLevel() == destination && !SkillChargingManager.isCharging(player.getUUID())
                                && !wing.canRelease(player, data, 100) && !player.getAbilities().mayfly,
                        "Real dimension event did not cancel StormWing's context and legacy permission");
                assertExternalFlight(h, player, "StormWing dimension cleanup after another source granted flight");
                h.succeed();
            } finally {
                SkillChargingManager.cancel(player);
                wing.onChargingAbort(player, data);
            }
        }
    }

    @GameTest(template = "empty", batch = "native_flight")
    public static void stormWingCannotRestoreFlightAfterExternalPermissionExpires(GameTestHelper h) {
        try (var fixture = connect(h)) {
            var player = fixture.player();
            var data = ability(player);
            data.setCurrentAbility(AbilityCategory.VECMANIP);
            data.learnSkill("storm_wing");
            data.setProficiency("storm_wing", 1);
            external(player);
            player.getAbilities().flying = true;
            var wing = (StormWingEffect) SkillRegistry.getSkill("storm_wing").getEffect();
            try {
                h.assertTrue(player.mayFly() && !player.getAbilities().mayfly,
                        "Existing flight must come solely from the external attribute");
                wing.onChargingStart(player, data);
                SkillChargingManager.startCharging(player.getUUID(), 0, "storm_wing", 1,
                        player.serverLevel().getGameTime());
                player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT).removeModifier(EXTERNAL);
                h.assertTrue(player.mayFly() && player.getAbilities().flying,
                        "Active StormWing lost its own permission when another grant expired");
                SkillChargingManager.cancel(player);
                h.assertTrue(!SkillChargingManager.isCharging(player.getUUID())
                                && !wing.canRelease(player, data, 100) && !player.mayFly()
                                && !player.getAbilities().mayfly && !player.getAbilities().flying,
                        "StormWing restored the initial flying state after its external permission expired");
                h.succeed();
            } finally {
                SkillChargingManager.cancel(player);
                wing.onChargingAbort(player, data);
            }
        }
    }

    @GameTest(template = "empty", batch = "native_flight", timeoutTicks = 100)
    public static void realDeathAndRespawnDoNotRetainAbilityFlight(GameTestHelper h) {
        var fixture = connect(h);
        var player = fixture.player();
        var data = ability(player);
        external(player);
        start(h, player, data);
        // Ordinary ServerPlayers have 60 ticks of login protection; keep the real damage path.
        h.runAfterDelay(65, () -> {
            try {
                h.assertTrue(AeroPassiveRuntime.isFlyingActive(player) && player.mayFly(),
                        "Natural player ticks lost the positive flight fixture before death");
                player.invulnerableTime = 0;
                player.setHealth(.01F);
                h.assertTrue(player.hurt(player.damageSources().genericKill(), 10) && !player.isAlive(),
                        "Real lethal damage did not produce a confirmed death");
                h.assertTrue(!AeroPassiveRuntime.isFlyingActive(player), "Death retained the paid flight context");
                assertExternalFlight(h, player, "Confirmed death cleanup");
                var replacement = player.server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
                h.assertTrue(replacement != player && replacement.getClass() == ServerPlayer.class,
                        "Respawn did not construct an ordinary replacement player");
                h.assertTrue(!replacement.getAttribute(NeoForgeMod.CREATIVE_FLIGHT).hasModifier(OWN)
                                && !replacement.mayFly() && !replacement.getAbilities().flying
                                && !AeroPassiveRuntime.isFlyingActive(replacement),
                        "Respawn copied a transient permission or resumed the previous paid context");
                h.succeed();
            } finally { fixture.close(); }
        });
    }

    private static PlayerAbilityData ability(ServerPlayer player) {
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(AbilityCategory.AEROHAND);
        data.setPlayerLevel(5);
        data.learnSkill("flying");
        data.setAbilityActive(true);
        data.setDevMode(false);
        data.setCurrentCp(8000);
        data.setCurrentOverload(0);
        return data;
    }

    private static void external(ServerPlayer player) {
        player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT).addTransientModifier(
                new AttributeModifier(EXTERNAL, 1, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void start(GameTestHelper h, ServerPlayer player, PlayerAbilityData data) {
        h.assertTrue(AeroPassiveRuntime.toggleFlying(player, data) && AeroPassiveRuntime.isFlyingActive(player),
                "Paid flying skill could not activate");
    }

    private static void stop(GameTestHelper h, ServerPlayer player, PlayerAbilityData data) {
        h.assertTrue(AeroPassiveRuntime.toggleFlying(player, data) && !AeroPassiveRuntime.isFlyingActive(player),
                "Paid flying skill could not deactivate");
    }

    private static void assertExternalFlight(GameTestHelper h, ServerPlayer player, String action) {
        var attribute = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        h.assertTrue(attribute.hasModifier(EXTERNAL) && !attribute.hasModifier(OWN)
                        && player.mayFly() && player.getAbilities().flying,
                action + " removed or interrupted another source's flight");
    }

    private static Fixture connect(GameTestHelper h) {
        var fixture = new Fixture(RecheckPlayers.connect(h));
        h.testInfo.addListener(fixture);
        return fixture;
    }

    /** Owns only this test's player/channel, including its replacement after respawn. */
    private static final class Fixture implements AutoCloseable, GameTestListener {
        private final RecheckPlayers.Session session;
        private boolean closed;
        private Fixture(RecheckPlayers.Session session) { this.session = session; }
        private ServerPlayer player() { return session.player(); }
        @Override public void close() {
            if (closed) return;
            closed = true;
            try {
                var original = player();
                var current = original.server.getPlayerList().getPlayer(original.getUUID());
                if (current != null && current != original) original.server.getPlayerList().remove(current);
            } finally { session.close(); }
        }
        @Override public void testStructureLoaded(GameTestInfo info) {}
        @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo previous, GameTestInfo next, GameTestRunner runner) { close(); }
    }
}
