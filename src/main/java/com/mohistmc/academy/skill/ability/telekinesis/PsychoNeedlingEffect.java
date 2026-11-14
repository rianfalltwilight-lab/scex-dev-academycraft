package com.mohistmc.academy.skill.ability.telekinesis;

import com.mohistmc.academy.api.mob.MobCastContext;
import com.mohistmc.academy.api.mob.MobCastable;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.DynamicOneShotSkillEffect;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.AcademyItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Consumes and returns one Academy needle while firing an armor-bypassing psycho projectile. */
public final class PsychoNeedlingEffect implements DynamicOneShotSkillEffect, MobCastable {
    @Override public String getId() { return "psycho_needling"; }
    @Override public float rawCp(float p) { return 800 - 400 * Math.clamp(p, 0, 1); }
    @Override public float rawOverload(float p) { return 20 - 10 * Math.clamp(p, 0, 1); }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        return findNeedle(player) != null && DynamicOneShotSkillEffect.super.canActivate(player, data);
    }

    @Override
    public boolean executeAndReport(ServerPlayer player, PlayerAbilityData data) {
        ItemStack needle = findNeedle(player);
        if (needle == null) return false;
        float p = data.getProficiency(getId());
        var resourcesBefore = data.captureDynamicResources();
        if (!DynamicSkillRules.tryPay(data, getId(), rawCp(p), rawOverload(p))) return false;
        var resourcesPaid = data.captureDynamicResources();
        ItemStack returned = needle.isEmpty() ? new ItemStack(AcademyItems.NEEDLE.get()) : needle.copyWithCount(1);
        if (!perform(player, data, returned, needle)) {
            data.rollbackDynamicPayment(resourcesBefore, resourcesPaid);
            return false;
        }
        return true;
    }

    private static ItemStack findNeedle(ServerPlayer player) {
        return PsychoAmmo.find(player, stack -> stack.is(AcademyItems.NEEDLE.get()));
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        executeAndReport(player, data);
    }

    private boolean perform(ServerPlayer player, PlayerAbilityData data, ItemStack returned, ItemStack consumed) {
        float p = data.getProficiency(getId());
        ServerLevel level = player.serverLevel();
        if (!PsychoAmmo.launch(player, consumed, returned, p, true)) return false;
        launched(level, player);
        if (!data.isDevMode()) DynamicSkillRules.addExp(player, data, getId(), 0.002F - 0.001F * p);
        return true;
    }

    /** Shared launch feedback; a player's sound source is PLAYERS, as before. */
    private static void launched(ServerLevel level, LivingEntity caster) {
        level.playSound(null, caster.blockPosition(), SoundEvents.ARROW_SHOOT,
                caster.getSoundSource(), 0.8F, 1.8F);
    }

    /**
     * Mob path: a virtual needle. No inventory is searched or consumed and the projectile never drops
     * or returns an item, so mobs cannot be farmed for needles. No payment or experience.
     */
    @Override
    public boolean mobCast(MobCastContext context) {
        LivingEntity caster = context.caster();
        ServerLevel level = context.level();
        var projectile = AcademyEntities.PSYCHO_NEEDLE.get().create(level);
        if (projectile == null) return false;
        projectile.prepare(caster, new ItemStack(AcademyItems.NEEDLE.get()), context.proficiency(),
                context.eye(), context.look());
        projectile.setVirtualAmmo(true);
        projectile.setMobBreaksBlocks(context.mayDestroyBlocks());
        if (!level.addFreshEntity(projectile) || projectile.isRemoved() || projectile.level() != level
                || !caster.isAlive() || caster.level() != level) {
            projectile.discard();
            return false;
        }
        projectile.arm();
        launched(level, caster);
        return true;
    }

    @Override public int mobCooldownTicks(float p) { return getCooldownTicks(p); }

    @Override public int getCooldownTicks(float p) { return (int) (20 - 10 * Math.clamp(p, 0, 1)); }
}
