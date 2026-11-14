package com.mohistmc.academy.world.item;

import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.terminal.AppRegistry;
import com.mohistmc.academy.terminal.MediaTrackRegistry;
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
import org.jetbrains.annotations.NotNull;

public abstract class MediaItem extends AcademyItem {

    public MediaItem() {
        super(new Properties().stacksTo(1));
    }

    public abstract String getMediaId();

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // As with BaseApp, only the server (which owns the media list) reports
        // the outcome; a stale client copy must not announce a refused load.
        if (!MediaTrackRegistry.isRegistered(getMediaId())) {
            if (!level.isClientSide()) {
                player.displayClientMessage(mediaMessage(Component.translatable("message.academy.media.unregistered", getMediaId())
                        .withStyle(ChatFormatting.RED)), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);

        if (!data.isTerminalInstalled()) {
            if (!level.isClientSide()) {
                player.displayClientMessage(BaseApp.terminalMessage(Component.translatable("ac.terminal.notinstalled")
                        .withStyle(ChatFormatting.RED)), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!data.hasApp(AppRegistry.MEDIA_PLAYER)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(BaseApp.terminalMessage(Component.translatable("ac.media.notinstalled")
                        .withStyle(ChatFormatting.RED)), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        Component name = Component.translatable(getDescriptionId());
        if (data.hasLoadedMedia(getMediaId())) {
            if (!level.isClientSide()) {
                player.displayClientMessage(mediaMessage(Component.translatable("ac.media.haveone", name)
                        .withStyle(ChatFormatting.RED)), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide()) {
            data.addLoadedMedia(getMediaId());
            if (!player.getAbilities().instabuild) stack.shrink(1);
            data.syncTo(player);
            player.displayClientMessage(mediaMessage(Component.translatable("ac.media.acquired", name)
                    .withStyle(ChatFormatting.GREEN)), false);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> components, TooltipFlag flag) {
        super.appendHoverText(stack, context, components, flag);
        components.add(Component.translatable("item.academy.media.tooltip").withStyle(ChatFormatting.GRAY));
    }

    private static Component mediaMessage(Component message) {
        return BaseApp.tagged("item.academy.app_media_player", message);
    }
}
