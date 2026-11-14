package com.mohistmc.academy.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.neoforged.neoforge.energy.EnergyStorage;
import org.junit.jupiter.api.Test;

class ExternalItemEnergyTest {
    /** A Singularity Iteration battery caps each call at its charge rate (128 EU = 512 FE). */
    private static EnergyStorage battery(int capacityFe, int rateFe, int storedFe) {
        return new EnergyStorage(capacityFe, rateFe, rateFe, storedFe);
    }

    @Test
    void extractsOnlyWholeImaginaryEnergy() {
        EnergyStorage storage = battery(1000, 1000, 10);
        assertEquals(2, ExternalItemEnergy.extract(storage, 100, true, 4));
        assertEquals(10, storage.getEnergyStored(), "simulation moved energy");
        assertEquals(2, ExternalItemEnergy.extract(storage, 100, false, 4));
        assertEquals(2, storage.getEnergyStored(), "the 2 FE remainder must stay in the item");
        assertEquals(0, ExternalItemEnergy.extract(storage, 100, false, 4));
        assertEquals(2, storage.getEnergyStored());
    }

    @Test
    void receivesOnlyWholeImaginaryEnergy() {
        EnergyStorage storage = battery(10, 1000, 0);
        assertEquals(2, ExternalItemEnergy.receive(storage, 100, false, 4));
        assertEquals(8, storage.getEnergyStored(), "only whole IF may be pushed");
        assertEquals(0, ExternalItemEnergy.receive(storage, 100, false, 4));
    }

    @Test
    void respectsTheItemRateAndTheRequest() {
        EnergyStorage storage = battery(100_000, 512, 50_000);
        assertEquals(128, ExternalItemEnergy.extract(storage, 1000, false, 4));
        assertEquals(50_000 - 512, storage.getEnergyStored());
        assertEquals(3, ExternalItemEnergy.extract(storage, 3, false, 4));
        assertEquals(50_000 - 524, storage.getEnergyStored());
        EnergyStorage empty = battery(100_000, 509, 0);
        assertEquals(127, ExternalItemEnergy.receive(empty, 1000, false, 4), "509 FE rounds down to 127 IF");
        assertEquals(508, empty.getEnergyStored());
    }

    @Test
    void refusesWhenTheItemCannotMoveEnergy() {
        EnergyStorage receiveOnly = new EnergyStorage(1000, 1000, 0, 500);
        assertEquals(0, ExternalItemEnergy.extract(receiveOnly, 10, false, 4));
        EnergyStorage extractOnly = new EnergyStorage(1000, 0, 1000, 0);
        assertEquals(0, ExternalItemEnergy.receive(extractOnly, 10, false, 4));
        assertEquals(0, ExternalItemEnergy.extract(null, 10, false, 4));
        assertEquals(0, ExternalItemEnergy.extract(receiveOnly, 0, false, 4));
        assertEquals(0, ExternalItemEnergy.extract(receiveOnly, -5, false, 4));
    }

    @Test
    void largeRequestsDoNotOverflow() {
        EnergyStorage storage = battery(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        int moved = ExternalItemEnergy.extract(storage, Integer.MAX_VALUE, false, 4);
        assertEquals(Integer.MAX_VALUE / 4, moved);
        assertEquals(Integer.MAX_VALUE - moved * 4, storage.getEnergyStored());
    }
}
