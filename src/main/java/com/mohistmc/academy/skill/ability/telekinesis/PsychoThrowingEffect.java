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
import net.minecraft.world.item.Items;

/** Throws one cobblestone projectile; etched stone is faster, stronger and cheaper. */
public final class PsychoThrowingEffect implements DynamicOneShotSkillEffect, MobCastable {
    private record Ammo(ItemStack stack, boolean etched) {}

    @Override public String getId() { return "psycho_throwing"; }
    @Override public float rawCp(float p) { return 400 - 200 * Math.clamp(p, 0, 1); }
    @Override public float rawOverload(float p) { return 30 - 10 * Math.clamp(p, 0, 1); }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        Ammo ammo = findAmmo(player);
        if (ammo == null) return false;
        float p = data.getProficiency(getId());
        float multiplier = ammo.etched ? 1.0F : 1.5F;
        return DynamicSkillRules.canPay(data, getId(), rawCp(p) * multiplier,
                rawOverload(p) * multiplier);
    }

    @Override
    public boolean executeAndReport(ServerPlayer player, PlayerAbilityData data) {
        Ammo ammo = findAmmo(player);
        if (ammo == null) return false;
        float p = data.getProficiency(getId());
        float multiplier = ammo.etched ? 1.0F : 1.5F;
        var resourcesBefore = data.captureDynamicResources();
        if (!DynamicSkillRules.tryPay(data, getId(), rawCp(p) * multiplier,
                rawOverload(p) * multiplier)) return false;
        var resourcesPaid = data.captureDynamicResources();
        ItemStack returned = ammo.stack.isEmpty() ? new ItemStack(AcademyItems.ETCHED_COBBLESTONE.get())
                : ammo.stack.copyWithCount(1);
        if (!perform(player, data, ammo.etched, returned, ammo.stack)) {
            data.rollbackDynamicPayment(resourcesBefore, resourcesPaid);
            return false;
        }
        return true;
    }

    @Override public void execute(ServerPlayer player, PlayerAbilityData data) {
        executeAndReport(player, data);
    }

    private static Ammo findAmmo(ServerPlayer player) {
        ItemStack stack = PsychoAmmo.find(player, item -> item.is(AcademyItems.ETCHED_COBBLESTONE.get()) || item.is(Items.COBBLESTONE));
        return stack == null ? null : new Ammo(stack, stack.isEmpty() || stack.is(AcademyItems.ETCHED_COBBLESTONE.get()));
    }

    private static boolean perform(ServerPlayer player, PlayerAbilityData data, boolean etched,
                                   ItemStack returned, ItemStack consumed) {
        float p = data.getProficiency("psycho_throwing");
        ServerLevel level = player.serverLevel();
        if (!PsychoAmmo.launch(player, consumed, returned, p, false)) return false;
        launched(level, player, etched);
        if (!data.isDevMode()) DynamicSkillRules.addExp(player, data, "psycho_throwing",
                0.002F - 0.001F * p);
        return true;
    }

    /** Shared launch feedback; a player's sound source is PLAYERS, as before. */
    private static void launched(ServerLevel level, LivingEntity caster, boolean etched) {
        level.playSound(null, caster.blockPosition(), SoundEvents.TRIDENT_THROW.value(),
                caster.getSoundSource(), 0.8F, etched ? 1.2F : 0.9F);
    }

    /**
     * Mob path: a virtual plain cobblestone (the unetched, weaker stone). No inventory is searched or
     * consumed and the projectile never drops or returns an item, so mobs cannot be farmed for stone.
     * No payment or experience.
     */
    @Override
    public boolean mobCast(MobCastContext context) {
        LivingEntity caster = context.caster();
        ServerLevel level = context.level();
        var projectile = AcademyEntities.PSYCHO_STONE.get().create(level);
        if (projectile == null) return false;
        projectile.prepare(caster, new ItemStack(Items.COBBLESTONE), context.proficiency(),
                context.eye(), context.look());
        projectile.setVirtualAmmo(true);
        projectile.setMobBreaksBlocks(context.mayDestroyBlocks());
        if (!level.addFreshEntity(projectile) || projectile.isRemoved() || projectile.level() != level
                || !caster.isAlive() || caster.level() != level) {
            projectile.discard();
            return false;
        }
        projectile.arm();
        launched(level, caster, false);
        return true;
    }

    @Override public int mobCooldownTicks(float p) { return getCooldownTicks(p); }

    @Override public int getCooldownTicks(float p) { return (int) (40 - 20 * Math.clamp(p, 0, 1)); }
}
