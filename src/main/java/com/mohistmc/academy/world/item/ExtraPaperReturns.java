package com.mohistmc.academy.world.item;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Paper paid into drills/armour remains owed if a full inventory and a drop veto prevent return. */
@EventBusSubscriber(modid="academy")
public final class ExtraPaperReturns {
    private static final String DUE="academy_extra_paper_due";
    private static final Set<UUID> SETTLING=new HashSet<>();
    private ExtraPaperReturns() {}

    public static void give(ServerPlayer player,int count) {
        if(count<=0)return;
        CompoundTag receipt=receipt(player);
        receipt.putInt(DUE,Math.addExact(Math.max(0,receipt.getInt(DUE)),count));
        flush(player);
    }

    private static CompoundTag receipt(ServerPlayer player) {
        CompoundTag data=player.getPersistentData();
        if(!data.contains(Player.PERSISTED_NBT_TAG,10))data.put(Player.PERSISTED_NBT_TAG,new CompoundTag());
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void flush(ServerPlayer player) {
        if(!SETTLING.add(player.getUUID()))return;
        try {
            CompoundTag receipt=receipt(player);
            int due=Math.max(0,receipt.getInt(DUE));
            if(due==0)return;
            // A bounded batch also handles a previously queued return after login/respawn.
            int offered=Math.min(64,due);
            ItemStack paper=new ItemStack(Items.PAPER,offered);
            player.getInventory().add(paper);
            int inserted=offered-paper.getCount();
            receipt.putInt(DUE,due-inserted);
            if(!paper.isEmpty()) {
                int emitted=paper.getCount();
                var drop=new ItemEntity(player.level(),player.getX(),player.getY(),player.getZ(),paper);
                drop.setDefaultPickUpDelay();
                if(player.level().addFreshEntity(drop))
                    receipt.putInt(DUE,Math.max(0,receipt.getInt(DUE)-emitted));
            }
            player.getInventory().setChanged();
            if(receipt.getInt(DUE)==0)receipt.remove(DUE);
        } finally {SETTLING.remove(player.getUUID());}
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if(event.getEntity() instanceof ServerPlayer player && player.tickCount%20==0
                && player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getInt(DUE)>0)flush(player);
    }
}
