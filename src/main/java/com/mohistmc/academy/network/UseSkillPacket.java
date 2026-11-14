package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillEffect;
import com.mohistmc.academy.skill.SkillPreset;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UseSkillPacket(int slotIndex, SkillInputToken actionToken) implements CustomPacketPayload {
    public UseSkillPacket(int slotIndex) { this(slotIndex, SkillInputToken.missing(0)); }

    public static final Type<UseSkillPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "use_skill"));

    public static final StreamCodec<ByteBuf, UseSkillPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.INT, UseSkillPacket::slotIndex, SkillInputTokenCodec.STREAM_CODEC, UseSkillPacket::actionToken, UseSkillPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UseSkillPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !SkillInputSessionManager.isCurrentPlayer(player)) return;
            PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            long now = player.serverLevel().getGameTime();

            // Normal input is edge-triggered and far below this budget. Modified clients
            // exceeding it are dropped before messages, registry work or effect execution.
            if (!PayloadRateLimiter.allow(player.getUUID(), "use_skill", now, 20, 20)) return;
            if (packet.slotIndex() < 0 || packet.slotIndex() >= SkillPreset.SLOT_COUNT) return;
            if (!SkillInputSessionManager.canAccept(player, packet.actionToken())) { SkillInputSessionManager.refresh(player); return; }
            Skill skill = data.getSlotSkill(data.getCurrentPresetIndex(), packet.slotIndex());
            if (skill == null) { reject(player, now, "empty", Component.translatable("message.academy.skill.slot_empty", packet.slotIndex() + 1)); return; }
            if (!data.hasLearnedSkill(skill.getId())) { reject(player, now, "unlearned", Component.translatable("message.academy.skill.not_learned", Component.translatable(skill.getTranslationKey()))); return; }
            SkillEffect effect = skill.getEffect();
            if (effect == null || effect instanceof com.mohistmc.academy.skill.ChargingSkillEffect) return;
            // Consume an authentic one-shot intent before transient gameplay gates.
            if (!SkillInputSessionManager.accept(player, packet.actionToken())) return;
            if (AbilityInterferenceService.isInterfered(player)) { AbilityInterferenceService.notifyBlocked(player); return; }
            if (!data.isAbilityActive()) { reject(player, now, "inactive", Component.translatable("message.academy.ability.inactive")); return; }
            if (data.isOnCooldown(skill.getId())) return;
            // PlayerAbilityData.canUseSkill checks enabled/learned/cooldown for
            // every skill, but deliberately skips registry CP/OL for dynamic
            // contexts before their live preflight below.
            if (!data.canUseSkill(skill)) {
                reject(player, now, "resource", Component.translatable("message.academy.skill.insufficient_cp"));
                return;
            }

            if (!effect.canActivate(player, data)) return;
            if (!com.mohistmc.academy.skill.SkillCastGate.allow(player, skill)) return;

            float preActivationProficiency = data.getProficiency(skill.getId());
            boolean executed;
            if (effect.appliesBaseResourceCost()) {
                data.useSkill(skill);
                executed = effect.executeAndReport(player, data);
            } else {
                // Dynamic legacy effects settle inside their authoritative
                // action.  Start cooldown only after that action commits.
                executed = effect.executeAndReport(player, data);
                if (executed && !data.isDevMode() && !effect.managesOwnCooldown())
                    data.setCooldown(skill.getId(), effect.getCooldownTicks(
                            effect.cooldownUsesPreActivationProficiency()
                                    ? preActivationProficiency : data.getProficiency(skill.getId())));
            }
            if (!executed) {
                data.syncTo(player);
                return;
            }
            if (effect == null || effect.grantsActivationProficiency()) {
                com.mohistmc.academy.skill.AbilityMutationService.addSkillExp(player, data, skill.getId(),
                        0.002f * com.mohistmc.academy.config.ACConfig.Server.legacyRules().proficiencyGrowth()
                                * com.mohistmc.academy.config.ACConfig.Server.skill(skill.getId()).exp());
            }
            com.mohistmc.academy.advancement.LegacyAdvancementBridge.used(player,skill);

            data.syncTo(player);
        });
    }

    private static void reject(ServerPlayer player, long now, String reason, Component message) {
        if (PayloadRateLimiter.allow(player.getUUID(), "use_skill_feedback:" + reason, now, 20, 1))
            player.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED));
    }
}
