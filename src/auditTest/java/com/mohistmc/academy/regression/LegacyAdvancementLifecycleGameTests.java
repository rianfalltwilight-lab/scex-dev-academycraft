package com.mohistmc.academy.regression;

import com.mohistmc.academy.advancement.LegacyAdvancementBridge;
import com.mohistmc.academy.gametest.recheck.RecheckPlayers;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("academy_audit")
@PrefixGameTestTemplate(false)
public final class LegacyAdvancementLifecycleGameTests {
    private static final String COUNT = "academy_legacy_tp_count";
    private static final ResourceLocation MASTERY = ResourceLocation.parse("academy:legacy/teleporter/mastery");

    @GameTest(template = "empty", batch = "core_advancement_lifecycle")
    public static void oldTeleportProgressSurvivesRealRespawnAndAwardsOnlyOnce(GameTestHelper h) {
        var session = RecheckPlayers.connect(h);
        var original = session.player();
        var observer = new EarnObserver(original.getUUID());
        NeoForge.EVENT_BUS.register(observer);
        try {
            var holder = original.server.getAdvancements().get(MASTERY);
            h.assertTrue(holder != null, "Real mastery advancement was not registered");
            h.assertTrue(!original.getAdvancements().getOrStartProgress(holder).isDone(), "Fresh player already has mastery");
            // An old save can die before making another teleport. Exercise migration
            // inside the real restoreFrom/Clone boundary, not just the next-use path.
            original.getPersistentData().putInt(COUNT, 399);
            var unrelated = new CompoundTag(); unrelated.putString("audit_unrelated", "keep");
            original.getPersistentData().put(Player.PERSISTED_NBT_TAG, unrelated);
            var replacement = respawn(original, h);
            h.assertTrue(replacement != original && replacement.getClass() == ServerPlayer.class,
                    "Respawn must construct an ordinary replacement ServerPlayer");
            var saved = replacement.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            h.assertTrue(saved.getInt(COUNT) == 399 && saved.getString("audit_unrelated").equals("keep")
                    && !original.getPersistentData().contains(COUNT) && !replacement.getPersistentData().contains(COUNT),
                    "Legacy progress was lost, not migrated, or replaced another persisted field");
            h.assertTrue(observer.earned == 0 && !replacement.getAdvancements().getOrStartProgress(holder).isDone(),
                    "Migration granted mastery before the 400th teleport");
            LegacyAdvancementBridge.teleported(replacement);
            LegacyAdvancementBridge.teleported(replacement);
            h.assertTrue(observer.earned == 1 && replacement.getAdvancements().getOrStartProgress(holder).isDone()
                    && saved.getInt(COUNT) == 400, "400th teleport must grant once and cap progress");

            var next = respawn(replacement, h);
            LegacyAdvancementBridge.teleported(next);
            h.assertTrue(next.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getInt(COUNT) == 400
                    && next.getAdvancements().getOrStartProgress(holder).isDone() && observer.earned == 1,
                    "Persisted progress or unlocked mastery changed/rewarded again across another respawn");
            h.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(observer);
            var current = original.server.getPlayerList().getPlayer(original.getUUID());
            if (current != null && current != original) original.server.getPlayerList().remove(current);
            session.close();
        }
    }

    private static ServerPlayer respawn(ServerPlayer player, GameTestHelper h) {
        // Use the production PlayerList method, which invokes ServerPlayer.restoreFrom
        // and posts Clone itself. This is a lifecycle test, not a lethal-damage test.
        var replacement = player.server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        replacement.setNoGravity(true);
        replacement.setPos(origin.getX() + 2.5, origin.getY() + 2, origin.getZ() + 2.5);
        return replacement;
    }

    public static final class EarnObserver {
        private final UUID player;
        private int earned;
        EarnObserver(UUID player) { this.player = player; }
        @SubscribeEvent
        public void earned(AdvancementEvent.AdvancementEarnEvent event) {
            if (event.getEntity().getUUID().equals(player) && event.getAdvancement().id().equals(MASTERY)) earned++;
        }
    }
}
