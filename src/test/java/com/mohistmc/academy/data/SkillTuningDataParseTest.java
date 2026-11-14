package com.mohistmc.academy.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mohistmc.academy.config.LegacyAbilityRules;
import com.mohistmc.academy.skill.Skill;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillTuningDataParseTest {
    private static final Set<String> KNOWN = Set.of("railgun", "arc_gen", "mag_movement");

    private static Map<String, SkillTuningData.SkillOverride> parse(String id, String json) {
        Map<ResourceLocation, JsonElement> files = Map.of(ResourceLocation.parse(id), JsonParser.parseString(json));
        return SkillTuningData.parse(files, KNOWN::contains);
    }

    @Test void absentFieldsKeepDefaults() {
        var o = parse("academy:railgun", "{}").get("railgun");
        assertNull(o.enabled()); assertNull(o.level()); assertNull(o.prerequisites()); assertNull(o.baseCp());
        assertEquals(1, o.damage()); assertEquals(1, o.cooldown());
    }

    @Test void multipliersAndLevelAreClamped() {
        var o = parse("academy:railgun", "{\"damage\": -3, \"cp\": 0.5, \"cooldown\": \"x\", \"level\": 0, \"base_cp\": -1}").get("railgun");
        assertEquals(0, o.damage()); assertEquals(.5f, o.cp()); assertEquals(1, o.cooldown());
        assertEquals(1, o.level()); assertEquals(0, o.baseCp());
        assertEquals(5, parse("academy:railgun", "{\"level\": 12}").get("railgun").level());
    }

    @Test void unknownSkillsNamespacesAndPrerequisitesAreSkipped() {
        assertTrue(parse("academy:nope", "{\"level\": 2}").isEmpty());
        assertTrue(parse("othermod:railgun", "{\"level\": 2}").isEmpty());
        var o = parse("academy:mag_movement", "{\"prerequisites\": [{\"skill\": \"nope\"}, {\"skill\": \"mag_movement\"},"
                + " {\"skill\": \"arc_gen\", \"proficiency\": 3}, {\"any_level\": 3}, {\"any_level\": 7}]}").get("mag_movement");
        assertEquals(List.of(new Skill.Prerequisite("arc_gen", 1f), new Skill.Prerequisite("any_level_3", 0)), o.prerequisites());
    }

    @Test void mergeMultipliesAndAndsWithConfig() {
        var config = LegacyAbilityRules.parseConfig("railgun", List.of("railgun.cp=0.5", "railgun.destroy_blocks=false", "railgun.cooldown=2"));
        var o = parse("academy:railgun", "{\"cp\": 3, \"cooldown\": 2, \"destroy_blocks\": true, \"enabled\": false}").get("railgun");
        var t = LegacyAbilityRules.merge(config, o);
        assertEquals(1.5f, t.cp(), 1e-6); assertEquals(4f, t.cooldown(), 1e-6);
        assertFalse(t.destroyBlocks()); assertFalse(t.enabled());
        assertSame(config, LegacyAbilityRules.merge(config, null));
    }
}
