package com.mohistmc.academy.data;

import com.google.gson.JsonElement;
import com.mohistmc.academy.config.BalanceProfile;
import com.mohistmc.academy.skill.SkillRegistry;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * {@code data/<namespace>/academy_balance/<profile>/skills/<skill>.json}: the balance profile layer.
 *
 * <p>Files use the {@code academy_skills} format.  The layer for the configured {@link BalanceProfile}
 * sits between the built-in 1.0.7 numbers and the {@code academy_skills/} overrides, so switching
 * {@code balance.profile} takes effect at once, without {@code /reload}.  AcademyCraft ships the
 * {@code modern} files; packs may add to or replace them like any datapack file.
 */
public final class BalanceProfileData extends SyncedJsonData {
    public static final BalanceProfileData INSTANCE = new BalanceProfileData();
    private static volatile Map<BalanceProfile, Map<String, SkillTuningData.SkillOverride>> skills = Map.of();

    private BalanceProfileData() { super("balance_profile", "academy_balance"); }

    /** The profile layer for {@code skillId}, or {@code null}. */
    public static SkillTuningData.SkillOverride skill(BalanceProfile profile, String skillId) {
        Map<String, SkillTuningData.SkillOverride> layer = skills.get(profile);
        return layer == null || skillId == null ? null : layer.get(skillId);
    }

    @Override
    protected void accept(Map<ResourceLocation, JsonElement> files) {
        skills = parse(files, id -> SkillRegistry.getSkill(id) != null);
    }

    /** Installs {@code files} as a datapack reload would; an empty map restores the built-in defaults. */
    public static void applyForTest(Map<ResourceLocation, JsonElement> files) {
        INSTANCE.accept(files);
    }

    /** The installed files, to restore after {@link #applyForTest}. */
    public static Map<ResourceLocation, JsonElement> installedForTest() {
        return INSTANCE.installed();
    }

    /** Splits {@code <profile>/skills/<skill>} paths per profile; unknown profiles and kinds are skipped. */
    public static Map<BalanceProfile, Map<String, SkillTuningData.SkillOverride>> parse(
            Map<ResourceLocation, JsonElement> files, Predicate<String> knownSkill) {
        Map<BalanceProfile, Map<ResourceLocation, JsonElement>> perProfile = new EnumMap<>(BalanceProfile.class);
        files.forEach((file, json) -> {
            String[] parts = file.getPath().split("/", 3);
            BalanceProfile profile = parts.length == 3 ? BalanceProfile.byId(parts[0]) : null;
            if (profile == null || !"skills".equals(parts[1])) {
                LOGGER.warn("Ignoring balance file {}: expected <profile>/skills/<skill> with profile one of modern, legacy",
                        file);
                return;
            }
            perProfile.computeIfAbsent(profile, p -> new HashMap<>())
                    .put(ResourceLocation.fromNamespaceAndPath(file.getNamespace(), parts[2]), json);
        });
        Map<BalanceProfile, Map<String, SkillTuningData.SkillOverride>> out = new EnumMap<>(BalanceProfile.class);
        perProfile.forEach((profile, profileFiles) -> out.put(profile, SkillTuningData.parse(profileFiles, knownSkill)));
        return Map.copyOf(out);
    }
}
