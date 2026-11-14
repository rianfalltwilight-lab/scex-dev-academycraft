package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.api.mob.AcademyMobAbilities;
import com.mohistmc.academy.api.mob.AcademyMobAbilities.CastOptions;
import com.mohistmc.academy.api.mob.AcademyMobAbilities.Result;
import com.mohistmc.academy.entity.MdBallEntity;
import com.mohistmc.academy.entity.MeltdownBeamEntity;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Mob-cast thunder_bolt, arc_gen, electron_bomb and ray_barrage: a still husk aims at a pig four blocks
 * away through {@link AcademyMobAbilities}.  Husks do not burn in daylight, so their health only moves
 * when a skill hits them.
 */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MobCastElectromasterGameTests {
    private static final String EMPTY = "empty";
    private MobCastElectromasterGameTests() {}

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

    /** Casts once (must be CAST) and immediately again (must be ON_COOLDOWN). */
    private static boolean castTwice(GameTestHelper helper, Mob caster, String skill, LivingEntity target) {
        CastOptions options = CastOptions.at(target).withProficiency(1F);
        Result first = AcademyMobAbilities.cast(caster, skill, options);
        if (first != Result.CAST) { helper.fail(skill + " mob cast returned " + first); return false; }
        Result second = AcademyMobAbilities.cast(caster, skill, options);
        if (second != Result.ON_COOLDOWN) {
            helper.fail(skill + " second immediate mob cast returned " + second + ", expected ON_COOLDOWN");
            return false;
        }
        return true;
    }

    @GameTest(template = EMPTY, batch = "mob_cast_electromaster")
    public static void mobThunderBoltHitsTargetAndAoeButSparesSameType(GameTestHelper helper) {
        Mob caster = caster(helper);
        Mob pig = target(helper);
        Mob nearbyPig = spawn(helper, EntityType.PIG, 4.5, 2, 6.5);
        Mob bystander = spawn(helper, EntityType.HUSK, 0.5, 2, 6.5);
        float pigHealth = pig.getHealth(), nearbyHealth = nearbyPig.getHealth(), husk = bystander.getHealth();
        if (!castTwice(helper, caster, "thunder_bolt", pig)) return;
        if (!hurt(pig, pigHealth)) { helper.fail("mob Thunder Bolt did not damage its target"); return; }
        if (!hurt(nearbyPig, nearbyHealth)) { helper.fail("mob Thunder Bolt AoE did not damage a nearby pig"); return; }
        if (bystander.getHealth() != husk) { helper.fail("mob Thunder Bolt AoE damaged a same-type husk"); return; }
        if (caster.getHealth() != caster.getMaxHealth()) { helper.fail("mob Thunder Bolt hurt its caster"); return; }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "mob_cast_electromaster")
    public static void mobArcGenHitsTarget(GameTestHelper helper) {
        Mob caster = caster(helper);
        Mob pig = target(helper);
        float before = pig.getHealth();
        if (!castTwice(helper, caster, "arc_gen", pig)) return;
        if (!hurt(pig, before)) { helper.fail("mob Arc Gen did not damage its target"); return; }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "mob_cast_electromaster")
    public static void mobArcGenSparesSameTypeTarget(GameTestHelper helper) {
        Mob caster = caster(helper);
        Mob other = spawn(helper, EntityType.HUSK, 2.5, 2, 6.5);
        float before = other.getHealth();
        Result result = AcademyMobAbilities.cast(caster, "arc_gen", CastOptions.at(other).withProficiency(1F));
        if (result != Result.CAST) { helper.fail("mob Arc Gen at a husk returned " + result); return; }
        if (other.getHealth() != before) { helper.fail("mob Arc Gen damaged a same-type husk"); return; }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "mob_cast_electromaster", timeoutTicks = 60)
    public static void mobElectronBombKeepsItsBallAndHitsTarget(GameTestHelper helper) {
        Mob caster = caster(helper);
        Mob pig = target(helper);
        float before = pig.getHealth();
        if (!castTwice(helper, caster, "electron_bomb", pig)) return;
        helper.runAfterDelay(1, () -> {
            List<MdBallEntity> balls = helper.getLevel().getEntitiesOfClass(MdBallEntity.class,
                    caster.getBoundingBox().inflate(4), ball -> caster.getUUID().equals(ball.ownerId()));
            if (balls.size() != 1 || balls.getFirst().isRemoved()) {
                helper.fail("mob-owned Electron Bomb ball was discarded or not spawned (" + balls.size() + ")");
                return;
            }
            if (balls.getFirst().position().distanceTo(caster.position()) > 3) {
                helper.fail("mob-owned Electron Bomb ball does not follow its caster");
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(hurt(pig, before),
                "mob Electron Bomb did not damage its target"));
    }

    @GameTest(template = EMPTY, batch = "mob_cast_electromaster", timeoutTicks = 40)
    public static void mobRayBarrageHitsTargetAndKeepsItsRay(GameTestHelper helper) {
        Mob caster = caster(helper);
        Mob pig = target(helper);
        float before = pig.getHealth();
        if (!castTwice(helper, caster, "ray_barrage", pig)) return;
        if (!hurt(pig, before)) { helper.fail("mob Ray Barrage did not damage its target"); return; }
        helper.runAfterDelay(2, () -> {
            List<MeltdownBeamEntity> rays = helper.getLevel().getEntitiesOfClass(MeltdownBeamEntity.class,
                    caster.getBoundingBox().inflate(8));
            if (rays.isEmpty() || rays.stream().anyMatch(MeltdownBeamEntity::isRemoved)) {
                helper.fail("mob Ray Barrage pre-ray was discarded or not spawned");
                return;
            }
            helper.succeed();
        });
    }
}
