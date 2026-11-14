package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.config.BalanceProfile;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mohistmc.academy.skill.SkillStats;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.MachineComparator;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** MZ5: real numbers in skill tooltips and machine comparator output. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ModernInfoGameTests {
    private static final String EMPTY = "empty";
    private static final String BATCH = "modern_info";
    private ModernInfoGameTests() {}

    private static void check(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }

    private static String text(List<Component> lines) {
        StringBuilder out = new StringBuilder();
        for (Component line : lines) out.append(line.getString()).append(" | ");
        return out.toString();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void skillTooltipsShowScaledDamageAndCooldown(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(AbilityCategory.ELECTROMASTER);
        data.setPlayerLevel(5);
        data.learnSkill("railgun");
        data.setProficiency("railgun", 1f);
        var railgun = SkillRegistry.getSkill(AbilityCategory.ELECTROMASTER, "railgun");
        BalanceProfile before = ACConfig.Server.BALANCE_PROFILE.get();
        try {
            ACConfig.Server.BALANCE_PROFILE.set(BalanceProfile.LEGACY);
            String legacy = text(SkillStats.lines(player, data, railgun));
            check(legacy.contains("110") && !legacy.contains("550"), "legacy railgun line: " + legacy);
            check(legacy.contains("ac.skill_stats.cooldown") || legacy.toLowerCase().contains("cooldown")
                    || legacy.contains("s |"), "cooldown line missing: " + legacy);
            ACConfig.Server.BALANCE_PROFILE.set(BalanceProfile.MODERN);
            String modern = text(SkillStats.lines(player, data, railgun));
            check(modern.contains("550"), "modern level 5 railgun damage missing: " + modern);
        } finally {
            ACConfig.Server.BALANCE_PROFILE.set(before);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void machinesDriveComparators(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(pos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        var node = (BaseNodeBlockEntity) helper.getLevel().getBlockEntity(pos);
        var state = helper.getLevel().getBlockState(pos);
        check(state.hasAnalogOutputSignal(), "node has no comparator output");
        node.setEnergy(0);
        check(MachineComparator.signal(helper.getLevel(), pos) == 0, "empty node signal");
        node.setEnergy(node.getMaxEnergy() / 2);
        int half = state.getAnalogOutputSignal(helper.getLevel(), pos);
        check(half == 8, "half-full node signal: " + half);
        node.setEnergy(node.getMaxEnergy());
        check(state.getAnalogOutputSignal(helper.getLevel(), pos) == 15, "full node signal");
        helper.succeed();
    }
}
