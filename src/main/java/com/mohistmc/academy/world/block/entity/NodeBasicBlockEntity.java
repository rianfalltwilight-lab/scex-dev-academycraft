package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.world.AcademyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class NodeBasicBlockEntity extends BaseNodeBlockEntity {
    public NodeBasicBlockEntity(BlockPos pos, BlockState state) {
        super(AcademyBlockEntities.NODE_BASIC.get(), pos, state);
    }

    @Override
    protected com.mohistmc.academy.config.MachineConfig.NodeTier tier() {
        return com.mohistmc.academy.config.MachineConfig.NODE_BASIC;
    }
}
