package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.electromaster.ArcGenEffect;
import com.mohistmc.academy.skill.ability.electromaster.MineDetectEffect;
import com.mohistmc.academy.skill.ability.meltdowner.MineRayBasicEffect;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.world.AcademyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** EM-5, EM-6 and MD-3: 1.12.2 target and block rules for Electromaster and Meltdowner. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class LegacyTargetRuleGameTests {
    private LegacyTargetRuleGameTests() {}

    @GameTest(template="empty")
    public static void mineRayAcceptsNonPickaxeBlocksButKeepsPickaxeTiers(GameTestHelper helper) {
        ItemStack iron = new ItemStack(Items.IRON_PICKAXE);
        if (!MineRayBasicEffect.canHarvestWith(Blocks.SNOW_BLOCK.defaultBlockState(), iron)
                || !MineRayBasicEffect.canHarvestWith(Blocks.SNOW.defaultBlockState(), iron)
                || !MineRayBasicEffect.canHarvestWith(Blocks.COBWEB.defaultBlockState(), iron)) {
            helper.fail("Mine Ray refused a shovel/sword block; legacy harvest level 0 always qualified"); return;
        }
        if (!MineRayBasicEffect.canHarvestWith(Blocks.DIAMOND_ORE.defaultBlockState(), iron)
                || MineRayBasicEffect.canHarvestWith(Blocks.OBSIDIAN.defaultBlockState(), iron)) {
            helper.fail("Mine Ray basic no longer follows the iron (level 2) pickaxe tier"); return;
        }
        helper.succeed();
    }

    @GameTest(template="empty")
    public static void mineDetectTreatsEveryTaggedOreAsOre(GameTestHelper helper) {
        if (!MineDetectEffect.isOre(Blocks.NETHER_QUARTZ_ORE.defaultBlockState())
                || !MineDetectEffect.isOre(Blocks.NETHER_GOLD_ORE.defaultBlockState())
                || !MineDetectEffect.isOre(Blocks.DEEPSLATE_IRON_ORE.defaultBlockState())
                || !MineDetectEffect.isOre(AcademyBlocks.CRYSTAL_ORE.get().defaultBlockState())) {
            helper.fail("Mine Detect missed an ore that legacy isOreBlock reported"); return;
        }
        if (MineDetectEffect.isOre(Blocks.STONE.defaultBlockState())
                || MineDetectEffect.isOre(Blocks.IRON_BLOCK.defaultBlockState())) {
            helper.fail("Mine Detect reported a non-ore block"); return;
        }
        helper.succeed();
    }

    @GameTest(template="empty", timeoutTicks=40)
    public static void arcGenPassesThroughBoatsToHitLivingTarget(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absolutePos(new BlockPos(1, 2, 1)).getCenter());
        player.setYRot(0); player.setYHeadRot(0); player.setXRot(0);
        Vec3 eye = player.getEyePosition();

        Boat boat = EntityType.BOAT.create(helper.getLevel());
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        if (boat == null || zombie == null) { helper.fail("targets could not be created"); return; }
        boat.setPos(eye.x, eye.y - 0.3D, eye.z + 2.0D);
        zombie.setPos(eye.x, player.getY(), eye.z + 4.0D);
        helper.getLevel().addFreshEntity(boat);
        helper.getLevel().addFreshEntity(zombie);
        float health = zombie.getHealth();

        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setDevMode(true);
        new ArcGenEffect().execute(player, data);
        if (boat.isRemoved()) { helper.fail("Arc Gen targeted a boat; legacy traceLiving ignores non-living entities"); return; }
        if (zombie.getHealth() >= health) { helper.fail("Arc Gen did not reach the living target behind the boat"); return; }
        helper.succeed();
    }
}
