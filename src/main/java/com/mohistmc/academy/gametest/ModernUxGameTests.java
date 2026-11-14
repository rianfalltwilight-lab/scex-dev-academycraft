package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mohistmc.academy.skill.SkillRejectFeedback;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** MZ1 usability fixes that run on the server: reject reasons and the permission-0 /academy info. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ModernUxGameTests {
    private static final String EMPTY = "empty";
    private static final String BATCH = "modern_ux";
    private ModernUxGameTests() {}

    private static void check(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    private static Object arg(Component component, int index) {
        return ((TranslatableContents) component.getContents()).getArgs()[index];
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void rejectReasonsNameTheFailedGate(GameTestHelper helper) {
        Skill arcGen = SkillRegistry.getSkill(AbilityCategory.ELECTROMASTER, "arc_gen");
        check(arcGen != null, "fixture skill missing");
        PlayerAbilityData data = new PlayerAbilityData();
        data.setCurrentAbility(AbilityCategory.ELECTROMASTER);
        data.setPlayerLevel(5);
        data.recalculateMaxResources(true);

        data.setCooldownRaw("arc_gen", 50);
        Component cooldown = SkillRejectFeedback.reason(data, arcGen);
        check(key(cooldown).equals("message.academy.skill.cooldown") && "2.5".equals(arg(cooldown, 1)),
                "cooldown reason: " + cooldown.getString());

        data.setOverloadLocked(true);
        check(key(SkillRejectFeedback.reason(data, arcGen)).equals("message.academy.skill.overloaded"),
                "overload lock must win over cooldown");
        data.setOverloadLocked(false);
        data.clearCooldowns();

        List<? extends String> overrides = ACConfig.Server.SKILL_OVERRIDES.get();
        try {
            ACConfig.Server.SKILL_OVERRIDES.set(List.of("arc_gen.enabled=false"));
            check(key(SkillRejectFeedback.reason(data, arcGen)).equals("message.academy.skill.disabled"),
                    "disabled reason missing");
        } finally {
            ACConfig.Server.SKILL_OVERRIDES.set(overrides);
        }

        // Every active skill settles its CP dynamically; a failed canPay records the shortfall it saw.
        data.setCurrentCp(10);
        check(!com.mohistmc.academy.config.DynamicSkillRules.canPay(data, "arc_gen", 30, 18), "fixture could pay");
        Component cp = SkillRejectFeedback.reason(data, arcGen);
        check(key(cp).equals("message.academy.skill.insufficient_cp_amount") && "10".equals(arg(cp, 0))
                && "30".equals(arg(cp, 1)), "cp reason: " + cp.getString());

        check(key(SkillRejectFeedback.reason(data, arcGen)).equals("message.academy.skill.cannot_use"),
                "a skill that passes every gate gets the generic reason");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void infoIsOpenButAdminBranchesStayGated(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        CommandSourceStack source = player.createCommandSourceStack().withPermission(0).withSuppressedOutput();
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        check(dispatcher.parse("academy info", source).getExceptions().isEmpty()
                        && dispatcher.parse("academy info", source).getContext().getCommand() != null,
                "permission 0 cannot run /academy info");
        for (String admin : new String[]{"academy learn @s arc_gen", "academy cp @s fill", "academy query @s",
                "academy mob supported", "academy level @s 5"}) {
            var parse = dispatcher.parse(admin, source);
            check(parse.getContext().getCommand() == null, "permission 0 reached /" + admin);
        }
        var op = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        check(dispatcher.parse("academy query @s", op).getContext().getCommand() != null, "op lost /academy query");
        helper.succeed();
    }
}
