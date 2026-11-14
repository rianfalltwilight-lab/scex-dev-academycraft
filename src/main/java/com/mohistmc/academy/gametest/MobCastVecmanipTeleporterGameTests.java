package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.api.mob.AcademyMobAbilities;
import com.mohistmc.academy.api.mob.AcademyMobAbilities.CastOptions;
import com.mohistmc.academy.api.mob.AcademyMobAbilities.Result;
import com.mohistmc.academy.entity.PlasmaOrbEntity;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Mob casts of the vector-manipulation and teleporter skills: a still husk standing on a stone floor
 * aims at a still pig a few blocks away through {@link AcademyMobAbilities}.  Casts are staggered so
 * parallel tests share the per-tick cast budget; the default {@code mobs.destroyBlocks=false} must leave
 * every block in place.
 */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MobCastVecmanipTeleporterGameTests {
    private static final String EMPTY = "empty";
    private static final float HEALTH = 200;
    private MobCastVecmanipTeleporterGameTests() {}

    /** A stone floor at relative y=1 under x/z in [x0, x1] x [z0, z1]; mobs stand on it at y=2. */
    private static void floor(GameTestHelper helper, int x0, int x1, int z0, int z1) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }

    private static <T extends Mob> T spawn(GameTestHelper helper, EntityType<T> type, double x, double z, float health) {
        T mob = type.create(helper.getLevel());
        if (mob == null) throw new IllegalStateException("could not create " + type);
        Vec3 pos = helper.absoluteVec(new Vec3(x, 2, z));
        mob.moveTo(pos.x, pos.y, pos.z, 0F, 0F);
        mob.setNoAi(true);
        mob.setSilent(true);
        mob.setPersistenceRequired();
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        mob.setHealth(health);
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static Mob caster(GameTestHelper helper, double x, double z) {
        return spawn(helper, EntityType.HUSK, x, z, HEALTH);
    }

    private static Mob pig(GameTestHelper helper, double x, double z) {
        return spawn(helper, EntityType.PIG, x, z, HEALTH);
    }

    private static boolean hurt(LivingEntity entity) {
        return !entity.isAlive() || entity.getHealth() < HEALTH;
    }

    private static boolean onCooldown(GameTestHelper helper, Mob caster, String skill) {
        return AcademyMobAbilities.data(caster).isOnCooldown(skill, helper.getLevel().getGameTime());
    }

    /**
     * After {@code delay} ticks casts at the target (must be {@code first}) and immediately again (must be
     * {@code second}), then hands over to {@code then}.
     */
    private static void cast(GameTestHelper helper, Mob caster, String skill, LivingEntity target, int delay,
                             Result first, Result second, Consumer<Mob> then) {
        helper.runAfterDelay(delay, () -> {
            CastOptions options = CastOptions.at(target).withProficiency(1F);
            Result a = AcademyMobAbilities.cast(caster, skill, options);
            if (a != first) { helper.fail(skill + " mob cast returned " + a + ", expected " + first); return; }
            Result b = AcademyMobAbilities.cast(caster, skill, options);
            if (b != second) { helper.fail(skill + " immediate re-cast returned " + b + ", expected " + second); return; }
            then.accept(caster);
        });
    }

    private static void assertBlock(GameTestHelper helper, BlockPos relative, Block block, String what) {
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(relative)).is(block),
                what + " at " + relative + " was changed although mobs.destroyBlocks is false");
    }

    @GameTest(template = EMPTY, batch = "mob_cast_vecmanip")
    public static void mobDirShockChargesThenHitsTarget(GameTestHelper helper) {
        floor(helper, 1, 4, 1, 7);
        Mob caster = caster(helper, 2.5, 2.5);
        Mob pig = pig(helper, 2.5, 5.5);
        cast(helper, caster, "dir_shock", pig, 1, Result.CHARGING, Result.ALREADY_CHARGING, c ->
                helper.succeedWhen(() -> {
                    helper.assertTrue(hurt(pig), "mob Directed Shock did not damage its target");
                    helper.assertTrue(onCooldown(helper, c, "dir_shock"), "mob Directed Shock set no cooldown");
                    helper.assertTrue(c.getHealth() == HEALTH, "mob Directed Shock hurt its caster");
                }));
    }

    @GameTest(template = EMPTY, batch = "mob_cast_vecmanip", timeoutTicks = 60)
    public static void mobDirShockOutOfReachFailsWithoutCooldown(GameTestHelper helper) {
        Mob caster = caster(helper, 2.5, 2.5);
        Mob pig = pig(helper, 2.5, 10.5);
        cast(helper, caster, "dir_shock", pig, 2, Result.CHARGING, Result.ALREADY_CHARGING, c ->
                helper.runAfterDelay(25, () -> {
                    if (AcademyMobAbilities.isCharging(c)) { helper.fail("dir_shock charge never ended"); return; }
                    if (hurt(pig)) { helper.fail("mob Directed Shock hit a target out of reach"); return; }
                    if (onCooldown(helper, c, "dir_shock")) {
                        helper.fail("a mob Directed Shock that hit nothing still set a cooldown"); return;
                    }
                    helper.succeed();
                }));
    }

    @GameTest(template = EMPTY, batch = "mob_cast_vecmanip")
    public static void mobDirBlastHitsTargetAndKeepsBlocks(GameTestHelper helper) {
        floor(helper, 0, 5, 0, 9);
        BlockPos dirt = new BlockPos(2, 2, 7);
        helper.setBlock(dirt, Blocks.DIRT);
        Mob caster = caster(helper, 2.5, 2.5);
        Mob pig = pig(helper, 2.5, 5.5);
        cast(helper, caster, "dir_blast", pig, 3, Result.CHARGING, Result.ALREADY_CHARGING, c ->
                helper.succeedWhen(() -> {
                    helper.assertTrue(hurt(pig), "mob Directed Blast did not damage its target");
                    helper.assertTrue(onCooldown(helper, c, "dir_blast"), "mob Directed Blast set no cooldown");
                    assertBlock(helper, dirt, Blocks.DIRT, "dirt beside the blast");
                    for (int z = 3; z <= 8; z++) assertBlock(helper, new BlockPos(2, 1, z), Blocks.STONE, "floor");
                }));
    }

    @GameTest(template = EMPTY, batch = "mob_cast_vecmanip")
    public static void mobGroundShockHitsTargetAndKeepsBlocks(GameTestHelper helper) {
        floor(helper, 0, 5, 0, 12);
        BlockPos dirt = new BlockPos(2, 2, 4);
        helper.setBlock(dirt, Blocks.DIRT);
        Mob caster = caster(helper, 2.5, 2.5);
        Mob pig = pig(helper, 2.5, 6.5);
        cast(helper, caster, "ground_shock", pig, 4, Result.CHARGING, Result.ALREADY_CHARGING, c ->
                helper.succeedWhen(() -> {
                    helper.assertTrue(hurt(pig), "mob Ground Shock did not damage its target");
                    helper.assertTrue(onCooldown(helper, c, "ground_shock"), "mob Ground Shock set no cooldown");
                    assertBlock(helper, dirt, Blocks.DIRT, "dirt above the wave path");
                    // Stone on the path would turn to cobblestone if blocks were touched.
                    for (int z = 2; z <= 10; z++) assertBlock(helper, new BlockPos(2, 1, z), Blocks.STONE, "floor");
                }));
    }

    @GameTest(template = EMPTY, batch = "mob_cast_vecmanip", timeoutTicks = 160)
    public static void mobPlasmaCannonKeepsItsOrbAndHitsTarget(GameTestHelper helper) {
        // The centre of the 64-wide template keeps the 15-block blast away from neighbouring tests.
        floor(helper, 29, 35, 27, 37);
        Mob caster = caster(helper, 32.5, 30.5);
        Mob pig = pig(helper, 32.5, 34.5);
        cast(helper, caster, "plasma_cannon", pig, 5, Result.CHARGING, Result.ALREADY_CHARGING, c -> {
            helper.runAfterDelay(3, () -> {
                List<PlasmaOrbEntity> orbs = helper.getLevel().getEntitiesOfClass(PlasmaOrbEntity.class,
                        c.getBoundingBox().inflate(4, 20, 4), orb -> c.getUUID().equals(orb.ownerId()));
                if (orbs.size() != 1 || !orbs.getFirst().isAlive() || orbs.getFirst().isArmed()) {
                    helper.fail("mob-owned plasma orb was discarded, armed early or not spawned (" + orbs.size() + ")");
                }
            });
            helper.succeedWhen(() -> {
                helper.assertTrue(hurt(pig), "mob Plasma Cannon did not damage its target");
                helper.assertTrue(onCooldown(helper, c, "plasma_cannon"), "mob Plasma Cannon set no cooldown");
                helper.assertTrue(c.isAlive() && c.getHealth() == HEALTH, "mob Plasma Cannon hurt its caster");
                for (int x = 30; x <= 34; x++)
                    for (int z = 28; z <= 36; z++) assertBlock(helper, new BlockPos(x, 1, z), Blocks.STONE, "floor");
            });
        });
    }

    @GameTest(template = EMPTY, batch = "mob_cast_vecmanip")
    public static void mobBloodRetroHoldsThenHitsTarget(GameTestHelper helper) {
        floor(helper, 1, 4, 1, 6);
        Mob caster = caster(helper, 2.5, 2.5);
        Mob pig = pig(helper, 2.5, 4.25);
        ResourceLocation slow = ResourceLocation.fromNamespaceAndPath("academy", "blood_retro_slow");
        cast(helper, caster, "blood_retro", pig, 6, Result.CHARGING, Result.ALREADY_CHARGING, c ->
                helper.succeedWhen(() -> {
                    helper.assertTrue(hurt(pig), "mob Blood Retrograde did not damage its target");
                    helper.assertTrue(onCooldown(helper, c, "blood_retro"), "mob Blood Retrograde set no cooldown");
                    helper.assertTrue(c.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(slow) == null,
                            "mob Blood Retrograde left its hold slow on the caster");
                }));
    }

    @GameTest(template = EMPTY, batch = "mob_cast_vecmanip", timeoutTicks = 40)
    public static void mobBloodRetroWithoutTargetAbortsWithoutCooldown(GameTestHelper helper) {
        Mob caster = caster(helper, 2.5, 2.5);
        helper.runAfterDelay(7, () -> {
            Result result = AcademyMobAbilities.cast(caster, "blood_retro", CastOptions.defaults().withProficiency(1F));
            if (result != Result.CHARGING) { helper.fail("blood_retro without target returned " + result); return; }
            helper.runAfterDelay(3, () -> {
                if (AcademyMobAbilities.isCharging(caster)) { helper.fail("targetless blood_retro kept charging"); return; }
                if (onCooldown(helper, caster, "blood_retro")) { helper.fail("targetless blood_retro set a cooldown"); return; }
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, batch = "mob_cast_vecmanip")
    public static void mobVecAccelLaunchesCasterTowardTarget(GameTestHelper helper) {
        floor(helper, 1, 4, 1, 8);
        Mob caster = caster(helper, 2.5, 2.5);
        Mob pig = pig(helper, 2.5, 6.5);
        cast(helper, caster, "vec_accel", pig, 8, Result.CHARGING, Result.ALREADY_CHARGING, c ->
                helper.succeedWhen(() -> {
                    // A NoAI mob does not travel, so its launch shows as velocity.
                    Vec3 motion = c.getDeltaMovement();
                    helper.assertTrue(motion.z > .5 && Math.abs(motion.x) < .1,
                            "mob Vector Acceleration did not launch its caster toward the target: " + motion);
                    helper.assertTrue(onCooldown(helper, c, "vec_accel"), "mob Vector Acceleration set no cooldown");
                    helper.assertTrue(c.fallDistance == 0, "mob Vector Acceleration kept the fall distance");
                }));
    }

    @GameTest(template = EMPTY, batch = "mob_cast_vecmanip")
    public static void mobFleshRippingMarksThenHitsTarget(GameTestHelper helper) {
        floor(helper, 1, 4, 1, 8);
        Mob caster = caster(helper, 2.5, 2.5);
        Mob pig = pig(helper, 2.5, 6.5);
        cast(helper, caster, "flesh_ripping", pig, 9, Result.CHARGING, Result.ALREADY_CHARGING, c ->
                helper.succeedWhen(() -> {
                    helper.assertTrue(hurt(pig), "mob Flesh Ripping did not damage its target");
                    helper.assertTrue(onCooldown(helper, c, "flesh_ripping"), "mob Flesh Ripping set no cooldown");
                }));
    }

    @GameTest(template = EMPTY, batch = "mob_cast_vecmanip")
    public static void mobMarkTeleportLandsBehindTarget(GameTestHelper helper) {
        floor(helper, 0, 5, 0, 10);
        Mob caster = caster(helper, 2.5, 2.5);
        Mob pig = pig(helper, 2.5, 6.5);
        caster.fallDistance = 5;
        cast(helper, caster, "mark_teleport", pig, 10, Result.CAST, Result.ON_COOLDOWN, c -> {
            if (c.getZ() <= pig.getZ() || c.position().distanceTo(pig.position()) > 2.5) {
                helper.fail("mob Mark Teleport did not land behind its target: " + c.position()); return;
            }
            if (!helper.getLevel().noCollision(c, c.getBoundingBox())) {
                helper.fail("mob Mark Teleport left its caster inside a block"); return;
            }
            if (c.fallDistance != 0) { helper.fail("mob Mark Teleport kept the fall distance"); return; }
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "mob_cast_vecmanip")
    public static void mobMarkTeleportAvoidsAWallBehindTarget(GameTestHelper helper) {
        floor(helper, 0, 5, 0, 10);
        // A two-high wall right behind the pig: the blink must pick another spot, never inside it.
        for (int x = 1; x <= 3; x++)
            for (int y = 2; y <= 3; y++) helper.setBlock(new BlockPos(x, y, 7), Blocks.STONE);
        Mob caster = caster(helper, 2.5, 2.5);
        Mob pig = pig(helper, 2.5, 6.4);
        Vec3 start = caster.position();
        cast(helper, caster, "mark_teleport", pig, 11, Result.CAST, Result.ON_COOLDOWN, c -> {
            if (c.position().distanceTo(start) < 1) { helper.fail("mob Mark Teleport did not move its caster"); return; }
            if (!helper.getLevel().noCollision(c, c.getBoundingBox())) {
                helper.fail("mob Mark Teleport ended inside a solid block: " + c.position()); return;
            }
            if (helper.getLevel().noCollision(c, c.getBoundingBox().move(0, -.1, 0))) {
                helper.fail("mob Mark Teleport left its caster in mid-air: " + c.position()); return;
            }
            helper.succeed();
        });
    }
}
