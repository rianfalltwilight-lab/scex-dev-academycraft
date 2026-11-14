package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.api.mob.AcademyMobAbilities;
import com.mohistmc.academy.api.mob.MobAbilityData;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Skill-independent parts of mob casting: result codes, the attachment codec, mob ally rules. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MobCastFoundationGameTests {
    private MobCastFoundationGameTests() {}

    @GameTest(template = "empty")
    public static void castRefusesPlayersUnknownAndUnsupportedSkills(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        LivingEntity zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 2, 1);
        var player_result = AcademyMobAbilities.cast(player, "thunder_bolt");
        var unknown = AcademyMobAbilities.cast(zombie, "no_such_skill");
        // Flying is a player-only sustained toggle and never mob-castable.
        var unsupported = AcademyMobAbilities.cast(zombie, "academy:flying");
        if (player_result != AcademyMobAbilities.Result.INVALID_CASTER
                || unknown != AcademyMobAbilities.Result.UNKNOWN_SKILL
                || unsupported != AcademyMobAbilities.Result.UNSUPPORTED) {
            helper.fail("results " + player_result + " / " + unknown + " / " + unsupported); return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mobAbilityDataRoundTripsThroughItsCodec(GameTestHelper helper) {
        MobAbilityData data = new MobAbilityData();
        data.setSkill("thunder_bolt", 0.75F);
        data.setSkill("air_blade", 7F);
        data.setCooldown("thunder_bolt", 100, 40);
        var tag = MobAbilityData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        MobAbilityData back = MobAbilityData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        if (!back.skills().equals(data.skills()) || back.proficiency("air_blade") != 1F
                || back.cooldownRemaining("thunder_bolt", 120) != 20 || back.isOnCooldown("thunder_bolt", 140)) {
            helper.fail("attachment did not survive a codec round trip: " + tag); return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mobCastersSpareThemselvesTheirKindAndTheirTeam(GameTestHelper helper) {
        LivingEntity zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 2, 1);
        LivingEntity otherZombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 1);
        LivingEntity pig = helper.spawnWithNoFreeWill(EntityType.PIG, 1, 2, 3);
        if (AcademyDamageHelper.allowsTarget(zombie, zombie) || AcademyDamageHelper.allowsTarget(zombie, otherZombie)) {
            helper.fail("a mob caster may hit itself or its own type"); return;
        }
        if (!AcademyDamageHelper.allowsTarget(zombie, pig)) { helper.fail("a mob caster may not hit a pig"); return; }
        var scoreboard = helper.getLevel().getScoreboard();
        var team = scoreboard.addPlayerTeam("academy_mobcast_" + helper.getTick());
        try {
            scoreboard.addPlayerToTeam(zombie.getStringUUID(), team);
            scoreboard.addPlayerToTeam(pig.getStringUUID(), team);
            if (AcademyDamageHelper.allowsTarget(zombie, pig)) { helper.fail("a mob caster hit its team mate"); return; }
        } finally {
            scoreboard.removePlayerTeam(team);
        }
        helper.succeed();
    }
}
