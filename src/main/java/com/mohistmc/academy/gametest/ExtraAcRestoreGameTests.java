package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.AcceptedAbilityDamage;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.aerohand.AeroPassiveRuntime;
import com.mohistmc.academy.skill.ability.telekinesis.PaperDrillEffect;
import com.mohistmc.academy.world.AcademyItems;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** ExtraAcC (1.12.2) behaviour restored in the aerohand / telekinesis port (EX-2/3/4/9). */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ExtraAcRestoreGameTests {
    private static final String EMPTY = "empty";
    private ExtraAcRestoreGameTests() {}

    private static ServerPlayer caster(GameTestHelper helper, AbilityCategory category, String... learned) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "[ExtraAcRestore]"));
        player.setGameMode(GameType.SURVIVAL);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        player.setPos(origin.getX() + 2.5, origin.getY() + 2, origin.getZ() + 1.5);
        player.setYRot(0);
        player.setXRot(0);
        player.setDeltaMovement(Vec3.ZERO);
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(category);
        data.setPlayerLevel(5);
        data.setAbilityActive(true);
        data.setDevMode(true);
        for (String id : learned) data.learnSkill(id);
        player.setData(AcademyAttachments.PLAYER_ABILITY, data);
        return player;
    }

    private static double sum(ItemStack stack, EquipmentSlot slot, Holder<Attribute> attribute) {
        double[] total = {0};
        stack.forEachModifier(slot, (holder, modifier) -> {
            if (holder.equals(attribute) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE)
                total[0] += modifier.amount();
        });
        return total[0];
    }

    @GameTest(template = EMPTY)
    public static void resonanceArmourKeepsItsLegacyArmourValues(GameTestHelper helper) {
        // ItemResoArmor: ArmorVars {2, 5, 6, 2} in 1.12 slot-index order (feet, legs, chest, head).
        Item[] items = {AcademyItems.RESO_BOOTS.get(), AcademyItems.RESO_LEGGINGS.get(),
                AcademyItems.RESO_CHESTPLATE.get(), AcademyItems.RESO_HELMET.get()};
        EquipmentSlot[] slots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
        int[] expected = {2, 5, 6, 2};
        double armour = 0;
        for (int i = 0; i < items.length; i++) {
            ItemStack stack = new ItemStack(items[i]);
            double piece = sum(stack, slots[i], Attributes.ARMOR);
            double toughness = sum(stack, slots[i], Attributes.ARMOR_TOUGHNESS);
            if (Math.abs(piece - expected[i]) > 1e-6 || Math.abs(toughness - 1) > 1e-6) {
                helper.fail(slots[i] + " resonance piece gave armour " + piece + " toughness " + toughness
                        + ", expected " + expected[i] + " / 1"); return;
            }
            armour += piece;
        }
        if (Math.abs(armour - 15) > 1e-6) {
            helper.fail("full resonance set gave " + armour + " armour instead of 15"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void offenseArmourDoesNotPushTheAttacker(GameTestHelper helper) {
        ServerPlayer player = caster(helper, AbilityCategory.AEROHAND, "offense_armour");
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        Pig attacker = EntityType.PIG.create(helper.getLevel());
        attacker.setPos(player.getX() + 1, player.getY(), player.getZ());
        attacker.setNoAi(true);
        attacker.setNoGravity(true);
        helper.getLevel().addFreshEntity(attacker);
        try {
            if (!AeroPassiveRuntime.toggleOffenseArmour(player, data)) {
                helper.fail("offense armour did not start"); return;
            }
            attacker.setDeltaMovement(Vec3.ZERO);
            AcceptedAbilityDamage event = new AcceptedAbilityDamage(player,
                    helper.getLevel().damageSources().mobAttack(attacker), 10);
            AeroPassiveRuntime.incomingDamage(event);
            if (event.getAmount() >= 10) {
                helper.fail("offense armour fixture did not absorb the hit"); return;
            }
            if (attacker.getDeltaMovement().lengthSqr() > 1e-9) {
                helper.fail("offense armour pushed the attacker: " + attacker.getDeltaMovement()); return;
            }
            helper.succeed();
        } finally {
            AeroPassiveRuntime.terminateSustained(player, data);
            attacker.discard();
        }
    }

    @GameTest(template = EMPTY)
    public static void offenseArmourDeactivationStillStartsItsCooldown(GameTestHelper helper) {
        ServerPlayer player = caster(helper, AbilityCategory.AEROHAND, "offense_armour");
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        try {
            if (!AeroPassiveRuntime.toggleOffenseArmour(player, data)) {
                helper.fail("offense armour did not start"); return;
            }
            data.setDevMode(false);
            data.clearCooldowns();
            data.setAbilityActive(false);
            AeroPassiveRuntime.playerTick(new PlayerTickEvent.Post(player));
            if (AeroPassiveRuntime.isOffenseArmourEngaged(player)) {
                helper.fail("deactivation left offense armour running"); return;
            }
            if (!data.isOnCooldown("offense_armour")) {
                helper.fail("ExtraAcC s_terminate set a 40-tick cooldown on every termination"); return;
            }
            helper.succeed();
        } finally {
            AeroPassiveRuntime.terminateSustained(player, data);
        }
    }

    @GameTest(template = EMPTY)
    public static void paperDrillIsAToggleWithoutAHoldLimit(GameTestHelper helper) {
        ServerPlayer player = caster(helper, AbilityCategory.TELEKINESIS, "perfect_paper", "paper_drill");
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        player.getInventory().add(new ItemStack(Items.PAPER, 64));
        PaperDrillEffect effect = new PaperDrillEffect();
        try {
            effect.onChargingStart(player, data);
            if (effect.canRelease(player, data, 1)) {
                helper.fail("an unpaid drill accepted a release"); return;
            }
            if (!effect.onChargingTick(player, data, 1)) {
                helper.fail("paper drill did not start"); return;
            }
            if (effect.getSessionTimeoutTicks(data) != Integer.MAX_VALUE) {
                helper.fail("paper drill still has a hard hold limit"); return;
            }
            int late = effect.getMaxChargeTicks(data) + 1;
            if (!effect.onChargingTick(player, data, late) || !PaperDrillEffect.isDrilling(player)) {
                helper.fail("paper drill ended past the old 72000-tick hold limit"); return;
            }
            if (!effect.canRelease(player, data, late) || !effect.tryRelease(player, data, late)) {
                helper.fail("the second press did not end the running drill"); return;
            }
            if (PaperDrillEffect.isDrilling(player) || player.getInventory().countItem(Items.PAPER) != 64) {
                helper.fail("ending the drill did not return its 64 paper"); return;
            }
            helper.succeed();
        } finally {
            effect.onChargingAbort(player, data);
        }
    }

    @GameTest(template = EMPTY)
    public static void paperDrillHitsThroughWallsLikeTheLegacyBox(GameTestHelper helper) {
        ServerPlayer player = caster(helper, AbilityCategory.TELEKINESIS, "perfect_paper", "paper_drill");
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        player.getInventory().add(new ItemStack(Items.PAPER, 64));
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Villager victim = EntityType.VILLAGER.create(helper.getLevel());
        victim.setPos(origin.getX() + 2.5, origin.getY() + 2, origin.getZ() + 3.9);
        victim.setNoAi(true);
        victim.setNoGravity(true);
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        victim.setHealth(200);
        helper.getLevel().addFreshEntity(victim);
        // A solid wall between the eye and the victim: EntityPaperDrill never ray-traced.
        for (int x = 0; x < 6; x++) {
            helper.setBlock(new BlockPos(x, 3, 3), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 2, 3), Blocks.STONE);
        }
        PaperDrillEffect effect = new PaperDrillEffect();
        try {
            effect.onChargingStart(player, data);
            if (!effect.onChargingTick(player, data, 5)) {
                helper.fail("drill fixture did not start"); return;
            }
            if (victim.getHealth() >= 200) {
                helper.fail("paper drill did not reach a target behind a wall inside its 5-block box"); return;
            }
            helper.succeed();
        } finally {
            effect.onChargingAbort(player, data);
            victim.discard();
        }
    }
}
