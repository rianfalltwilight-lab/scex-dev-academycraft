package com.mohistmc.academy.data;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.network.SafePayloadSender;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/** Registers the mod's datapack-driven tuning and keeps clients in step with the server. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class AcademyDataManager {
    static final List<SyncedJsonData> ALL = List.of(BalanceProfileData.INSTANCE, SkillTuningData.INSTANCE, ProgressionData.INSTANCE);

    private AcademyDataManager() {}

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        ALL.forEach(event::addListener);
    }

    /** Fires on login (one player) and after /reload (every player). */
    @SubscribeEvent
    public static void sync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(AcademyDataManager::send);
    }

    private static void send(ServerPlayer player) {
        for (SyncedJsonData data : ALL) SafePayloadSender.send(player, new DataSyncPacket(data.id(), data.snapshot()));
    }

    /** Client: a payload arrived. In singleplayer the server already installed the same data. */
    static void receive(String id, String json) {
        for (SyncedJsonData data : ALL) if (data.id().equals(id)) data.acceptSnapshot(json);
    }

    /** Client: left the server; restore the built-in defaults. */
    public static void resetClient() {
        ALL.forEach(SyncedJsonData::reset);
    }
}
