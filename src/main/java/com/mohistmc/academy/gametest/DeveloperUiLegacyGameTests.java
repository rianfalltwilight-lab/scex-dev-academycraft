package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.network.DevLearningSessionManager;
import com.mohistmc.academy.network.LearnSkillPacket;
import com.mohistmc.academy.network.PayloadRateLimiter;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.DevMachineType;
import com.mohistmc.academy.world.item.DeveloperPortable;
import com.mohistmc.academy.capability.EnergyItemHelper;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 1.12.2 developer rules: up-front energy check and the skill tree's visibility filter. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class DeveloperUiLegacyGameTests {
    private DeveloperUiLegacyGameTests() {}

    private static IPayloadContext context(ServerPlayer p) {
        return (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
                new Class<?>[]{IPayloadContext.class}, (proxy, method, args) -> {
                    if (method.getName().equals("player")) return p;
                    if (method.getName().equals("enqueueWork") && args[0] instanceof Runnable work) {
                        work.run();
                        return CompletableFuture.completedFuture(null);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @GameTest(template = "empty")
    public static void developerRefusesToStartWithoutTheWholeEstimate(GameTestHelper helper) {
        try (var session = com.mohistmc.academy.gametest.recheck.RecheckPlayers.connect(helper)) {
            ServerPlayer player = session.player();
            PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            ItemStack portable = new ItemStack(AcademyItems.DEVELOPER_PORTABLE.get());
            int estimate = DevMachineType.PORTABLE.actualEnergyPerStimulation()
                    * DevLearningSessionManager.INDUCTION_STIMULATIONS;
            ((DeveloperPortable) portable.getItem()).setEnergy(portable, estimate - 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, portable);
            UUID nonce = DevLearningSessionManager.issue(player, DevMachineType.PORTABLE, Optional.empty());
            LearnSkillPacket.handle(new LearnSkillPacket(LearnSkillPacket.INDUCTION_ACTION,
                    DevMachineType.PORTABLE.ordinal(), Optional.empty(), nonce), context(player));
            for (int i = 0; i < 200; i++) DevLearningSessionManager.tick(player);
            if (data.hasAbility() || EnergyItemHelper.getEnergy(portable) != estimate - 1) {
                helper.fail("induction started (and spent energy) without the whole estimate"); return;
            }
            PayloadRateLimiter.forget(player.getUUID());
            ((DeveloperPortable) portable.getItem()).setEnergy(portable, estimate);
            nonce = DevLearningSessionManager.issue(player, DevMachineType.PORTABLE, Optional.empty());
            LearnSkillPacket.handle(new LearnSkillPacket(LearnSkillPacket.INDUCTION_ACTION,
                    DevMachineType.PORTABLE.ordinal(), Optional.empty(), nonce), context(player));
            int ticks = DevMachineType.PORTABLE.developmentTicksPerStimulation()
                    * DevLearningSessionManager.INDUCTION_STIMULATIONS;
            for (int i = 0; i < ticks; i++) DevLearningSessionManager.tick(player);
            if (!data.hasAbility()) {
                helper.fail("induction with exactly the estimate did not complete"); return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void skillTreeHidesSkillsThatCannotYetBeReached(GameTestHelper helper) {
        PlayerAbilityData data = new PlayerAbilityData();
        data.setCurrentAbility(AbilityCategory.ELECTROMASTER);
        data.setPlayerLevel(1);
        var arcGen = SkillRegistry.getSkill(AbilityCategory.ELECTROMASTER, "arc_gen");
        var railgun = SkillRegistry.getSkill(AbilityCategory.ELECTROMASTER, "railgun");
        if (!com.mohistmc.academy.skill.SkillTreeVisibility.visible(data, arcGen)) {
            helper.fail("a Level 1 root skill was hidden from a Level 1 player"); return;
        }
        if (com.mohistmc.academy.skill.SkillTreeVisibility.visible(data, railgun)) {
            helper.fail("Railgun was shown to a Level 1 player who has not learned its parent"); return;
        }
        data.setPlayerLevel(4);
        if (!com.mohistmc.academy.skill.SkillTreeVisibility.visible(data, railgun)) {
            helper.fail("reaching Railgun's level did not reveal it"); return;
        }
        helper.succeed();
    }
}
