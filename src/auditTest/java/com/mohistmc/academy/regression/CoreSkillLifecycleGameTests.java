package com.mohistmc.academy.regression;

import com.mohistmc.academy.entity.MdBallEntity;
import com.mohistmc.academy.entity.MeltdownBeamEntity;
import com.mohistmc.academy.entity.PlasmaOrbEntity;
import com.mohistmc.academy.gametest.recheck.RecheckPlayers;
import com.mohistmc.academy.skill.*;
import com.mohistmc.academy.skill.ability.meltdowner.ElectronBombEffect;
import com.mohistmc.academy.skill.ability.meltdowner.ScatterBombEffect;
import com.mohistmc.academy.world.AcademyEntities;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("academy_audit")
@PrefixGameTestTemplate(false)
public final class CoreSkillLifecycleGameTests {
    private static PlayerAbilityData ability(ServerPlayer player) {
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(AbilityCategory.MELTDOWNER); data.setPlayerLevel(5);
        data.learnSkill("electron_bomb"); data.learnSkill("scatter_bomb");
        data.setProficiency("electron_bomb", 1); data.setProficiency("scatter_bomb", 1);
        data.setAbilityActive(true); data.setCurrentCp(data.getMaxCp()); data.setCurrentOverload(0);
        return data;
    }

    @GameTest(template = "empty", batch = "core_skill_lifecycle")
    public static void realDimensionChangeCannotFireOldScatterAndElectronBalls(GameTestHelper h) {
        try (var session = RecheckPlayers.connect(h)) {
            var player = session.player(); player.setPos(player.position().add(0, 40, 0));
            var data = ability(player); var original = player.serverLevel();
            var nearby = player.getBoundingBox().inflate(40);
            var electron = new ElectronBombEffect();
            int beams = original.getEntitiesOfClass(MeltdownBeamEntity.class, nearby).size();
            h.assertTrue(electron.executeAndReport(player, data), "Same-world electron fixture rejected");
            for (int i = 0; i < 3; i++) ElectronBombEffect.tick(new ServerTickEvent.Post(() -> true, player.server));
            h.assertTrue(original.getEntitiesOfClass(MeltdownBeamEntity.class, nearby).size() > beams,
                    "Positive control: a same-world mastered electron bomb must really fire");
            for (int i = 0; i < 2; i++) ElectronBombEffect.tick(new ServerTickEvent.Post(() -> true, player.server));

            var scatter = new ScatterBombEffect(); scatter.onChargingStart(player, data);
            scatter.onChargingTick(player, data, 20);
            SkillChargingManager.startCharging(player.getUUID(), 0, "scatter_bomb", 1, original.getGameTime());
            h.assertTrue(electron.executeAndReport(player, data), "Cross-world electron fixture rejected");
            var balls = original.getEntitiesOfClass(MdBallEntity.class, nearby,
                    ball -> player.getUUID().equals(ball.ownerId()));
            h.assertTrue(balls.size() == 2, "Expected one actual scatter orb and one delayed electron ball");
            var destination = player.server.getLevel(Level.NETHER);
            h.assertTrue(destination != null, "Nether dimension missing");
            var targetArea = new AABB(-20, 80, -20, 20, 120, 20);
            int destinationBeams = destination.getEntitiesOfClass(MeltdownBeamEntity.class, targetArea).size();
            float proficiency = data.getProficiency("scatter_bomb");
            player.teleportTo(destination, .5, 100, .5, Set.of(), 0, 0);
            h.assertTrue(player.level() == destination && SkillChargingManager.getState(player.getUUID()) == null,
                    "Real dimension transition failed to cancel charging");
            ElectronBombEffect.tick(new ServerTickEvent.Post(() -> true, player.server));
            h.assertTrue(balls.stream().allMatch(MdBallEntity::isRemoved), "Original-world balls survived cancellation");
            h.assertTrue(destination.getEntitiesOfClass(MeltdownBeamEntity.class, targetArea).size() == destinationBeams
                    && data.getProficiency("scatter_bomb") == proficiency,
                    "Lifecycle cancellation fired old balls or awarded release proficiency in the new world");
            h.succeed();
        }
    }

    @GameTest(template = "empty", batch = "core_plasma_protection")
    public static void plasmaExplosionHonorsBreakEventAndStillBreaksAllowedBlocks(GameTestHelper h) {
        try (var session = RecheckPlayers.connect(h)) {
            var player = session.player(); var level = h.getLevel();
            BlockPos center = h.absolutePos(new BlockPos(8, 50, 8));
            // Explicit test terrain fixture; production explosion never generates chunks.
            for (int x = (center.getX() - 8) >> 4; x <= (center.getX() + 8) >> 4; x++)
                for (int z = (center.getZ() - 8) >> 4; z <= (center.getZ() + 8) >> 4; z++) level.getChunk(x, z);
            player.setPos(Vec3.atCenterOf(center.offset(15, 0, 0)));
            level.setBlockAndUpdate(center, Blocks.GLASS.defaultBlockState());
            level.setBlockAndUpdate(center.east(), Blocks.GLASS.defaultBlockState());
            level.setBlockAndUpdate(center.below(), Blocks.BEDROCK.defaultBlockState());
            int[] events = {0};
            Consumer<BlockEvent.BreakEvent> protection = event -> {
                if (event.getLevel() == level && event.getPos().equals(center)) { events[0]++; event.setCanceled(true); }
            };
            NeoForge.EVENT_BUS.addListener(protection);
            try {
                PlasmaOrbEntity orb = new PlasmaOrbEntity(AcademyEntities.PLASMA_ORB.get(), level);
                orb.configureCharging(player, Vec3.atCenterOf(center));
                h.assertTrue(level.addFreshEntity(orb) && orb.arm(Vec3.atCenterOf(center), 0, 3), "Plasma fixture rejected");
                orb.tick();
                h.assertTrue(orb.isRemoved() && events[0] == 1 && level.getBlockState(center).is(Blocks.GLASS),
                        "Actual plasma blast ignored the claim event or repeated the same block permission event");
                h.assertTrue(level.getBlockState(center.east()).isAir(), "Positive control: permitted glass did not explode");
                h.assertTrue(level.getBlockState(center.below()).is(Blocks.BEDROCK), "Plasma destroyed unbreakable terrain");
                h.succeed();
            } finally { NeoForge.EVENT_BUS.unregister(protection); }
        }
    }
}
