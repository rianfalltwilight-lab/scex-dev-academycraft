package com.mohistmc.academy.capability;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Forge-energy face of a machine, limited to its IF bandwidth per tick in each direction.  Consumers
 * only receive, generators only give, storage does both.  The view is rebuilt per capability query, so
 * the per-tick budget lives in a weak map keyed by the block entity.  Whole IF only, as at every FE edge.
 */
public final class BandwidthFeView implements IEnergyStorage {
    public enum Mode { RECEIVE, EXTRACT, BOTH }

    /** Moves up to {@code amountIf} IF; returns the IF moved. */
    @FunctionalInterface public interface Transfer { double move(double amountIf, boolean simulate); }

    private static final Map<BlockEntity, long[]> BUDGETS = new WeakHashMap<>();

    private final BlockEntity owner;
    private final Mode mode;
    private final DoubleSupplier stored, max, bandwidth;
    private final Transfer receive, extract;
    private final BooleanSupplier validity;

    public BandwidthFeView(BlockEntity owner, Mode mode, DoubleSupplier stored, DoubleSupplier max,
                           DoubleSupplier bandwidth, Transfer receive, Transfer extract, BooleanSupplier validity) {
        this.owner = owner; this.mode = mode; this.stored = stored; this.max = max; this.bandwidth = bandwidth;
        this.receive = receive; this.extract = extract; this.validity = validity;
    }

    /** Over an {@link IFEnergyStorage}: receive / extract go straight to its storage. */
    public static BandwidthFeView of(BlockEntity owner, IFEnergyStorage storage, Mode mode, DoubleSupplier bandwidth,
                                     BooleanSupplier validity) {
        return new BandwidthFeView(owner, mode, storage::getEnergyStored, storage::getMaxEnergyStored, bandwidth,
                (amount, simulate) -> storage.receiveEnergy((int) Math.min(Integer.MAX_VALUE, amount), simulate),
                (amount, simulate) -> storage.extractEnergy((int) Math.min(Integer.MAX_VALUE, amount), simulate),
                validity);
    }

    private boolean valid() {
        return !owner.isRemoved() && owner.getLevel() != null && validity.getAsBoolean();
    }

    /** [tick, received IF, extracted IF] for this tick. */
    private long[] budget() {
        long tick = owner.getLevel().getGameTime();
        synchronized (BUDGETS) {
            long[] b = BUDGETS.computeIfAbsent(owner, k -> new long[]{tick, 0, 0});
            if (b[0] != tick) { b[0] = tick; b[1] = 0; b[2] = 0; }
            return b;
        }
    }

    private long room(long used) {
        double bw = bandwidth.getAsDouble();
        if (!Double.isFinite(bw) || bw <= 0) return 0;
        return Math.max(0, (long) Math.floor(bw) - used);
    }

    @Override public int receiveEnergy(int amountFe, boolean simulate) {
        if (mode == Mode.EXTRACT || !valid() || amountFe <= 0) return 0;
        long[] b = budget();
        long want = Math.min(ExternalEnergyConversion.wholeIfFromFe(amountFe), room(b[1]));
        if (want <= 0) return 0;
        long moved = (long) Math.floor(Math.max(0, Math.min(want, receive.move(want, simulate))));
        if (!simulate) b[1] += moved;
        return ExternalEnergyConversion.ifToFe((int) moved);
    }

    @Override public int extractEnergy(int amountFe, boolean simulate) {
        if (mode == Mode.RECEIVE || !valid() || amountFe <= 0) return 0;
        long[] b = budget();
        long want = Math.min(ExternalEnergyConversion.wholeIfFromFe(amountFe), room(b[2]));
        if (want <= 0) return 0;
        long moved = (long) Math.floor(Math.max(0, Math.min(want, extract.move(want, simulate))));
        if (!simulate) b[2] += moved;
        return ExternalEnergyConversion.ifToFe((int) moved);
    }

    @Override public int getEnergyStored() { return valid() ? fe(stored.getAsDouble()) : 0; }
    @Override public int getMaxEnergyStored() { return valid() ? fe(max.getAsDouble()) : 0; }
    @Override public boolean canReceive() { return mode != Mode.EXTRACT && valid(); }
    @Override public boolean canExtract() { return mode != Mode.RECEIVE && valid(); }

    private static int fe(double imaginaryEnergy) {
        if (!Double.isFinite(imaginaryEnergy) || imaginaryEnergy <= 0) return 0;
        return ExternalEnergyConversion.ifToFe((int) Math.min(Integer.MAX_VALUE, Math.floor(imaginaryEnergy)));
    }
}
