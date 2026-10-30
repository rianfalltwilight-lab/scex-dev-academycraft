package com.mohistmc.academy.world.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;

/** Resident FULL chunks only: a ticket can exist while generation is unfinished. */
public final class LoadedMachineChunks {
    private LoadedMachineChunks() {}

    public static boolean contains(LevelReader level, BlockPos pos) {
        return level instanceof ServerLevel server
                ? server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null
                : level.hasChunkAt(pos);
    }

    public static boolean containsArea(LevelReader level, BlockPos from, BlockPos to) {
        for (int x = from.getX() >> 4; x <= to.getX() >> 4; x++) {
            for (int z = from.getZ() >> 4; z <= to.getZ() >> 4; z++) {
                if (!contains(level, new BlockPos(x << 4, from.getY(), z << 4))) return false;
            }
        }
        return true;
    }
}
