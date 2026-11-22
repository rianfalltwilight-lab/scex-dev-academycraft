package com.mohistmc.academy.skill.ability.electromaster;

import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.config.LegacyMetalIdRules;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Reload-safe registry/tag matching for the legacy Electromaster target lists. */
public final class ElectromasterMetalTargets {
    /** Pack-facing additions to the normalMetalBlocks config list (config OR tag). */
    public static final TagKey<Block> NORMAL_METAL_TAG = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("academy", "metal/normal"));
    /** Pack-facing additions to the weakMetalBlocks config list (config OR tag). */
    public static final TagKey<Block> WEAK_METAL_TAG = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("academy", "metal/weak"));
    /** Pack-facing additions to the metalEntities config list (config OR tag). */
    public static final TagKey<net.minecraft.world.entity.EntityType<?>> METAL_ENTITY_TAG = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("academy", "metal"));

    private ElectromasterMetalTargets() {}

    public static boolean isNormal(BlockState state) {
        return state != null && (state.is(NORMAL_METAL_TAG)
                || matchesBlock(state, ACConfig.Server.normalMetalBlocks()));
    }

    public static boolean isWeak(BlockState state) {
        return state != null && (state.is(WEAK_METAL_TAG)
                || matchesBlock(state, ACConfig.Server.weakMetalBlocks()));
    }

    public static boolean isAny(BlockState state) {
        return isNormal(state) || isWeak(state);
    }

    public static boolean isMetalEntity(Entity entity) {
        if (entity == null) return false;
        if (entity.getType().is(METAL_ENTITY_TAG)) return true;
        ResourceLocation actual = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        for (String raw : ACConfig.Server.metalEntities()) {
            String normalized = LegacyMetalIdRules.entityId(raw);
            if (normalized.startsWith("#")) {
                ResourceLocation id = ResourceLocation.tryParse(normalized.substring(1));
                if (id != null && entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, id))) return true;
            } else if (actual.equals(ResourceLocation.tryParse(normalized))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesBlock(BlockState state, List<? extends String> entries) {
        if (state == null) return false;
        ResourceLocation actual = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (String raw : entries) {
            String normalized = LegacyMetalIdRules.blockId(raw);
            if (normalized.startsWith("#")) {
                ResourceLocation id = ResourceLocation.tryParse(normalized.substring(1));
                if (id != null && state.is(TagKey.create(Registries.BLOCK, id))) return true;
            } else if (actual.equals(ResourceLocation.tryParse(normalized))) {
                return true;
            }
        }
        return false;
    }
}
