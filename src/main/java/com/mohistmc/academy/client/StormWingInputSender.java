package com.mohistmc.academy.client;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.network.StormWingInputPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Forwards WASD to the server while Storm Wing runs; see {@link StormWingInputPacket}. */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class StormWingInputSender {
    private static final int HEARTBEAT_TICKS = 20;
    private static byte lastForward, lastLeft;
    private static int sinceSent;

    private StormWingInputSender() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null || !ChargingHudOverlay.isCharging("storm_wing")) {
            lastForward = lastLeft = 0;
            sinceSent = 0;
            return;
        }
        byte forward = StormWingInputPacket.axis(player.input.forwardImpulse);
        byte left = StormWingInputPacket.axis(player.input.leftImpulse);
        // Send on change, plus a slow heartbeat so a dropped packet cannot pin a direction.
        if (forward != lastForward || left != lastLeft || ++sinceSent >= HEARTBEAT_TICKS) {
            PacketDistributor.sendToServer(new StormWingInputPacket(forward, left));
            lastForward = forward;
            lastLeft = left;
            sinceSent = 0;
        }
    }
}
