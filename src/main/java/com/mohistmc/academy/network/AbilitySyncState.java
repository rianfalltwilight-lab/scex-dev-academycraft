package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Per-attachment, non-persistent delivery state. Holds no player, level, or connection references. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class AbilitySyncState {
    public static final int RUNTIME_INTERVAL_TICKS = 5;
    public static final int PROFILE_INTERVAL_TICKS = 20;
    private CompoundTag profile;
    private CompoundTag runtime;
    private long profileFingerprint;
    private long runtimeFingerprint;
    private boolean hasFingerprints;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % RUNTIME_INTERVAL_TICKS == 0)
            player.getData(AcademyAttachments.PLAYER_ABILITY).syncPeriodicTo(player,
                    player.tickCount % PROFILE_INTERVAL_TICKS == 0);
    }

    public void full(ServerPlayer player, PlayerAbilityData data) {
        if (!SafePayloadSender.canSend(player, SyncAbilityDataPacket.TYPE)) return;
        if (SafePayloadSender.send(player, new SyncAbilityDataPacket(data.toSyncTag()))) {
            profile = data.toProfileSyncTag();
            runtime = data.toRuntimeSyncTag();
            profileFingerprint = data.profileFingerprint();
            runtimeFingerprint = data.runtimeFingerprint();
            hasFingerprints = true;
        }
    }

    public void changed(ServerPlayer player, PlayerAbilityData data, boolean profileDue) {
        if (profile == null || runtime == null) { full(player, data); return; }
        boolean profileChanged = false;
        if (profileDue) {
            long nextProfileFp = data.profileFingerprint();
            if (!hasFingerprints || nextProfileFp != profileFingerprint) {
                CompoundTag next = data.toProfileSyncTag();
                if (!next.equals(profile)) {
                    if (!SafePayloadSender.send(player, new SyncAbilityProfilePacket(next))) return;
                    profile = next;
                    profileChanged = true;
                }
                profileFingerprint = nextProfileFp;
            }
        }
        long nextRuntimeFp = data.runtimeFingerprint();
        if (!profileChanged && hasFingerprints && nextRuntimeFp == runtimeFingerprint) return;
        CompoundTag next = data.toRuntimeSyncTag();
        CompoundTag delta = profileChanged ? next.copy() : difference(runtime, next);
        if (!delta.isEmpty()) {
            if (!SafePayloadSender.send(player, new SyncAbilityRuntimePacket(delta))) return;
            runtime = next;
        }
        runtimeFingerprint = nextRuntimeFp;
        hasFingerprints = true;
    }

    public void resendRuntime(ServerPlayer player, PlayerAbilityData data) {
        if (profile == null) { full(player, data); return; }
        CompoundTag next = data.toRuntimeSyncTag();
        if (SafePayloadSender.send(player, new SyncAbilityRuntimePacket(next))) {
            runtime = next;
            runtimeFingerprint = data.runtimeFingerprint();
        }
    }

    static CompoundTag difference(CompoundTag previous, CompoundTag next) {
        CompoundTag result = new CompoundTag();
        for (String key : next.getAllKeys())
            if (!Objects.equals(previous.get(key), next.get(key))) result.put(key, next.get(key).copy());
        return result;
    }
}
