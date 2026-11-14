package com.mohistmc.academy.regression;

import com.mohistmc.academy.gametest.KubeJSIntegrationGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Separate namespace: the KubeJS probe must not cancel skills in unrelated suites. */
@GameTestHolder("academy_kubejs_compat")
@PrefixGameTestTemplate(false)
public final class ProductionKubeJSCompatGate {
    private static void requireKubeJS(GameTestHelper h) {
        h.assertTrue(ModList.get().isLoaded("kubejs"), "Actual KubeJS must be installed");
    }
    @GameTest(template = "empty")
    public static void targetedCast(GameTestHelper h) {
        requireKubeJS(h);
        KubeJSIntegrationGameTests.kubejsSkillCastListenerCancelsOnlyItsSkill(h);
    }
    @GameTest(template = "empty")
    public static void damageHook(GameTestHelper h) {
        requireKubeJS(h);
        KubeJSIntegrationGameTests.kubejsSkillDamageListenerChangesTheAmount(h);
    }
    @GameTest(template = "empty")
    public static void recipeSchemas(GameTestHelper h) {
        requireKubeJS(h);
        KubeJSIntegrationGameTests.kubejsRecipeSchemasProduceWorkingRecipes(h);
    }
}
