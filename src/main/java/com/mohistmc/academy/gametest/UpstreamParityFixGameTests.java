package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.aerohand.AeroPassiveRuntime;
import com.mohistmc.academy.skill.ability.aerohand.AeroProjectileRules;
import com.mohistmc.academy.skill.ability.telekinesis.TelekinesisPassiveHandler;
import com.mohistmc.academy.skill.ability.vecmanip.StormWingEffect;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regressions for behaviour that diverged from AcademyCraft 1.12.2 / ExtraAcC. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class UpstreamParityFixGameTests {
    private static final String EMPTY = "empty";
    private UpstreamParityFixGameTests() {}

    private static PlayerAbilityData ability(ServerPlayer player, AbilityCategory category, String... learned) {
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(category);
        data.setAbilityActive(true);
        data.setDevMode(true);
        for (String id : learned) data.learnSkill(id);
        player.setData(AcademyAttachments.PLAYER_ABILITY, data);
        return data;
    }

    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setYRot(0);
        player.setXRot(0);
        player.setDeltaMovement(Vec3.ZERO);
        return player;
    }

    private static <T extends Entity> T spawnBeside(GameTestHelper helper, ServerPlayer player, T entity) {
        entity.setPos(player.getX() + 1, player.getY() + 0.5, player.getZ());
        entity.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    @GameTest(template = EMPTY)
    public static void stormWingFliesTowardReportedMovementKeys(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        PlayerAbilityData data = ability(player, AbilityCategory.VECMANIP, "storm_wing");
        StormWingEffect effect = new StormWingEffect();
        effect.onChargingStart(player, data);
        // Real servers never fill xxa/zza for an unmounted player; the packet is the only input.
        StormWingEffect.acceptInput(player, (byte) 1, (byte) 0);
        int activation = effect.getMinChargeTicks(data);
        if (!effect.onChargingTick(player, data, activation)) {
            helper.fail("storm wing failed its activation tick"); return;
        }
        Vec3 motion = player.getDeltaMovement();
        effect.onChargingRelease(player, data, activation);
        // Yaw 0 / pitch 0 looks along +Z; one tick approaches the target by ACCEL = .16.
        if (motion.z < 0.15 || Math.abs(motion.x) > 1.0e-3) {
            helper.fail("forward key did not accelerate storm wing along the look vector: " + motion); return;
        }
        StormWingEffect.acceptInput(player, (byte) 1, (byte) 0);
        player.setDeltaMovement(Vec3.ZERO);
        effect.onChargingStart(player, data);
        effect.onChargingTick(player, data, activation);
        Vec3 idle = player.getDeltaMovement();
        effect.onChargingRelease(player, data, activation);
        if (Math.abs(idle.z) > 1.0e-3) {
            helper.fail("input sent outside a flight leaked into the next one: " + idle); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void psychoHardenReleaseLeavesOnlyLightSlowness(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        PlayerAbilityData data = ability(player, AbilityCategory.TELEKINESIS, "psycho_harden");
        if (!TelekinesisPassiveHandler.togglePsychoHarden(player, data)) {
            helper.fail("psycho harden did not start"); return;
        }
        TelekinesisPassiveHandler.playerTick(new PlayerTickEvent.Post(player));
        MobEffectInstance held = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (held == null || held.getAmplifier() != 9) {
            helper.fail("hardening did not apply its Slowness X lock"); return;
        }
        TelekinesisPassiveHandler.togglePsychoHarden(player, data);
        MobEffectInstance after = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (after == null || after.getAmplifier() != 4) {
            helper.fail("releasing harden kept the player frozen: "
                    + (after == null ? "none" : "amplifier " + after.getAmplifier())); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void offenseArmourIgnoresDropsAndKeepsTridents(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        PlayerAbilityData data = ability(player, AbilityCategory.AEROHAND, "offense_armour");
        if (!AeroPassiveRuntime.toggleOffenseArmour(player, data)) {
            helper.fail("offense armour did not start"); return;
        }
        ItemEntity drop = spawnBeside(helper, player,
                new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.DIAMOND)));
        Projectile trident = spawnBeside(helper, player, EntityType.TRIDENT.create(helper.getLevel()));
        Projectile arrow = spawnBeside(helper, player, EntityType.ARROW.create(helper.getLevel()));
        AeroPassiveRuntime.playerTick(new PlayerTickEvent.Post(player));
        AeroPassiveRuntime.toggleOffenseArmour(player, data);
        if (drop.getDeltaMovement().lengthSqr() > 1.0e-6) {
            helper.fail("offense armour flung a dropped item: " + drop.getDeltaMovement()); return;
        }
        if (!trident.isAlive()) {
            helper.fail("offense armour deleted a trident and its item"); return;
        }
        if (arrow.isAlive()) {
            helper.fail("offense armour no longer clears ordinary arrows"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aeroBarriersClearOnlyLegacyProjectiles(GameTestHelper helper) {
        var level = helper.getLevel();
        if (!AeroProjectileRules.clearsLikeLegacy(EntityType.ARROW.create(level))
                || !AeroProjectileRules.clearsLikeLegacy(EntityType.SNOWBALL.create(level))) {
            helper.fail("arrows and thrown items must still be cleared"); return;
        }
        if (AeroProjectileRules.clearsLikeLegacy(EntityType.TRIDENT.create(level))
                || AeroProjectileRules.clearsLikeLegacy(EntityType.SMALL_FIREBALL.create(level))
                || AeroProjectileRules.clearsLikeLegacy(EntityType.WIND_CHARGE.create(level))) {
            helper.fail("tridents and fireball-type projectiles were never IProjectile in 1.12"); return;
        }
        helper.succeed();
    }
}
