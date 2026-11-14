package com.mohistmc.academy.api.mob;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Attachment {@code academy:mob_ability}: which skills a non-player entity knows (skill id to proficiency
 * 0..1) and until which game time each is cooling down.  Settable from NBT, e.g.
 * {@code /summon zombie ~ ~ ~ {"neoforge:attachments":{"academy:mob_ability":{skills:{thunder_bolt:0.8f}}}}}.
 */
public final class MobAbilityData {
    public static final Codec<MobAbilityData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("skills", Map.of()).forGetter(d -> d.skills),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("cooldowns", Map.of()).forGetter(d -> d.cooldownUntil)
    ).apply(instance, MobAbilityData::new));

    private final Map<String, Float> skills;
    private final Map<String, Long> cooldownUntil;

    public MobAbilityData() { this(Map.of(), Map.of()); }

    private MobAbilityData(Map<String, Float> skills, Map<String, Long> cooldowns) {
        this.skills = new HashMap<>();
        skills.forEach((id, value) -> { if (id != null && Float.isFinite(value)) this.skills.put(id, Math.clamp(value, 0F, 1F)); });
        this.cooldownUntil = new HashMap<>(cooldowns);
    }

    public Map<String, Float> skills() { return Collections.unmodifiableMap(skills); }

    public @Nullable Float proficiency(String skillId) { return skills.get(skillId); }

    public void setSkill(String skillId, float proficiency) {
        if (Float.isFinite(proficiency)) skills.put(skillId, Math.clamp(proficiency, 0F, 1F));
    }

    public boolean removeSkill(String skillId) {
        cooldownUntil.remove(skillId);
        return skills.remove(skillId) != null;
    }

    public void clear() { skills.clear(); cooldownUntil.clear(); }

    public boolean isOnCooldown(String skillId, long gameTime) {
        Long until = cooldownUntil.get(skillId);
        return until != null && until > gameTime;
    }

    public long cooldownRemaining(String skillId, long gameTime) {
        Long until = cooldownUntil.get(skillId);
        return until == null ? 0 : Math.max(0, until - gameTime);
    }

    public void setCooldown(String skillId, long gameTime, int ticks) {
        if (ticks <= 0) cooldownUntil.remove(skillId);
        else cooldownUntil.put(skillId, gameTime + ticks);
    }

    public void clearCooldowns() { cooldownUntil.clear(); }
}
