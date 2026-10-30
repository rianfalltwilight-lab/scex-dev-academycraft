package com.mohistmc.academy.skill.ability.teleporter;

import com.mohistmc.academy.skill.ability.LoadedRaycast;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Collision checks shared by previews and server commits. Airborne destinations remain valid. */
public final class TeleportDestinations {
    private TeleportDestinations() {}

    public static boolean isSafe(Player player, Level level, Vec3 feet) {
        if (!isWithinBounds(player, level, feet)) return false;
        AABB box = player.getBoundingBox().move(feet.subtract(player.position()));
        // BlockCollisions also visits adjacent cells for protruding shapes.
        return LoadedRaycast.isAreaLoaded(level, box.inflate(1)) && level.noCollision(player, box);
    }

    public static boolean isWithinBounds(Player player, Level level, Vec3 feet) {
        if (feet == null || !Double.isFinite(feet.x) || !Double.isFinite(feet.y)
                || !Double.isFinite(feet.z)) return false;
        AABB box = player.getBoundingBox().move(feet.subtract(player.position()));
        var border = level.getWorldBorder();
        if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight()
                || box.minX < border.getMinX() || box.maxX > border.getMaxX()
                || box.minZ < border.getMinZ() || box.maxZ > border.getMaxZ()) return false;
        return Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ);
    }
}
