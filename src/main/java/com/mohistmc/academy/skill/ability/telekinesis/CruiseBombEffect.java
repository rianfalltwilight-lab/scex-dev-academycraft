package com.mohistmc.academy.skill.ability.telekinesis;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.api.mob.MobCastContext;
import com.mohistmc.academy.api.mob.MobCastable;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import com.mohistmc.academy.skill.ability.aerohand.AeroBehaviorMath;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.entity.CruiseBombEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Paid water-bomb controller; each orb has its own real flight and collision lifecycle.  A mob cast
 * runs a bounded, unpaid session keyed by the mob that only locks onto the mob's target.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class CruiseBombEffect implements SkillEffect, MobCastable {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** A mob session's lifetime; its orbs are discarded when it ends. */
    private static final int MOB_SESSION_TICKS = 100;
    private static final class Session {
        private final ResourceKey<Level> dimension;
        private final float proficiency;
        private final int maximumOrbs;
        private final List<CruiseBombEntity> orbs = new ArrayList<>();
        private long nextSummon;
        /** Mob sessions only (null/unused for players): caster, locked target, end time, griefing decision. */
        private final LivingEntity mob;
        private LivingEntity mobTarget;
        private final long mobUntil;
        private final boolean mobBreaksBlocks;
        private Session(ServerPlayer owner, float proficiency) {
            dimension=owner.level().dimension(); this.proficiency=proficiency;
            maximumOrbs=AeroBehaviorMath.cruiseBombOrbCount(proficiency);
            nextSummon=owner.serverLevel().getGameTime();
            mob=null; mobUntil=0; mobBreaksBlocks=false;
        }
        private Session(MobCastContext context) {
            dimension=context.level().dimension(); proficiency=context.proficiency();
            maximumOrbs=AeroBehaviorMath.cruiseBombOrbCount(proficiency);
            nextSummon=context.level().getGameTime();
            mob=context.caster(); mobTarget=context.target();
            mobUntil=nextSummon+MOB_SESSION_TICKS; mobBreaksBlocks=context.mayDestroyBlocks();
        }
    }
    @Override public String getId() { return "cruise_bomb"; }
    @Override public boolean canActivate(ServerPlayer player,PlayerAbilityData data) {
        return SESSIONS.containsKey(player.getUUID()) || (player.hasInfiniteMaterials() || findWaterBucket(player)>=0)
                && DynamicSkillRules.canPay(data,getId(),200,20);
    }
    @Override public boolean appliesBaseResourceCost(){return false;}
    @Override public boolean grantsActivationProficiency(){return false;}
    @Override public boolean managesOwnCooldown() { return true; }
    @Override public boolean executeAndReport(ServerPlayer player,PlayerAbilityData data) {
        if(SESSIONS.containsKey(player.getUUID())) { terminate(player,data,true);return true; }
        int water=findWaterBucket(player);
        if(water<0&&!player.hasInfiniteMaterials() || !DynamicSkillRules.tryPay(data,getId(),200,20))return false;
        if(!player.hasInfiniteMaterials())player.getInventory().setItem(water,new ItemStack(Items.BUCKET));
        SESSIONS.put(player.getUUID(),new Session(player,data.getProficiency(getId())));
        player.serverLevel().playSound(null,player.blockPosition(),SoundEvents.BUCKET_EMPTY,SoundSource.PLAYERS,.8F,1.25F);
        return true;
    }
    @Override public void execute(ServerPlayer player,PlayerAbilityData data){executeAndReport(player,data);}
    private static int findWaterBucket(ServerPlayer player) {
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++)
            if(player.getInventory().getItem(slot).is(Items.WATER_BUCKET))return slot;
        return -1;
    }
    public static boolean owns(LivingEntity player,CruiseBombEntity orb) {
        Session session=SESSIONS.get(player.getUUID());
        return session!=null&&session.dimension.equals(orb.level().dimension())&&session.orbs.contains(orb);
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        Session session=SESSIONS.get(player.getUUID());
        if(session==null)return;
        var data=player.getData(AcademyAttachments.PLAYER_ABILITY);
        if(!valid(player,data,session)||!DynamicSkillRules.tryPay(data,"cruise_bomb",2-session.proficiency,.05F)) {
            terminate(player,data,true);return;
        }
        DynamicSkillRules.addExp(player,data,"cruise_bomb",.00002F);
        session.orbs.removeIf(Entity::isRemoved);
        long now=player.serverLevel().getGameTime();
        if(session.orbs.size()<session.maximumOrbs&&now>=session.nextSummon) {
            var before=data.captureDynamicResources();
            if(!DynamicSkillRules.tryPay(data,"cruise_bomb",50,0)){terminate(player,data,true);return;}
            var paid=data.captureDynamicResources();
            var orb=new CruiseBombEntity(AcademyEntities.CRUISE_BOMB.get(),player.level());
            orb.prepare(player,session.proficiency);
            session.orbs.add(orb); // A reservation prevents a nested callback from adding another orb.
            session.nextSummon=now+11;
            if(!player.serverLevel().addFreshEntity(orb)||!orb.isAlive()||SESSIONS.get(player.getUUID())!=session
                    ||!valid(player,data,session)) {
                session.orbs.remove(orb);orb.discard();data.rollbackDynamicPayment(before,paid);
                terminate(player,data,true);return;
            }
            orb.arm();
            DynamicSkillRules.addExp(player,data,"cruise_bomb",.0001F);
        }
        AABB area=new AABB(player.position(),player.position()).inflate(4);
        for(Entity target:player.serverLevel().getEntities(player,area,e->targetable(player,e))) {
            if(session.orbs.stream().anyMatch(orb->orb.target()==target))continue;
            CruiseBombEntity free=session.orbs.stream().filter(orb->orb.isAlive()&&orb.target()==null).findFirst().orElse(null);
            if(free==null)return;
            if(!DynamicSkillRules.tryPay(data,"cruise_bomb",50,0)){terminate(player,data,true);return;}
            free.lock(target);
            DynamicSkillRules.addExp(player,data,"cruise_bomb",.0002F);
        }
    }
    private static boolean valid(ServerPlayer player,PlayerAbilityData data,Session session) {
        return player.isAlive()&&!player.isRemoved()&&!AbilityInterferenceService.isInterfered(player)
                &&session.dimension.equals(player.level().dimension())&&data.isAbilityActive()
                &&data.getCurrentAbility()==AbilityCategory.TELEKINESIS&&data.hasLearnedSkill("cruise_bomb")
                &&DynamicSkillRules.enabled("cruise_bomb");
    }
    private static boolean targetable(ServerPlayer player,Entity entity) {
        if(!entity.isAlive()||entity==player||entity instanceof CruiseBombEntity)return false;
        if(entity instanceof OwnableEntity owned&&player.getUUID().equals(owned.getOwnerUUID()))return false;
        if(entity instanceof Projectile projectile) {
            Entity owner=projectile.getOwner();return owner!=player&&(owner==null||!player.isAlliedTo(owner));
        }
        return entity instanceof LivingEntity living&&!player.isAlliedTo(living)&&AcademyDamageHelper.allowsTarget(living);
    }
    @Override public int getCooldownTicks(float proficiency){return 40;}

    /**
     * Mob path: no bucket, no CP, no experience. Spawns the first orb now and the rest every 11 ticks
     * (up to the player's orb count) for {@link #MOB_SESSION_TICKS}; free orbs lock only onto the
     * target once it is within the player's 4-block lock box. A recast while active retargets.
     */
    @Override public boolean mobCast(MobCastContext context) {
        LivingEntity caster=context.caster(), target=context.target();
        if(target==null||!target.isAlive()||target.level()!=context.level()
                ||!AcademyDamageHelper.allowsTarget(caster,target))return false;
        Session existing=SESSIONS.get(caster.getUUID());
        if(existing!=null) {
            if(existing.mob!=caster)return false;
            existing.mobTarget=target;return true;
        }
        Session session=new Session(context);
        SESSIONS.put(caster.getUUID(),session);
        context.level().playSound(null,caster.blockPosition(),SoundEvents.BUCKET_EMPTY,caster.getSoundSource(),.8F,1.25F);
        if(!summonForMob(context.level(),session)){clear(caster);return false;}
        return true;
    }
    // A mob session runs MOB_SESSION_TICKS; never let one caster stack overlapping sessions.
    @Override public int mobCooldownTicks(float proficiency){return Math.max(getCooldownTicks(proficiency),MOB_SESSION_TICKS);}
    /** Whether a mob-owned cruise session is running (tests, addons). */
    public static boolean hasMobSession(UUID owner) {
        Session session=SESSIONS.get(owner);
        return session!=null&&session.mob!=null;
    }
    private static boolean summonForMob(ServerLevel level,Session session) {
        LivingEntity mob=session.mob;
        var orb=new CruiseBombEntity(AcademyEntities.CRUISE_BOMB.get(),level);
        orb.prepare(mob,session.proficiency);
        orb.setMobBreaksBlocks(session.mobBreaksBlocks);
        session.orbs.add(orb);
        session.nextSummon=level.getGameTime()+11;
        if(!level.addFreshEntity(orb)||!orb.isAlive()||SESSIONS.get(mob.getUUID())!=session
                ||!mob.isAlive()||mob.level()!=level) {
            session.orbs.remove(orb);orb.discard();return false;
        }
        orb.arm();return true;
    }
    @SubscribeEvent public static void mobTick(LevelTickEvent.Post event) {
        if(SESSIONS.isEmpty()||!(event.getLevel() instanceof ServerLevel level))return;
        for(Session session:List.copyOf(SESSIONS.values())) {
            LivingEntity mob=session.mob;
            if(mob==null||!session.dimension.equals(level.dimension())||SESSIONS.get(mob.getUUID())!=session)continue;
            LivingEntity target=session.mobTarget;
            if(!mob.isAlive()||mob.isRemoved()||mob.level()!=level||level.getGameTime()>=session.mobUntil
                    ||!DynamicSkillRules.enabled("cruise_bomb")||target==null||!target.isAlive()||target.level()!=level) {
                clear(mob);continue;
            }
            session.orbs.removeIf(Entity::isRemoved);
            if(session.orbs.size()<session.maximumOrbs&&level.getGameTime()>=session.nextSummon
                    &&!summonForMob(level,session)) { clear(mob);continue; }
            if(!AcademyDamageHelper.allowsTarget(mob,target)
                    ||!new AABB(mob.position(),mob.position()).inflate(4).intersects(target.getBoundingBox())
                    ||session.orbs.stream().anyMatch(orb->orb.target()==target))continue;
            session.orbs.stream().filter(orb->orb.isAlive()&&orb.target()==null).findFirst()
                    .ifPresent(orb->orb.lock(target));
        }
    }
    /** A mob owner that dies, despawns or unloads takes its session and orbs with it at once. */
    @SubscribeEvent public static void mobLeft(EntityLeaveLevelEvent event) {
        if(SESSIONS.isEmpty()||event.getLevel().isClientSide()||event.getEntity() instanceof Player)return;
        Session session=SESSIONS.get(event.getEntity().getUUID());
        if(session!=null&&session.mob==event.getEntity())clear(event.getEntity());
    }
    private static void terminate(ServerPlayer player,PlayerAbilityData data,boolean cooldown) {
        if(clear(player)&&cooldown&&!data.isDevMode())data.setCooldown("cruise_bomb",40);
    }
    /** Activation-key "end special": recalls the orbs like a second cast. */
    public static boolean endFromActivateKey(ServerPlayer player,PlayerAbilityData data) {
        if(!SESSIONS.containsKey(player.getUUID()))return false;
        terminate(player,data,true);return true;
    }
    private static boolean clear(Entity entity) {
        Session session=SESSIONS.remove(entity.getUUID());
        if(session==null)return false;
        for(CruiseBombEntity orb:List.copyOf(session.orbs))orb.discard();
        session.orbs.clear();return true;
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){clear(event.getEntity());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){clear(event.getEntity());}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event){clear(event.getEntity());}
    public static void onConfirmedDeath(LivingEntity entity){clear(entity);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){SESSIONS.clear();}
}
