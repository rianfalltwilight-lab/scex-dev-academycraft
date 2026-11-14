package com.mohistmc.academy.world.item;

import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import com.mohistmc.academy.api.event.TerminalEvents;
import com.mohistmc.academy.terminal.AppRegistry;

public abstract class BaseApp extends AcademyItem {

    public BaseApp(Properties p_41383_) {
        super(p_41383_);
    }

    public abstract String getAppId();

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);

        // Feedback comes only from the server, which owns the install state.
        // The client copy can be stale, and its own prediction used to report
        // a successful install that the server had in fact refused.
        if (!data.isTerminalInstalled()) {
            if (!level.isClientSide()) {
                player.displayClientMessage(terminalMessage(Component.translatable("ac.terminal.notinstalled")
                        .withStyle(ChatFormatting.RED)), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        Component appName = Component.translatable(getDescriptionId());
        if (data.hasApp(getAppId())) {
            if (!level.isClientSide()) {
                player.displayClientMessage(terminalMessage(Component.translatable("ac.terminal.app_alrdy_installed", appName)
                        .withStyle(ChatFormatting.RED)), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide()) {
            data.installApp(getAppId());
            var installed = AppRegistry.getApp(getAppId());
            if (installed != null) NeoForge.EVENT_BUS.post(new TerminalEvents.AppInstalled(player, installed));
            data.syncTo(player);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            player.displayClientMessage(terminalMessage(Component.translatable("ac.terminal.app_installed", appName)
                    .withStyle(ChatFormatting.GREEN)), false);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** Prefixes a feedback line with the grey "[Data Terminal]" tag. */
    public static Component terminalMessage(Component message) {
        return tagged("gui.academy.data_terminal", message);
    }

    /** Prefixes a feedback line with a grey "[label]" tag naming its source. */
    public static Component tagged(String labelKey, Component message) {
        return Component.literal("[").append(Component.translatable(labelKey)).append("] ")
                .withStyle(ChatFormatting.GRAY).append(message);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, Item.TooltipContext p_333372_, List<Component> components, TooltipFlag tooltipFlag) {
        components.add(Component.translatable("item.academy.apps.tooltip").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public Component getName(ItemStack p_41458_) {
        return Component.translatable(getDescriptionId());
    }
}
