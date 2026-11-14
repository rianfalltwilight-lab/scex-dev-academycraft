package com.mohistmc.academy.skill;

import com.mohistmc.academy.api.event.AbilityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

/** Posts {@link AbilityEvents.SkillCast} at every server-side start of a skill. */
public final class SkillCastGate {
    private SkillCastGate() {}

    /** @return false when a listener cancelled the cast; the caller must then neither pay nor start it. */
    public static boolean allow(ServerPlayer player, Skill skill) {
        return skill == null || !NeoForge.EVENT_BUS.post(new AbilityEvents.SkillCast(player, skill)).isCanceled();
    }
}
