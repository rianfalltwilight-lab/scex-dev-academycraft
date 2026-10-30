package com.mohistmc.academy.skill.ability.teleporter;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.PlayerAbilityData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** Read-only penetration scan shared by the local marker and authoritative release. */
public final class PenetrateTargeting {
    private PenetrateTargeting() {}
    public record Destination(Vec3 position, boolean available) {}

    public static double maximumDistance(PlayerAbilityData data) {
        float exp = data.getProficiency("penetrate_teleport");
        double perBlock = Math.max(.0001, DynamicSkillRules.cp("penetrate_teleport", lerpf(14, 9, exp)));
        double distance = lerpf(10, 35, exp);
        return data.isDevMode() ? distance : Math.min(distance, data.getCurrentCp() / perBlock);
    }

    public static Destination destination(Player player, PlayerAbilityData data, float requestedDistance) {
        double maxDistance = maximumDistance(data);
        if (Float.isFinite(requestedDistance)) maxDistance = Math.max(0, Math.min(requestedDistance, maxDistance));
        final double step = .8;
        int stage = 0, counter = 0;
        double travelled = 0;
        Vec3 direction = player.getLookAngle().normalize();
        // The destination is at the feet. Only the rendered marker is lifted by eye height.
        Vec3 cursor = player.position();
        Vec3 lastSafe = null;
        while (travelled <= maxDistance) {
            boolean free = TeleportDestinations.isSafe(player, player.level(), cursor);
            if (free) lastSafe = cursor;
            if (stage == 0) {
                if (!free) stage = 1;
            } else if (stage == 1) {
                if (free) stage = 2;
            } else if (!free || ++counter > 4) {
                break;
            }
            travelled += step;
            cursor = cursor.add(direction.scale(step));
        }
        return new Destination(lastSafe == null ? player.position() : lastSafe, stage != 1 && lastSafe != null);
    }
}
