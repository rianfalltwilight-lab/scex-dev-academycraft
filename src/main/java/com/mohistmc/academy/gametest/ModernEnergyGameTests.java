package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.capability.BandwidthFeView;
import com.mohistmc.academy.capability.ExternalEnergyConversion;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.config.BalanceProfile;
import com.mohistmc.academy.config.MachineConfig;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import com.mohistmc.academy.world.block.entity.DevNormalBlockEntity;
import com.mohistmc.academy.world.block.entity.SolarGenBlockEntity;
import com.mohistmc.academy.world.item.DeveloperPortable;
import com.mohistmc.academy.world.item.EnergyUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** MZ4: bandwidth-limited one-way FE faces, FE on every machine, and the modern machine multipliers. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ModernEnergyGameTests {
    private static final String EMPTY = "empty";
    private static final String BATCH = "modern_energy";
    private ModernEnergyGameTests() {}

    private static void check(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }

    private interface Body { void run(); }

    private static void withProfile(BalanceProfile profile, Body body) {
        BalanceProfile before = ACConfig.Server.BALANCE_PROFILE.get();
        ACConfig.Server.BALANCE_PROFILE.set(profile);
        try { body.run(); } finally { ACConfig.Server.BALANCE_PROFILE.set(before); }
    }

    private static IEnergyStorage place(GameTestHelper helper, BlockPos rel, Block block) {
        BlockPos pos = helper.absolutePos(rel);
        helper.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        return helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void feFacesAreOneWayAndBandwidthLimited(GameTestHelper helper) {
        int ratio = ExternalEnergyConversion.fePerIf();
        IEnergyStorage dev = place(helper, new BlockPos(1, 1, 1), AcademyBlocks.DEV_NORMAL.get());
        check(dev instanceof BandwidthFeView, "developer FE face missing");
        check(dev.canReceive() && !dev.canExtract(), "developer must only receive");
        var devBe = (DevNormalBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)));
        int bandwidthFe = (int) devBe.getBandwidth() * ratio;
        check(dev.receiveEnergy(1_000_000, false) == bandwidthFe, "developer took more than its bandwidth");
        check(dev.receiveEnergy(1_000_000, false) == 0, "per-tick budget not shared between calls");
        check(dev.extractEnergy(1_000, false) == 0, "pipes could drain a developer");

        BlockPos solarPos = helper.absolutePos(new BlockPos(3, 1, 1));
        IEnergyStorage solar = place(helper, new BlockPos(3, 1, 1), AcademyBlocks.SOLAR_GEN.get());
        check(solar != null && !solar.canReceive() && solar.canExtract(), "solar must only give");
        ((SolarGenBlockEntity) helper.getLevel().getBlockEntity(solarPos)).setEnergy(500);
        check(solar.receiveEnergy(400, false) == 0, "solar accepted FE");
        check(solar.extractEnergy(400, false) == 400, "solar did not give FE");

        for (Block block : new Block[]{AcademyBlocks.IMAG_FUSOR.get(), AcademyBlocks.METAL_FORMER.get(),
                AcademyBlocks.PHASE_GEN.get(), AcademyBlocks.NODE_BASIC.get()}) {
            check(place(helper, new BlockPos(5, 1, 3), block) != null, block + " has no FE face");
        }
        IEnergyStorage node = place(helper, new BlockPos(1, 1, 4), AcademyBlocks.NODE_BASIC.get());
        var nodeBe = (BaseNodeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 4)));
        nodeBe.setEnergy(0);
        int nodeBandwidthFe = (int) nodeBe.getBandwidth() * ratio;
        check(node.receiveEnergy(1_000_000, false) == nodeBandwidthFe && (int) nodeBe.getEnergy() == nodeBe.getBandwidth(),
                "node receive: " + nodeBe.getEnergy());
        check(node.extractEnergy(1_000_000, false) == nodeBandwidthFe && nodeBe.getEnergy() == 0, "node extract");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void modernProfileScalesMachinesItemsAndDevelopers(GameTestHelper helper) {
        withProfile(BalanceProfile.LEGACY, () -> {
            check(MachineConfig.NODE_BASIC.maxEnergy.get() == MachineConfig.NODE_BASIC.maxEnergy.configured(), "legacy node");
            check(EnergyUnit.maxEnergy() == EnergyUnit.MAX_ENERGY, "legacy energy unit");
            check(DevNormalBlockEntity.maxEnergy() == DevNormalBlockEntity.MAX_ENERGY, "legacy developer");
        });
        withProfile(BalanceProfile.MODERN, () -> {
            check(MachineConfig.NODE_BASIC.maxEnergy.get() == 10 * MachineConfig.NODE_BASIC.maxEnergy.configured(),
                    "modern node storage: " + MachineConfig.NODE_BASIC.maxEnergy.get());
            check(MachineConfig.BRIDGE_BANDWIDTH.get() == 10 * MachineConfig.BRIDGE_BANDWIDTH.configured(), "bridge bandwidth");
            check(Math.abs(MachineConfig.SOLAR_DAY_RATE.get() - 4 * MachineConfig.SOLAR_DAY_RATE.configured()) < 1e-9,
                    "solar generation");
            check(Math.abs(MachineConfig.NODE_BASIC.range.get() - 1.5 * MachineConfig.NODE_BASIC.range.configured()) < 1e-9,
                    "node range");
            check(MachineConfig.FE_PER_IF.get() == 4, "fePerIf must not scale");
            check(EnergyUnit.maxEnergy() == 10 * EnergyUnit.MAX_ENERGY && DeveloperPortable.maxEnergy() == 100_000,
                    "item energy");
            check(DevNormalBlockEntity.maxEnergy() == 10 * DevNormalBlockEntity.MAX_ENERGY, "developer buffer");
        });
        helper.succeed();
    }
}
