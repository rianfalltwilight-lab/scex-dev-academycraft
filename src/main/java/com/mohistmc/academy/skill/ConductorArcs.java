package com.mohistmc.academy.skill;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.api.event.AbilityEvents;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 1.0.7's IC2 support drew a surrounding arc on every IC2 cable within 5 blocks when a player switched
 * the ability on.  The cables are the block tag {@code academy:arc_conductors}; it lists Singularity
 * Iteration's cables as optional entries, and packs may add their own.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class ConductorArcs {
    public static final TagKey<Block> ARC_CONDUCTORS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "arc_conductors"));
    static final int RADIUS = 5;
    /** 1.0.7 allowed 100; each arc is a synced effect entity, so the nearest 32 are drawn. */
    static final int MAX_ARCS = 32;
    private static final int ARC_TICKS = 20;

    private ConductorArcs() {}

    @SubscribeEvent
    public static void activated(AbilityEvents.Activate event) {
        if (event.player instanceof ServerPlayer player) spawn(player.serverLevel(), player.blockPosition());
    }

    /** The conductors that get an arc, nearest first. */
    public static List<BlockPos> conductors(ServerLevel level, BlockPos centre) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-RADIUS, -RADIUS, -RADIUS),
                centre.offset(RADIUS, RADIUS, RADIUS))) {
            if (level.isLoaded(pos) && level.getBlockState(pos).is(ARC_CONDUCTORS)) found.add(pos.immutable());
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(centre)));
        return found.size() > MAX_ARCS ? new ArrayList<>(found.subList(0, MAX_ARCS)) : found;
    }

    static int spawn(ServerLevel level, BlockPos centre) {
        List<BlockPos> conductors = conductors(level, centre);
        for (BlockPos pos : conductors)
            EffectHelper.arcSpark(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 1, .5, ARC_TICKS);
        return conductors.size();
    }
}
