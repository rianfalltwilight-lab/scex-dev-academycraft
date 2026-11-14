package com.mohistmc.academy.skill;

import com.mohistmc.academy.config.ACConfig;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Final boundary for damage caused by AcademyCraft abilities only. */
public final class AcademyDamageHelper {
    private static final class Call {
        final net.minecraft.world.entity.LivingEntity attacker;
        final Entity target;
        final DamageSource source;
        final boolean ability;
        AbilityDamageFrames.Frame frame;
        Call(net.minecraft.world.entity.LivingEntity attacker, Entity target, DamageSource source, boolean ability) {
            this.attacker = attacker; this.target = target; this.source = source; this.ability = ability;
        }
    }
    private static final ThreadLocal<Deque<Call>> CALLS = ThreadLocal.withInitial(ArrayDeque::new);
    private AcademyDamageHelper() {}

    /** Ability damage from a caster: a player (PvP rules) or, for mob-cast skills, any living entity. */
    public static boolean hurt(net.minecraft.world.entity.LivingEntity attacker, Entity target, DamageSource source, float amount) {
        return allowsTarget(attacker, target) && valid(target, source, amount)
                && apply(attacker, target, source, amount, true);
    }

    /** Academy-owned item damage keeps the PvP policy without inheriting a surrounding ability call. */
    public static boolean hurtNonAbility(Entity target, DamageSource source, float amount) {
        return allowsTarget(target) && valid(target, source, amount) && apply(null, target, source, amount, false);
    }

    /** Explicit caster-only ability damage bypasses PvP and hostile Insulation. */
    public static boolean hurtSelf(ServerPlayer attacker, Entity target, DamageSource source, float amount) {
        return attacker != null && target == attacker && valid(target, source, amount)
                && apply(null, target, source, amount, true);
    }

    /** Whether {@code target.hurt(source)} is running for an ability hit right now (any frame). */
    public static boolean isAbilityHit(Entity target, DamageSource source) {
        Call call = CALLS.get().peek();
        return call != null && call.ability && call.target == target && call.source == source;
    }

    public static boolean isAbilityDamageInProgress() {
        Call call = CALLS.get().peek();
        return call != null && call.ability;
    }

    /** A nested ordinary damage callback must never inherit its caller's ability armour rules. */
    public static boolean isAbilityDamageInProgress(Entity target, DamageSource source) {
        Call call = CALLS.get().peek();
        return call != null && call.ability && call.frame != null && call.frame == AbilityDamageFrames.peek()
                && call.target == target && call.source == source;
    }
    static ServerPlayer hostileAbilityAttacker(Entity target, DamageSource source) {
        Call call = CALLS.get().peek();
        return call != null && call.ability && call.frame != null && call.frame == AbilityDamageFrames.peek()
                && call.target == target && call.source == source
                && call.attacker != target && call.attacker instanceof ServerPlayer player ? player : null;
    }

    static void bindDamageFrame(AbilityDamageFrames.Frame frame, DamageSource source) {
        Call call = CALLS.get().peek();
        if (call != null && call.frame == null && call.target == frame.player && call.source == source)
            call.frame = frame;
    }
    private static boolean valid(Entity target, DamageSource source, float amount) {
        return target != null && source != null && amount > 0 && Float.isFinite(amount);
    }

    private static boolean apply(net.minecraft.world.entity.LivingEntity attacker, Entity target, DamageSource source,
                                 float amount, boolean ability) {
        if (ability) {
            // Attribute stage (the per-skill and global config stage already ran in the effect), then the
            // public SkillDamage event. The attacker is the caster when known, else the source's owner.
            net.minecraft.world.entity.LivingEntity owner = attacker != null ? attacker
                    : source.getEntity() instanceof net.minecraft.world.entity.LivingEntity living
                    && living != target ? living : null;
            // Balance profile stage: level scaling, skill_power, mob attack scaling (all neutral in LEGACY).
            boolean reflected = com.mohistmc.academy.skill.passive.PassiveSkillEventHandler.isReflecting()
                    && !com.mohistmc.academy.data.ProgressionData.reflectionUsesOffenseScaling();
            float scaled = reflected ? amount * SkillDamageMath.targetMultiplier(target)
                    : SkillDamageMath.applyAttributes(CombatRules.offense(owner, amount), owner, target);
            if (!reflected) scaled = CombatRules.bossBonus(target, scaled);
            scaled = CombatRules.capVsPlayer(owner, target, scaled);
            var event = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new com.mohistmc.academy.api.event.AbilityEvents.SkillDamage(owner, target, source, scaled));
            if (event.isCanceled() || !(event.getAmount() > 0)) return false;
            amount = event.getAmount();
        }
        var calls = CALLS.get();
        calls.push(new Call(attacker, target, source, ability));
        try {
            // Defensive payment is deferred until the accepted-hit mixin, after all rejection gates.
            return target.hurt(source, amount);
        } finally {
            calls.pop();
            if (calls.isEmpty()) CALLS.remove();
        }
    }

    public static boolean allowsTarget(Entity target) {
        return allowsTarget(target, ACConfig.Server.pvpEnabled());
    }

    /**
     * Players follow the PvP setting. A mob caster never hits itself, its allies or team, its owner
     * (tamed mobs), or by default other mobs of its own type ({@code mobs.sameTypeFriendly}).
     */
    public static boolean allowsTarget(@org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity attacker,
                                       Entity target) {
        if (attacker == null || attacker instanceof Player) return allowsTarget(target);
        if (target == attacker || attacker.isAlliedTo(target) || target.isAlliedTo(attacker)) return false;
        if (attacker instanceof net.minecraft.world.entity.OwnableEntity ownable && ownable.getOwner() == target) return false;
        return !(com.mohistmc.academy.config.MobCastConfig.sameTypeFriendly() && target.getType() == attacker.getType());
    }

    static boolean allowsTarget(Entity target, boolean pvpEnabled) {
        return pvpEnabled || !(target instanceof Player);
    }
}
