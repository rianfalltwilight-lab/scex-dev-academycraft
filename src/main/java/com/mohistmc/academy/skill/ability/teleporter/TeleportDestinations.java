package com.mohistmc.academy.skill.ability.teleporter;

import com.mohistmc.academy.skill.ability.LoadedRaycast;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
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

    /**
     * Mob-caster variant: the same bounds, loaded-area and collision checks for any entity's box, plus
     * solid footing (a mob is never parked in mid-air).  Player destinations keep {@link #isSafe(Player, Level, Vec3)}.
     */
    public static boolean isSafeGrounded(Entity entity, Level level, Vec3 feet) {
        if (feet == null || !Double.isFinite(feet.x) || !Double.isFinite(feet.y) || !Double.isFinite(feet.z)) return false;
        AABB box = entity.getBoundingBox().move(feet.subtract(entity.position()));
        var border = level.getWorldBorder();
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
                || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)) return false;
        if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight()
                || box.minX < border.getMinX() || box.maxX > border.getMaxX()
                || box.minZ < border.getMinZ() || box.maxZ > border.getMaxZ()) return false;
        if (!LoadedRaycast.isAreaLoaded(level, box.inflate(1)) || !level.noCollision(entity, box)) return false;
        // Something to stand on within a tenth of a block below the feet.
        return !level.noCollision(entity, new AABB(box.minX, box.minY - .1, box.minZ, box.maxX, box.minY, box.maxZ));
    }
}
