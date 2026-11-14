package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.capability.IFEnergyStorage;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mohistmc.academy.world.block.DevMachineType;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.entity.DevAdvancedBlockEntity;
import com.mohistmc.academy.world.block.entity.DevNormalBlockEntity;
import com.mohistmc.academy.world.menu.DevAdvancedMenu;
import com.mohistmc.academy.world.menu.DevNormalMenu;
import com.mohistmc.academy.world.item.DeveloperPortable;
import com.mohistmc.academy.world.item.BaseFactor;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.common.NeoForge;
import com.mohistmc.academy.api.event.AbilityEvents;

public record LearnSkillPacket(String skillId, int typeOrdinal, Optional<BlockPos> devPos, UUID nonce) implements CustomPacketPayload {

    private static final int MAX_SKILL_ID_LENGTH = 64;
    /** Reserved action id: explicit level-zero developer induction, never a skill registry id. */
    public static final String INDUCTION_ACTION = "__induct__";
    public static final String LEVEL_UP_ACTION = "__level_up__";
    /** Reserved action id for 1.0.7 DevelopActionReset. */
    public static final String RESET_ACTION = "__reset__";
    private static final StreamCodec<ByteBuf, String> SKILL_ID_CODEC =
            ByteBufCodecs.stringUtf8(MAX_SKILL_ID_LENGTH);

    public static final Type<LearnSkillPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "learn_skill"));

    public static final StreamCodec<ByteBuf, LearnSkillPacket> STREAM_CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                SKILL_ID_CODEC.encode(buf, packet.skillId());
                buf.writeInt(packet.typeOrdinal());
                buf.writeBoolean(packet.devPos().isPresent());
                packet.devPos().ifPresent(pos -> BlockPos.STREAM_CODEC.encode(buf, pos));
                buf.writeLong(packet.nonce().getMostSignificantBits());
                buf.writeLong(packet.nonce().getLeastSignificantBits());
            },
            buf -> new LearnSkillPacket(SKILL_ID_CODEC.decode(buf), buf.readInt(),
                    buf.readBoolean() ? Optional.of(BlockPos.STREAM_CODEC.decode(buf)) : Optional.empty(),
                    new UUID(buf.readLong(), buf.readLong())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LearnSkillPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!SkillInputSessionManager.isCurrentPlayer(player)
                    || !PayloadRateLimiter.allow(player.getUUID(), "learn_skill", player.serverLevel().getGameTime(), 20, 4)) return;
            PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            if (packet.typeOrdinal() < 0 || packet.typeOrdinal() >= DevMachineType.values().length) {
                return;
            }
            DevMachineType devType = DevMachineType.fromOrdinal(packet.typeOrdinal());
            if (!DevLearningSessionManager.validate(player, packet.nonce(), devType, packet.devPos())) {
                result(player, packet, devType, false, Component.translatable("ac.skill_tree.result.session_expired")); return;
            }

            if (INDUCTION_ACTION.equals(packet.skillId())) {
                if (data.hasAbility()) {
                    result(player, packet, devType, false, Component.translatable("ac.skill_tree.result.induct_has_ability"));
                    return;
                }
                BlockPos inductionPos = packet.devPos().orElse(null);
                // SkillTree.scala refused to start unless the developer held the whole estimated cost.
                if (!validateEnergySource(player, devType, devType.actualEnergyPerStimulation()
                        * DevLearningSessionManager.INDUCTION_STIMULATIONS, inductionPos)) {
                    result(player, packet, devType, false, Component.translatable("ac.skill_tree.result.unavailable"));
                    return;
                }
                DevLearningSessionManager.InductionSelection selection =
                        DevLearningSessionManager.startInduction(player, packet.nonce(), devType, packet.devPos());
                if (selection == null) {
                    result(player, packet, devType, false, Component.translatable("ac.skill_tree.result.busy"));
                    return;
                }
                int totalCost = devType.actualEnergyPerStimulation()
                        * DevLearningSessionManager.INDUCTION_STIMULATIONS;
                player.sendSystemMessage(Component.translatable("ac.skill_tree.msg.induct_start",
                        Component.translatable(selection.category().getTranslationKey()),
                        Component.translatable(selection.factorBacked()
                                ? "ac.skill_tree.msg.induct_factor" : "ac.skill_tree.msg.induct_random"),
                        totalCost).withStyle(ChatFormatting.AQUA));
                return;
            }

            if (LEVEL_UP_ACTION.equals(packet.skillId())) {
                if (!data.hasAbility() || !data.canLevelUp()) {
                    result(player, packet, devType, false, Component.translatable("ac.skill_tree.result.level_not_ready"));
                    return;
                }
                int level = data.getPlayerLevel();
                int stimulations = 5 * (level + 1);
                int totalCost = devType.actualEnergyPerStimulation() * stimulations;
                if (!validateEnergySource(player, devType, totalCost, packet.devPos().orElse(null))) {
                    result(player, packet, devType, false, Component.translatable("ac.skill_tree.result.level_unavailable"));
                    return;
                }
                if (!DevLearningSessionManager.startLevelUp(player, packet.nonce(), devType,
                        packet.devPos(), data.getCurrentAbility(), level)) {
                    result(player, packet, devType, false, Component.translatable("ac.skill_tree.result.busy"));
                    return;
                }
                player.sendSystemMessage(Component.translatable("ac.skill_tree.msg.level_start",
                        level, level + 1, totalCost).withStyle(ChatFormatting.AQUA));
                return;
            }

            if (RESET_ACTION.equals(packet.skillId())) {
                if (devType != DevMachineType.ADVANCED || !data.hasAbility()
                        || data.getPlayerLevel() < 3) {
                    result(player, packet, devType, false,
                            Component.translatable("ac.skill_tree.result.reset_requirements"));
                    return;
                }
                if (!player.getMainHandItem().is(AcademyItems.MAGNETIC_COIL.get())) {
                    result(player, packet, devType, false, Component.translatable("ac.skill_tree.result.reset_no_coil"));
                    return;
                }
                boolean hasDifferentFactor = player.getInventory().items.stream().anyMatch(stack ->
                        stack.getItem() instanceof BaseFactor factor
                                && factor.getCategory() != data.getCurrentAbility());
                if (!hasDifferentFactor) {
                    result(player, packet, devType, false,
                            Component.translatable("ac.skill_tree.result.reset_no_factor"));
                    return;
                }
                int resetLevel = data.getPlayerLevel();
                if (!validateEnergySource(player, devType,
                        devType.actualEnergyPerStimulation() * resetLevel * 10,
                        packet.devPos().orElse(null))) {
                    result(player, packet, devType, false,
                            Component.translatable("ac.skill_tree.result.reset_unavailable"));
                    return;
                }
                DevLearningSessionManager.ResetSelection selection =
                        DevLearningSessionManager.startReset(player, packet.nonce(), devType, packet.devPos());
                if (selection == null) {
                    result(player, packet, devType, false,
                            Component.translatable("ac.skill_tree.result.reset_busy"));
                    return;
                }
                int stimulations = selection.expectedLevel() * 10;
                int totalCost = devType.actualEnergyPerStimulation() * stimulations;
                player.sendSystemMessage(Component.translatable("ac.skill_tree.msg.reset_start",
                        Component.translatable(selection.oldCategory().getTranslationKey()),
                        Component.translatable(selection.newCategory().getTranslationKey()),
                        totalCost).withStyle(ChatFormatting.AQUA));
                return;
            }

            Skill skill = SkillRegistry.getSkill(data.getCurrentAbility(), packet.skillId());
            if (skill == null) {
                player.sendSystemMessage(Component.translatable("ac.skill_tree.msg.unknown_skill", packet.skillId()).withStyle(ChatFormatting.RED));
                result(player, packet, devType, false, Component.translatable("ac.skill_tree.result.unknown_skill"));
                return;
            }

            if (data.hasLearnedSkill(skill.getId())) {
                player.sendSystemMessage(Component.translatable("ac.skill_tree.msg.already_learned",
                        Component.translatable(skill.getTranslationKey())).withStyle(ChatFormatting.RED));
                result(player, packet, devType, false, Component.translatable("ac.skill_tree.result.already_learned"));
                return;
            }

            if (!data.canLearnSkill(skill)) {
                player.sendSystemMessage(Component.translatable("ac.skill_tree.msg.prereq",
                        Component.translatable(skill.getTranslationKey())).withStyle(ChatFormatting.RED));
                result(player, packet, devType, false, Component.translatable("ac.skill_tree.result.prereq"));
                return;
            }

            if (skill.getLevel() > devType.maxLevel) {
                player.sendSystemMessage(Component.translatable("ac.skill_tree.msg.sync_rate", skill.getLevel())
                        .withStyle(ChatFormatting.RED));
                result(player, packet, devType, false, Component.translatable("ac.skill_tree.result.sync_rate"));
                return;
            }

            BlockPos devPos = packet.devPos().orElse(null);
            int stimulations = (int) (3 + skill.getLevel() * skill.getLevel() * .5f);
            // The UI and the session both use the real per-stimulation drain.
            int totalCost = devType.actualEnergyPerStimulation() * stimulations;
            if (!validateEnergySource(player, devType, totalCost, devPos)) {
                player.sendSystemMessage(Component.translatable("ac.skill_tree.msg.unavailable").withStyle(ChatFormatting.RED));
                result(player, packet, devType, false, Component.translatable("ac.skill_tree.result.unavailable"));
                return;
            }
            if (!DevLearningSessionManager.start(player,packet.nonce(),devType,packet.devPos(),
                    skill.getId(), skill.getCategory(), skill.getLevel())) {
                result(player, packet, devType, false, Component.translatable("ac.skill_tree.result.busy")); return;
            }
            player.sendSystemMessage(Component.translatable("ac.skill_tree.msg.learn_start",
                    Component.translatable(skill.getTranslationKey()), totalCost).withStyle(ChatFormatting.AQUA));
        });
    }

    private static void result(ServerPlayer player, LearnSkillPacket packet, DevMachineType type,
                               boolean success, Component reason) {
        result(player, packet, type, success, reason, false);
    }

    private static void result(ServerPlayer player, LearnSkillPacket packet, DevMachineType type,
                               boolean success, Component reason, boolean inProgress) {
        int energy = 0, maximum = 0;
        if (type == DevMachineType.PORTABLE) {
            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof DeveloperPortable) {
                energy = EnergyItemHelper.getEnergy(stack); maximum = DeveloperPortable.MAX_ENERGY;
            }
        } else if (packet.devPos().isPresent()
                && validateEnergySource(player, type, 0, packet.devPos().get())) {
            BlockEntity be = player.level().getBlockEntity(packet.devPos().get());
            if (be instanceof IFEnergyStorage storage) {
                energy = storage.getEnergyStored(); maximum = storage.getMaxEnergyStored();
            }
        }
        SafePayloadSender.send(player, new DevLearningResultPacket(packet.nonce(), success,
                Math.max(0, energy), Math.max(0, maximum), reason, inProgress));
    }

    static boolean consumeEnergy(ServerPlayer player, DevMachineType devType, int cost, BlockPos devPos) {
        if (devType == DevMachineType.PORTABLE) {
            ItemStack mainHand = player.getMainHandItem();
            if (mainHand.getItem() instanceof DeveloperPortable) {
                int energy = EnergyItemHelper.getEnergy(mainHand);
                if (cost < 0 || energy < cost) {
                    return false;
                }
                // Legacy PortableDevData pulled with ignoreBandwidth=true: the 50 IF
                // item bandwidth limits charging, not the developer's own debit.
                EnergyItemHelper.setEnergy(mainHand, energy - cost);
                return true;
            }
            return false;
        } else {
            if (devPos == null) return false;
            Level level = player.level();
            if (!com.mohistmc.academy.skill.ability.LoadedRaycast.isLoaded(level, devPos)
                    || player.distanceToSqr(devPos.getX() + 0.5, devPos.getY() + 0.5, devPos.getZ() + 0.5) > 64.0
                    || !level.mayInteract(player, devPos)) {
                return false;
            }

            boolean validMenu = switch (devType) {
                case NORMAL -> player.containerMenu instanceof DevNormalMenu menu && devPos.equals(menu.pos);
                case ADVANCED -> player.containerMenu instanceof DevAdvancedMenu menu && devPos.equals(menu.pos);
                case PORTABLE -> false;
            };
            // Existing normal/advanced skill-tree screens are not container screens. A
            // matching live menu is accepted as defense-in-depth but the nonce is the authority.
            if (validMenu && !player.containerMenu.stillValid(player)) return false;
            BlockEntity be = level.getBlockEntity(devPos);
            boolean expectedMachine = switch (devType) {
                case NORMAL -> be instanceof DevNormalBlockEntity;
                case ADVANCED -> be instanceof DevAdvancedBlockEntity;
                case PORTABLE -> false;
            };
            if (!expectedMachine || !(be instanceof IFEnergyStorage storage)) {
                return false;
            }
            // All payload handlers run on the server thread. Checking the exact return
            // value makes the debit atomic with respect to other packet handlers/ticks.
            return storage.extractEnergy(cost, false) == cost;
        }
    }

    static boolean validateEnergySource(ServerPlayer player, DevMachineType devType, int cost, BlockPos devPos) {
        if (devType == DevMachineType.PORTABLE) {
            ItemStack mainHand = player.getMainHandItem();
            return mainHand.getItem() instanceof DeveloperPortable
                    && EnergyItemHelper.getEnergy(mainHand) >= cost;
        }
        if (devPos == null) return false;
        Level level = player.level();
        if (!com.mohistmc.academy.skill.ability.LoadedRaycast.isLoaded(level, devPos)
                || player.distanceToSqr(devPos.getX() + 0.5, devPos.getY() + 0.5, devPos.getZ() + 0.5) > 64.0
                || !level.mayInteract(player, devPos)) return false;
        BlockEntity be = level.getBlockEntity(devPos);
        return switch (devType) {
            case NORMAL -> be instanceof DevNormalBlockEntity normal && normal.getEnergyStored() >= cost;
            case ADVANCED -> be instanceof DevAdvancedBlockEntity advanced && advanced.getEnergyStored() >= cost;
            case PORTABLE -> false;
        };
    }

    static void progress(ServerPlayer player, UUID nonce, DevMachineType type, Optional<BlockPos> pos,
                         int stimulation, int maximum, Component reason) {
        result(player, new LearnSkillPacket("", type.ordinal(), pos, nonce), type, false, reason, true);
    }

    static void completeDevelopment(ServerPlayer player, UUID nonce, DevMachineType type,
                                    Optional<BlockPos> pos, String skillId, AbilityCategory skillCategory) {
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        Skill skill = SkillRegistry.getSkill(skillCategory, skillId);
        LearnSkillPacket packet = new LearnSkillPacket(skillId,type.ordinal(),pos,nonce);
        if (data.getCurrentAbility() != skillCategory || skill == null
                || data.hasLearnedSkill(skillId) || !data.canLearnSkill(skill)) {
            result(player,packet,type,false,Component.translatable("ac.skill_tree.result.learn_changed")); return;
        }
        data.learnSkill(skillId);
        NeoForge.EVENT_BUS.post(new AbilityEvents.SkillLearned(player, skill));
        com.mohistmc.academy.advancement.LegacyAdvancementBridge.learned(player,data,skill);
        data.syncTo(player);
        player.sendSystemMessage(Component.translatable("ac.skill_tree.msg.learn_done",
                Component.translatable(skill.getTranslationKey())).withStyle(ChatFormatting.GREEN));
        result(player,packet,type,true,Component.translatable("ac.skill_tree.result.learn_done"));
    }

    static void completeLevelUp(ServerPlayer player, UUID nonce, DevMachineType type,
                                Optional<BlockPos> pos, AbilityCategory category, int expectedLevel) {
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        LearnSkillPacket packet = new LearnSkillPacket(LEVEL_UP_ACTION, type.ordinal(), pos, nonce);
        if (!data.hasAbility() || data.getCurrentAbility() != category
                || data.getPlayerLevel() != expectedLevel || !data.canLevelUp()) {
            result(player, packet, type, false, Component.translatable("ac.skill_tree.result.level_changed"));
            return;
        }
        int next = expectedLevel + 1;
        data.setPlayerLevel(next);
        NeoForge.EVENT_BUS.post(new AbilityEvents.LevelChanged(player, expectedLevel, next));
        com.mohistmc.academy.advancement.LegacyAdvancementBridge.levels(player, data);
        data.syncTo(player);
        player.sendSystemMessage(Component.translatable("ac.skill_tree.msg.level_done", next).withStyle(ChatFormatting.GREEN));
        result(player, packet, type, true, Component.translatable("ac.skill_tree.result.level_done", next));
    }

    /** Commit the old reset only after level*10 stimulations complete. */
    static void completeReset(ServerPlayer player, UUID nonce, DevMachineType type,
                              Optional<BlockPos> pos,
                              DevLearningSessionManager.ResetSelection selection) {
        LearnSkillPacket packet = new LearnSkillPacket(RESET_ACTION, type.ordinal(), pos, nonce);
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (type != DevMachineType.ADVANCED || !data.hasAbility()
                || data.getCurrentAbility() != selection.oldCategory()
                || data.getPlayerLevel() != selection.expectedLevel()
                || !selection.stillPresent(player) || !selection.consume(player)) {
            result(player, packet, type, false, Component.translatable("ac.skill_tree.result.reset_changed"));
            return;
        }

        AbilityCategory oldCategory = selection.oldCategory();
        int oldLevel = selection.expectedLevel();
        int newLevel = oldLevel - 1;
        data.reset();
        data.setCurrentAbility(selection.newCategory());
        data.setPlayerLevel(newLevel);
        NeoForge.EVENT_BUS.post(new AbilityEvents.CategoryChanged(player, oldCategory,
                selection.newCategory()));
        NeoForge.EVENT_BUS.post(new AbilityEvents.LevelChanged(player, oldLevel, newLevel));
        com.mohistmc.academy.advancement.LegacyAdvancementBridge.levels(player, data);
        data.syncTo(player);
        player.sendSystemMessage(Component.translatable("ac.skill_tree.msg.reset_done",
                Component.translatable(selection.newCategory().getTranslationKey()), newLevel)
                .withStyle(ChatFormatting.GREEN));
        result(player, packet, type, true, Component.translatable("ac.skill_tree.result.reset_done"));
    }

    /** Commit a factor/random category only after all five server ticks groups complete. */
    static void completeInduction(ServerPlayer player, UUID nonce, DevMachineType type,
                                  Optional<BlockPos> pos,
                                  DevLearningSessionManager.InductionSelection selection) {
        LearnSkillPacket packet = new LearnSkillPacket(INDUCTION_ACTION, type.ordinal(), pos, nonce);
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (data.hasAbility()) {
            result(player, packet, type, false, Component.translatable("ac.skill_tree.result.induct_changed"));
            return;
        }
        if (!selection.stillPresent(player, type, pos) || !selection.consume(player, type, pos)) {
            result(player, packet, type, false, Component.translatable("ac.skill_tree.result.induct_factor_gone"));
            return;
        }

        AbilityCategory oldCategory = data.getCurrentAbility();
        int oldLevel = data.getPlayerLevel();
        data.reset();
        data.setCurrentAbility(selection.category());
        data.setPlayerLevel(1);
        NeoForge.EVENT_BUS.post(new AbilityEvents.CategoryChanged(player, oldCategory, selection.category()));
        NeoForge.EVENT_BUS.post(new AbilityEvents.LevelChanged(player, oldLevel, 1));
        com.mohistmc.academy.advancement.LegacyAdvancementBridge.levels(player, data);
        data.syncTo(player);
        player.sendSystemMessage(Component.translatable("ac.skill_tree.msg.induct_done",
                Component.translatable(selection.category().getTranslationKey())).withStyle(ChatFormatting.GREEN));
        result(player, packet, type, true, Component.translatable("ac.skill_tree.result.induct_done"));
    }

    private static int getSkillCost(Skill skill) {
        return 100 + skill.getLevel() * 50;
    }

    public static void syncToClient(ServerPlayer player) {
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        syncToClient(player, data);
    }

    public static void syncToClient(ServerPlayer player, PlayerAbilityData data) {
        data.syncFullTo(player);
    }
}
