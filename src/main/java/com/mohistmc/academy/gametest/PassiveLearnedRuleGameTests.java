package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.telekinesis.TelekinesisPassiveHandler;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Legacy passives checked only isSkillLearned: they work while the ability is switched off. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class PassiveLearnedRuleGameTests {
    private PassiveLearnedRuleGameTests() {}

    @GameTest(template = "empty")
    public static void insulationProtectsWithTheAbilitySwitchedOff(GameTestHelper helper) {
        ServerPlayer defender = helper.makeMockServerPlayerInLevel();
        defender.setGameMode(GameType.SURVIVAL);
        PlayerAbilityData data = defender.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(AbilityCategory.TELEKINESIS);
        data.setPlayerLevel(5);
        data.learnSkill("insulation");
        data.setProficiency("insulation", 1);
        data.setAbilityActive(false);
        data.recalculateMaxResources(true);
        ServerPlayer attacker = helper.makeMockServerPlayerInLevel();
        attacker.getData(AcademyAttachments.PLAYER_ABILITY).setCurrentAbility(AbilityCategory.ELECTROMASTER);
        float reduced = TelekinesisPassiveHandler.mitigateAbilityDamage(attacker, defender, 10);
        if (reduced >= 10) {
            helper.fail("insulation stopped protecting once the ability was switched off"); return;
        }
        helper.succeed();
    }
}
