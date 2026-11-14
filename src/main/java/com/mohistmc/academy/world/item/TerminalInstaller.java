package com.mohistmc.academy.world.item;

import com.mohistmc.academy.network.StartTerminalInstallPacket;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import java.util.List;
import net.minecraft.ChatFormatting;
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
import net.neoforged.neoforge.common.NeoForge;
import com.mohistmc.academy.api.event.TerminalEvents;

public class TerminalInstaller extends AcademyItem {
    public TerminalInstaller() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);

        if (data.isTerminalInstalled()) {
            if (level.isClientSide()) {
                player.displayClientMessage(BaseApp.terminalMessage(Component.translatable("ac.terminal.alrdy_installed")
                        .withStyle(ChatFormatting.RED)), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide()) {
            data.setTerminalInstalled(true);
            NeoForge.EVENT_BUS.post(new TerminalEvents.Installed(player));
            data.syncTo(player);
            com.mohistmc.academy.network.SafePayloadSender.send((ServerPlayer) player, new StartTerminalInstallPacket());
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> components, TooltipFlag flag) {
        components.add(Component.translatable("item.academy.terminal_installer.tooltip").withStyle(ChatFormatting.GRAY));
        components.add(Component.translatable("item.academy.terminal_installer.tooltip.open",
                Component.keybind("key.academy.open_terminal")).withStyle(ChatFormatting.GRAY));
    }
}
