package com.mohistmc.academy.world;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Pack-facing c: and academy: tags exist, reference real ids, and are read by code. */
class ModpackTagContractTest {
    private static final Path DATA = Path.of("src/main/resources/data");
    private static final Path JAVA = Path.of("src/main/java/com/mohistmc/academy");
    private static final Pattern REGISTER = Pattern.compile("\\.register(?:SimpleItem)?\\(\"([a-z0-9_/]+)\"");

    private static final Map<String, List<String>> EXPECTED = Map.ofEntries(
            Map.entry("c/tags/block/ores.json", List.of("#c:ores/constraint_metal", "#c:ores/imag_crystal",
                    "#c:ores/resonant_crystal", "#c:ores/imag_silicon")),
            Map.entry("c/tags/item/ores.json", List.of("#c:ores/constraint_metal", "#c:ores/imag_crystal",
                    "#c:ores/resonant_crystal", "#c:ores/imag_silicon")),
            Map.entry("c/tags/block/ores/constraint_metal.json", List.of("academy:constraint_metal")),
            Map.entry("c/tags/block/ores/imag_crystal.json", List.of("academy:crystal_ore")),
            Map.entry("c/tags/block/ores/resonant_crystal.json", List.of("academy:reso_ore")),
            Map.entry("c/tags/block/ores/imag_silicon.json", List.of("academy:imagsil_ore")),
            Map.entry("c/tags/item/ores/constraint_metal.json", List.of("academy:constraint_metal")),
            Map.entry("c/tags/item/ores/imag_crystal.json", List.of("academy:crystal_ore")),
            Map.entry("c/tags/item/ores/resonant_crystal.json", List.of("academy:reso_ore")),
            Map.entry("c/tags/item/ores/imag_silicon.json", List.of("academy:imagsil_ore")),
            Map.entry("c/tags/block/ores_in_ground/stone.json", List.of("academy:constraint_metal",
                    "academy:crystal_ore", "academy:reso_ore", "academy:imagsil_ore")),
            Map.entry("c/tags/item/ores_in_ground/stone.json", List.of("academy:constraint_metal",
                    "academy:crystal_ore", "academy:reso_ore", "academy:imagsil_ore")),
            Map.entry("c/tags/item/ingots.json", List.of("#c:ingots/constraint_metal", "#c:ingots/imag_silicon")),
            Map.entry("c/tags/item/ingots/constraint_metal.json", List.of("academy:constraint_ingot")),
            Map.entry("c/tags/item/ingots/imag_silicon.json", List.of("academy:imag_silicon_ingot")),
            Map.entry("c/tags/item/plates.json", List.of("#c:plates/constraint_metal")),
            Map.entry("c/tags/item/plates/constraint_metal.json", List.of("academy:constraint_plate")),
            Map.entry("c/tags/item/gems.json", List.of("#c:gems/imag_crystal", "#c:gems/resonant_crystal")),
            Map.entry("c/tags/item/gems/imag_crystal.json", List.of("academy:crystal_low")),
            Map.entry("c/tags/item/gems/resonant_crystal.json", List.of("academy:reso_crystal")),
            Map.entry("academy/tags/entity_type/vector_affectable.json", List.of()),
            Map.entry("academy/tags/entity_type/vector_immune.json", List.of()),
            Map.entry("academy/tags/block/mine_ray_immune.json", List.of()),
            Map.entry("academy/tags/block/metal/normal.json", List.of()),
            Map.entry("academy/tags/block/metal/weak.json", List.of()));

    @Test void packTagsExistAndReferenceRealIds() throws Exception {
        Set<String> blocks = registered("world/AcademyBlocks.java");
        Set<String> items = registered("world/AcademyItems.java");
        for (var entry : EXPECTED.entrySet()) {
            Path file = DATA.resolve(entry.getKey());
            assertTrue(Files.isRegularFile(file), "missing " + file);
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            assertFalse(json.get("replace").getAsBoolean(), file + " must merge, not replace");
            List<String> values = json.getAsJsonArray("values").asList().stream()
                    .map(v -> v.getAsString()).toList();
            assertEquals(entry.getValue(), values, file.toString());
            String registry = entry.getKey().split("/")[2];
            for (String value : values) {
                if (value.startsWith("#")) {
                    String[] id = value.substring(1).split(":", 2);
                    assertTrue(Files.isRegularFile(DATA.resolve(id[0] + "/tags/" + registry + "/" + id[1] + ".json")),
                            file + " references missing tag " + value);
                } else {
                    Set<String> known = "block".equals(registry) ? blocks : items;
                    assertTrue(value.startsWith("academy:") && known.contains(value.substring(8)),
                            file + " references unknown " + registry + " " + value);
                }
            }
        }
    }

    @Test void isAbilityCoversEveryAcademyDamageType() throws Exception {
        Set<String> expected = new HashSet<>();
        try (Stream<Path> files = Files.list(DATA.resolve("academy/damage_type"))) {
            files.forEach(f -> expected.add("academy:" + f.getFileName().toString().replace(".json", "")));
        }
        assertFalse(expected.isEmpty());
        JsonObject json = JsonParser.parseString(Files.readString(
                DATA.resolve("academy/tags/damage_type/is_ability.json"))).getAsJsonObject();
        Set<String> actual = new HashSet<>();
        json.getAsJsonArray("values").forEach(v -> actual.add(v.getAsString()));
        assertEquals(expected, actual);
    }

    @Test void codeReadsTheModTags() throws Exception {
        String vector = Files.readString(JAVA.resolve("skill/passive/PassiveSkillEventHandler.java"));
        assertTrue(vector.contains("\"vector_immune\"") && vector.contains("\"vector_affectable\""));
        int immune = vector.indexOf("entity.getType().is(VECTOR_IMMUNE)) return false;");
        int affectable = vector.indexOf("entity.getType().is(VECTOR_AFFECTABLE)");
        assertTrue(immune > 0 && immune < affectable, "vector_immune must be checked before the opt-in");
        assertTrue(vector.contains("!source.getType().is(VECTOR_IMMUNE)") && vector.contains("target.getType().is(VECTOR_IMMUNE)"),
                "reflected counter-damage must skip vector_immune entities");
        String mineRay = Files.readString(JAVA.resolve("skill/ability/meltdowner/AbstractMineRayEffect.java"));
        assertTrue(mineRay.contains("\"mine_ray_immune\"") && mineRay.contains("if(state.is(MINE_RAY_IMMUNE))return false;"));
        String metal = Files.readString(JAVA.resolve("skill/ability/electromaster/ElectromasterMetalTargets.java"));
        assertTrue(metal.contains("\"metal/normal\"") && metal.contains("\"metal/weak\""));
        assertTrue(metal.contains("state.is(NORMAL_METAL_TAG)") && metal.contains("ACConfig.Server.normalMetalBlocks()"));
        assertTrue(metal.contains("state.is(WEAK_METAL_TAG)") && metal.contains("ACConfig.Server.weakMetalBlocks()"));
    }

    private static Set<String> registered(String source) throws Exception {
        Set<String> ids = new HashSet<>();
        Matcher m = REGISTER.matcher(Files.readString(JAVA.resolve(source)));
        while (m.find()) ids.add(m.group(1));
        assertFalse(ids.isEmpty(), source);
        return ids;
    }
}
