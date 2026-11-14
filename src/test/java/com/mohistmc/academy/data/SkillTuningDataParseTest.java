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
        assertEquals(2, parse("othermod:railgun", "{\"level\": 2}").get("railgun").level());
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

    @Test void namespacesStackAcademyFirst() {
        Map<ResourceLocation, JsonElement> files = new java.util.LinkedHashMap<>();
        files.put(ResourceLocation.parse("zpack:railgun"), JsonParser.parseString("{\"cp\": 2, \"level\": 4}"));
        files.put(ResourceLocation.parse("academy:railgun"), JsonParser.parseString("{\"cp\": 3, \"level\": 2, \"base_cp\": 7}"));
        files.put(ResourceLocation.parse("apack:railgun"), JsonParser.parseString("{\"enabled\": false, \"level\": 3}"));
        var o = SkillTuningData.parse(files, KNOWN::contains).get("railgun");
        assertEquals(6f, o.cp(), 1e-6);
        assertEquals(4, o.level(), "zpack comes last, so its level wins");
        assertEquals(7f, o.baseCp(), "fields a later file leaves out stay");
        assertFalse(o.enabled());
    }

    @Test void layerKeepsBaseWhereTopIsSilent() {
        var base = parse("academy:railgun", "{\"damage\": 2, \"level\": 3, \"destroy_blocks\": false}").get("railgun");
        var top = parse("academy:railgun", "{\"damage\": 1.5, \"tree_x\": 4}").get("railgun");
        var o = SkillTuningData.layer(base, top);
        assertEquals(3f, o.damage(), 1e-6); assertEquals(3, o.level()); assertEquals(4.0, o.treeX());
        assertFalse(o.destroyBlocks());
        assertSame(top, SkillTuningData.layer(null, top));
        assertSame(base, SkillTuningData.layer(base, null));
    }

    @Test void balanceFilesSplitPerProfile() {
        Map<ResourceLocation, JsonElement> files = new java.util.LinkedHashMap<>();
        files.put(ResourceLocation.parse("academy:modern/skills/railgun"), JsonParser.parseString("{\"damage\": 2}"));
        files.put(ResourceLocation.parse("academy:legacy/skills/arc_gen"), JsonParser.parseString("{\"cp\": 0.5}"));
        files.put(ResourceLocation.parse("academy:modern/machines/node"), JsonParser.parseString("{}"));
        files.put(ResourceLocation.parse("academy:future/skills/railgun"), JsonParser.parseString("{}"));
        var profiles = BalanceProfileData.parse(files, KNOWN::contains);
        assertEquals(2f, profiles.get(com.mohistmc.academy.config.BalanceProfile.MODERN).get("railgun").damage(), 1e-6);
        assertEquals(.5f, profiles.get(com.mohistmc.academy.config.BalanceProfile.LEGACY).get("arc_gen").cp(), 1e-6);
        assertEquals(1, profiles.get(com.mohistmc.academy.config.BalanceProfile.MODERN).size());
    }

    @Test void globalMultipliersScaleCpAndCooldownOnly() {
        var t = LegacyAbilityRules.parseConfig("railgun", List.of("railgun.cp=0.5", "railgun.cooldown=2", "railgun.damage=3"));
        var g = LegacyAbilityRules.withGlobal(t, 4, 0.25f);
        assertEquals(2f, g.cp(), 1e-6); assertEquals(.5f, g.cooldown(), 1e-6);
        assertEquals(3f, g.damage(), 1e-6); assertEquals(t.overload(), g.overload());
        assertSame(t, LegacyAbilityRules.withGlobal(t, 1, 1));
    }
}
