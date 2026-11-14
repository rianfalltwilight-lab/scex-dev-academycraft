package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.api.mob.AcademyMobAbilities;
import com.mohistmc.academy.api.mob.AcademyMobAbilities.CastOptions;
import com.mohistmc.academy.api.mob.AcademyMobAbilities.Result;
import com.mohistmc.academy.entity.MdBallEntity;
import com.mohistmc.academy.world.entity.CoinEntity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Mob casts of the charged / sustained electromaster and meltdowner skills: railgun, thunder_clap,
 * body_intensify, meltdowner, scatter_bomb, electron_missile and jet_engine.  A still husk aims at a
 * pig four blocks away (same-type mobs are friendly by default).
 */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MobCastChargeElectromasterGameTests {
    private static final String EMPTY = "empty";
    private MobCastChargeElectromasterGameTests() {}

    private static <T extends Mob> T spawn(GameTestHelper helper, EntityType<T> type, double x, double y, double z) {
        T mob = type.create(helper.getLevel());
        if (mob == null) throw new IllegalStateException("could not create " + type);
        Vec3 pos = helper.absoluteVec(new Vec3(x, y, z));
        mob.moveTo(pos.x, pos.y, pos.z, 0F, 0F);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static Mob caster(GameTestHelper helper) {
        return spawn(helper, EntityType.HUSK, 2.5, 2, 2.5);
    }

    private static Mob target(GameTestHelper helper) {
        return spawn(helper, EntityType.PIG, 2.5, 2, 6.5);
    }

    private static boolean hurt(LivingEntity entity, float before) {
        return !entity.isAlive() || entity.getHealth() < before;
    }

    private static CastOptions options(LivingEntity target) {
        return CastOptions.at(target).withProficiency(1F);
    }

    /** Starts a charge (must be CHARGING); an immediate re-cast must be ALREADY_CHARGING. */
    private static boolean startCharge(GameTestHelper helper, Mob caster, String skill, CastOptions options) {
        Result first = AcademyMobAbilities.cast(caster, skill, options);
        if (first != Result.CHARGING) { helper.fail(skill + " mob cast returned " + first + ", expected CHARGING"); return false; }
        Result second = AcademyMobAbilities.cast(caster, skill, options);
        if (second != Result.ALREADY_CHARGING) {
            helper.fail(skill + " immediate re-cast returned " + second + ", expected ALREADY_CHARGING");
            return false;
        }
        return true;
    }

    /** After release the skill must be on cooldown for the caster. */
    private static void assertCooldown(GameTestHelper helper, Mob caster, String skill, CastOptions options) {
        Result again = AcademyMobAbilities.cast(caster, skill, options);
        helper.assertTrue(again == Result.ON_COOLDOWN, skill + " re-cast after release returned " + again);
    }

    @GameTest(template = EMPTY, batch = "mob_cast_charge", timeoutTicks = 80)
    public static void mobRailgunChargesHitsTargetAndLeavesNoCoin(GameTestHelper helper) {
        Mob caster = caster(helper);
        Mob pig = target(helper);
        // Sturdy enough to survive the shot, so no loot drop is mistaken for railgun ammunition.
        pig.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
        pig.setHealth(1000);
        float before = pig.getHealth();
        CastOptions options = options(pig);
        if (!startCharge(helper, caster, "railgun", options)) return;
        AABB area = caster.getBoundingBox().inflate(32);
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(pig, before), "mob Railgun did not damage its target");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(CoinEntity.class, area).isEmpty(),
                    "mob Railgun left a coin entity");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).isEmpty(),
                    "mob Railgun left an item entity");
            assertCooldown(helper, caster, "railgun", options);
        });
    }

    @GameTest(template = EMPTY, batch = "mob_cast_charge", timeoutTicks = 120)
    public static void mobThunderClapStrikesTargetAfterCharge(GameTestHelper helper) {
        Mob caster = caster(helper);
        Mob pig = target(helper);
        float before = pig.getHealth();
        CastOptions options = CastOptions.at(pig).withProficiency(0F);
        if (!startCharge(helper, caster, "thunder_clap", options)) return;
        helper.runAfterDelay(20, () -> {
            if (pig.getHealth() != before) helper.fail("mob Thunder Clap struck before its 40..60 tick charge");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(pig, before), "mob Thunder Clap did not damage its target");
            helper.assertTrue(caster.getHealth() == caster.getMaxHealth(), "mob Thunder Clap hurt its caster");
            assertCooldown(helper, caster, "thunder_clap", options);
        });
    }

    @GameTest(template = EMPTY, batch = "mob_cast_charge", timeoutTicks = 80)
    public static void mobBodyIntensifyBuffsItselfWithoutTarget(GameTestHelper helper) {
        Mob caster = caster(helper);
        CastOptions options = CastOptions.defaults().withProficiency(1F);
        if (!startCharge(helper, caster, "body_intensify", options)) return;
        helper.succeedWhen(() -> {
            helper.assertTrue(caster.hasEffect(MobEffects.JUMP), "mob Body Intensify gave no Jump Boost");
            helper.assertTrue(caster.hasEffect(MobEffects.HUNGER), "mob Body Intensify skipped its Hunger side effect");
            assertCooldown(helper, caster, "body_intensify", options);
        });
    }

    @GameTest(template = EMPTY, batch = "mob_cast_charge", timeoutTicks = 80)
    public static void mobMeltdownerChargesAndHitsTarget(GameTestHelper helper) {
        Mob caster = caster(helper);
        Mob pig = target(helper);
        float before = pig.getHealth();
        CastOptions options = options(pig);
        if (!startCharge(helper, caster, "meltdowner", options)) return;
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(pig, before), "mob Meltdowner did not damage its target");
            helper.assertTrue(caster.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                    .getModifier(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy",
                            "meltdowner_charge_slow")) == null, "mob Meltdowner kept its charge slow");
            assertCooldown(helper, caster, "meltdowner", options);
        });
    }

    private static List<MdBallEntity> balls(GameTestHelper helper, Mob caster) {
        return helper.getLevel().getEntitiesOfClass(MdBallEntity.class, caster.getBoundingBox().inflate(6),
                ball -> caster.getUUID().equals(ball.ownerId()));
    }

    @GameTest(template = EMPTY, batch = "mob_cast_charge", timeoutTicks = 140)
    public static void mobScatterBombKeepsBallsThenFiresAtTarget(GameTestHelper helper) {
        Mob caster = caster(helper);
        Mob pig = target(helper);
        float before = pig.getHealth();
        CastOptions options = options(pig);
        if (!startCharge(helper, caster, "scatter_bomb", options)) return;
        helper.runAfterDelay(45, () -> {
            if (balls(helper, caster).size() < 2) helper.fail("mob-owned Scatter Bomb balls were discarded or not spawned");
            if (pig.getHealth() != before) helper.fail("mob Scatter Bomb fired before its release");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(pig, before), "mob Scatter Bomb did not damage its target");
            helper.assertTrue(balls(helper, caster).isEmpty(), "mob Scatter Bomb left balls behind");
            helper.assertFalse(AcademyMobAbilities.isCharging(caster), "mob Scatter Bomb still charging");
        });
    }

    @GameTest(template = EMPTY, batch = "mob_cast_charge", timeoutTicks = 60)
    public static void mobElectronMissileShootsTarget(GameTestHelper helper) {
        Mob caster = caster(helper);
        Mob pig = target(helper);
        float before = pig.getHealth();
        if (!startCharge(helper, caster, "electron_missile", options(pig))) return;
        helper.succeedWhen(() -> helper.assertTrue(hurt(pig, before), "mob Electron Missile did not damage its target"));
    }

    @GameTest(template = EMPTY, batch = "mob_cast_charge", timeoutTicks = 260)
    public static void mobElectronMissileCleansUpOrbsAndCoolsDown(GameTestHelper helper) {
        Mob caster = caster(helper);
        CastOptions options = CastOptions.defaults().withProficiency(0F);
        if (!startCharge(helper, caster, "electron_missile", options)) return;
        helper.runAfterDelay(30, () -> {
            if (balls(helper, caster).isEmpty()) helper.fail("mob-owned Electron Missile orbs were discarded or not spawned");
        });
        helper.succeedWhen(() -> {
            helper.assertFalse(AcademyMobAbilities.isCharging(caster), "mob Electron Missile still running");
            helper.assertTrue(balls(helper, caster).isEmpty(), "mob Electron Missile left orbs behind");
            assertCooldown(helper, caster, "electron_missile", options);
        });
    }

    @GameTest(template = EMPTY, batch = "mob_cast_charge", timeoutTicks = 40)
    public static void mobJetEngineMovesCasterTowardTarget(GameTestHelper helper) {
        Mob caster = caster(helper);
        Mob pig = target(helper);
        Vec3 start = caster.position();
        CastOptions options = options(pig);
        Result first = AcademyMobAbilities.cast(caster, "jet_engine", options);
        if (first != Result.CAST) { helper.fail("mob Jet Engine returned " + first + ", expected CAST"); return; }
        Result second = AcademyMobAbilities.cast(caster, "jet_engine", options);
        if (second != Result.ON_COOLDOWN) { helper.fail("mob Jet Engine re-cast returned " + second); return; }
        helper.succeedWhen(() -> {
            helper.assertTrue(caster.position().z - start.z > 2, "mob Jet Engine did not move its caster toward the target");
            helper.assertTrue(helper.getLevel().noCollision(caster, caster.getBoundingBox()),
                    "mob Jet Engine left its caster inside blocks");
        });
    }

    /** Discarding the caster mid-charge aborts quietly: no cooldown, no leftover balls. */
    private static void abortTest(GameTestHelper helper, String skill, boolean ballsExpected) {
        Mob caster = caster(helper);
        Mob pig = target(helper);
        CastOptions options = options(pig);
        if (!startCharge(helper, caster, skill, options)) return;
        helper.runAfterDelay(32, () -> {
            if (ballsExpected && balls(helper, caster).isEmpty()) {
                helper.fail(skill + " spawned no ball before the abort");
                return;
            }
            caster.discard();
            helper.runAfterDelay(2, () -> {
                if (AcademyMobAbilities.isCharging(caster)) { helper.fail(skill + " still charging after discard"); return; }
                if (AcademyMobAbilities.data(caster).isOnCooldown(skill, helper.getLevel().getGameTime())) {
                    helper.fail(skill + " applied a cooldown on abort");
                    return;
                }
                if (!helper.getLevel().getEntitiesOfClass(MdBallEntity.class, caster.getBoundingBox().inflate(6),
                        ball -> caster.getUUID().equals(ball.ownerId())).isEmpty()) {
                    helper.fail(skill + " left balls after its caster was discarded");
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, batch = "mob_cast_charge", timeoutTicks = 60)
    public static void mobThunderClapAbortsWhenCasterDiscarded(GameTestHelper helper) {
        abortTest(helper, "thunder_clap", false);
    }

    @GameTest(template = EMPTY, batch = "mob_cast_charge", timeoutTicks = 60)
    public static void mobMeltdownerAbortsWhenCasterDiscarded(GameTestHelper helper) {
        abortTest(helper, "meltdowner", false);
    }

    @GameTest(template = EMPTY, batch = "mob_cast_charge", timeoutTicks = 60)
    public static void mobScatterBombAbortsWhenCasterDiscarded(GameTestHelper helper) {
        abortTest(helper, "scatter_bomb", true);
    }

    @GameTest(template = EMPTY, batch = "mob_cast_charge", timeoutTicks = 60)
    public static void mobElectronMissileAbortsWhenCasterDiscarded(GameTestHelper helper) {
        abortTest(helper, "electron_missile", true);
    }
}
