package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.api.mob.AcademyMobAbilities;
import com.mohistmc.academy.api.mob.AcademyMobAbilities.CastOptions;
import com.mohistmc.academy.api.mob.AcademyMobAbilities.Result;
import com.mohistmc.academy.skill.ability.telekinesis.CruiseBombEffect;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.entity.CruiseBombEntity;
import com.mohistmc.academy.world.entity.PsychoProjectileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Mob casts of aero separator, air jet, psycho throwing, psycho slam and cruise bomb: the effect
 * lands, the cooldown applies, a mob's stone is virtual, and a mob's cruise orbs die with their caster.
 */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MobCastAerohandTelekinesisGameTests {
    private static final String EMPTY = "empty";
    private static final float HEALTH = 200;
    private MobCastAerohandTelekinesisGameTests() {}

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

    private static CastOptions at(Mob target) {
        return CastOptions.at(target).withProficiency(1F);
    }

    private static boolean castThenCooldown(GameTestHelper helper, Mob caster, String skill, CastOptions options) {
        Result first = AcademyMobAbilities.cast(caster, skill, options);
        Result second = AcademyMobAbilities.cast(caster, skill, options);
        if (first != Result.CAST || second != Result.ON_COOLDOWN) {
            helper.fail(skill + " mob casts returned " + first + " / " + second);
            return false;
        }
        return true;
    }

    @GameTest(template = EMPTY, batch = "mob_cast_telekinesis")
    public static void mobAeroSeparatorPulsesAfterItsChargeAndSparesItsCaster(GameTestHelper helper) {
        Mob caster = mob(helper, EntityType.VILLAGER, 2.5, 2.5);
        Mob target = mob(helper, EntityType.PIG, 2.5, 5.0);
        helper.runAfterDelay(10, () -> {
            Result first = AcademyMobAbilities.cast(caster, "aero_separator", at(target));
            if (first != Result.CHARGING) { helper.fail("aero_separator mob cast returned " + first); return; }
            helper.runAfterDelay(8, () -> {
                Result again = AcademyMobAbilities.cast(caster, "aero_separator", at(target));
                if (again != Result.ON_COOLDOWN) { helper.fail("aero_separator re-cast returned " + again); return; }
                helper.succeedWhen(() -> {
                    helper.assertTrue(target.getHealth() < HEALTH, "mob separator did not damage the target");
                    helper.assertTrue(caster.getHealth() >= HEALTH, "mob separator suffocated its own caster");
                });
            });
        });
    }

    @GameTest(template = EMPTY, batch = "mob_cast_telekinesis")
    public static void mobAirJetDashesTheCaster(GameTestHelper helper) {
        Mob caster = mob(helper, EntityType.VILLAGER, 2.5, 2.5);
        Mob target = mob(helper, EntityType.PIG, 2.5, 7.5);
        helper.runAfterDelay(11, () -> {
            Vec3 start = caster.position();
            if (!castThenCooldown(helper, caster, "air_jet", at(target))) return;
            if (caster.getDeltaMovement().length() < 1.5) {
                helper.fail("air_jet gave the mob no dash: " + caster.getDeltaMovement()); return;
            }
            // A NoAI mob does not integrate its own motion; let physics run the dash.
            caster.setNoAi(false);
            helper.succeedWhen(() -> helper.assertTrue(caster.position().distanceTo(start) > 1.5,
                    "air_jet did not move the mob"));
        });
    }

    @GameTest(template = EMPTY, batch = "mob_cast_telekinesis")
    public static void mobPsychoThrowingIsVirtualAndDropsNothing(GameTestHelper helper) {
        Mob caster = mob(helper, EntityType.VILLAGER, 2.5, 2.5);
        Mob target = mob(helper, EntityType.PIG, 2.5, 7.5);
        AABB area = caster.getBoundingBox().inflate(16);
        helper.runAfterDelay(12, () -> {
            if (!castThenCooldown(helper, caster, "psycho_throwing", at(target))) return;
            var shots = helper.getLevel().getEntitiesOfClass(PsychoProjectileEntity.class, area,
                    shot -> shot.getOwner() == caster && shot.isAlive());
            if (shots.size() != 1 || !shots.getFirst().isVirtualAmmo()
                    || shots.getFirst().kind() != PsychoProjectileEntity.Kind.STONE) {
                helper.fail("expected one virtual stone owned by the mob, got " + shots); return;
            }
            helper.succeedWhen(() -> {
                helper.assertTrue(target.getHealth() < HEALTH, "mob stone did not damage the target");
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(PsychoProjectileEntity.class, area,
                        Entity::isAlive).isEmpty(), "mob stone did not settle after the hit");
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area,
                        item -> item.getItem().is(Items.COBBLESTONE)
                                || item.getItem().is(AcademyItems.ETCHED_COBBLESTONE.get())).isEmpty(),
                        "a mob's stone dropped a stone item");
            });
        });
    }

    @GameTest(template = EMPTY, batch = "mob_cast_telekinesis")
    public static void mobPsychoSlamNeedsATargetAndHurtsIt(GameTestHelper helper) {
        Mob caster = mob(helper, EntityType.VILLAGER, 2.5, 2.5);
        Mob target = mob(helper, EntityType.PIG, 2.5, 6.5);
        helper.runAfterDelay(13, () -> {
            Result none = AcademyMobAbilities.cast(caster, "psycho_slam", CastOptions.defaults().withProficiency(1F));
            if (none != Result.FAILED) { helper.fail("psycho_slam without a target returned " + none); return; }
            if (!castThenCooldown(helper, caster, "psycho_slam", at(target))) return;
            helper.succeedWhen(() -> helper.assertTrue(target.getHealth() < HEALTH,
                    "mob slam did not damage the target"));
        });
    }

    @GameTest(template = EMPTY, batch = "mob_cast_telekinesis", timeoutTicks = 200)
    public static void mobCruiseBombLocksOntoItsTarget(GameTestHelper helper) {
        Mob caster = mob(helper, EntityType.VILLAGER, 2.5, 2.5);
        Mob target = mob(helper, EntityType.PIG, 2.5, 5.5);
        helper.runAfterDelay(14, () -> {
            if (!castThenCooldown(helper, caster, "cruise_bomb", at(target))) return;
            var orbs = helper.getLevel().getEntitiesOfClass(CruiseBombEntity.class, caster.getBoundingBox().inflate(4),
                    orb -> orb.getOwner() == caster && orb.isAlive());
            if (orbs.isEmpty() || !CruiseBombEffect.hasMobSession(caster.getUUID())) {
                helper.fail("cruise_bomb spawned no orb / session for the mob"); return;
            }
            helper.succeedWhen(() -> helper.assertTrue(target.getHealth() < HEALTH,
                    "mob cruise bomb did not damage the target"));
        });
    }

    @GameTest(template = EMPTY, batch = "mob_cast_telekinesis")
    public static void mobCruiseBombOrbsEndWithTheirCaster(GameTestHelper helper) {
        Mob caster = mob(helper, EntityType.VILLAGER, 2.5, 2.5);
        // Out of the 4-block lock box, so the orbs keep orbiting until the caster goes.
        Mob target = mob(helper, EntityType.PIG, 2.5, 12.5);
        AABB area = caster.getBoundingBox().inflate(16);
        helper.runAfterDelay(15, () -> {
            Result cast = AcademyMobAbilities.cast(caster, "cruise_bomb", at(target));
            if (cast != Result.CAST) { helper.fail("cruise_bomb mob cast returned " + cast); return; }
            helper.runAfterDelay(12, () -> {
                var orbs = helper.getLevel().getEntitiesOfClass(CruiseBombEntity.class, area,
                        orb -> orb.getOwner() == caster && orb.isAlive());
                if (orbs.isEmpty()) { helper.fail("no mob cruise orbs before the caster left"); return; }
                caster.discard();
                if (CruiseBombEffect.hasMobSession(caster.getUUID())) {
                    helper.fail("a discarded mob's cruise session kept running"); return;
                }
                helper.succeedWhen(() -> helper.assertTrue(orbs.stream().allMatch(Entity::isRemoved)
                        && helper.getLevel().getEntitiesOfClass(CruiseBombEntity.class, area, Entity::isAlive).isEmpty(),
                        "a discarded mob's cruise orbs survived"));
            });
        });
    }
}
