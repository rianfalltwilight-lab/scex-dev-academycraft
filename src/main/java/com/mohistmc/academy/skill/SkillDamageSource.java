package com.mohistmc.academy.skill;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Ability sources keep their native mitigation profile without borrowing a held weapon's Breach. */
public final class SkillDamageSource extends DamageSource {
    public static final ResourceKey<DamageType> ABILITY = key("ability");
    public static final ResourceKey<DamageType> MAGIC = key("ability_magic");
    public static final ResourceKey<DamageType> THROWN = key("ability_thrown");
    public static final ResourceKey<DamageType> DROWNING = key("ability_drowning");

    private SkillDamageSource(Holder<DamageType> type, @Nullable Entity direct, @Nullable Entity owner) {
        super(type, direct, owner);
    }

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("academy", path));
    }

    private static SkillDamageSource create(ServerPlayer caster, ResourceKey<DamageType> type,
            @Nullable Entity direct, @Nullable Entity owner) {
        return new SkillDamageSource(caster.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(type), direct, owner);
    }

    public static SkillDamageSource attack(ServerPlayer caster) { return create(caster, ABILITY, caster, caster); }
    public static SkillDamageSource magic(ServerPlayer caster) { return create(caster, MAGIC, caster, caster); }
    public static SkillDamageSource thrown(ServerPlayer caster, Entity projectile) { return create(caster, THROWN, projectile, caster); }

    /** Plasma Cannon keeps its existing native explosion classification and direct entity. */
    public static SkillDamageSource explosion(ServerPlayer caster) {
        return create(caster, DamageTypes.PLAYER_EXPLOSION, caster, caster);
    }

    /** Preserve the three released piercing-projectile registry IDs and direct entity. */
    public static SkillDamageSource projectile(ServerPlayer caster, Entity projectile, ResourceKey<DamageType> type) {
        return create(caster, type, projectile, caster);
    }

    /** Self suffocation has no hostile owner; other victims must see the caster for native PvP/kill credit. */
    public static SkillDamageSource drowning(ServerPlayer caster, boolean self) {
        return create(caster, DROWNING, null, self ? null : caster);
    }

    @Override public @Nullable ItemStack getWeaponItem() { return null; }

    @Override public Component getLocalizedDeathMessage(LivingEntity victim) {
        String path = typeHolder().unwrapKey().orElseThrow().location().getPath();
        String key = "death.attack.academy." + path;
        Entity credited = getEntity() != null ? getEntity() : getDirectEntity();
        if (credited == null) credited = victim.getKillCredit();
        return credited == null ? Component.translatable(key, victim.getDisplayName())
                : Component.translatable(key + ".player", victim.getDisplayName(), credited.getDisplayName());
    }
}
