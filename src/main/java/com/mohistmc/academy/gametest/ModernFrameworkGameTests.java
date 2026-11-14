package com.mohistmc.academy.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.config.BalanceProfile;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.config.MachineConfig;
import com.mohistmc.academy.data.BalanceProfileData;
import com.mohistmc.academy.data.SkillTuningData;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.entity.AbilityInterfererBlockEntity;
import com.mohistmc.academy.world.menu.AbilityInterfererMenu;
import com.mohistmc.academy.world.menu.NodeBasicMenu;
import io.netty.buffer.Unpooled;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** MZ0: balance profile switch, profile data layer, global multipliers and 32-bit menu values. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ModernFrameworkGameTests {
    private static final String EMPTY = "empty";
    private static final String BATCH = "modern_framework";
    private ModernFrameworkGameTests() {}

    private static Map<ResourceLocation, JsonElement> files(String... idAndJson) {
        Map<ResourceLocation, JsonElement> out = new LinkedHashMap<>();
        for (int i = 0; i < idAndJson.length; i += 2)
            out.put(ResourceLocation.parse(idAndJson[i]), JsonParser.parseString(idAndJson[i + 1]));
        return out;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }

    private static boolean near(float a, float b) { return Math.abs(a - b) < 1e-3f; }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void profileLayerFollowsTheConfiguredProfile(GameTestHelper helper) {
        BalanceProfile before = ACConfig.Server.BALANCE_PROFILE.get();
        Map<ResourceLocation, JsonElement> shipped = BalanceProfileData.installedForTest();
        try {
            BalanceProfileData.applyForTest(files(
                    "academy:modern/skills/railgun", "{\"damage\": 2, \"cp\": 0.5}",
                    "academy:legacy/skills/railgun", "{\"damage\": 0.5}"));
            ACConfig.Server.BALANCE_PROFILE.set(BalanceProfile.MODERN);
            check(near(DynamicSkillRules.damage("railgun", 10), 20), "modern layer not applied: "
                    + DynamicSkillRules.damage("railgun", 10));
            check(near(DynamicSkillRules.cp("railgun", 100), 50), "modern cp layer not applied");
            ACConfig.Server.BALANCE_PROFILE.set(BalanceProfile.LEGACY);
            check(near(DynamicSkillRules.damage("railgun", 10), 5), "switching to legacy needed a reload");
            check(near(DynamicSkillRules.cp("railgun", 100), 100), "legacy picked up the modern cp layer");

            ACConfig.Server.BALANCE_PROFILE.set(BalanceProfile.MODERN);
            SkillTuningData.applyForTest(files("academy:railgun", "{\"damage\": 3}"));
            check(near(DynamicSkillRules.damage("railgun", 10), 60), "pack override does not stack on the profile");
        } finally {
            SkillTuningData.applyForTest(Map.of());
            BalanceProfileData.applyForTest(shipped);
            ACConfig.Server.BALANCE_PROFILE.set(before);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void globalCooldownAndCpMultipliersReachEverySkill(GameTestHelper helper) {
        double cooldown = ACConfig.Server.COOLDOWN_MULTIPLIER.get(), cp = ACConfig.Server.CP_COST_MULTIPLIER.get();
        try {
            ACConfig.Server.COOLDOWN_MULTIPLIER.set(0.5);
            ACConfig.Server.CP_COST_MULTIPLIER.set(2.0);
            check(DynamicSkillRules.cooldown("railgun", 100) == 50, "cooldown multiplier missing");
            check(DynamicSkillRules.cooldown("arc_gen", 15) == 8, "cooldown multiplier rounding");
            check(near(DynamicSkillRules.cp("arc_gen", 30), 60), "cp multiplier missing");
            check(near(DynamicSkillRules.overload("arc_gen", 18), 18), "cp multiplier leaked into overload");
            check(near(DynamicSkillRules.damage("arc_gen", 9), 9), "cp multiplier leaked into damage");
        } finally {
            ACConfig.Server.COOLDOWN_MULTIPLIER.set(cooldown);
            ACConfig.Server.CP_COST_MULTIPLIER.set(cp);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void menusCarryValuesAboveAShort(GameTestHelper helper) {
        var level = helper.getLevel();
        int bandwidth = MachineConfig.NODE_BASIC.bandwidth.get();
        int buffer = MachineConfig.INTERFERER_BUFFER.get();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            MachineConfig.NODE_BASIC.bandwidth.set(100_000);
            MachineConfig.INTERFERER_BUFFER.set(250_000);
            BlockPos nodePos = helper.absolutePos(new BlockPos(1, 1, 1));
            level.setBlock(nodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
            player.setPos(nodePos.getX() + .5, nodePos.getY() + 1, nodePos.getZ() + .5);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(nodePos);
            NodeBasicMenu node;
            try { node = new NodeBasicMenu(91, player.getInventory(), buf); } finally { buf.release(); }
            check(node.getNodeBandwidth() == 100_000, "node bandwidth wrapped: " + node.getNodeBandwidth());

            BlockPos interfererPos = helper.absolutePos(new BlockPos(3, 1, 1));
            level.setBlock(interfererPos, AcademyBlocks.ABILITY_INTERFERER.get().defaultBlockState(), 3);
            if (!(level.getBlockEntity(interfererPos) instanceof AbilityInterfererBlockEntity interferer)) {
                helper.fail("interferer fixture missing"); return;
            }
            interferer.setEnergy(200_000);
            buf = new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(interfererPos);
            AbilityInterfererMenu menu;
            try { menu = new AbilityInterfererMenu(92, player.getInventory(), buf); } finally { buf.release(); }
            check(menu.getMaxEnergy() == 250_000, "interferer max energy wrapped: " + menu.getMaxEnergy());
            check(menu.getEnergy() == 200_000, "interferer energy wrapped: " + menu.getEnergy());
        } finally {
            MachineConfig.NODE_BASIC.bandwidth.set(bandwidth);
            MachineConfig.INTERFERER_BUFFER.set(buffer);
        }
        helper.succeed();
    }
}
