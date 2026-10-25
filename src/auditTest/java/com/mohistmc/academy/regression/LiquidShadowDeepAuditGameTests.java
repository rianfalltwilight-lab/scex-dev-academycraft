package com.mohistmc.academy.regression;

import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.ability.telekinesis.LiquidShadowEntity;
import com.mohistmc.academy.skill.ability.telekinesis.TelekinesisPassiveHandler;
import com.mohistmc.academy.world.AcademyEntities;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Natural entity ticks exercise fixed goals/navigation; resource tests call the actual melee boundary. */
@GameTestHolder("academy_audit")
@PrefixGameTestTemplate(false)
public final class LiquidShadowDeepAuditGameTests {
    private LiquidShadowDeepAuditGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ownerAttackNavigatesAroundObstacleAndPays(GameTestHelper h) {
        ServerPlayer p = owner(h);
        LiquidShadowEntity shadow = summon(h, p);
        Villager target = target(h, p.position().add(0, 0, 7));
        Vec3 start = shadow.position();
        // The target is reachable around the east end, but not through this solid pane wall.
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        for (int x = 0; x <= 6; x++) for (int y = 24; y <= 27; y++)
            h.setBlock(new BlockPos(x, y, 7), Blocks.GLASS_PANE);
        p.tickCount = 10;
        p.setLastHurtMob(target);
        var data = p.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setProficiency("liquid_shadow", 0);
        data.setCurrentCp(5000);
        int[] ticks = {0};
        double[] farthestX = {shadow.getX()};
        AtomicBoolean done = new AtomicBoolean();
        h.onEachTick(() -> {
            if (done.get()) return;
            p.tickCount++;
            TelekinesisPassiveHandler.playerTick(new PlayerTickEvent.Post(p));
            ticks[0]++;
            farthestX[0] = Math.max(farthestX[0], shadow.getX());
        });
        h.runAfterDelay(75, () -> {
            done.set(true);
            try {
                float damage = 500 - target.getHealth();
                h.assertTrue(shadow.getType() == AcademyEntities.LIQUID_SHADOW.get() && !shadow.isNoAi(), "Summon is not registered AI entity");
                h.assertTrue(shadow.getTarget() == target, "Owner attack behind cover never entered the fixed target goal");
                h.assertTrue(shadow.position().distanceTo(start) > 5 && farthestX[0] > origin.getX() + 6.5,
                        "Vanilla navigation did not walk around the obstacle: start=" + start
                                + " end=" + shadow.position() + " farthestX=" + farthestX[0]
                                + " damage=" + damage + " path=" + shadow.getNavigation().getPath());
                h.assertTrue(damage >= 20, "Owner attack goal did not navigate into a paid melee hit");
                // Proficiency changes slightly after each successful hit, so compare actual damage settlement.
                h.assertTrue(Math.abs(5000 - data.getCurrentCp() - ticks[0] - damage * 5) < .2,
                        "Melee or upkeep cost was skipped/duplicated");
                h.assertTrue(goals(shadow, "goalSelector") == 5 && goals(shadow, "targetSelector") == 3,
                        "Runtime navigation mutated the fixed goal set");
                h.succeed();
            } finally { target.discard(); close(p); }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 45)
    public static void ownerDefenseThenSelfDefenseSelectRealTargets(GameTestHelper h) {
        ServerPlayer p = owner(h);
        LiquidShadowEntity shadow = summon(h, p);
        Villager ownerAttacker = target(h, p.position().add(5, 0, 3));
        Villager selfAttacker = target(h, p.position().add(-3, 0, 2));
        AtomicBoolean done = new AtomicBoolean();
        h.onEachTick(() -> {
            if (!done.get()) { p.tickCount++; TelekinesisPassiveHandler.playerTick(new PlayerTickEvent.Post(p)); }
        });
        p.tickCount = 10;
        p.setLastHurtByMob(ownerAttacker);
        h.runAfterDelay(5, () -> {
            try {
                h.assertTrue(shadow.getTarget() == ownerAttacker, "Owner hurt-by goal did not defend owner");
                ownerAttacker.discard();
                p.setLastHurtByMob(null);
                shadow.setTarget(null);
                h.assertTrue(shadow.hurt(shadow.damageSources().mobAttack(selfAttacker), 1), "Self-defense damage fixture rejected");
            } catch (RuntimeException | AssertionError error) {
                done.set(true); ownerAttacker.discard(); selfAttacker.discard(); close(p); throw error;
            }
        });
        h.runAfterDelay(12, () -> {
            done.set(true);
            try {
                h.assertTrue(shadow.getTarget() == selfAttacker, "Shadow's own hurt-by goal never retaliated");
                h.assertTrue(goals(shadow, "goalSelector") == 5 && goals(shadow, "targetSelector") == 3,
                        "Changing targets added/removed AI goals");
                h.succeed();
            } finally { ownerAttacker.discard(); selfAttacker.discard(); close(p); }
        });
    }

    @GameTest(template = "empty")
    public static void meleePrepaymentAndPvpVetoKeepNegativeControls(GameTestHelper h) {
        ServerPlayer p = owner(h);
        ServerPlayer other = FollowupGameTests.player(h, AbilityCategory.TELEKINESIS);
        LiquidShadowEntity shadow = summon(h, p);
        Villager victim = target(h, shadow.position().add(0, 0, .9));
        var data = p.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setProficiency("liquid_shadow", 0);
        boolean oldPvp = ACConfig.Server.PVP_ENABLED.get();
        try {
            other.setPos(shadow.position().add(.9, 0, 0));
            ACConfig.Server.PVP_ENABLED.set(false);
            float before = data.getCurrentCp();
            h.assertTrue(!shadow.canAttack(p) && !shadow.canAttack(other) && !shadow.doHurtTarget(other), "Owned AI bypassed player target policy");
            h.assertTrue(data.getCurrentCp() == before && other.getHealth() == other.getMaxHealth(), "Rejected player attack charged or damaged");
            data.setCurrentCp(100);
            h.assertTrue(shadow.doHurtTarget(victim) && victim.getHealth() == 480 && data.getCurrentCp() == 0,
                    "Paid vanilla melee boundary failed positive control");
            victim.invulnerableTime = 0;
            data.setCurrentCp(50);
            h.assertTrue(!shadow.doHurtTarget(victim) && victim.getHealth() == 480 && data.getCurrentCp() == 50,
                    "Insufficient CP caused unpaid damage or partial debit");
            h.assertTrue(!TelekinesisPassiveHandler.hasLiquidShadow(p), "Unpayable attack did not terminate session");
            h.succeed();
        } finally { ACConfig.Server.PVP_ENABLED.set(oldPvp); victim.discard(); close(p); other.server.getPlayerList().remove(other); }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void waterTerminatesEntityAndOwnerSession(GameTestHelper h) {
        ServerPlayer p = owner(h);
        LiquidShadowEntity shadow = summon(h, p);
        h.getLevel().setBlock(shadow.blockPosition(), Blocks.WATER.defaultBlockState(), 3);
        h.runAfterDelay(4, () -> {
            try {
                h.assertTrue(!shadow.isAlive() && !TelekinesisPassiveHandler.hasLiquidShadow(p), "Touching water left a live shadow session");
                h.assertTrue(!shadow.shouldBeSaved() && !shadow.save(new CompoundTag()), "Transient AI mob became persistent");
                h.succeed();
            } finally { close(p); }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 55)
    public static void idleFollowerUsesOwnerNavigationWithoutAttackTarget(GameTestHelper h) {
        ServerPlayer p = owner(h);
        LiquidShadowEntity shadow = summon(h, p);
        p.setPos(p.position().add(10, 0, 0));
        double initial = shadow.distanceToSqr(p);
        AtomicBoolean done = new AtomicBoolean();
        h.onEachTick(() -> {
            if (!done.get()) { p.tickCount++; TelekinesisPassiveHandler.playerTick(new PlayerTickEvent.Post(p)); }
        });
        h.runAfterDelay(35, () -> {
            done.set(true);
            try {
                h.assertTrue(shadow.getTarget() == null && shadow.distanceToSqr(p) < initial / 2,
                        "Idle follower did not follow its owner using navigation");
                h.assertTrue(shadow.getMaxHealth() == 100 && shadow.getAttributeValue(Attributes.MOVEMENT_SPEED) == 1,
                        "Waterman upstream attributes changed");
                h.succeed();
            } finally { close(p); }
        });
    }

    private static ServerPlayer owner(GameTestHelper h) {
        for (int x = -2; x < 20; x++) for (int z = -2; z < 20; z++) {
            h.setBlock(new BlockPos(x, 23, z), Blocks.STONE);
            for (int y = 24; y <= 27; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        ServerPlayer p = FollowupGameTests.player(h, AbilityCategory.TELEKINESIS);
        var data = p.getData(AcademyAttachments.PLAYER_ABILITY);
        data.learnSkill("liquid_shadow"); data.setDevMode(false); data.setCurrentCp(8000); data.setCurrentOverload(0);
        p.getInventory().add(new ItemStack(Items.WATER_BUCKET));
        return p;
    }

    private static LiquidShadowEntity summon(GameTestHelper h, ServerPlayer p) {
        h.assertTrue(TelekinesisPassiveHandler.toggleLiquidShadow(p), "Shadow fixture failed to summon");
        return h.getLevel().getEntitiesOfClass(LiquidShadowEntity.class, p.getBoundingBox().inflate(5),
                e -> p.getUUID().equals(e.getOwnerUUID())).getFirst();
    }

    private static Villager target(GameTestHelper h, Vec3 pos) {
        Villager target = EntityType.VILLAGER.create(h.getLevel());
        target.setPos(pos); target.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        target.setHealth(500);
        h.assertTrue(h.getLevel().addFreshEntity(target), "Target fixture rejected");
        return target;
    }

    private static int goals(Mob mob, String name) {
        try {
            var field = Mob.class.getDeclaredField(name); field.setAccessible(true);
            return ((GoalSelector) field.get(mob)).getAvailableGoals().size();
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void close(ServerPlayer p) {
        TelekinesisPassiveHandler.logout(new PlayerEvent.PlayerLoggedOutEvent(p));
        p.server.getPlayerList().remove(p);
    }
}
