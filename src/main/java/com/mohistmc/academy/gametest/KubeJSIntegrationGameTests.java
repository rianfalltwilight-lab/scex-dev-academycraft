package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.SkillCastGate;
import com.mohistmc.academy.skill.SkillDamageSource;
import com.mohistmc.academy.skill.SkillRegistry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Runs only with KubeJS installed (./gradlew runGameTestServer -PwithKubeJS=true) and the probe script
 * kubejs/server_scripts/academy_probe.js in the game directory; otherwise each test passes immediately.
 */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class KubeJSIntegrationGameTests {
    private KubeJSIntegrationGameTests() {}

    private static boolean active() {
        return ModList.get().isLoaded("kubejs");
    }

    /**
     * A server player that never logs in: KubeJS sends its own payload on login, which the GameTest
     * mock connection cannot carry.
     */
    private static net.minecraft.server.level.ServerPlayer offlinePlayer(GameTestHelper helper) {
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "kjs_probe");
        return new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile,
                net.minecraft.server.level.ClientInformation.createDefault());
    }

    @GameTest(template = "empty")
    public static void kubejsSkillCastListenerCancelsOnlyItsSkill(GameTestHelper helper) {
        if (!active()) { helper.succeed(); return; }
        var player = offlinePlayer(helper);
        if (SkillCastGate.allow(player, SkillRegistry.getSkill("arc_gen"))) {
            helper.fail("AcademyEvents.skillCast('arc_gen') did not cancel the cast"); return;
        }
        if (!SkillCastGate.allow(player, SkillRegistry.getSkill("thunder_bolt"))) {
            helper.fail("a skill-targeted listener cancelled another skill"); return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void kubejsSkillDamageListenerChangesTheAmount(GameTestHelper helper) {
        if (!active()) { helper.succeed(); return; }
        // A mob attacker (mob-cast skills) avoids logging a mock player in.
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, 3, 2, 1);
        husk.addTag("academy_probe_double");
        LivingEntity pig = helper.spawnWithNoFreeWill(EntityType.PIG, 1, 2, 1);
        float before = pig.getHealth();
        AcademyDamageHelper.hurt(husk, pig, SkillDamageSource.attack(husk), 2F);
        float dealt = before - pig.getHealth();
        if (Math.abs(dealt - 4F) > 1.0e-3) { helper.fail("script did not double the hit: " + dealt); return; }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void kubejsRecipeSchemasProduceWorkingRecipes(GameTestHelper helper) {
        if (!active()) { helper.succeed(); return; }
        var manager = helper.getLevel().getRecipeManager();
        var forming = manager.byKey(ResourceLocation.fromNamespaceAndPath("academy", "kubejs_probe_forming"));
        var fusor = manager.byKey(ResourceLocation.fromNamespaceAndPath("academy", "kubejs_probe_fusor"));
        if (forming.isEmpty() || !(forming.get().value() instanceof com.mohistmc.academy.crafting.MetalFormingRecipe former)) {
            helper.fail("event.recipes.academy.metal_forming did not produce a metal forming recipe"); return;
        }
        if (former.processTime() != 5) { helper.fail("the .time(5) builder call was lost: " + former.processTime()); return; }
        if (fusor.isEmpty() || !(fusor.get().value() instanceof com.mohistmc.academy.crafting.ImagFusorRecipe)) {
            helper.fail("event.recipes.academy.imag_fusor did not produce an imag fusor recipe"); return;
        }
        helper.succeed();
    }
}
