package com.mohistmc.academy.capability;

import com.mohistmc.academy.capability.BandwidthFeView.Mode;
import com.mohistmc.academy.energy.api.block.IWirelessGenerator;
import com.mohistmc.academy.energy.api.block.IWirelessNode;
import com.mohistmc.academy.energy.api.block.IWirelessReceiver;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import net.minecraft.world.level.block.entity.BlockEntity;

/** The Forge-energy faces of the machines, each limited to its own IF bandwidth per tick. */
public final class MachineEnergyViews {
    private MachineEnergyViews() {}

    private static final BooleanSupplier ALWAYS = () -> true;

    /** A consumer with an IF store (developers, interferer): receive only. */
    public static BandwidthFeView consumer(BlockEntity be, IFEnergyStorage storage, DoubleSupplier bandwidth,
                                           BooleanSupplier validity) {
        return BandwidthFeView.of(be, storage, Mode.RECEIVE, bandwidth, validity);
    }

    public static BandwidthFeView consumer(BlockEntity be, IFEnergyStorage storage, DoubleSupplier bandwidth) {
        return consumer(be, storage, bandwidth, ALWAYS);
    }

    /** A generator with an IF store (solar, wind base): give only. */
    public static BandwidthFeView generator(BlockEntity be, IFEnergyStorage storage, DoubleSupplier bandwidth,
                                            BooleanSupplier validity) {
        return BandwidthFeView.of(be, storage, Mode.EXTRACT, bandwidth, validity);
    }

    /** A wireless receiver without an IF store interface (imaginary fusor, metal former). */
    public static <T extends BlockEntity & IWirelessReceiver> BandwidthFeView receiver(T be, DoubleSupplier stored,
                                                                                       DoubleSupplier max) {
        return new BandwidthFeView(be, Mode.RECEIVE, stored, max, be::getBandwidth,
                (amount, simulate) -> {
                    double room = Math.max(0, Math.floor(be.getRequiredEnergy()));
                    double offer = Math.min(amount, room);
                    if (simulate || offer <= 0) return offer;
                    return offer - Math.max(0, be.injectEnergy(offer));
                },
                (amount, simulate) -> 0, ALWAYS);
    }

    /** A wireless generator (phase generator): give only. */
    public static <T extends BlockEntity & IWirelessGenerator> BandwidthFeView source(T be, DoubleSupplier stored,
                                                                                      DoubleSupplier max) {
        return new BandwidthFeView(be, Mode.EXTRACT, stored, max, be::getBandwidth,
                (amount, simulate) -> 0,
                (amount, simulate) -> {
                    double available = Math.min(amount, Math.max(0, Math.floor(stored.getAsDouble())));
                    if (simulate || available <= 0) return available;
                    return Math.max(0, be.getProvidedEnergy(available));
                }, ALWAYS);
    }

    /** A wireless node: storage, both directions, within its bandwidth. */
    public static <T extends BlockEntity & IWirelessNode> BandwidthFeView node(T be) {
        return new BandwidthFeView(be, Mode.BOTH, be::getEnergy, be::getMaxEnergy, be::getBandwidth,
                (amount, simulate) -> {
                    double room = Math.max(0, Math.floor(be.getMaxEnergy() - be.getEnergy()));
                    double moved = Math.min(amount, room);
                    if (!simulate && moved > 0) be.setEnergy(be.getEnergy() + moved);
                    return moved;
                },
                (amount, simulate) -> {
                    double moved = Math.min(amount, Math.max(0, Math.floor(be.getEnergy())));
                    if (!simulate && moved > 0) be.setEnergy(be.getEnergy() - moved);
                    return moved;
                }, ALWAYS);
    }
}
