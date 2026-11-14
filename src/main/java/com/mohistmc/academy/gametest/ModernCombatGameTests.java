package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.config.BalanceProfile;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.CombatRules;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillDamageSource;
import com.mohistmc.academy.skill.passive.VecDefenseLegacyMath;
import com.mohistmc.academy.world.AcademyAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** MZ3: the modern combat stage (level scaling, skill_power, bosses, mob scaling, defence costs, health). */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ModernCombatGameTests {
    private static final String EMPTY = "empty";
    private static final String BATCH = "modern_combat";
    private static final ResourceLocation MODIFIER = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "mz3_test");
    private ModernCombatGameTests() {}

    private static void check(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }

    private static boolean near(float a, float b) { return Math.abs(a - b) <= Math.max(.01f, Math.abs(b) * 1e-3f); }

    private interface Body { void run(); }

    private static void withProfile(BalanceProfile profile, Body body) {
        BalanceProfile before = ACConfig.Server.BALANCE_PROFILE.get();
        ACConfig.Server.BALANCE_PROFILE.set(profile);
        try { body.run(); } finally { ACConfig.Server.BALANCE_PROFILE.set(before); }
    }

    private static ServerPlayer esper(GameTestHelper helper, int level) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(AbilityCategory.ELECTROMASTER);
        data.setPlayerLevel(level);
        return player;
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void levelScalingAndSkillPowerOnlyInModern(GameTestHelper helper) {
        ServerPlayer player = esper(helper, 5);
        withProfile(BalanceProfile.LEGACY, () -> check(near(CombatRules.offense(player, 10), 10), "legacy scaled"));
        withProfile(BalanceProfile.MODERN, () -> check(near(CombatRules.offense(player, 10), 50),
                "modern L5 x5: " + CombatRules.offense(player, 10)));
        var power = player.getAttribute(AcademyAttributes.SKILL_POWER);
        check(power != null, "skill_power missing on players");
        power.addTransientModifier(new AttributeModifier(MODIFIER, 4, AttributeModifier.Operation.ADD_VALUE));
        try {
            withProfile(BalanceProfile.LEGACY, () -> check(near(CombatRules.offense(player, 10), 14), "skill_power not added"));
            withProfile(BalanceProfile.MODERN, () -> check(near(CombatRules.offense(player, 10), 70),
                    "skill_power must be added before the level multiplier"));
        } finally {
            power.removeModifier(MODIFIER);
        }

        // End to end on an armourless pig.
        withProfile(BalanceProfile.MODERN, () -> {
            Pig pig = EntityType.PIG.create(helper.getLevel());
            pig.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2, 2, 2)));
            pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
            pig.setHealth(500);
            helper.getLevel().addFreshEntity(pig);
            AcademyDamageHelper.hurt(player, pig, SkillDamageSource.attack(player), 10);
            check(near(500 - pig.getHealth(), 50), "pig lost " + (500 - pig.getHealth()));
            pig.discard();
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void bossBonusMobScalingAndPlayerCap(GameTestHelper helper) {
        var wither = EntityType.WITHER.create(helper.getLevel());
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        ServerPlayer player = esper(helper, 1);
        withProfile(BalanceProfile.LEGACY, () -> {
            check(near(CombatRules.bossBonus(wither, 100), 100), "legacy boss bonus");
            check(near(CombatRules.offense(zombie, 10), 10), "legacy mob scaling");
            check(near(CombatRules.capVsPlayer(zombie, player, 100), 100), "legacy player cap");
        });
        withProfile(BalanceProfile.MODERN, () -> {
            float bonus = .02f * wither.getMaxHealth();
            check(near(CombatRules.bossBonus(wither, 100), 100 + bonus), "boss bonus: " + CombatRules.bossBonus(wither, 100));
            check(near(CombatRules.bossBonus(wither, 1), 3), "boss bonus not capped at 2x the hit");
            check(near(CombatRules.bossBonus(zombie, 100), 100), "non-boss got the boss bonus");
            var attack = zombie.getAttribute(Attributes.ATTACK_DAMAGE);
            attack.addTransientModifier(new AttributeModifier(MODIFIER, attack.getBaseValue(),
                    AttributeModifier.Operation.ADD_VALUE));
            check(near(CombatRules.offense(zombie, 10), 20), "mob attack scaling: " + CombatRules.offense(zombie, 10));
            check(near(CombatRules.capVsPlayer(zombie, player, 100), .5f * player.getMaxHealth()), "player cap");
            check(near(CombatRules.capVsPlayer(player, zombie, 100), 100), "cap applied to a player's hit");
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void defenceCostsBendAndLevelHealthApplies(GameTestHelper helper) {
        withProfile(BalanceProfile.LEGACY, () -> {
            check(near(VecDefenseLegacyMath.reflectionDamageCost(1, 100), 1500), "legacy reflection cost");
            check(near(VecDefenseLegacyMath.deviationDamageCost(1, 100), 12), "legacy deviation cost");
        });
        withProfile(BalanceProfile.MODERN, () -> {
            check(near(VecDefenseLegacyMath.reflectionDamageCost(1, 100), 15 * (float) Math.pow(100, .7)),
                    "modern reflection cost: " + VecDefenseLegacyMath.reflectionDamageCost(1, 100));
            check(near(VecDefenseLegacyMath.deviationDamageCost(1, 100), 62), "modern deviation cost");
        });
        ServerPlayer player = esper(helper, 5);
        try {
            withProfile(BalanceProfile.MODERN, () -> {
                CombatRules.applyLevelHealth(player);
                check(near(player.getMaxHealth(), 32), "modern L5 health: " + player.getMaxHealth());
            });
            withProfile(BalanceProfile.LEGACY, () -> {
                CombatRules.applyLevelHealth(player);
                check(near(player.getMaxHealth(), 20), "legacy kept the level health: " + player.getMaxHealth());
            });
        } finally {
            withProfile(BalanceProfile.LEGACY, () -> CombatRules.applyLevelHealth(player));
        }
        helper.succeed();
    }
}
