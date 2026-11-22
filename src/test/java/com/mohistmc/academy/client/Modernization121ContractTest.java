package com.mohistmc.academy.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Regression contracts for 1.21.1 NeoForge modernization (M-01 through M-06). */
class Modernization121ContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy").resolve(relative))
                .replace("\r\n", "\n");
    }

    @Test
    void noDeprecatedModBusSubscriberAttributeRemains() throws Exception {
        for (String file : new String[]{
                "listener/ClientModListener.java",
                "listener/CommonListener.java",
                "api/mob/AcademyMobDataMaps.java",
                "config/ACConfig.java",
                "world/AcademyAttributes.java",
                "skill/ability/telekinesis/LiquidShadowEntity.java",
                "gametest/ChargingOutputGameTests.java"}) {
            assertFalse(source(file).contains("EventBusSubscriber.Bus.MOD"), file);
        }
        assertTrue(source("energy/impl/WirelessSystem.java").contains("@EventBusSubscriber(modid ="));
    }

    @Test
    void extraItemDataUsesNativeDataComponentsForActiveAndLastUse() throws Exception {
        String components = source("world/item/AcademyDataComponents.java");
        String extra = source("world/item/ExtraItemData.java");
        assertTrue(components.contains("DataComponentType<Boolean>> ACTIVE"));
        assertTrue(components.contains("DataComponentType<Long>> LAST_USE"));
        assertTrue(extra.contains("AcademyDataComponents.ACTIVE.get()"));
        assertTrue(extra.contains("AcademyDataComponents.LAST_USE.get()"));
    }

    @Test
    void jeiAndRenderersUseModern121Apis() throws Exception {
        String jei = source("client/jei/MetalFormerJeiCategory.java");
        assertTrue(jei.contains("getTooltip(ITooltipBuilder"));
        assertFalse(jei.contains("getTooltipStrings("));

        assertTrue(source("client/render/PsychoProjectileRenderer.java").contains("InventoryMenu.BLOCK_ATLAS"));
        assertTrue(source("client/render/MagManipBlockRenderer.java").contains("ModelData.EMPTY"));
        assertTrue(source("api/mob/AcademyMobDataMaps.java").contains("BuiltInRegistries.ENTITY_TYPE.wrapAsHolder"));
    }
}
