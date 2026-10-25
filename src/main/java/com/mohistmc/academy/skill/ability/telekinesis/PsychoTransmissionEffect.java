package com.mohistmc.academy.skill.ability.telekinesis;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.ability.SkillRaycast;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Legacy toggle context: repeatedly transmits one sighted dropped stack into the inventory. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class PsychoTransmissionEffect implements SkillEffect {
    private record Session(ResourceKey<Level> dimension) {}
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    @Override public String getId() { return "psycho_transmission"; }
    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }
    @Override public boolean managesOwnCooldown() { return true; }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        return SESSIONS.containsKey(player.getUUID())
                || DynamicSkillRules.canPay(data, getId(), 0, 5F);
    }

    @Override public void execute(ServerPlayer player, PlayerAbilityData data) { executeAndReport(player, data); }

    @Override
    public boolean executeAndReport(ServerPlayer player, PlayerAbilityData data) {
        if (SESSIONS.containsKey(player.getUUID())) {
            terminate(player, data, true);
            return true;
        }
        if (!DynamicSkillRules.tryPay(data, getId(), 0, 5F)) return false;
        SESSIONS.put(player.getUUID(), new Session(player.level().dimension()));
        return true;
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return;
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (!valid(player, data, session)
                || !DynamicSkillRules.tryPay(data, "psycho_transmission", 0.5F, 0)) {
            terminate(player, data, true);
            return;
        }

        float proficiency = data.getProficiency("psycho_transmission");
        ItemEntity target = findTarget(player, proficiency);
        if (target == null || target.distanceToSqr(player) < 1) return;
        float distanceSquared = (float) target.distanceToSqr(player);
        float cp = (4F - 2F * proficiency) * distanceSquared;
        var resourcesBefore = data.captureDynamicResources();
        if (!DynamicSkillRules.tryPay(data, "psycho_transmission", cp, 2.5F)) return;
        var resourcesPaid = data.captureDynamicResources();

        int before = target.getItem().getCount();
        Vec3 from = player.getEyePosition();
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
        if (!hasInventorySpace(player,target.getItem())) {
            // The original ability still brings a stack to the caster when the inventory is full.
            // Keep its entity, components, owner and pickup delay intact; the move stays in a loaded chunk.
            var permission=net.neoforged.neoforge.event.EventHooks.fireItemPickupPre(target,player).canPickup();
            if (permission.isFalse() || SESSIONS.get(player.getUUID())!=session || !valid(player,data,session)
                    || !target.isAlive() || target.level()!=player.level() || target.hasPickUpDelay()
                    || target.getTarget()!=null&&!player.getUUID().equals(target.getTarget())
                    || !player.level().isLoaded(player.blockPosition())) {
                data.rollbackDynamicPayment(resourcesBefore,resourcesPaid);
                return;
            }
            target.setPos(player.position());
            target.setDeltaMovement(Vec3.ZERO);
            target.hasImpulse=true;
            EffectHelper.electricTether(player.serverLevel(),from,at);
            return;
        }
        target.playerTouch(player);
        int after = target.isAlive() ? target.getItem().getCount() : 0;
        if (before - after <= 0) {
            // The per-tick upkeep remains paid; only the unsuccessful pickup is rolled back.
            data.rollbackDynamicPayment(resourcesBefore, resourcesPaid);
            return;
        }
        ServerLevel level = player.serverLevel();
        EffectHelper.electricTether(level, from, at);
        EffectHelper.psychoBurst(level, at.x, at.y, at.z, 12, 0.35);
        level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS, 0.7F, 1.35F);
        DynamicSkillRules.addExp(player, data, "psycho_transmission", distanceSquared / 50_000F);
    }

    private static boolean valid(ServerPlayer player, PlayerAbilityData data, Session session) {
        return player.isAlive() && !player.isRemoved()
                && player.level().dimension().equals(session.dimension())
                && !AbilityInterferenceService.isInterfered(player)
                && data.isAbilityActive()
                && data.getCurrentAbility() == AbilityCategory.TELEKINESIS
                && data.hasLearnedSkill("psycho_transmission");
    }

    static ItemEntity findTarget(ServerPlayer player, float proficiency) {
        Vec3 from = player.getEyePosition();
        Vec3 intended = from.add(player.getLookAngle().normalize()
                .scale(TelekinesisRules.psychoTransmissionRange(proficiency)));
        UUID playerId = player.getUUID();
        var block=com.mohistmc.academy.skill.ability.LoadedRaycast.clip(player.level(),new net.minecraft.world.level.ClipContext(
                from,intended,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        Vec3 end=block.getType()==net.minecraft.world.phys.HitResult.Type.MISS?intended:block.getLocation();
        // 1.12 AxisAlignedBB.expand(1,1,1) expands the positive sides, unlike grow/inflate.
        var area=new net.minecraft.world.phys.AABB(from,end).expandTowards(1,1,1);
        return player.serverLevel().getEntitiesOfClass(ItemEntity.class,area,item->
                !item.getItem().isEmpty()&&!item.hasPickUpDelay()&&item.distanceToSqr(player)>=1
                        &&(item.getTarget()==null||playerId.equals(item.getTarget()))
                        &&com.mohistmc.academy.skill.ability.LoadedRaycast.clip(player.level(),new net.minecraft.world.level.ClipContext(
                                from,item.getBoundingBox().getCenter(),net.minecraft.world.level.ClipContext.Block.COLLIDER,
                                net.minecraft.world.level.ClipContext.Fluid.NONE,player)).getType()==net.minecraft.world.phys.HitResult.Type.MISS)
                .stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }

    private static boolean hasInventorySpace(ServerPlayer player, ItemStack incoming) {
        // Inventory.add uses empty main slots and existing mergeable stacks (including offhand),
        // not empty armor/offhand slots. Damaged stacks require an empty main slot.
        var inventory = player.getInventory();
        return player.hasInfiniteMaterials() || inventory.getFreeSlot() >= 0
                || !incoming.isDamaged() && inventory.getSlotWithRemainingSpace(incoming) >= 0;
    }

    private static void terminate(ServerPlayer player, PlayerAbilityData data, boolean cooldown) {
        if (SESSIONS.remove(player.getUUID()) != null && cooldown && !data.isDevMode()) {
            data.setCooldown("psycho_transmission", 20);
        }
    }

    private static void clear(Entity entity) { SESSIONS.remove(entity.getUUID()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { clear(event.getEntity()); }
    public static void onConfirmedDeath(net.minecraft.world.entity.LivingEntity entity) { clear(entity); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SESSIONS.clear(); }
    @Override public int getCooldownTicks(float proficiency) { return 20; }
}
