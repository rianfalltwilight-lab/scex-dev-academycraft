package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.telekinesis.TelekinesisPassiveHandler;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import com.mohistmc.academy.skill.ability.telekinesis.LiquidShadowEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** Natural world ticks move the summoned shadow; no shadow repositioning, tick(), or move() calls. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class LiquidShadowMovementGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private LiquidShadowMovementGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 90, batch = "liquid_shadow_motion_open")
    public static void shadowPursuesFiveMetresAndPaysForAttack(GameTestHelper helper) { pursue(helper, false, false); }

    @GameTest(template = "empty", timeoutTicks = 90, batch = "liquid_shadow_motion_glass")
    public static void pursuingShadowStopsAtGlassCollision(GameTestHelper helper) { pursue(helper, true, false); }

    @GameTest(template = "empty", timeoutTicks = 90, batch = "liquid_shadow_motion_short_cp")
    public static void pursuingShadowCannotHurtWhenAttackCpIsShort(GameTestHelper helper) { pursue(helper, false, true); }

    private static void pursue(GameTestHelper helper, boolean wall, boolean shortCp) {
        var level = helper.getLevel();
        // Keep the whole pursuit platform, including its north end at z=-2, inside
        // the template's forced chunks even when the template starts on a chunk edge.
        var fixtureOffset = new BlockPos(16, 0, 16);
        var origin = helper.absolutePos(fixtureOffset);
        for (int x = 0; x < 6; x++) for (int z = -2; z < 9; z++) {
            helper.setBlock(fixtureOffset.offset(x, 1, z), Blocks.STONE);
            for (int y = 2; y < 6; y++) helper.setBlock(fixtureOffset.offset(x, y, z), Blocks.AIR);
        }
        if (wall) {
            // Keep the pursuit on its raised platform: otherwise vanilla navigation can
            // walk off either edge and pass below the elevated glass without crossing it.
            for (int z = -2; z < 9; z++) for (int y = 2; y < 6; y++) {
                helper.setBlock(fixtureOffset.offset(-1, y, z), Blocks.STONE);
                helper.setBlock(fixtureOffset.offset(6, y, z), Blocks.STONE);
            }
            for (int x = 0; x < 6; x++) for (int y = 2; y < 6; y++) {
                helper.setBlock(fixtureOffset.offset(x, y, -2), Blocks.STONE);
                helper.setBlock(fixtureOffset.offset(x, y, 5), Blocks.GLASS_PANE);
            }
        }
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[ShadowMotion]"));
        player.setGameMode(GameType.SURVIVAL);
        // The summoned shadow starts 1.6 blocks behind its owner on the shifted platform.
        player.setPos(origin.getX() + 2.5, origin.getY() + 2, origin.getZ() + 2.5);
        player.setYRot(0); player.setXRot(0);
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(AbilityCategory.TELEKINESIS); data.setPlayerLevel(5);
        data.setAbilityActive(true); data.setDevMode(false); data.learnSkill("liquid_shadow");
        data.setCurrentCp(8000); data.setCurrentOverload(0); data.setProficiency("liquid_shadow", 0);
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(Items.WATER_BUCKET));
        check(helper, TelekinesisPassiveHandler.toggleLiquidShadow(player), "shadow fixture did not summon");
        LiquidShadowEntity shadow = level.getEntitiesOfClass(LiquidShadowEntity.class, player.getBoundingBox().inflate(8),
                e -> e.getTags().contains("academy_liquid_shadow_owner_" + player.getUUID())).getFirst();
        Vec3 start = shadow.position();
        Villager target = EntityType.VILLAGER.create(level);
        target.setPos(player.getX(), player.getY(), player.getZ() + 5);
        target.setNoAi(true); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        target.setHealth(200);
        check(helper, level.addFreshEntity(target), "target spawn was rejected");
        player.tickCount = 10;
        player.setLastHurtMob(target);
        check(helper, shadow.distanceToSqr(target) > 25, "target must require several metres of real pursuit");
        data.setCurrentCp(shortCp ? 50 : 5000);
        float startCp = data.getCurrentCp();
        int[] ownerTicks = {0};
        boolean[] finished = {false};
        helper.onEachTick(() -> {
            if (finished[0]) return;
            // Fake players have no connection-driven player loop. Invoke that production event once
            // per real GameTest tick; the level itself, not this fixture, must tick/move the shadow.
            player.tickCount++;
            ownerTicks[0]++;
            TelekinesisPassiveHandler.playerTick(new PlayerTickEvent.Post(player));
        });
        helper.runAfterDelay(60, () -> {
            finished[0] = true;
            try {
                double distanceMoved = shadow.position().distanceTo(start);
                float damage = 200 - target.getHealth();
                LOGGER.info("SHADOW_MOVEMENT_OBSERVATION wall={} shortCP={} entityTicks={} ownerTicks={} moved={} targetDistance={} damage={} cp={} startCP={}",
                        wall, shortCp, shadow.tickCount, ownerTicks[0], distanceMoved, shadow.distanceTo(target), damage,
                        data.getCurrentCp(), startCp);
                int intactGlass = 0;
                if (wall) for (int x = 0; x < 6; x++) for (int y = 2; y < 6; y++) {
                    BlockPos pos = helper.absolutePos(fixtureOffset.offset(x, y, 5));
                    var state = level.getBlockState(pos);
                    if (state.is(Blocks.GLASS_PANE) && !state.getCollisionShape(level, pos).isEmpty()) intactGlass++;
                }
                LOGGER.info("SHADOW_MOVEMENT_GEOMETRY wall={} startRelative={} endRelative={} boundsRelative={} intactGlass={}/{}",
                        wall, start.subtract(origin.getX(), origin.getY(), origin.getZ()),
                        shadow.position().subtract(origin.getX(), origin.getY(), origin.getZ()),
                        shadow.getBoundingBox().move(-origin.getX(), -origin.getY(), -origin.getZ()),
                        intactGlass, wall ? 24 : 0);
                check(helper, shadow.tickCount >= (shortCp ? 5 : 45), "the actual world did not tick the shadow fixture");
                if (wall) check(helper, shadow.getTarget() == target,
                        "owner's marked target behind glass never entered the fixed target goal");
                check(helper, distanceMoved > 3, "summoned shadow never navigated toward its marked target");
                if (wall) {
                    check(helper, intactGlass == 24, "the isolated glass collision wall was modified during pursuit");
                    check(helper, shadow.getBoundingBox().maxZ <= origin.getZ() + 5.4375 + .001,
                            "shadow crossed a real glass-pane collision shape");
                    check(helper, damage == 0 && Math.abs(data.getCurrentCp() - (startCp - ownerTicks[0])) < .01,
                            "shadow damaged or paid attack CP through its collision wall");
                } else if (shortCp) {
                    check(helper, damage == 0 && !TelekinesisPassiveHandler.hasLiquidShadow(player)
                            && data.getCurrentCp() >= 0, "pursuing shadow attacked without complete prepaid CP");
                } else {
                    check(helper, damage >= 20, "shadow approached but did not attack its visible marked target");
                    check(helper, Math.abs((startCp - data.getCurrentCp()) - ownerTicks[0] - damage * 5) < .1,
                            "natural shadow pursuit/attacks did not settle upkeep and attack CP exactly");
                    shadow.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                    check(helper, !TelekinesisPassiveHandler.hasLiquidShadow(player) && !shadow.shouldBeSaved(),
                            "moving shadow retained a session or became persistent when unloaded");
                }
                LOGGER.info("SHADOW_MOVEMENT_PASS wall={} shortCP={} real pursuit, collision, resource boundary and lifecycle", wall, shortCp);
                helper.succeed();
            } finally {
                TelekinesisPassiveHandler.logout(new PlayerEvent.PlayerLoggedOutEvent(player));
                target.discard();
            }
        });
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}
