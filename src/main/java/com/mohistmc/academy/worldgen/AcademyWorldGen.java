package com.mohistmc.academy.worldgen;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.world.AcademyBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;

/**
 * AcademyCraft 世界生成 —— 矿石和 PhaseLiquid 湖。
 */
public final class AcademyWorldGen {

    private AcademyWorldGen() {}

    // ==================== Configured Feature Keys ====================

    public static final ResourceKey<ConfiguredFeature<?, ?>> ORE_IMAGSIL =
            ResourceKey.create(Registries.CONFIGURED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "ore_imagsil"));

    public static final ResourceKey<ConfiguredFeature<?, ?>> ORE_RESO =
            ResourceKey.create(Registries.CONFIGURED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "ore_reso"));

    public static final ResourceKey<ConfiguredFeature<?, ?>> ORE_CRYSTAL =
            ResourceKey.create(Registries.CONFIGURED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "ore_crystal"));

    public static final ResourceKey<ConfiguredFeature<?, ?>> ORE_CONSTRAINT =
            ResourceKey.create(Registries.CONFIGURED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "ore_constraint"));

    // ==================== Datagen (bootstrap) ====================

    public static void bootstrapConfiguredFeatures(BootstrapContext<ConfiguredFeature<?, ?>> ctx) {
        // Keep this bootstrap path byte-for-behaviour aligned with the checked-in
        // datapack and the four WorldGenMinable declarations in AcademyCraft
        // 1.0.7.  It is currently a datagen/bootstrap helper, but divergent
        // fallback values make later regeneration silently change world balance.
        ctx.register(ORE_IMAGSIL, new ConfiguredFeature<>(
                Feature.ORE,
                new OreConfiguration(
                        new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES),
                        AcademyBlocks.IMAGSIL_ORE.get().defaultBlockState(),
                        11
                )
        ));

        ctx.register(ORE_RESO, new ConfiguredFeature<>(
                Feature.ORE,
                new OreConfiguration(
                        new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES),
                        AcademyBlocks.RESO_ORE.get().defaultBlockState(),
                        9
                )
        ));

        ctx.register(ORE_CRYSTAL, new ConfiguredFeature<>(
                Feature.ORE,
                new OreConfiguration(
                        new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES),
                        AcademyBlocks.CRYSTAL_ORE.get().defaultBlockState(),
                        12
                )
        ));

        ctx.register(ORE_CONSTRAINT, new ConfiguredFeature<>(
                Feature.ORE,
                new OreConfiguration(
                        new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES),
                        AcademyBlocks.CONSTRAIN_METAL.get().defaultBlockState(),
                        12
                )
        ));
    }

}
