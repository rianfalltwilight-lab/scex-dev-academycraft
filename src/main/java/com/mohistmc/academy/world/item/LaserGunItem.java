package com.mohistmc.academy.world.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Eight-block continuous IF mining beam with server-side permission checks. */
public final class LaserGunItem extends ExtraEnergyItem {
    private static final java.util.Set<java.util.UUID> BREAKING = new java.util.HashSet<>();
    public LaserGunItem() { super(100_000, 200); }

    @Override public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return !state.is(net.minecraft.tags.BlockTags.INCORRECT_FOR_DIAMOND_TOOL);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (ExtraItemData.isActive(stack)) stop(stack);
            else if (player.getAbilities().instabuild || getEnergyStored(stack) > 20)
                ExtraItemData.setActive(stack, true);
            if (player instanceof ServerPlayer serverPlayer) LaserMiningSessions.clear(serverPlayer, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide) return;
        if (!ExtraItemData.isActive(stack)) {
            if (entity instanceof ServerPlayer player) LaserMiningSessions.clear(player, stack);
            return;
        }
        if (!(entity instanceof ServerPlayer player)) {
            stop(stack);
            return;
        }
        if (!selected || player.getMainHandItem() != stack) {
            LaserMiningSessions.clear(player, stack);
            stop(stack);
            return;
        }
        if (BREAKING.contains(player.getUUID())) return;
        if (!player.getAbilities().instabuild && !consume(stack, 2)) {
            stop(stack);
            LaserMiningSessions.clear(player);
            return;
        }

        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 end = start.add(direction.scale(8));
        BlockHitResult result = com.mohistmc.academy.skill.ability.LoadedRaycast.clip(level, new ClipContext(start, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double beamLength = result.getType() == HitResult.Type.MISS ? 8 : start.distanceTo(result.getLocation());
        ExtraItemActions.beam((ServerLevel) level, start, direction, beamLength);
        if (result.getType() != HitResult.Type.BLOCK || !level.isLoaded(result.getBlockPos())) {
            LaserMiningSessions.clear(player);
            return;
        }
        BlockPos pos = result.getBlockPos();
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0 || state.isAir() || !isCorrectToolForDrops(stack, state)
                || !player.getAbilities().mayBuild || !player.mayInteract(level,pos) || !level.mayInteract(player,pos)
                || !player.mayUseItemAt(pos, result.getDirection(), stack)) {
            LaserMiningSessions.clear(player);
            return;
        }
        float progress = LaserMiningSessions.advance(player, stack, slot, pos, state);
        if (progress + 1.0e-5F < hardness) {
            return;
        }
        LaserMiningSessions.clear(player);
        BREAKING.add(player.getUUID());
        try {
            if (harvest((ServerLevel)level, player, stack, pos, state)) {
                level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.18F, 1.8F);
            }
        } finally { BREAKING.remove(player.getUUID()); }
    }

    /** Vanilla harvest callbacks with a state/ownership recheck after the cancellable break event. */
    private boolean harvest(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos pos, BlockState expected) {
        var event=net.neoforged.neoforge.common.CommonHooks.fireBlockBreak(level,player.gameMode.getGameModeForPlayer(),player,pos,expected);
        if(event.isCanceled()||!player.isAlive()||player.level()!=level||player.getMainHandItem()!=stack
                ||!ExtraItemData.isActive(stack)||!level.isLoaded(pos)||level.getBlockState(pos)!=expected
                ||!player.getAbilities().mayBuild||!player.mayInteract(level,pos)||!level.mayInteract(player,pos)
                ||player.blockActionRestricted(level,pos,player.gameMode.getGameModeForPlayer()))return false;
        var block=expected.getBlock();
        if(block instanceof net.minecraft.world.level.block.GameMasterBlock&&!player.canUseGameMasterBlocks())return false;
        var blockEntity=level.getBlockEntity(pos);
        BlockState state=block.playerWillDestroy(level,pos,expected,player);
        if(level.getBlockState(pos)!=expected||state.getDestroySpeed(level,pos)<0)return false;
        boolean drops=!player.hasInfiniteMaterials()&&state.canHarvestBlock(level,pos,player);
        ItemStack tool=stack.copy();
        if(!state.onDestroyedByPlayer(level,pos,player,drops,level.getFluidState(pos)))return false;
        block.destroy(level,pos,state);
        if(drops)block.playerDestroy(level,player,pos,state,blockEntity,tool);
        return true;
    }

    private static void stop(ItemStack stack) {
        ExtraItemData.setActive(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(ExtraItemData.isActive(stack)
                ? "item.academy.extra.enabled" : "item.academy.extra.disabled")
                .withStyle(ExtraItemData.isActive(stack) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override public boolean isFoil(ItemStack stack) { return ExtraItemData.isActive(stack); }
}
