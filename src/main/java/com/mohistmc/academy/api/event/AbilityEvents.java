package com.mohistmc.academy.api.event;

import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

/** Public, player-observable ability lifecycle events (the modern counterpart of 1.0.7's ability events). */
public final class AbilityEvents {
    private AbilityEvents() {}
    public abstract static class AbilityEvent extends Event {
        public final Player player;
        protected AbilityEvent(Player player) { this.player = player; }
        public PlayerAbilityData getAbilityData() { return player.getData(com.mohistmc.academy.skill.AcademyAttachments.PLAYER_ABILITY); }
    }
    public static final class Activate extends AbilityEvent { public Activate(Player p){super(p);} }
    public static final class Deactivate extends AbilityEvent { public Deactivate(Player p){super(p);} }
    public static final class CategoryChanged extends AbilityEvent {
        public final AbilityCategory oldCategory, category;
        public CategoryChanged(Player p, AbilityCategory oldCategory, AbilityCategory category){super(p);this.oldCategory=oldCategory;this.category=category;}
    }
    public static final class LevelChanged extends AbilityEvent {
        public final int oldLevel, level;
        public LevelChanged(Player p,int oldLevel,int level){super(p);this.oldLevel=oldLevel;this.level=level;}
    }
    public static final class Overloaded extends AbilityEvent {
        public final float overload, maximum;
        public Overloaded(Player p,float overload,float maximum){super(p);this.overload=overload;this.maximum=maximum;}
    }
    public static final class PresetSwitched extends AbilityEvent {
        public final int oldPreset, preset;
        public PresetSwitched(Player p,int oldPreset,int preset){super(p);this.oldPreset=oldPreset;this.preset=preset;}
    }
    public static final class PresetUpdated extends AbilityEvent {
        public final int preset, slot; public final String oldSkillId, skillId;
        public PresetUpdated(Player p,int preset,int slot,String oldSkillId,String skillId){super(p);this.preset=preset;this.slot=slot;this.oldSkillId=oldSkillId;this.skillId=skillId;}
    }
    public static final class SkillExpAdded extends AbilityEvent {
        public final Skill skill; public final float amount, oldExp, exp;
        public SkillExpAdded(Player p,Skill skill,float amount,float oldExp,float exp){super(p);this.skill=skill;this.amount=amount;this.oldExp=oldExp;this.exp=exp;}
    }
    public static final class SkillExpChanged extends AbilityEvent {
        public final Skill skill; public final float oldExp, exp;
        public SkillExpChanged(Player p,Skill skill,float oldExp,float exp){super(p);this.skill=skill;this.oldExp=oldExp;this.exp=exp;}
    }
    /**
     * Fired on the server just before a player starts a skill (one-shot use, start of a charge or toggle,
     * Flashing, Location Teleport).  Cancel it to refuse the cast, e.g. inside a protected region; nothing
     * is paid and no cooldown is set.
     */
    public static final class SkillCast extends AbilityEvent implements net.neoforged.bus.api.ICancellableEvent {
        public final Skill skill;
        public SkillCast(Player p, Skill skill){super(p);this.skill=skill;}
    }

    /**
     * Fired for every hit of ability damage, after the config multipliers and the {@code academy:skill_damage}
     * / {@code academy:skill_resistance} attributes have been applied.  {@code getAmount}/{@code setAmount} change
     * the final damage; cancelling drops the hit.  {@code attacker} is null for self-inflicted or ownerless hits.
     */
    public static final class SkillDamage extends Event implements net.neoforged.bus.api.ICancellableEvent {
        public final @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity attacker;
        public final net.minecraft.world.entity.Entity target;
        public final net.minecraft.world.damagesource.DamageSource source;
        public final float originalAmount;
        private float amount;
        public SkillDamage(net.minecraft.world.entity.LivingEntity attacker, net.minecraft.world.entity.Entity target,
                           net.minecraft.world.damagesource.DamageSource source, float amount) {
            this.attacker=attacker;this.target=target;this.source=source;this.originalAmount=amount;this.amount=amount;
        }
        public float getAmount(){return amount;}
        public void setAmount(float amount){this.amount=Float.isFinite(amount)?Math.max(0,amount):0;}
    }
    /** Fired before a non-player entity casts a skill through AcademyMobAbilities; cancel to refuse it. */
    public static final class MobSkillCast extends Event implements net.neoforged.bus.api.ICancellableEvent {
        public final net.minecraft.world.entity.LivingEntity caster;
        public final Skill skill;
        public final float proficiency;
        public final @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity target;
        public MobSkillCast(net.minecraft.world.entity.LivingEntity caster, Skill skill, float proficiency,
                            net.minecraft.world.entity.LivingEntity target) {
            this.caster=caster;this.skill=skill;this.proficiency=proficiency;this.target=target;
        }
    }
    public static final class SkillLearned extends AbilityEvent {
        public final Skill skill;
        public SkillLearned(Player p,Skill skill){super(p);this.skill=skill;}
    }
}
