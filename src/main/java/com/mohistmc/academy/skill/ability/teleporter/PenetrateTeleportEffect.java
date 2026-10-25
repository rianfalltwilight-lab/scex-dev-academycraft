package com.mohistmc.academy.skill.ability.teleporter;

import com.mohistmc.academy.skill.ability.LoadedRaycast;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.teleporter.PenetrateTargeting.Destination;
import com.mohistmc.academy.world.AcademySounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** Final 1.12.2 wall scanner: hold a marker, tune distance with the wheel, then release. */
public final class PenetrateTeleportEffect implements ChargingSkillEffect {

    @Override public String getId() { return "penetrate_teleport"; }
    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }
    @Override public int getMinChargeTicks() { return 0; }
    @Override public int getMaxChargeTicks() { return 1; }
    @Override public int getSessionTimeoutTicks(PlayerAbilityData data) { return Integer.MAX_VALUE; }

    @Override public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.enabled(getId());
    }
    @Override public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {}

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return true;
    }

    @Override public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return TickResult.CONTINUE;
    }
    @Override public boolean canRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        Destination destination = destination(player, data);
        BlockPos pos = BlockPos.containing(destination.position());
        return destination.available() && LoadedRaycast.isLoaded(player.serverLevel(), pos)
                && TeleportDestinations.isSafe(player, player.serverLevel(), destination.position());
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return tryRelease(player, data, ticks, Float.NaN);
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks, float releaseValue) {
        float exp = data.getProficiency(getId());
        Destination destination = destination(player, data, releaseValue);
        if (!destination.available()) return false;
        BlockPos pos = BlockPos.containing(destination.position());
        if (!LoadedRaycast.isLoaded(player.serverLevel(), pos)
                || !TeleportDestinations.isSafe(player, player.serverLevel(), destination.position())) return false;
        double distance = player.position().distanceTo(destination.position());
        if (!DynamicSkillRules.payForced(data, getId(),
                (float) (distance * lerpf(14, 9, exp)), lerpf(80, 50, exp))) return false;

        ServerLevel level = player.serverLevel();
        TeleportSkillHelper.teleport(player, destination.position());
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                AcademySounds.TP_TP, SoundSource.PLAYERS, .5f, 1f);
        DynamicSkillRules.addExp(player, data, getId(), (float) (.00014 * distance));
        return true;
    }

    @Override public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        tryRelease(player, data, ticks);
    }
    @Override public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {}
    @Override public void execute(ServerPlayer player, PlayerAbilityData data) {}
    @Override public int getCooldownTicks(float proficiency) { return (int) lerpf(50, 30, proficiency); }

    private Destination destination(ServerPlayer player, PlayerAbilityData data) {
        return destination(player, data, Float.NaN);
    }

    private Destination destination(ServerPlayer player, PlayerAbilityData data, float requestedDistance) {
        return PenetrateTargeting.destination(player, data, requestedDistance);
    }
}
