package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.ConfirmedAbilityDeath;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEventHandler;
import com.mohistmc.academy.skill.ability.telekinesis.TelekinesisPassiveHandler;
import com.mohistmc.academy.skill.ability.teleporter.FlashingSessionManager;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.item.ElectricaliburItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Follow-ups to the overload lockout, death reset and creative exemption, plus the Electricalibur heal bug. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class OverloadAndDeathFollowupGameTests {
    private static final String EMPTY = "empty";
    private OverloadAndDeathFollowupGameTests() {}

    private static PlayerAbilityData ability(ServerPlayer player, AbilityCategory category, String... learned) {
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(category);
        data.setPlayerLevel(5);
        data.setAbilityActive(true);
        for (String id : learned) data.learnSkill(id);
        data.recalculateMaxResources(true);
        return data;
    }

    private static ServerPlayer survival(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    @GameTest(template = EMPTY)
    public static void forcedPaymentsAreRefusedWhileOverloaded(GameTestHelper helper) {
        PlayerAbilityData data = ability(survival(helper), AbilityCategory.TELEPORTER);
        data.setCurrentOverload(data.getMaxOverload() - 1);
        data.tryConsumeDynamic(0, 5);
        if (!data.isOverloadLocked()) { helper.fail("setup did not latch the lockout"); return; }
        float cp = data.getCurrentCp();
        // Mark / Penetrate / Flesh Ripping / Location Teleport all pay with the forced debit.
        if (data.consumeDynamicForced(10, 10) || data.getCurrentCp() != cp) {
            helper.fail("a forced payment went through while overloaded"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void overloadEndsFlashingWithItsCooldown(GameTestHelper helper) {
        ServerPlayer player = survival(helper);
        PlayerAbilityData data = ability(player, AbilityCategory.TELEPORTER, "flashing");
        if (!FlashingSessionManager.start(player, data)) { helper.fail("flashing did not start"); return; }
        data.setCurrentOverload(data.getMaxOverload() - 1);
        data.tryConsumeDynamic(0, 5);
        SkillEventHandler.onPlayerTick(new PlayerTickEvent.Post(player));
        if (!data.isOnCooldown("flashing")) {
            FlashingSessionManager.abort(player);
            helper.fail("overload ended Flashing without the legacy cooldown"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void deathLeavesNoCooldownFromEndingSustainedSkills(GameTestHelper helper) {
        ServerPlayer player = survival(helper);
        PlayerAbilityData data = ability(player, AbilityCategory.TELEKINESIS, "psycho_harden");
        if (!TelekinesisPassiveHandler.togglePsychoHarden(player, data)) { helper.fail("harden did not start"); return; }
        ConfirmedAbilityDeath.accept(player);
        TelekinesisPassiveHandler.playerTick(new PlayerTickEvent.Post(player));
        if (data.isOnCooldown("psycho_harden") || TelekinesisPassiveHandler.isHardened(player)) {
            helper.fail("dying while hardened left a cooldown or a live harden"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void gameModeChangeUpdatesCreativeExemptionImmediately(GameTestHelper helper) {
        try (var session = com.mohistmc.academy.gametest.recheck.RecheckPlayers.connect(helper)) {
            ServerPlayer player = session.player();
            PlayerAbilityData data = ability(player, AbilityCategory.ELECTROMASTER);
            player.setGameMode(GameType.CREATIVE);
            if (!data.isCreativeExempt()) { helper.fail("switching to creative left the cast paid"); return; }
            player.setGameMode(GameType.SURVIVAL);
            if (data.isCreativeExempt()) { helper.fail("switching back kept the exemption"); return; }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void drainedElectricaliburNeverDealsNegativeDamage(GameTestHelper helper) {
        ServerPlayer player = survival(helper);
        ItemStack sword = new ItemStack(AcademyItems.ELECTRICALIBUR.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        ElectricaliburItem item = (ElectricaliburItem) sword.getItem();
        item.setEnergy(sword, 0);
        var zombie = EntityType.ZOMBIE.create(helper.getLevel());
        var source = player.damageSources().playerAttack(player);
        // Player.attack passes the swing-scaled damage; 15 is a full swing, 3 a 20% swing.
        for (float base : new float[]{15F, 3F, 0.6F}) {
            float total = base + item.getAttackDamageBonus(zombie, base, source);
            if (total < 0) { helper.fail("drained sword dealt " + total + " (heals) at base " + base); return; }
        }
        item.setEnergy(sword, 100_000);
        float charged = 15F + item.getAttackDamageBonus(zombie, 15F, source);
        if (Math.abs(charged - 16F) > 1.0e-3) { helper.fail("charged full swing is " + charged + ", not 16"); return; }
        helper.succeed();
    }
}
