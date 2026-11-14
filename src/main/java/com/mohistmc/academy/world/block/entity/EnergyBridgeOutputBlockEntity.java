package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.capability.ExternalEnergyConversion;
import com.mohistmc.academy.energy.api.block.IWirelessReceiver;
import com.mohistmc.academy.world.AcademyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.minecraft.server.level.ServerLevel;
import java.util.EnumMap;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** IF output bridge: receives wireless IF and pushes/exposes wired FE. */
public final class EnergyBridgeOutputBlockEntity extends EnergyBridgeBlockEntity
        implements IWirelessReceiver {
    private final EnumMap<Direction, BlockCapabilityCache<IEnergyStorage, Direction>> neighbors =
            new EnumMap<>(Direction.class);
    private final IEnergyStorage external = new IEnergyStorage() {
        @Override public int receiveEnergy(int amount, boolean simulate) { return 0; }
        @Override public int extractEnergy(int amount, boolean simulate) {
            return extractExternalFe(amount, simulate);
        }
        @Override public int getEnergyStored() { return getStoredFe(); }
        @Override public int getMaxEnergyStored() { return maxFe(); }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return false; }
    };

    public EnergyBridgeOutputBlockEntity(BlockPos pos, BlockState state) {
        super(AcademyBlockEntities.RF_OUTPUT.get(), pos, state);
    }

    public IEnergyStorage externalEnergy() { return external; }

    public void serverTick() {
        if (!(level instanceof ServerLevel server) || isRemoved() || getStoredFe() <= 0) return;
        for (Direction direction : Direction.values()) {
            if (getStoredFe() <= 0) break;
            if (!com.mohistmc.academy.world.block.LoadedMachineChunks.contains(server, worldPosition.relative(direction))) continue;
            IEnergyStorage target = neighbors.computeIfAbsent(direction, side ->
                    BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, server,
                            worldPosition.relative(side), side.getOpposite(),
                            () -> !isRemoved() && getLevel() == server, () -> {})).getCapability();
            // AcademyCraft's own machines are fed by the wireless network; pushing into them would loop.
            if (target == null || !target.canReceive()
                    || target instanceof com.mohistmc.academy.capability.BandwidthFeView) continue;
            int offered = getStoredFe();
            int accepted = Math.clamp(target.receiveEnergy(offered, false), 0, offered);
            if (accepted > 0) extractExternalFe(accepted, false);
        }
    }

    @Override public void setRemoved() {
        neighbors.clear();
        super.setRemoved();
    }

    @Override public void onChunkUnloaded() {
        neighbors.clear();
        super.onChunkUnloaded();
    }

    @Override public double getRequiredEnergy() {
        return Math.max(0, maxFe() - getStoredFe()) / (double) ExternalEnergyConversion.fePerIf();
    }

    @Override
    public double injectEnergy(double amountIf) {
        if (!Double.isFinite(amountIf) || amountIf <= 0) return amountIf;
        int offeredFe = (int) Math.min(Integer.MAX_VALUE,
                Math.floor(amountIf * ExternalEnergyConversion.fePerIf() + 1.0e-9));
        int acceptedFe = receiveExternalFe(offeredFe, false);
        return Math.max(0, amountIf
                - acceptedFe / (double) ExternalEnergyConversion.fePerIf());
    }

    @Override
    public double pullEnergy(double amountIf) {
        if (!Double.isFinite(amountIf) || amountIf <= 0) return 0;
        int requestedFe = (int) Math.min(Integer.MAX_VALUE,
                Math.floor(amountIf * ExternalEnergyConversion.fePerIf() + 1.0e-9));
        int extractedFe = extractExternalFe(requestedFe, false);
        return extractedFe / (double) ExternalEnergyConversion.fePerIf();
    }

    @Override public double getBandwidth() { return bandwidthIf(); }
}
