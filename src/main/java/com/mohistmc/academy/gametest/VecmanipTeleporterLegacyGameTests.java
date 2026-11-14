package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.teleporter.FlashingSessionManager;
import com.mohistmc.academy.skill.ability.vecmanip.GroundShockEffect;
import com.mohistmc.academy.skill.ability.vecmanip.StormWingEffect;
import com.mohistmc.academy.skill.ability.vecmanip.VecDeviationEffect;
import com.mohistmc.academy.skill.passive.PassiveSkillEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Vecmanip / teleporter behaviour restored to the AcademyCraft 1.12.2 source (audit batch 3). */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class VecmanipTeleporterLegacyGameTests {
    private static final String EMPTY = "empty";
    private static final String VECTOR_MARK = "academy:vec_deviated";

    private VecmanipTeleporterLegacyGameTests() {}

    private static PlayerAbilityData ability(ServerPlayer player, AbilityCategory category, String skill,
                                             float proficiency, float cp) {
        player.setGameMode(GameType.SURVIVAL);
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(category);
        data.setPlayerLevel(5);
        data.setAbilityActive(true);
        data.learnSkill(skill);
        data.setProficiency(skill, proficiency);
        data.setCurrentCp(cp);
        player.setData(AcademyAttachments.PLAYER_ABILITY, data);
        return data;
    }

    /** Stands the player at the centre of a relative block, facing +Z, on a stone block. */
    private static ServerPlayer standing(GameTestHelper helper, BlockPos relativeFeet, boolean ground) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos feet = helper.absolutePos(relativeFeet);
        if (ground) helper.getLevel().setBlockAndUpdate(feet.below(), Blocks.STONE.defaultBlockState());
        player.setPos(feet.getX() + .5, feet.getY(), feet.getZ() + .5);
        player.setYRot(0);
        player.setXRot(0);
        player.setDeltaMovement(Vec3.ZERO);
        player.setOnGround(ground);
        return player;
    }

    private static <T extends Entity> T spawn(GameTestHelper helper, T entity, Vec3 pos) {
        entity.setPos(pos.x, pos.y, pos.z);
        entity.setDeltaMovement(new Vec3(.2, 0, 0));
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static boolean marked(Entity entity) {
        return entity.getPersistentData().getBoolean(VECTOR_MARK);
    }

    @GameTest(template = EMPTY)
    public static void deviationExcludesLivingAndXpBottlesButStopsXpOrbs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = standing(helper, new BlockPos(16, 4, 16), true);
        PlayerAbilityData data = ability(player, AbilityCategory.VECMANIP, "vec_deviation", 1, 1_000);
        Vec3 at = player.position();
        ArmorStand stand = spawn(helper, new ArmorStand(level, at.x + 1, at.y, at.z), at.add(1, 0, 0));
        ServerPlayer other = helper.makeMockServerPlayerInLevel();
        other.setPos(at.x - 1.5, at.y, at.z);
        ThrownExperienceBottle bottle = spawn(helper, new ThrownExperienceBottle(level, at.x, at.y + 1, at.z + 1.5),
                at.add(0, 1, 1.5));
        ExperienceOrb orb = spawn(helper, new ExperienceOrb(level, at.x, at.y + 1, at.z - 1.5, 3), at.add(0, 1, -1.5));

        new VecDeviationEffect().onChargingStart(player, data);
        PassiveSkillEventHandler.tick(new PlayerTickEvent.Post(player));

        if (marked(stand) || marked(other)) {
            helper.fail("legacy excluded \"living\" (EntityLivingBase) but deviation affected a living entity"); return;
        }
        if (marked(bottle)) {
            helper.fail("legacy excluded \"xp_bottle\" but deviation affected a thrown experience bottle"); return;
        }
        if (!marked(orb)) {
            helper.fail("experience orbs are not in the legacy excluded list and must be stopped"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void stormWingHoversDuringFreeWindUp(GameTestHelper helper) {
        ServerPlayer player = standing(helper, new BlockPos(16, 10, 16), false);
        PlayerAbilityData data = ability(player, AbilityCategory.VECMANIP, "storm_wing", 0, 1_000);
        StormWingEffect effect = new StormWingEffect();
        effect.onChargingStart(player, data);
        StormWingEffect.acceptInput(player, (byte) 1, (byte) 0);
        player.setDeltaMovement(Vec3.ZERO);
        float cpBefore = data.getCurrentCp();
        boolean alive = effect.onChargingTick(player, data, 1);
        Vec3 motion = player.getDeltaMovement();
        float cp = data.getCurrentCp();
        effect.onChargingRelease(player, data, 1);
        if (!alive) {
            helper.fail("storm wing wind-up tick terminated"); return;
        }
        if (Math.abs(motion.y - .078) > 1.0e-6) {
            helper.fail("wind-up must run the legacy no-key hover (+.078 Y in the air): " + motion); return;
        }
        if (Math.abs(motion.z) > 1.0e-6 || Math.abs(motion.x) > 1.0e-6) {
            helper.fail("wind-up must not fly toward movement keys: " + motion); return;
        }
        if (cp != cpBefore) {
            helper.fail("legacy wind-up was free but CP changed to " + cp); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void groundShockMaxExpBreaksGroundAndFeetLayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = standing(helper, new BlockPos(16, 4, 16), true);
        PlayerAbilityData data = ability(player, AbilityCategory.VECMANIP, "ground_shock", 1, 10_000);
        data.setDevMode(true);
        BlockPos feet = player.blockPosition();
        // Behind the player (look is +Z), so only the max-exp sweep can reach these.
        BlockPos below2 = feet.offset(-4, -2, -4), ground = feet.offset(-4, -1, -4), feetLayer = feet.offset(-4, 0, -4);
        for (BlockPos pos : new BlockPos[]{below2, ground, feetLayer})
            level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());

        new GroundShockEffect().onChargingRelease(player, data, 10);

        if (!level.getBlockState(ground).isAir() || !level.getBlockState(feetLayer).isAir()) {
            helper.fail("Groundshock.scala breaks y0-1 and y0 (ground and feet layers)"); return;
        }
        if (level.getBlockState(below2).isAir()) {
            helper.fail("max-exp sweep reached two layers below the feet"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void groundShockClearsAbovePathEvenOverAirCells(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = standing(helper, new BlockPos(16, 4, 16), true);
        PlayerAbilityData data = ability(player, AbilityCategory.VECMANIP, "ground_shock", .5f, 10_000);
        data.setDevMode(true);
        BlockPos feet = player.blockPosition();
        // Path cell (x, feet-1, z+3) is air; the block above it sits on the feet layer.
        BlockPos above = feet.offset(0, 0, 3);
        level.setBlockAndUpdate(above, Blocks.DIRT.defaultBlockState());

        new GroundShockEffect().onChargingRelease(player, data, 10);

        if (!level.getBlockState(above).isAir()) {
            helper.fail("legacy breaks the 3 blocks above every plotter step, even over an air cell"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void flashingFailedStartDebitAppliesFullCooldown(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = ability(player, AbilityCategory.TELEPORTER, "flashing", 0, 0);
        if (FlashingSessionManager.start(player, data)) {
            helper.fail("flashing started with zero CP"); return;
        }
        if (data.getCooldownTicks("flashing") != 900) {
            helper.fail("legacy terminate after a failed start debit sets the full cooldown, got "
                    + data.getCooldownTicks("flashing")); return;
        }
        helper.succeed();
    }
}
