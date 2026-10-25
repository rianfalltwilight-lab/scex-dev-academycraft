package com.mohistmc.academy.regression;

import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.tutorial.TutorialUnlocks;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the production player attachment entry points, not a duplicate Set implementation. */
@GameTestHolder("academy_audit")
@PrefixGameTestTemplate(false)
public final class TutorialUnlockDeepAuditGameTests {
    @GameTest(template = "empty")
    public static void playerTutorialActivationDeduplicatesAndRejectsUnknownItems(GameTestHelper h) {
        var player = FollowupGameTests.player(h, AbilityCategory.AEROHAND);
        try {
            var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            var expected = new HashSet<>(data.getActivatedTutorials());
            h.assertTrue(expected.add("ores"), "Fixture already activated ores");
            h.assertTrue("ores".equals(TutorialUnlocks.activateForItem(data, "academy:constraint_metal")),
                    "First matching item did not activate the player's tutorial");
            h.assertTrue(data.hasActivatedTutorial("ores"), "Activation did not reach the player's attachment");
            h.assertTrue(TutorialUnlocks.activateForItem(data, "academy:crystal_ore") == null,
                    "Second item for the same tutorial emitted a duplicate activation");
            h.assertTrue(TutorialUnlocks.activateForItem(data, "minecraft:stone") == null,
                    "Unknown item invented a tutorial activation");
            h.assertTrue(TutorialUnlocks.activateForItem(data, null) == null,
                    "Null item invented a tutorial activation");
            h.assertTrue(data.getActivatedTutorials().equals(expected), "Duplicate or unknown item changed tutorial state");
            h.succeed();
        } finally {
            player.server.getPlayerList().remove(player);
        }
    }

    @GameTest(template = "empty")
    public static void playerObtainedHistoryReconcilesOnceWithoutInventingDefaultTutorials(GameTestHelper h) {
        var player = FollowupGameTests.player(h, AbilityCategory.AEROHAND);
        try {
            var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            var expected = new HashSet<>(data.getActivatedTutorials());
            h.assertTrue(expected.add("wind_generator"), "Fixture already activated wind generator");
            h.assertTrue(!data.hasActivatedTutorial("welcome"), "Fixture already activated default tutorial");
            data.markObtained("academy:windgen_main");
            data.markObtained("academy:windgen_fan");
            data.markObtained("minecraft:stone");
            var obtained = Set.copyOf(data.getObtainedItems());
            TutorialUnlocks.reconcile(data);
            h.assertTrue(data.hasActivatedTutorial("wind_generator"), "Obtained history did not update the player attachment");
            h.assertTrue(!data.hasActivatedTutorial("welcome"), "Reconciliation invented a default tutorial");
            h.assertTrue(data.getActivatedTutorials().equals(expected), "Reconciliation activated unrelated tutorials");
            TutorialUnlocks.reconcile(data);
            h.assertTrue(data.getActivatedTutorials().equals(expected), "Reconciliation is not idempotent");
            h.assertTrue(TutorialUnlocks.activateForItem(data, "academy:windgen_base") == null,
                    "Reconciled tutorial emitted another activation");
            h.assertTrue(data.getObtainedItems().equals(obtained), "Reconciliation changed the obtained-item history");
            h.succeed();
        } finally {
            player.server.getPlayerList().remove(player);
        }
    }
}
