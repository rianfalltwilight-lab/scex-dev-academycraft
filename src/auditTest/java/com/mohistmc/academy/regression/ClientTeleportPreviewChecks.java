package com.mohistmc.academy.regression;

import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.teleporter.PenetrateTargeting;
import com.mohistmc.academy.skill.ability.teleporter.TeleportDestinations;
import com.mojang.authlib.GameProfile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Actual ClientLevel collision shapes; temporary local-only blocks are restored in the same tick. */
final class ClientTeleportPreviewChecks {
    private ClientTeleportPreviewChecks() {}

    static boolean ready(Minecraft mc) {
        for (int x = -1; x <= 0; x++) for (int z = -1; z <= 0; z++)
            if (mc.level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }

    static void run(Minecraft mc, List<String> evidence) {
        require(ready(mc), "client preview fixture chunks are not resident");
        var player = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "AuditPreview"));
        var data = new PlayerAbilityData();
        data.setCurrentAbility(AbilityCategory.TELEPORTER); data.setPlayerLevel(5);
        data.learnSkill("penetrate_teleport"); data.setDevMode(true);
        int y = mc.player.blockPosition().getY() + 10;
        for (int sign : new int[]{1, -1}) {
            var saved = new LinkedHashMap<BlockPos, BlockState>();
            Vec3 start = new Vec3(sign * 4.5, y, sign * 4.25);
            player.setPos(start); player.setYRot(sign == 1 ? 0 : 180); player.setXRot(0);
            try {
                var clear = PenetrateTargeting.destination(player, data, 1);
                require(clear.available() && near(clear.position(), start.add(0, 0, sign * .8)),
                        "clear marker overshot its last tested in-range step, sign=" + sign);
                int x = sign == 1 ? 4 : -5;
                for (int z : sign == 1 ? new int[]{6, 10} : new int[]{-7, -11}) {
                    for (int dy = 0; dy <= 1; dy++) {
                        BlockPos pos = new BlockPos(x, y + dy, z);
                        saved.put(pos, mc.level.getBlockState(pos));
                        require(saved.get(pos).isAir(), "fresh isolated preview fixture is not empty");
                        mc.level.setBlock(pos, Blocks.GLASS_PANE.defaultBlockState(), 2);
                    }
                }
                // Both points are in the pane's block cell. Only one intersects
                // its thin shape; a two-block occupied/empty test cannot distinguish them.
                require(TeleportDestinations.isSafe(player, mc.level, new Vec3(start.x, y, sign * 6.1)),
                        "thin pane rejected a non-intersecting player box, sign=" + sign);
                require(!TeleportDestinations.isSafe(player, mc.level, new Vec3(start.x, y, sign * 6.5)),
                        "actual player box entered a thin pane, sign=" + sign);
                var gap = PenetrateTargeting.destination(player, data, 10);
                require(gap.available() && near(gap.position(), new Vec3(start.x, y, sign * 9.85)),
                        "marker did not retain the last safe step before the second pane, sign=" + sign + ": " + gap);
                require(mc.level.noCollision(player, player.getBoundingBox().move(gap.position().subtract(start))),
                        "returned marker overlaps actual client collision shapes");
            } finally {
                for (var entry : saved.entrySet()) mc.level.setBlock(entry.getKey(), entry.getValue(), 2);
            }
        }
        evidence.add("PASS shared penetration preview: positive/negative coordinates, actual thin-pane shapes, last-safe wall exit and one-block range; local fixture restored, no player move or packet");
    }

    private static boolean near(Vec3 first, Vec3 second) { return first.distanceToSqr(second) < 1e-8; }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
