package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEventHandler;
import com.mohistmc.academy.skill.ability.aerohand.AeroPassiveRuntime;
import com.mohistmc.academy.skill.ability.telekinesis.TelekinesisPassiveHandler;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** AcademyCraft 1.12.2 CPData rules: overload lockout, death/sleep recovery, creative exemption, V key. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class LegacyResourceRuleGameTests {
    private static final String EMPTY = "empty";
    private LegacyResourceRuleGameTests() {}

    private static PlayerAbilityData ability(ServerPlayer player, AbilityCategory category, String... learned) {
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(category);
        data.setPlayerLevel(3);
        data.setAbilityActive(true);
        for (String id : learned) data.learnSkill(id);
        data.recalculateMaxResources(true);
        player.setData(AcademyAttachments.PLAYER_ABILITY, data);
        return data;
    }

    private static ServerPlayer survival(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void overloadClampsLocksAndUnlocksAtZero(GameTestHelper helper) {
        ServerPlayer player = survival(helper);
        PlayerAbilityData data = ability(player, AbilityCategory.ELECTROMASTER);
        float max = data.getMaxOverload();
        data.setCurrentOverload(max - 5);
        // CPData.perform checks only CP: the cast that crosses the cap still goes off.
        if (!data.tryConsumeDynamic(10, 10)) {
            helper.fail("a cast pushing overload past the cap was refused instead of clamped"); return;
        }
        if (data.getCurrentOverload() != max || !data.isOverloadLocked() || !data.consumeOverloadTrigger()) {
            helper.fail("reaching max overload did not clamp and latch the lockout"); return;
        }
        if (data.tryConsumeDynamic(0, 0) || DynamicSkillRules.canPay(data, "arc_gen", 1, 0)) {
            helper.fail("an overloaded player could still pay for a skill"); return;
        }
        for (int i = 0; i < 20_000 && data.getCurrentOverload() > 0; i++) data.tick();
        if (data.isOverloadLocked() || !data.tryConsumeDynamic(1, 1)) {
            helper.fail("the lockout did not lift once overload drained to zero"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void overloadEndsSustainedSkillsOnTheNextTick(GameTestHelper helper) {
        ServerPlayer player = survival(helper);
        PlayerAbilityData data = ability(player, AbilityCategory.AEROHAND, "offense_armour");
        if (!AeroPassiveRuntime.toggleOffenseArmour(player, data)) {
            helper.fail("offense armour did not start"); return;
        }
        data.setCurrentOverload(data.getMaxOverload() - 1);
        data.tryConsumeDynamic(0, 5);
        SkillEventHandler.onPlayerTick(new PlayerTickEvent.Post(player));
        if (AeroPassiveRuntime.isOffenseArmourEngaged(player)) {
            AeroPassiveRuntime.terminateSustained(player, data);
            helper.fail("overloading left a sustained skill running"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void deathRestoresResourcesClearsCooldownsAndDeactivates(GameTestHelper helper) {
        ServerPlayer player = survival(helper);
        PlayerAbilityData data = ability(player, AbilityCategory.ELECTROMASTER);
        data.setCurrentCp(0);
        data.setCurrentOverload(data.getMaxOverload());
        data.setOverloadLocked(true);
        data.setCooldown("railgun", 400);
        SkillEventHandler.onConfirmedDeath(player);
        if (data.getCurrentCp() != data.getMaxCp() || data.getCurrentOverload() != 0
                || data.isOverloadLocked() || data.isAbilityActive() || data.isOnCooldown("railgun")) {
            helper.fail("death kept CP " + data.getCurrentCp() + ", overload " + data.getCurrentOverload()
                    + ", lock " + data.isOverloadLocked() + ", active " + data.isAbilityActive()); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void onlyAFullNightsSleepRestoresCp(GameTestHelper helper) {
        ServerPlayer player = survival(helper);
        PlayerAbilityData data = ability(player, AbilityCategory.ELECTROMASTER);
        data.setCurrentCp(0);
        SkillEventHandler.onPlayerWakeUp(new PlayerWakeUpEvent(player, true, true));
        if (data.getCurrentCp() != 0) {
            helper.fail("leaving the bed early restored CP"); return;
        }
        SkillEventHandler.onPlayerWakeUp(new PlayerWakeUpEvent(player, false, false));
        if (data.getCurrentCp() != data.getMaxCp()) {
            helper.fail("waking at morning did not restore CP"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void creativePaysNeitherCpNorOverload(GameTestHelper helper) {
        // The mock player hard-codes isCreative() == true, so use a real connected ServerPlayer.
        try (var session = com.mohistmc.academy.gametest.recheck.RecheckPlayers.connect(helper)) {
            creativeCase(helper, session.player());
        }
    }

    private static void creativeCase(GameTestHelper helper, ServerPlayer player) {
        PlayerAbilityData data = ability(player, AbilityCategory.ELECTROMASTER);
        player.setGameMode(GameType.CREATIVE);
        SkillEventHandler.onPlayerTick(new PlayerTickEvent.Post(player));
        data.setCurrentCp(5);
        float growth = data.getUsageMaxCp();
        if (!data.tryConsumeDynamic(100, 50)) {
            helper.fail("creative could not afford a cast above its CP"); return;
        }
        if (data.getCurrentCp() != 5 || data.getCurrentOverload() != 0) {
            helper.fail("creative paid CP or overload"); return;
        }
        if (data.getUsageMaxCp() <= growth) {
            helper.fail("creative casts no longer grow max CP, unlike CPData.performInternal"); return;
        }
        player.setGameMode(GameType.SURVIVAL);
        SkillEventHandler.onPlayerTick(new PlayerTickEvent.Post(player));
        if (data.tryConsumeDynamic(100, 0)) {
            helper.fail("leaving creative kept the exemption"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void activationKeyEndsSpecialModesBeforeToggling(GameTestHelper helper) {
        ServerPlayer player = survival(helper);
        PlayerAbilityData data = ability(player, AbilityCategory.TELEKINESIS, "psycho_harden");
        data.setDevMode(true);
        if (!TelekinesisPassiveHandler.togglePsychoHarden(player, data)) {
            helper.fail("psycho harden did not start"); return;
        }
        if (!SkillEventHandler.endSpecialFromActivateKey(player, data)
                || TelekinesisPassiveHandler.isHardened(player)) {
            helper.fail("V did not end the running special mode"); return;
        }
        if (SkillEventHandler.endSpecialFromActivateKey(player, data)) {
            helper.fail("V claimed to end something with nothing running, so it could never toggle"); return;
        }
        helper.succeed();
    }
}
