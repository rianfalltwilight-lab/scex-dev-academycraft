package com.mohistmc.academy.skill.ability.telekinesis;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.ability.SkillRaycast;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AbilityMutationService;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import com.mohistmc.academy.skill.AcceptedAbilityDamage;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-authoritative runtime for Telekinesis passives. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class TelekinesisPassiveHandler {
    private static final String SHADOW_TAG = "academy_liquid_shadow";
    private static final String OWNER_PREFIX = "academy_liquid_shadow_owner_";
    private static final Map<UUID, Anchor> HARDENED = new HashMap<>();
    private static final Map<UUID, ShadowSession> SHADOWS = new HashMap<>();

    private record ShadowSession(ServerPlayer owner, LiquidShadowEntity shadow) {}

    private record Anchor(ResourceKey<Level> dimension, Vec3 position) {}
    private TelekinesisPassiveHandler() {}

    private static boolean passive(ServerPlayer player, PlayerAbilityData data, String id) {
        return data.isAbilityActive() && learned(player, data, id);
    }

    /** ExtraAcC Insulation only checked isSkillLearned, not activation. */
    private static boolean learned(ServerPlayer player, PlayerAbilityData data, String id) {
        return player.isAlive() && !AbilityInterferenceService.isInterfered(player)
                && data.getCurrentAbility() == AbilityCategory.TELEKINESIS
                && data.hasLearnedSkill(id) && DynamicSkillRules.enabled(id);
    }

    /** Called exclusively by AcademyDamageHelper, so ordinary melee/environment damage is not mislabeled. */
    public static float mitigateAbilityDamage(ServerPlayer attacker, Entity target, float amount) {
        if (!(target instanceof ServerPlayer defender) || attacker == defender
                || !Float.isFinite(amount) || amount <= 0) return amount;
        PlayerAbilityData defense = defender.getData(AcademyAttachments.PLAYER_ABILITY);
        if (!learned(defender, defense, "insulation")) return amount;
        PlayerAbilityData offense = attacker.getData(AcademyAttachments.PLAYER_ABILITY);
        AbilityCategory source = offense.getCurrentAbility();
        float proficiency = defense.getProficiency("insulation");
        float multiplier = source == AbilityCategory.MELTDOWNER ? 2.5F
                : source == AbilityCategory.ELECTROMASTER ? 2.0F : 1.0F;
        float reduction = TelekinesisRules.insulationReduction(proficiency, multiplier);
        if (!DynamicSkillRules.tryPay(defense, "insulation", 100F * amount, 0)) return amount;
        float reduced = amount * (1F - Math.clamp(reduction, 0, 0.95F));
        if (reduced < amount) {
            AbilityMutationService.addSkillExp(defender, defense, "insulation", amount * 0.002f);
            EffectHelper.psychoBurst(defender.serverLevel(), defender.getX(),
                    defender.getY() + defender.getBbHeight() * 0.55, defender.getZ(), 2, 0.18);
        }
        return reduced;
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        Anchor anchor = HARDENED.get(player.getUUID());
        if (anchor != null && (!passive(player, data, "psycho_harden")
                || !anchor.dimension().equals(player.level().dimension()))) {
            stopHarden(player, data, true);
            anchor = null;
        }
        if (anchor != null) {
            if (!DynamicSkillRules.tryPay(data, "psycho_harden", 2F, 0)) {
                stopHarden(player, data, true);
                tickShadow(player, data);
                return;
            }
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 39, 9,
                    false, false, false));
            DynamicSkillRules.addExp(player, data, "psycho_harden", 0.00001F);
            if (player.tickCount % 10 == 0) {
                EffectHelper.psychoBurst(player.serverLevel(), player.getX(),
                        player.getY() + player.getBbHeight() * 0.5, player.getZ(), 2, 0.12);
            }
        }
        tickShadow(player, data);
    }

    /** Invoked only after the complete public damage-veto, shield and iframe stages. */
    public static void incomingDamage(AcceptedAbilityDamage event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) return;
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.LIGHTNING_BOLT)
                && learned(player, data, "insulation")) {
            float original = event.getAmount();
            if (DynamicSkillRules.tryPay(data, "insulation", 100F * original, 0)) {
                float reduction = 0.20F + 0.20F * data.getProficiency("insulation");
                event.setAmount(original * (1F - reduction));
                DynamicSkillRules.addExp(player, data, "insulation", original * 0.002F);
            }
        }
        if (!HARDENED.containsKey(player.getUUID())) return;
        if (!passive(player, data, "psycho_harden")) {
            stopHarden(player, data, true); // ExtraAcC s_terminate always set 160
            return;
        }
        float blocked = event.getAmount();
        float p = data.getProficiency("psycho_harden");
        if (!DynamicSkillRules.tryPay(data, "psycho_harden",
                blocked * (60F - 30F * p), blocked * 0.1F)) {
            stopHarden(player, data, true);
            return;
        }
        event.setAmount(0);
        AbilityMutationService.addSkillExp(player, data, "psycho_harden", blocked * 0.0005f);
        EffectHelper.psychoBurst(player.serverLevel(), player.getX(),
                player.getY() + player.getBbHeight() * 0.5, player.getZ(), 6, 0.25);
    }

    public static boolean isHardened(ServerPlayer player) {
        return HARDENED.containsKey(player.getUUID());
    }

    public static boolean togglePsychoHarden(ServerPlayer player, PlayerAbilityData data) {
        if (isHardened(player)) {
            stopHarden(player, data, true);
            return true;
        }
        float p = data.getProficiency("psycho_harden");
        if (!DynamicSkillRules.tryPay(data, "psycho_harden",
                1500F - 500F * p, 100F - 50F * p)) return false;
        HARDENED.put(player.getUUID(), new Anchor(player.level().dimension(), player.position()));
        EffectHelper.psychoBurst(player.serverLevel(), player.getX(), player.getY() + 0.9,
                player.getZ(), 12, 0.45);
        return true;
    }

    private static void stopHarden(ServerPlayer player, PlayerAbilityData data, boolean cooldown) {
        if (!HARDENED.containsKey(player.getUUID())) return;
        HARDENED.remove(player.getUUID());
        if (cooldown && !data.isDevMode()) data.setCooldown("psycho_harden", 160);
        // ExtraAcC removed the hardening Slowness X first.  Without that, 1.21 keeps the
        // stronger effect running and only stores Slowness V as hidden, freezing the player.
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 4,
                false, false, false));
    }

    public static boolean hasLiquidShadow(ServerPlayer player) { return resolveShadow(player) != null; }

    public static boolean hasWaterBucket(ServerPlayer player) {
        if (player.getAbilities().instabuild) return true;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            if (player.getInventory().getItem(slot).is(Items.WATER_BUCKET)) return true;
        return player.getOffhandItem().is(Items.WATER_BUCKET);
    }

    /** Toggle seam kept public for dedicated-server GameTests and skill dispatch. */
    public static boolean toggleLiquidShadow(ServerPlayer player) {
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (!passive(player, data, "liquid_shadow")) return false;
        LiquidShadowEntity existing = resolveShadow(player);
        if (existing != null) {
            stopShadow(player, data, true);
            return true;
        }
        if (!hasWaterBucket(player)) return false;
        float p = data.getProficiency("liquid_shadow");
        float startCp = 2000F - 1000F * p;
        float startOverload = 300F - 100F * p;
        if (!DynamicSkillRules.canPay(data, "liquid_shadow", startCp, startOverload)) return false;
        ServerLevel level = player.serverLevel();
        LiquidShadowEntity shadow = com.mohistmc.academy.world.AcademyEntities.LIQUID_SHADOW.get().create(level);
        if (shadow == null) return false;
        shadow.setOwnerUUID(player.getUUID());
        Vec3 spawn = shadowTarget(player);
        shadow.setPos(spawn.x, spawn.y, spawn.z);
        shadow.setPersistenceRequired();
        shadow.setCustomName(Component.translatable(
                "item.academy.factor_telekinesis.liquid_shadow"));
        shadow.setCustomNameVisible(false);
        shadow.addTag(SHADOW_TAG);
        shadow.addTag(ownerTag(player.getUUID()));
        shadow.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60 * 60,
                0, false, false));
        var maxHealth = shadow.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(100.0);
        shadow.setHealth(100.0F);
        ShadowSession session = new ShadowSession(player, shadow);
        SHADOWS.put(player.getUUID(), session);
        // Entity insertion can be vetoed by another mod. Commit resources only after acceptance;
        // if a callback changes the owner's resources, remove the uncommitted follower.
        if (!level.addFreshEntity(shadow) || !shadow.isAlive()
                || SHADOWS.get(player.getUUID()) != session || player.level() != level
                || !passive(player, data, "liquid_shadow") || !hasWaterBucket(player)
                || !DynamicSkillRules.tryPay(data, "liquid_shadow", startCp, startOverload)) {
            SHADOWS.remove(player.getUUID(), session);
            shadow.discard();
            return false;
        }
        if (!player.getAbilities().instabuild) {
            consumeWaterBucket(player);
        }
        EffectHelper.psychoBurst(level, spawn.x, spawn.y + 0.8, spawn.z, 16, 0.4);
        AbilityMutationService.addSkillExp(player, data, "liquid_shadow", 0.002f);
        return true;
    }

    private static void tickShadow(ServerPlayer player, PlayerAbilityData data) {
        LiquidShadowEntity shadow = resolveShadow(player);
        if (shadow == null) return;
        if (shadow.isInWater()) {
            stopShadow(player, data, true);
            return;
        }
        if (!passive(player, data, "liquid_shadow")) {
            stopShadow(player, data, true); // ExtraAcC s_terminate always set 100
            return;
        }
        if (!DynamicSkillRules.tryPay(data, "liquid_shadow", 1F, 0)) {
            stopShadow(player, data, true);
            return;
        }
        shadow.setRemainingFireTicks(0);
        if (player.tickCount % 2 == 0) {
            player.serverLevel().sendParticles(ParticleTypes.SPLASH,
                    shadow.getX(), shadow.getY() + 0.9, shadow.getZ(),
                    5, 0.32, 0.75, 0.32, 0.02);
        }
        DynamicSkillRules.addExp(player, data, "liquid_shadow", 0.0001F);
    }

    static boolean validShadowVictim(LiquidShadowEntity shadow, LivingEntity victim) {
        ServerPlayer player = shadowOwner(shadow);
        return player != null && victim != null && victim != player && victim != shadow && victim.isAlive()
                && victim.level() == shadow.level() && !player.isAlliedTo(victim)
                && (!(victim instanceof net.minecraft.world.entity.player.Player targetPlayer)
                    || player.canHarmPlayer(targetPlayer))
                && com.mohistmc.academy.skill.AcademyDamageHelper.allowsTarget(victim);
    }

    static ServerPlayer shadowOwner(LiquidShadowEntity shadow) {
        ShadowSession session = SHADOWS.get(shadow.abilityOwner());
        return session != null && session.shadow() == shadow && session.owner().isAlive()
                && !session.owner().isRemoved() && session.owner().level() == shadow.level() ? session.owner() : null;
    }

    static boolean attackWithShadow(LiquidShadowEntity shadow, LivingEntity victim) {
        ServerPlayer player = shadowOwner(shadow);
        if (player == null || !validShadowVictim(shadow, victim)) return false;
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (!passive(player, data, "liquid_shadow")) { endShadow(shadow); return false; }
        float rawDamage = 20F + 10F * data.getProficiency("liquid_shadow");
        if (!DynamicSkillRules.tryPay(data, "liquid_shadow", 5F * rawDamage, 0)) {
            stopShadow(player, data, true);
            return false;
        }
        boolean accepted = com.mohistmc.academy.skill.AcademyDamageHelper.hurt(player, victim,
                com.mohistmc.academy.skill.SkillDamageSource.attack(player), DynamicSkillRules.damage("liquid_shadow", rawDamage));
        if (accepted) AbilityMutationService.addSkillExp(player, data, "liquid_shadow", rawDamage * .0001F);
        return accepted;
    }

    static void endShadow(LiquidShadowEntity shadow) {
        ServerPlayer owner = shadowOwner(shadow);
        if (owner != null) stopShadow(owner, owner.getData(AcademyAttachments.PLAYER_ABILITY), true);
        else shadow.discard();
    }

    static boolean isCurrentShadow(LiquidShadowEntity shadow) {
        ServerPlayer owner = shadowOwner(shadow);
        return owner != null && passive(owner, owner.getData(AcademyAttachments.PLAYER_ABILITY), "liquid_shadow");
    }

    private static LiquidShadowEntity resolveShadow(ServerPlayer player) {
        ShadowSession session = SHADOWS.get(player.getUUID());
        if (session == null) return null;
        LiquidShadowEntity shadow = session.shadow();
        if (session.owner() == player && shadow.isAlive() && !shadow.isRemoved()
                && shadow.level() == player.level()) return shadow;
        SHADOWS.remove(player.getUUID(), session);
        shadow.discard();
        return null;
    }

    private static Vec3 shadowTarget(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0, look.z);
        if (horizontal.lengthSqr() < 1.0e-5) horizontal = new Vec3(0, 0, 1);
        return player.position().subtract(horizontal.normalize().scale(1.6)).add(0, 0.1, 0);
    }

    private static String ownerTag(UUID owner) { return OWNER_PREFIX + owner; }

    private static void consumeWaterBucket(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.WATER_BUCKET)) {
                if (stack.getCount() == 1) {
                    player.getInventory().setItem(slot, new ItemStack(Items.BUCKET));
                    return;
                }
                stack.shrink(1);
                if (!player.getInventory().add(new ItemStack(Items.BUCKET)))
                    player.drop(new ItemStack(Items.BUCKET), false);
                return;
            }
        }
    }

    private static void stopShadow(ServerPlayer player, PlayerAbilityData data, boolean cooldown) {
        ShadowSession session = SHADOWS.remove(player.getUUID());
        if (session == null) return;
        session.shadow().discard();
        if (cooldown && !data.isDevMode()) data.setCooldown("liquid_shadow", 100);
    }

    private static void clearPlayer(Entity entity) {
        HARDENED.remove(entity.getUUID());
        ShadowSession session = SHADOWS.remove(entity.getUUID());
        // A direct session reference still identifies the old-dimension entity after transfer.
        if (session != null) session.shadow().discard();
    }

    private static UUID legacyShadowOwner(Entity entity) {
        if (!(entity instanceof Drowned drowned) || !drowned.isNoAi()
                || !entity.getTags().contains(SHADOW_TAG)) return null;
        UUID owner = null;
        for (String tag : entity.getTags()) {
            if (!tag.startsWith(OWNER_PREFIX)) continue;
            try {
                String suffix = tag.substring(OWNER_PREFIX.length());
                UUID parsed = UUID.fromString(suffix);
                if (!parsed.toString().equals(suffix) || owner != null) return null;
                owner = parsed;
            } catch (IllegalArgumentException ignored) { return null; }
        }
        return owner;
    }

    /** Retire old saved no-AI shadows; ordinary Drowned entities do not match this migration. */
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.loadedFromDisk()
                && legacyShadowOwner(event.getEntity()) != null) {
            // This event can precede chunk promotion to FULL. Do not query/load the world here.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof LiquidShadowEntity shadow)) return;
        ShadowSession session = SHADOWS.get(shadow.abilityOwner());
        if (session == null || session.shadow() != shadow) return;
        SHADOWS.remove(shadow.abilityOwner(), session);
        var data = session.owner().getData(AcademyAttachments.PLAYER_ABILITY);
        if (!data.isDevMode()) data.setCooldown("liquid_shadow", 100);
    }

    @SubscribeEvent public static void drops(LivingDropsEvent event) {
        if (event.getEntity() instanceof LiquidShadowEntity) event.setCanceled(true);
    }
    public static void onConfirmedDeath(net.minecraft.world.entity.LivingEntity entity) {
        if (entity instanceof LiquidShadowEntity shadow) {
            ShadowSession session = SHADOWS.get(shadow.abilityOwner());
            if (session != null && session.shadow() == shadow) {
                SHADOWS.remove(shadow.abilityOwner(), session);
                var data = session.owner().getData(AcademyAttachments.PLAYER_ABILITY);
                if (!data.isDevMode()) data.setCooldown("liquid_shadow", 100);
            }
        } else if (entity instanceof ServerPlayer) clearPlayer(entity);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clearPlayer(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clearPlayer(event.getEntity()); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { clearPlayer(event.getEntity()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        HARDENED.clear();
        // Level teardown owns entity removal; forget the bounded owner sessions.
        SHADOWS.clear();
    }
}
