package com.mohistmc.academy.skill.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;

/** Vanilla shape raycast whose world reads never request a missing chunk. */
public final class LoadedRaycast {
    private LoadedRaycast() {}

    public static boolean isLoaded(Level level, BlockPos pos) {
        // ServerChunkCache.hasChunk also accepts a holder still generating FULL.
        return level.isLoaded(pos) && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    public static boolean isAreaLoaded(Level level, AABB box) {
        int minX = net.minecraft.util.Mth.floor(box.minX) >> 4, maxX = net.minecraft.util.Mth.floor(Math.nextDown(box.maxX)) >> 4;
        int minZ = net.minecraft.util.Mth.floor(box.minZ) >> 4, maxZ = net.minecraft.util.Mth.floor(Math.nextDown(box.maxZ)) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }

    /** An unavailable cell is an obstruction; callers must check isLoaded before using its position. */
    public static BlockHitResult clip(Level level, ClipContext context) {
        return new BlockGetter() {
            @Override public BlockEntity getBlockEntity(BlockPos pos) {
                var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                return chunk == null ? null : chunk.getBlockEntity(pos);
            }
            @Override public BlockState getBlockState(BlockPos pos) {
                if (level.isOutsideBuildHeight(pos)) return Blocks.VOID_AIR.defaultBlockState();
                var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                return chunk == null ? Blocks.BARRIER.defaultBlockState() : chunk.getBlockState(pos);
            }
            @Override public FluidState getFluidState(BlockPos pos) {
                if (level.isOutsideBuildHeight(pos)) return Fluids.EMPTY.defaultFluidState();
                var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                return chunk == null ? Fluids.EMPTY.defaultFluidState() : chunk.getFluidState(pos);
            }
            @Override public int getHeight() { return level.getHeight(); }
            @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
        }.clip(context);
    }

    public static BlockHitResult pick(Entity entity, double range) {
        var start = entity.getEyePosition();
        return clip(entity.level(), new ClipContext(start, start.add(entity.getLookAngle().scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
    }
}
