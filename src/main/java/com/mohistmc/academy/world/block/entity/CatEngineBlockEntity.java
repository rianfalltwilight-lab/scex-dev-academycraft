package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.config.MachineConfig;
import com.mohistmc.academy.energy.api.block.IWirelessGenerator;
import com.mohistmc.academy.energy.impl.WiWorldData;
import com.mohistmc.academy.world.AcademyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 风力猫引擎 —— 实现 IWirelessGenerator 向无线能源网络供电。
 * @author Mgazul
 */
public class CatEngineBlockEntity extends AcademyContainerBlockEntity implements IWirelessGenerator {

    /** Transient client animation; the renderer only interpolates this tick-owned state. */
    private double previousSpinDegrees;
    private double spinDegrees;
    public boolean enable = false;

    /** AcademyCraft 1.0.7 TileCatEngine: buffer 2000, bandwidth 200, generation 500/t (machines.catEngine). */
    public static final int MAX_STORAGE = 2000;
    public static final double BANDWIDTH = 200;
    public static final double MAX_GENERATION = 500;

    private static int maxStorage() { return MachineConfig.CAT_BUFFER.get(); }
    private float storedEnergy = 0;
    private double thisTickGeneration;
    private boolean linked;
    private double lastSyncedGeneration;
    private long lastGenerationSyncTick = Long.MIN_VALUE;

    public CatEngineBlockEntity(BlockPos pos, BlockState state) {
        super(AcademyBlockEntities.CAT_ENGINE.get(), pos, state);
    }

    @Override public int getContainerSize() { return 0; }

    // ==================== Animation ====================

    public static void tickAnim(Level level, BlockPos blockPos, BlockState blockState, CatEngineBlockEntity e) {
        e.previousSpinDegrees = e.spinDegrees;
        // Upstream: elapsed milliseconds * actual generation * .01 degrees.
        e.spinDegrees += e.thisTickGeneration * .5;
        if (e.spinDegrees >= 360) {
            double turns = Math.floor(e.spinDegrees / 360) * 360;
            e.spinDegrees -= turns;
            e.previousSpinDegrees -= turns;
        }
    }

    public float interpolatedSpinDegrees(float partialTick) {
        return (float) (previousSpinDegrees + (spinDegrees - previousSpinDegrees)
                * Math.clamp(partialTick, 0, 1));
    }

    /** Fill the legacy finite buffer; wireless transfer happens later in the server tick. */
    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) return;
        float before = storedEnergy;
        int maxStorage = maxStorage();
        if (storedEnergy > maxStorage) storedEnergy = maxStorage;
        double required = Math.max(0, maxStorage - storedEnergy);
        thisTickGeneration = Math.min(required, MachineConfig.CAT_GENERATION.get());
        storedEnergy = Math.min(maxStorage, storedEnergy + (float) thisTickGeneration);

        boolean nowLinked = false;
        if (level instanceof ServerLevel server) {
            WiWorldData data = WiWorldData.getNonCreate(server);
            nowLinked = data != null && data.getNodeConnection(this) != null;
        }
        boolean runtimeChanged = linked != nowLinked || enable != (thisTickGeneration > 0);
        linked = nowLinked;
        enable = thisTickGeneration > 0;
        if (before != storedEnergy || runtimeChanged) markMachineChanged();

        boolean generationSyncDue = thisTickGeneration != lastSyncedGeneration
                && (lastGenerationSyncTick == Long.MIN_VALUE
                    || level.getGameTime() - lastGenerationSyncTick >= 10);
        if (runtimeChanged || generationSyncDue) {
            lastSyncedGeneration = thisTickGeneration;
            lastGenerationSyncTick = level.getGameTime();
            level.sendBlockUpdated(pos, state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    // ==================== IWirelessGenerator ====================

    @Override
    public double getProvidedEnergy(double req) {
        if (!Double.isFinite(req) || req <= 0) return 0;
        double give = Math.min(Math.max(0, req), storedEnergy);
        storedEnergy -= (float) give;
        if (give > 0) markMachineChanged();
        return give;
    }

    @Override
    public double getBandwidth() {
        return MachineConfig.CAT_BANDWIDTH.get();
    }

    public float getStoredEnergy() { return storedEnergy; }
    public int getMaxStorage() { return maxStorage(); }
    public double getThisTickGeneration() { return thisTickGeneration; }
    public boolean isLinked() { return linked; }

    public void setLinkedForSync(boolean value) {
        if (linked == value) return;
        linked = value;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    // ==================== NBT ====================

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("storedEnergy")) storedEnergy = MachineStateSanitizer.clampFinite(tag.getFloat("storedEnergy"), maxStorage());
        if (tag.contains("thisTickGeneration")) {
            double loaded = tag.getDouble("thisTickGeneration");
            thisTickGeneration = Double.isFinite(loaded) ? Math.clamp(loaded, 0, MachineConfig.CAT_GENERATION.get()) : 0;
        }
        if (tag.contains("linked")) linked = tag.getBoolean("linked");
        if (tag.contains("enable")) enable = tag.getBoolean("enable");
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putFloat("storedEnergy", storedEnergy);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        tag.putFloat("storedEnergy", storedEnergy);
        tag.putDouble("thisTickGeneration", thisTickGeneration);
        tag.putBoolean("linked", linked);
        tag.putBoolean("enable", enable);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
