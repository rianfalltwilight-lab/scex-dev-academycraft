package com.mohistmc.academy.skill;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.network.LearnSkillPacket;
import com.mohistmc.academy.network.RequestNodesPacket;
import com.mohistmc.academy.network.DevLearningSessionManager;
import com.mohistmc.academy.network.ConnectToNodePacket;
import com.mohistmc.academy.network.SyncChargingStatePacket;
import com.mohistmc.academy.network.LocationTeleportActionPacket;
import com.mohistmc.academy.skill.ability.teleporter.FlashingSessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = AcademyCraft.MODID)
public class SkillEventHandler {
    public static final int CHARGING_SYNC_INTERVAL_TICKS = 5;

    @SubscribeEvent public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers())
            com.mohistmc.academy.skill.ability.meltdowner.ScatterBombEffect.discardOnLifecycle(player);
        SkillChargingManager.cancelAll(event.getServer());
    }
    @SubscribeEvent public static void onServerStopped(ServerStoppedEvent event) { SkillChargingManager.clearAll(); }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        PlayerAbilityData data = event.getEntity().getData(AcademyAttachments.PLAYER_ABILITY);
        if (event.getEntity() instanceof ServerPlayer player) data.setCreativeExempt(player.isCreative());
        data.tick(); // 服务端和客户端都递减冷却


        if (event.getEntity() instanceof ServerPlayer player) {
            if (data.consumeOverloadTrigger()) {
                onOverloaded(player, data);
                player.setData(AcademyAttachments.PLAYER_ABILITY, data);
                return;
            }
            DevLearningSessionManager.tick(player);
            DevLearningSessionManager.clearExpired(player);
            LocationTeleportActionPacket.clearExpired(player);
            SkillChargingManager.ChargingState state = SkillChargingManager.getState(player.getUUID());
            if (AbilityInterferenceService.isInterfered(player)) {
                if (state != null) {
                    SkillChargingManager.finalizeCharging(player, state,
                            SkillChargingManager.FinalResult.ABORTED);
                    notifyCharging(player, state, -1, 0);
                }
                FlashingSessionManager.abort(player);
                LocationTeleportActionPacket.forgetPlayer(player.getUUID());
                player.setData(AcademyAttachments.PLAYER_ABILITY, data);
                return;
            }
            if (state != null && !state.releasing) {
                if (com.mohistmc.academy.network.ChargingHandshake.serverStartExpired(state.acknowledged,state.startedAt,player.serverLevel().getGameTime())) {
                    SkillChargingManager.cancel(player);
                    notifyCharging(player,state,-1,0);
                    player.setData(AcademyAttachments.PLAYER_ABILITY, data);
                    return;
                }
                Skill skill = data.getSlotSkill(data.getCurrentPresetIndex(), state.slotIndex);
                if (skill == null || !skill.getId().equals(state.skillId)
                        || !data.isAbilityActive() || !data.hasLearnedSkill(state.skillId)
                        || !data.isDevMode() && data.getPlayerLevel() < skill.getLevel()) {
                    SkillChargingManager.cancel(player);
                    notifyCharging(player,state,-1,0);
                } else {
                    SkillEffect effect = skill.getEffect();
                    if (effect instanceof ChargingSkillEffect chargingEffect) {
                        if (!state.acknowledged) return;
                        state.ticks++;
                        if (state.ticks % CHARGING_SYNC_INTERVAL_TICKS == 0) {
                            com.mohistmc.academy.network.SafePayloadSender.send(player,
                                    new SyncChargingStatePacket(state.ticks, chargingEffect.getMaxChargeTicks(data), state.slotIndex, state.skillId, state.epoch, state.generation, true));
                        }

                        ChargingSkillEffect.TickResult tickResult = chargingEffect.getTickResult(player, data, state.ticks);
                        int sessionTimeout = Math.max(1, chargingEffect.getSessionTimeoutTicks(data));
                        if (tickResult == ChargingSkillEffect.TickResult.CONTINUE && state.ticks >= sessionTimeout) {
                            tickResult = chargingEffect.getSessionTimeoutResult(player, data, state.ticks);
                        }
                        if (tickResult != ChargingSkillEffect.TickResult.CONTINUE) {
                            state.releasing = true;
                            if (tickResult == ChargingSkillEffect.TickResult.ABORT_RESOURCE) {
                                SkillChargingManager.finalizeCharging(player, state,
                                        SkillChargingManager.FinalResult.ABORTED);
                            }
                            float preCastProficiency = data.getProficiency(skill.getId());
                            // The charging session's immutable skill identity and learned/active state
                            // were checked above. Dynamic effects own their release ledger, so registry
                            // placeholder CP/OL must never veto a session which already paid its cost.
                            boolean canUse = true;
                            ChargingSettlement.TickOutcome outcome = ChargingSettlement.TickOutcome.valueOf(tickResult.name());
                            ChargingSettlement.Decision preflight = ChargingSettlement.decide(outcome,
                                    state.ticks, chargingEffect.getMinChargeTicks(data), canUse, false);
                            boolean released = preflight.attemptRelease()
                                    && chargingEffect.tryRelease(player, data, state.ticks);
                            ChargingSettlement.Decision settlement = ChargingSettlement.decide(outcome,
                                    state.ticks, chargingEffect.getMinChargeTicks(data), canUse, released);
                            if (settlement.grantProficiency()) {
                                com.mohistmc.academy.advancement.LegacyAdvancementBridge.used(player,skill);
                                SkillChargingManager.finalizeCharging(player, state,
                                        SkillChargingManager.FinalResult.RELEASED);
                                if (effect.grantsActivationProficiency()) {
                                    AbilityMutationService.addSkillExp(player, data, skill.getId(), 0.002f);
                                }
                                if (!data.isDevMode() && chargingEffect.shouldApplyCooldownAfterRelease(player, data, state.ticks)) {
                                    int cd = chargingEffect.getCooldownTicks(preCastProficiency, state.ticks);
                                    data.setCooldown(skill.getId(), cd);
                                }
                            } else if (!settlement.abortResource()) {
                                SkillChargingManager.finalizeCharging(player, state,
                                        SkillChargingManager.FinalResult.ABORTED);
                            }
                            notifyCharging(player,state,-1,0);
                            data.syncTo(player);
                        }
                    } else {
                        SkillChargingManager.cancel(player);
                        notifyCharging(player,state,-1,0);
                    }
                }
            }

            player.setData(AcademyAttachments.PLAYER_ABILITY, data);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            LearnSkillPacket.syncToClient(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.mohistmc.academy.skill.ability.meltdowner.ScatterBombEffect.discardOnLifecycle(player);
            SkillChargingManager.cancel(player);
        }
        com.mohistmc.academy.network.SwitchPresetPacket.forget(event.getEntity().getUUID());
        RequestNodesPacket.forgetPlayer(event.getEntity().getUUID());
        ConnectToNodePacket.forgetPlayer(event.getEntity().getUUID());
        DevLearningSessionManager.clear(event.getEntity().getUUID());
        LocationTeleportActionPacket.forgetPlayer(event.getEntity().getUUID());
        com.mohistmc.academy.network.PayloadRateLimiter.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cancelChargingAndNotify(player);
            LearnSkillPacket.syncToClient(player);
        }
        LocationTeleportActionPacket.forgetPlayer(event.getEntity().getUUID());
        DevLearningSessionManager.clear(event.getEntity().getUUID());
    }

    public static void onConfirmedDeath(net.minecraft.world.entity.LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            com.mohistmc.academy.network.TeleportChunkRequests.cancel(player.getUUID());
            cancelChargingAndNotify(player);
            LocationTeleportActionPacket.forgetPlayer(player.getUUID());
            DevLearningSessionManager.clear(player.getUUID());
            // CPData.Events.playerDeath + CooldownData.onPlayerDead. This runs before the
            // copyOnDeath attachment is cloned, so the respawned player inherits the reset.
            PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            data.recoverAll();
            data.clearCooldowns();
            if (data.isAbilityActive()) {
                data.setAbilityActive(false);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                        new com.mohistmc.academy.api.event.AbilityEvents.Deactivate(player));
            }
            data.syncTo(player);
        }
    }

    /** CPData.Events.playerWakeup: only a full night's sleep (the server waking everyone) restores. */
    @SubscribeEvent
    public static void onPlayerWakeUp(net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.wakeImmediately() || event.updateLevel()) return;
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (!data.hasAbility()) return;
        data.recoverAll();
        data.syncTo(player);
    }

    /**
     * Legacy activation-key priority: while the ability is on, a short V press first ends a
     * live special mode or held skill ({@code terminatesContext} / {@code abortDelegates}),
     * and only toggles the ability when nothing is running.  Returns whether anything ended.
     */
    public static boolean endSpecialFromActivateKey(ServerPlayer player, PlayerAbilityData data) {
        boolean ended = false;
        SkillChargingManager.ChargingState state = SkillChargingManager.getState(player.getUUID());
        if (state != null) {
            // Storm Wing, Vector Deviation/Reflection and every held charge are charging sessions.
            SkillChargingManager.cancel(player);
            notifyCharging(player, state, -1, 0);
            ended = true;
        }
        ended |= FlashingSessionManager.endFromActivateKey(player, data);
        if (com.mohistmc.academy.skill.ability.aerohand.AeroPassiveRuntime.isOffenseArmourEngaged(player)
                || com.mohistmc.academy.skill.ability.aerohand.AeroPassiveRuntime.isFlyingActive(player)) {
            com.mohistmc.academy.skill.ability.aerohand.AeroPassiveRuntime.terminateSustained(player, data);
            ended = true;
        }
        if (com.mohistmc.academy.skill.ability.telekinesis.TelekinesisPassiveHandler.isHardened(player)) {
            com.mohistmc.academy.skill.ability.telekinesis.TelekinesisPassiveHandler.togglePsychoHarden(player, data);
            ended = true;
        }
        if (com.mohistmc.academy.skill.ability.telekinesis.TelekinesisPassiveHandler.hasLiquidShadow(player)) {
            ended |= com.mohistmc.academy.skill.ability.telekinesis.TelekinesisPassiveHandler.toggleLiquidShadow(player);
        }
        ended |= com.mohistmc.academy.skill.ability.telekinesis.PsychoTransmissionEffect.endFromActivateKey(player, data);
        ended |= com.mohistmc.academy.skill.ability.telekinesis.CruiseBombEffect.endFromActivateKey(player, data);
        return ended;
    }

    /** ContextManager.__onOverload: reaching maximum overload disposes every live context. */
    private static void onOverloaded(ServerPlayer player, PlayerAbilityData data) {
        SkillChargingManager.ChargingState state = SkillChargingManager.getState(player.getUUID());
        if (state != null) {
            SkillChargingManager.finalizeCharging(player, state, SkillChargingManager.FinalResult.ABORTED);
            notifyCharging(player, state, -1, 0);
        }
        FlashingSessionManager.abort(player);
        LocationTeleportActionPacket.forgetPlayer(player.getUUID());
        com.mohistmc.academy.skill.ability.aerohand.AeroPassiveRuntime.terminateSustained(player, data);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new com.mohistmc.academy.api.event.AbilityEvents.Overloaded(
                player, data.getCurrentOverload(), data.getMaxOverload()));
        data.syncTo(player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            cancelChargingAndNotify(sp);
            DevLearningSessionManager.clear(sp.getUUID());
            LearnSkillPacket.syncToClient(sp);
        }
    }

    private static void cancelChargingAndNotify(ServerPlayer player){com.mohistmc.academy.skill.ability.meltdowner.ScatterBombEffect.discardOnLifecycle(player);SkillChargingManager.ChargingState state=SkillChargingManager.getState(player.getUUID());if(state==null)return;SkillChargingManager.cancel(player);notifyCharging(player,state,-1,0);}
    private static void notifyCharging(ServerPlayer player,SkillChargingManager.ChargingState state,int ticks,int maxTicks){
        com.mohistmc.academy.network.SafePayloadSender.send(player,
                new SyncChargingStatePacket(ticks,maxTicks,state.slotIndex,state.skillId,state.epoch,state.generation,true));
    }
}
