package com.mohistmc.academy.skill;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.data.ProgressionData;
import com.mohistmc.academy.world.AcademyAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The balance profile's combat stage (ProgressionData {@code combat}); every rule is neutral in LEGACY.
 * {@link AcademyDamageHelper} runs {@link #offense}, then the skill_damage / skill_resistance attributes,
 * then {@link #bossBonus} and {@link #capVsPlayer}; {@link #incoming} lowers armour for ability hits.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class CombatRules {
    static final ResourceLocation LEVEL_HEALTH = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "level_health");
    private CombatRules() {}

    /** skill_power added flat, then the player's ability-level multiplier or the mob's attack scaling. */
    public static float offense(@Nullable LivingEntity owner, float amount) {
        if (owner == null) return amount;
        float raised = amount + skillPower(owner);
        if (owner instanceof Player player) {
            int level = player.getData(AcademyAttachments.PLAYER_ABILITY).getPlayerLevel();
            return finite(raised * ProgressionData.levelDamageMultiplier(level), amount);
        }
        // The mob_skills data map's damage_scale for the caster's type (1 when unlisted).
        float scale = com.mohistmc.academy.api.mob.MobSkillAi.profile(owner).damageScale();
        if (owner instanceof Mob mob && ProgressionData.mobAttackScaling()) scale *= attackRatio(mob);
        return finite(raised * scale, amount);
    }

    /** A mob's attack damage over its type's base value, so L2Hostility-style scaling reaches its casts. */
    static float attackRatio(Mob mob) {
        var attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null || !(attack.getBaseValue() > 0)) return 1;
        double ratio = attack.getValue() / attack.getBaseValue();
        return Double.isFinite(ratio) ? (float) Math.clamp(ratio, 1, 100) : 1;
    }

    static float skillPower(LivingEntity entity) {
        var instance = entity.getAttribute(AcademyAttributes.SKILL_POWER);
        if (instance == null) return 0;
        double value = instance.getValue();
        return Double.isFinite(value) ? (float) Math.max(0, value) : 0;
    }

    /** On {@code c:bosses}: + min(fraction x max health, cap x hit). */
    public static float bossBonus(Entity target, float amount) {
        float fraction = ProgressionData.bossMaxHealthFraction();
        if (fraction <= 0 || !(target instanceof LivingEntity living) || !target.getType().is(Tags.EntityTypes.BOSSES))
            return amount;
        float bonus = Math.min(fraction * living.getMaxHealth(), ProgressionData.bossBonusCap() * amount);
        return finite(amount + Math.max(0, bonus), amount);
    }

    /** A mob-cast hit on a player is at most {@code mob_hit_cap_vs_players} of the player's max health. */
    public static float capVsPlayer(@Nullable LivingEntity owner, Entity target, float amount) {
        float cap = ProgressionData.mobHitCapVsPlayers();
        if (cap <= 0 || owner == null || owner instanceof Player || !(target instanceof Player player)) return amount;
        return Math.min(amount, cap * player.getMaxHealth());
    }

    @SubscribeEvent
    public static void incoming(LivingIncomingDamageEvent event) {
        float pierce = ProgressionData.armorPierce();
        if (pierce <= 0 || !AcademyDamageHelper.isAbilityHit(event.getEntity(), event.getSource())) return;
        float keep = 1 - Math.min(1, pierce);
        event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> reduction * keep);
    }

    /** Level max health: a transient MAX_HEALTH modifier, refreshed once a second. */
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        applyLevelHealth(player);
    }

    public static void applyLevelHealth(ServerPlayer player) {
        var health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        float bonus = data.hasAbility() ? ProgressionData.levelMaxHealth(data.getPlayerLevel()) : 0;
        AttributeModifier current = health.getModifier(LEVEL_HEALTH);
        if (bonus <= 0) {
            if (current != null) health.removeModifier(LEVEL_HEALTH);
            return;
        }
        if (current != null && current.amount() == bonus) return;
        health.removeModifier(LEVEL_HEALTH);
        health.addTransientModifier(new AttributeModifier(LEVEL_HEALTH, bonus, AttributeModifier.Operation.ADD_VALUE));
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    /** Defensive cost per damage point: {@code damage ^ defense_cost_exponent} (1.0.7: linear). */
    public static float defenseDamage(float damage) {
        if (!(damage > 0)) return 0;
        float exponent = ProgressionData.defenseCostExponent();
        return exponent == 1f ? damage : finite((float) Math.pow(damage, exponent), damage);
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) && value >= 0 ? value : fallback;
    }
}
