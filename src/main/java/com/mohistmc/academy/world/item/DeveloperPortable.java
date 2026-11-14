package com.mohistmc.academy.world.item;

import com.mohistmc.academy.capability.IEnergyItem;
import com.mohistmc.academy.network.LearnSkillPacket;
import com.mohistmc.academy.network.OpenDevGuiPacket;
import com.mohistmc.academy.network.DevLearningSessionManager;
import com.mohistmc.academy.world.block.DevMachineType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public class DeveloperPortable extends AcademyItem implements IEnergyItem {

    public static final int MAX_ENERGY = 10000;
    public static int maxEnergy() { return com.mohistmc.academy.config.MachineConfig.scale(MAX_ENERGY, com.mohistmc.academy.config.MachineConfig.Scale.ITEM); }
    /** Legacy 1.12.2 per-call IF transfer limit: DeveloperType.PORTABLE bandwidth (LATENCY_MK1). */
    public static final int BANDWIDTH = 50;
    public static int bandwidth() { return com.mohistmc.academy.config.MachineConfig.scale(BANDWIDTH, com.mohistmc.academy.config.MachineConfig.Scale.ITEM); }

    public DeveloperPortable() {
        super(new Properties().stacksTo(1).setNoRepair());
    }

    @Override
    public int getEnergyStored(ItemStack stack) {
        return ExtraItemData.energy(stack, maxEnergy());
    }

    @Override
    public int getMaxEnergyStored(ItemStack stack) {
        return maxEnergy();
    }

    @Override
    public void setEnergy(ItemStack stack, int energy) {
        ExtraItemData.setEnergy(stack, energy, maxEnergy());
    }

    @Override
    public int extractEnergy(ItemStack stack, int maxExtract, boolean simulate) {
        int energy = getEnergyStored(stack);
        int extracted = Math.min(energy, Math.min(Math.max(0, maxExtract), bandwidth()));
        if (!simulate && extracted > 0) {
            setEnergy(stack, energy - extracted);
        }
        return extracted;
    }

    @Override
    public int receiveEnergy(ItemStack stack, int maxReceive, boolean simulate) {
        int energy = getEnergyStored(stack);
        int maxEnergy = getMaxEnergyStored(stack);
        int received = Math.min(maxEnergy - energy, Math.min(Math.max(0, maxReceive), bandwidth()));
        if (!simulate && received > 0) {
            setEnergy(stack, energy + received);
        }
        return received;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            LearnSkillPacket.syncToClient(serverPlayer);
            int energy = getEnergyStored(stack);
            UUID nonce = DevLearningSessionManager.issue(serverPlayer, DevMachineType.PORTABLE, Optional.empty());
            com.mohistmc.academy.network.SafePayloadSender.send(serverPlayer,
                    new OpenDevGuiPacket(0, energy, maxEnergy(), Optional.empty(), nonce, ""));
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, Item.TooltipContext p_333372_, List<Component> p_41423_, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_333372_, p_41423_, p_41424_);
        int energy = getEnergyStored(p_41421_);
        p_41423_.add(Component.literal(com.mohistmc.academy.capability.ExternalEnergyConversion.describe(energy, maxEnergy())));
    }
}
