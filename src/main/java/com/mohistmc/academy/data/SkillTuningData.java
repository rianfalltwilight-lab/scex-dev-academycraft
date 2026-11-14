package com.mohistmc.academy.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * {@code data/academy/academy_skills/<skill>.json}: per-skill tuning overrides (see docs/MODPACK-SKILLS.md).
 *
 * <p>Every field is optional; an absent field keeps the built-in value.  Multipliers and switches combine
 * with the server config {@code skill.overrides} lines in {@code LegacyAbilityRules.parseSkill}; level,
 * prerequisites, tree position and base costs are read by the {@link Skill} getters.
 */
public final class SkillTuningData extends SyncedJsonData {
    public static final SkillTuningData INSTANCE = new SkillTuningData();
    private static volatile Map<String, SkillOverride> overrides = Map.of();

    private SkillTuningData() { super("skill_tuning", "academy_skills"); }

    /** One skill's datapack override; {@code null} components are "not set", multipliers default to 1. */
    public record SkillOverride(Boolean enabled, Boolean destroyBlocks, float damage, float cp, float overload,
                                float exp, float cooldown, Integer level, List<Skill.Prerequisite> prerequisites,
                                Double treeX, Double treeY, Float baseCp, Float baseOverload) {}

    /** The installed override for {@code skillId}, or {@code null}. */
    public static SkillOverride override(String skillId) {
        return skillId == null ? null : overrides.get(skillId);
    }

    @Override
    protected void accept(Map<ResourceLocation, JsonElement> files) {
        overrides = parse(files, id -> SkillRegistry.getSkill(id) != null);
    }

    /** Installs {@code files} as a datapack reload would; an empty map restores the built-in defaults. */
    public static void applyForTest(Map<ResourceLocation, JsonElement> files) {
        INSTANCE.accept(files);
    }

    /** Parses the files; other namespaces, unknown skills and bad prerequisite entries are skipped with a warning. */
    public static Map<String, SkillOverride> parse(Map<ResourceLocation, JsonElement> files, Predicate<String> knownSkill) {
        Map<String, SkillOverride> out = new HashMap<>();
        files.forEach((file, json) -> {
            if (!AcademyCraft.MODID.equals(file.getNamespace())) {
                LOGGER.warn("Ignoring skill tuning {}: only the '{}' namespace is read", file, AcademyCraft.MODID);
                return;
            }
            String id = file.getPath();
            if (!knownSkill.test(id)) {
                LOGGER.warn("Ignoring skill tuning {}: unknown skill '{}'", file, id);
                return;
            }
            if (json == null || !json.isJsonObject()) {
                LOGGER.warn("Ignoring skill tuning {}: not a JSON object", file);
                return;
            }
            out.put(id, parseOne(file, id, json.getAsJsonObject(), knownSkill));
        });
        return Map.copyOf(out);
    }

    private static SkillOverride parseOne(ResourceLocation file, String id, JsonObject o, Predicate<String> knownSkill) {
        Double rawLevel = number(file, o, "level");
        Integer level = rawLevel == null ? null : (int) Math.max(1, Math.min(5, Math.round(rawLevel)));
        List<Skill.Prerequisite> prerequisites = null;
        if (o.has("prerequisites")) {
            JsonElement element = o.get("prerequisites");
            if (element.isJsonArray()) prerequisites = prerequisites(file, id, element.getAsJsonArray(), knownSkill);
            else LOGGER.warn("Skill tuning {}: 'prerequisites' must be an array", file);
        }
        Double baseCp = number(file, o, "base_cp"), baseOverload = number(file, o, "base_overload");
        return new SkillOverride(bool(file, o, "enabled"), bool(file, o, "destroy_blocks"),
                multiplier(file, o, "damage"), multiplier(file, o, "cp"), multiplier(file, o, "overload"),
                multiplier(file, o, "exp"), multiplier(file, o, "cooldown"), level, prerequisites,
                number(file, o, "tree_x"), number(file, o, "tree_y"),
                baseCp == null ? null : nonNegative(baseCp), baseOverload == null ? null : nonNegative(baseOverload));
    }

    private static List<Skill.Prerequisite> prerequisites(ResourceLocation file, String id, JsonArray array,
                                                          Predicate<String> knownSkill) {
        List<Skill.Prerequisite> out = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                LOGGER.warn("Skill tuning {}: skipping prerequisite {} (not an object)", file, element);
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            if (entry.has("any_level")) {
                Double level = number(file, entry, "any_level");
                if (level == null || level < 1 || level > 5) {
                    LOGGER.warn("Skill tuning {}: skipping prerequisite {} (any_level must be 1..5)", file, entry);
                    continue;
                }
                out.add(new Skill.Prerequisite("any_level_" + Math.round(level), 0));
                continue;
            }
            JsonElement skillJson = entry.get("skill");
            String skill = skillJson != null && skillJson.isJsonPrimitive() ? skillJson.getAsString() : null;
            if (skill == null || skill.equals(id) || !knownSkill.test(skill)) {
                LOGGER.warn("Skill tuning {}: skipping prerequisite {} (unknown or self skill)", file, entry);
                continue;
            }
            Double proficiency = number(file, entry, "proficiency");
            float required = proficiency == null ? 0 : (float) Math.max(0, Math.min(1, proficiency));
            out.add(new Skill.Prerequisite(skill, required));
        }
        return List.copyOf(out);
    }

    private static Boolean bool(ResourceLocation file, JsonObject o, String key) {
        if (!o.has(key)) return null;
        JsonElement e = o.get(key);
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
        LOGGER.warn("Skill tuning {}: '{}' must be true or false", file, key);
        return null;
    }

    private static Double number(ResourceLocation file, JsonObject o, String key) {
        if (!o.has(key)) return null;
        JsonElement e = o.get(key);
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            double v = e.getAsDouble();
            if (Double.isFinite(v)) return v;
        }
        LOGGER.warn("Skill tuning {}: '{}' must be a finite number", file, key);
        return null;
    }

    private static Float nonNegative(double v) {
        float f = (float) Math.max(0, v);
        return Float.isFinite(f) ? f : null;
    }

    private static float multiplier(ResourceLocation file, JsonObject o, String key) {
        Double v = number(file, o, key);
        if (v == null) return 1;
        Float f = nonNegative(v);
        return f == null ? 1 : f;
    }
}
