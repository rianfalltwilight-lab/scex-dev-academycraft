package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.api.event.AbilityEvents;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.SkillCastGate;
import com.mohistmc.academy.skill.SkillDamageSource;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mohistmc.academy.world.AcademyAttributes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Modpack-facing surface: skill damage/resistance attributes and the SkillCast / SkillDamage events. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ModpackApiGameTests {
    private static final String EMPTY = "empty";
    private static final ResourceLocation TEST_MODIFIER = ResourceLocation.fromNamespaceAndPath("academy", "gametest");
    private ModpackApiGameTests() {}

    /** Test-only listeners; inert unless a test arms them. */
    @EventBusSubscriber(modid = AcademyCraft.MODID)
    public static final class Probe {
        static volatile boolean cancelCasts;
        static volatile float damageOverride = -1;
        @SubscribeEvent public static void cast(AbilityEvents.SkillCast event) { if (cancelCasts) event.setCanceled(true); }
        @SubscribeEvent public static void damage(AbilityEvents.SkillDamage event) {
            if (damageOverride >= 0) event.setAmount(damageOverride);
        }
    }

    private static LivingEntity pig(GameTestHelper helper) {
        LivingEntity pig = EntityType.PIG.create(helper.getLevel());
        pig.setPos(helper.absolutePos(net.minecraft.core.BlockPos.ZERO).getCenter());
        helper.getLevel().addFreshEntity(pig);
        return pig;
    }

    @GameTest(template = EMPTY)
    public static void everyLivingEntityCarriesTheSkillAttributes(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        LivingEntity zombie = EntityType.ZOMBIE.create(helper.getLevel());
        for (LivingEntity entity : new LivingEntity[]{player, zombie}) {
            var damage = entity.getAttribute(AcademyAttributes.SKILL_DAMAGE);
            var resistance = entity.getAttribute(AcademyAttributes.SKILL_RESISTANCE);
            if (damage == null || resistance == null || damage.getValue() != 1.0 || resistance.getValue() != 0.0) {
                helper.fail(entity.getType() + " lacks the skill attributes or has wrong defaults"); return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void abilityDamageScalesWithAttackerAndTargetAttributes(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        LivingEntity pig = pig(helper);
        player.getAttribute(AcademyAttributes.SKILL_DAMAGE).addTransientModifier(
                new AttributeModifier(TEST_MODIFIER, 1.0, AttributeModifier.Operation.ADD_VALUE));
        pig.getAttribute(AcademyAttributes.SKILL_RESISTANCE).addTransientModifier(
                new AttributeModifier(TEST_MODIFIER, 0.5, AttributeModifier.Operation.ADD_VALUE));
        float before = pig.getHealth();
        AcademyDamageHelper.hurt(player, pig, SkillDamageSource.attack(player), 3F);
        float dealt = before - pig.getHealth();
        // 3 raw x 2.0 skill damage x (1 - 0.5) resistance = 3.
        if (Math.abs(dealt - 3F) > 1.0e-3) { helper.fail("expected 3 damage after attributes, got " + dealt); return; }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void skillDamageEventCanRewriteTheFinalAmount(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        LivingEntity pig = pig(helper);
        float before = pig.getHealth();
        Probe.damageOverride = 1F;
        try {
            AcademyDamageHelper.hurt(player, pig, SkillDamageSource.attack(player), 6F);
        } finally {
            Probe.damageOverride = -1;
        }
        float dealt = before - pig.getHealth();
        if (Math.abs(dealt - 1F) > 1.0e-3) { helper.fail("SkillDamage listener did not set the amount: " + dealt); return; }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void skillCastEventCanRefuseACast(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var skill = SkillRegistry.getSkill("arc_gen");
        if (!SkillCastGate.allow(player, skill)) { helper.fail("an unarmed probe refused the cast"); return; }
        Probe.cancelCasts = true;
        try {
            if (SkillCastGate.allow(player, skill)) { helper.fail("a cancelled SkillCast still allowed the cast"); return; }
        } finally {
            Probe.cancelCasts = false;
        }
        helper.succeed();
    }
}
