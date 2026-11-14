package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.api.mob.AcademyMobAbilities;
import com.mohistmc.academy.api.mob.AcademyMobAbilities.CastOptions;
import com.mohistmc.academy.api.mob.AcademyMobAbilities.Result;
import com.mohistmc.academy.skill.ability.aerohand.AirWallEffect;
import com.mohistmc.academy.skill.ability.aerohand.StormCoreEffect;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.entity.AeroProjectileEntity;
import com.mohistmc.academy.world.entity.PsychoProjectileEntity;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Mob casts of the six aerohand / needle skills: the effect lands, the projectile survives a
 * non-player owner, the cooldown applies, a mob's needle is virtual, and a mob's storm core or air
 * wall dies with its caster.
 */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MobCastAerohandGameTests {
    private static final String EMPTY = "empty";
    private static final float HEALTH = 200;
    private MobCastAerohandGameTests() {}

    /** A still, weightless, sturdy mob; villagers neither burn in daylight nor despawn in peaceful. */
    private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double z) {
        T mob = type.create(helper.getLevel());
        var origin = helper.absolutePos(BlockPos.ZERO);
        mob.moveTo(origin.getX() + x, origin.getY() + 2, origin.getZ() + z, 0, 0);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setSilent(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
        mob.setHealth(HEALTH);
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }

    /**
     * Casts after {@code delay} ticks (staggered so parallel tests share the per-tick cast budget),
     * checks CAST then ON_COOLDOWN, hands the caster to {@code afterCast}, and succeeds once the
     * target has lost health.
     */
    private static void castAt(GameTestHelper helper, String skill, double distance, int delay,
                               Consumer<Mob> afterCast) {
        Mob caster = mob(helper, EntityType.VILLAGER, 2.5, 2.5);
        Mob target = mob(helper, EntityType.PIG, 2.5, 2.5 + distance);
        helper.runAfterDelay(delay, () -> {
            CastOptions options = CastOptions.at(target).withProficiency(1F);
            Result first = AcademyMobAbilities.cast(caster, skill, options);
            Result second = AcademyMobAbilities.cast(caster, skill, options);
            if (first != Result.CAST) { helper.fail(skill + " mob cast returned " + first); return; }
            if (second != Result.ON_COOLDOWN) { helper.fail(skill + " second cast returned " + second); return; }
            afterCast.accept(caster);
            helper.succeedWhen(() -> helper.assertTrue(target.getHealth() < HEALTH || !target.isAlive(),
                    skill + " cast by a mob did not damage the target"));
        });
    }

    private static <T extends Projectile> void projectile(GameTestHelper helper, String skill, Class<T> type, int delay) {
        castAt(helper, skill, 5, delay, caster -> {
            var shots = helper.getLevel().getEntitiesOfClass(type, caster.getBoundingBox().inflate(3),
                    shot -> shot.getOwner() == caster && shot.isAlive());
            // The target can only be hurt by a projectile that survived its non-player owner check.
            if (shots.isEmpty()) helper.fail(skill + " spawned no projectile owned by the mob");
        });
    }

    @GameTest(template = EMPTY, batch = "mob_cast_aerohand")
    public static void mobCastsVolcanicBall(GameTestHelper helper) {
        projectile(helper, "volcanic_ball", AeroProjectileEntity.class, 2);
    }

    @GameTest(template = EMPTY, batch = "mob_cast_aerohand")
    public static void mobCastsAirBlade(GameTestHelper helper) {
        projectile(helper, "academy:air_blade", AeroProjectileEntity.class, 3);
    }

    @GameTest(template = EMPTY, batch = "mob_cast_aerohand")
    public static void mobCastsBomberLance(GameTestHelper helper) {
        projectile(helper, "bomber_lance", AeroProjectileEntity.class, 4);
    }

    @GameTest(template = EMPTY, batch = "mob_cast_aerohand")
    public static void mobCastsStormCore(GameTestHelper helper) {
        castAt(helper, "storm_core", 4, 5, caster -> {
            if (!StormCoreEffect.hasActiveCore(caster.getUUID())) helper.fail("storm core did not start for the mob");
        });
    }

    @GameTest(template = EMPTY, batch = "mob_cast_aerohand")
    public static void mobCastsAirWall(GameTestHelper helper) {
        castAt(helper, "air_wall", 3, 6, caster -> {
            if (!AirWallEffect.hasActiveRing(caster.getUUID())) helper.fail("air wall did not start for the mob");
        });
    }

    @GameTest(template = EMPTY, batch = "mob_cast_aerohand")
    public static void mobPsychoNeedleIsVirtualAndDropsNothing(GameTestHelper helper) {
        Mob caster = mob(helper, EntityType.VILLAGER, 2.5, 2.5);
        Mob target = mob(helper, EntityType.PIG, 2.5, 7.5);
        AABB area = caster.getBoundingBox().inflate(16);
        helper.runAfterDelay(7, () -> {
            CastOptions options = CastOptions.at(target).withProficiency(1F);
            Result first = AcademyMobAbilities.cast(caster, "psycho_needling", options);
            Result second = AcademyMobAbilities.cast(caster, "psycho_needling", options);
            if (first != Result.CAST || second != Result.ON_COOLDOWN) {
                helper.fail("psycho_needling mob casts returned " + first + " / " + second); return;
            }
            var shots = helper.getLevel().getEntitiesOfClass(PsychoProjectileEntity.class, area,
                    shot -> shot.getOwner() == caster && shot.isAlive());
            if (shots.size() != 1 || !shots.getFirst().isVirtualAmmo()) {
                helper.fail("expected one virtual needle owned by the mob, got " + shots); return;
            }
            helper.succeedWhen(() -> {
                helper.assertTrue(target.getHealth() < HEALTH, "mob needle did not damage the target");
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(PsychoProjectileEntity.class, area,
                        Entity::isAlive).isEmpty(), "mob needle did not settle after the hit");
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area,
                        item -> item.getItem().is(AcademyItems.NEEDLE.get())).isEmpty(),
                        "a mob's needle dropped a needle item");
            });
        });
    }

    @GameTest(template = EMPTY, batch = "mob_cast_aerohand")
    public static void mobStormCoreAndAirWallEndWithTheirCaster(GameTestHelper helper) {
        Mob coreCaster = mob(helper, EntityType.VILLAGER, 2.5, 2.5);
        Mob wallCaster = mob(helper, EntityType.VILLAGER, 12.5, 2.5);
        helper.runAfterDelay(8, () -> {
            Result core = AcademyMobAbilities.cast(coreCaster, "storm_core", CastOptions.defaults().withProficiency(1F));
            Result wall = AcademyMobAbilities.cast(wallCaster, "air_wall", CastOptions.defaults().withProficiency(1F));
            if (core != Result.CAST || wall != Result.CAST) { helper.fail("casts returned " + core + " / " + wall); return; }
            if (!StormCoreEffect.hasActiveCore(coreCaster.getUUID()) || !AirWallEffect.hasActiveRing(wallCaster.getUUID())) {
                helper.fail("storm core / air wall did not start"); return;
            }
            coreCaster.discard();
            wallCaster.discard();
            if (StormCoreEffect.hasActiveCore(coreCaster.getUUID()) || AirWallEffect.hasActiveRing(wallCaster.getUUID())) {
                helper.fail("a discarded mob's storm core or air wall kept running"); return;
            }
            helper.succeed();
        });
    }
}
