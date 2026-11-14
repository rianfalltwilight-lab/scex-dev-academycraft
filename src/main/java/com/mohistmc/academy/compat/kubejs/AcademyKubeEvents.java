package com.mohistmc.academy.compat.kubejs;

import com.mohistmc.academy.api.event.AbilityEvents;
import dev.latvian.mods.kubejs.entity.KubeEntityEvent;
import dev.latvian.mods.kubejs.entity.KubeLivingEntityEvent;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventTargetType;
import dev.latvian.mods.kubejs.event.TargetedEventHandler;
import dev.latvian.mods.kubejs.player.KubePlayerEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * {@code AcademyEvents.*} server events, bridged from the mod's NeoForge events.
 * <pre>
 * AcademyEvents.skillCast('railgun', e => { if (inSpawn(e.player)) e.cancel() })
 * AcademyEvents.skillDamage(e => { if (e.attacker?.type == 'minecraft:blaze') e.amount *= 2 })
 * AcademyEvents.mobSkillCast(e => e.cancel())
 * </pre>
 */
public final class AcademyKubeEvents {
    public static final EventGroup GROUP = EventGroup.of("AcademyEvents");

    public static final TargetedEventHandler<String> SKILL_CAST =
            GROUP.server("skillCast", () -> SkillCastEvent.class).hasResult().supportsTarget(EventTargetType.STRING);
    public static final EventHandler SKILL_DAMAGE =
            GROUP.server("skillDamage", () -> SkillDamageEvent.class).hasResult();
    public static final TargetedEventHandler<String> MOB_SKILL_CAST =
            GROUP.server("mobSkillCast", () -> MobSkillCastEvent.class).hasResult().supportsTarget(EventTargetType.STRING);
    public static final TargetedEventHandler<String> SKILL_LEARNED =
            GROUP.server("skillLearned", () -> SkillLearnedEvent.class).supportsTarget(EventTargetType.STRING);
    public static final EventHandler LEVEL_CHANGED = GROUP.server("levelChanged", () -> LevelChangedEvent.class);
    public static final EventHandler OVERLOADED = GROUP.server("overloaded", () -> OverloadedEvent.class);

    private AcademyKubeEvents() {}

    /** Called once from the plugin; listeners only do work when scripts listen. */
    static void bridge() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, AbilityEvents.SkillCast.class, event -> {
            if (event.player.level().isClientSide() || !SKILL_CAST.hasListeners()) return;
            SKILL_CAST.post(new SkillCastEvent(event.player, event.skill.getId()), event.skill.getId()).applyCancel(event);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, AbilityEvents.SkillDamage.class, event -> {
            if (!SKILL_DAMAGE.hasListeners()) return;
            SKILL_DAMAGE.post(new SkillDamageEvent(event)).applyCancel(event);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, AbilityEvents.MobSkillCast.class, event -> {
            if (!MOB_SKILL_CAST.hasListeners()) return;
            MOB_SKILL_CAST.post(new MobSkillCastEvent(event), event.skill.getId()).applyCancel(event);
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, AbilityEvents.SkillLearned.class, event -> {
            if (event.player.level().isClientSide() || !SKILL_LEARNED.hasListeners()) return;
            SKILL_LEARNED.post(new SkillLearnedEvent(event.player, event.skill.getId()), event.skill.getId());
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, AbilityEvents.LevelChanged.class, event -> {
            if (event.player.level().isClientSide() || !LEVEL_CHANGED.hasListeners()) return;
            LEVEL_CHANGED.post(new LevelChangedEvent(event.player, event.oldLevel, event.level));
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, AbilityEvents.Overloaded.class, event -> {
            if (event.player.level().isClientSide() || !OVERLOADED.hasListeners()) return;
            OVERLOADED.post(new OverloadedEvent(event.player, event.overload, event.maximum));
        });
    }

    /** A player is about to start a skill. Cancel to refuse it (nothing is paid). */
    public static final class SkillCastEvent implements KubePlayerEvent {
        private final Player player; private final String skill;
        SkillCastEvent(Player player, String skill) { this.player = player; this.skill = skill; }
        @Override public Player getEntity() { return player; }
        public String getSkill() { return skill; }
    }

    /** An ability hit; {@code amount} is final damage after config and attributes. Cancel drops the hit. */
    public static final class SkillDamageEvent implements KubeEntityEvent {
        private final AbilityEvents.SkillDamage source;
        SkillDamageEvent(AbilityEvents.SkillDamage source) { this.source = source; }
        @Override public Entity getEntity() { return source.target; }
        public Entity getTarget() { return source.target; }
        public @Nullable LivingEntity getAttacker() { return source.attacker; }
        public DamageSource getSource() { return source.source; }
        public float getOriginalAmount() { return source.originalAmount; }
        public float getAmount() { return source.getAmount(); }
        public void setAmount(float amount) { source.setAmount(amount); }
    }

    /** A non-player entity is about to cast a skill. Cancel to refuse it. */
    public static final class MobSkillCastEvent implements KubeLivingEntityEvent {
        private final AbilityEvents.MobSkillCast source;
        MobSkillCastEvent(AbilityEvents.MobSkillCast source) { this.source = source; }
        @Override public LivingEntity getEntity() { return source.caster; }
        public String getSkill() { return source.skill.getId(); }
        public float getProficiency() { return source.proficiency; }
        public @Nullable LivingEntity getTarget() { return source.target; }
    }

    public static final class SkillLearnedEvent implements KubePlayerEvent {
        private final Player player; private final String skill;
        SkillLearnedEvent(Player player, String skill) { this.player = player; this.skill = skill; }
        @Override public Player getEntity() { return player; }
        public String getSkill() { return skill; }
    }

    public static final class LevelChangedEvent implements KubePlayerEvent {
        private final Player player; private final int oldLevel, level;
        LevelChangedEvent(Player player, int oldLevel, int level) { this.player = player; this.oldLevel = oldLevel; this.level = level; }
        @Override public Player getEntity() { return player; }
        public int getOldAbilityLevel() { return oldLevel; }
        public int getAbilityLevel() { return level; }
    }

    public static final class OverloadedEvent implements KubePlayerEvent {
        private final Player player; private final float overload, maximum;
        OverloadedEvent(Player player, float overload, float maximum) { this.player = player; this.overload = overload; this.maximum = maximum; }
        @Override public Player getEntity() { return player; }
        public float getOverload() { return overload; }
        public float getMaximum() { return maximum; }
    }
}
