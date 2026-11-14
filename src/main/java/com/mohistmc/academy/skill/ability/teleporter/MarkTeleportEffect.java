package com.mohistmc.academy.skill.ability.teleporter;

import com.mohistmc.academy.skill.ability.LoadedRaycast;

import com.mohistmc.academy.api.mob.MobCastContext;
import com.mohistmc.academy.api.mob.MobCastable;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.mohistmc.academy.world.AcademyParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** 1.0.7 MTContext: the server advances a visible mark by two blocks per held tick, then teleports on key-up. */
public final class MarkTeleportEffect implements ChargingSkillEffect, MobCastable {
    private static final Map<UUID,State> ACTIVE=new ConcurrentHashMap<>();
    private record State(ResourceKey<Level> dimension,Vec3 destination){}
    public static double computeMaxDistance(float exp,float cp,int ticks){return Math.min((ticks+1)*2.0,Math.min(lerpf(25,60,exp),cp/lerpf(12,4,exp)));}
    @Override public String getId(){return "mark_teleport";}
    @Override public boolean canStartCharging(ServerPlayer p,PlayerAbilityData d){return com.mohistmc.academy.config.DynamicSkillRules.enabled(getId());}
    @Override public void onChargingStart(ServerPlayer p,PlayerAbilityData d){ACTIVE.put(p.getUUID(),new State(p.level().dimension(),p.position()));}
    @Override public boolean onChargingTick(ServerPlayer p,PlayerAbilityData d,int ticks){
        State old=ACTIVE.get(p.getUUID());if(old==null||!old.dimension.equals(p.level().dimension()))return false;
        float exp=d.getProficiency(getId());
        double distance=d.isDevMode()?Math.min((ticks+1)*2.0,lerpf(25,60,exp)):computeMaxDistance(exp,d.getCurrentCp(),ticks);
        Vec3 dest=FlashingTargeting.destination(p,p.getLookAngle(),distance);
        if(dest==null)return true;
        ACTIVE.put(p.getUUID(),new State(old.dimension,dest));
        return true;
    }
    @Override public TickResult getTickResult(ServerPlayer p,PlayerAbilityData d,int ticks){return onChargingTick(p,d,ticks)?TickResult.CONTINUE:TickResult.ABORT_RESOURCE;}
    @Override public boolean tryRelease(ServerPlayer p,PlayerAbilityData d,int ticks){
        State state=ACTIVE.remove(p.getUUID());if(state==null||!state.dimension.equals(p.level().dimension()))return false;
        float exp=d.getProficiency(getId());double cpb=lerpf(12,4,exp);
        Vec3 dest=state.destination; // immutable last server-validated marker; do not release at a newer view ray
        BlockPos block=BlockPos.containing(dest);
        if(!LoadedRaycast.isLoaded(p.serverLevel(), block)||!TeleportDestinations.isSafe(p,p.serverLevel(),dest))return false;
        double distance=p.position().distanceTo(dest);if(distance<3||!com.mohistmc.academy.config.DynamicSkillRules.payForced(d,getId(),(float)(distance*cpb),lerpf(40,20,exp)))return false;
        ServerLevel level=p.serverLevel();
        TeleportSkillHelper.teleport(p,dest);
        level.playSound(null,p.getX(),p.getY(),p.getZ(),AcademySounds.TP_TP,SoundSource.PLAYERS,.5f,1f);
        com.mohistmc.academy.config.DynamicSkillRules.addExp(p,d,getId(),(float)(.00018*distance));return true;
    }
    @Override public void onChargingRelease(ServerPlayer p,PlayerAbilityData d,int ticks){tryRelease(p,d,ticks);}
    @Override public void onChargingAbort(ServerPlayer p,PlayerAbilityData d){ACTIVE.remove(p.getUUID());}
    @Override public void execute(ServerPlayer p,PlayerAbilityData d){}
    @Override public boolean appliesBaseResourceCost(){return false;}
    @Override public boolean grantsActivationProficiency(){return false;}
    @Override public int getMinChargeTicks(){return 0;}
    @Override public int getMaxChargeTicks(){return 30;}
    @Override public int getMaxChargeTicks(PlayerAbilityData data){return Math.max(1,(int)Math.ceil(lerpf(25,60,data.getProficiency(getId()))/2.0));}
    @Override public int getSessionTimeoutTicks(PlayerAbilityData data){return Integer.MAX_VALUE;}
    @Override public int getCooldownTicks(float exp,int ticks){return(int)lerpf(30,0,exp);}

    /**
     * Mob path: an instant blink (no mark), only for casters that may move themselves.  With a target the
     * mob lands behind it, else beside it, else in front of it; without one, at the furthest spot along its
     * aim.  Every spot is within the legacy range, inside the world border and loaded, collision-free and
     * on solid footing; with none the cast fails.
     */
    @Override public boolean mobCast(MobCastContext c){
        if(!c.mayMoveCaster())return false;
        LivingEntity caster=c.caster();
        Vec3 dest=mobDestination(c);
        if(dest==null)return false;
        if(caster.isPassenger())caster.stopRiding();
        caster.teleportTo(dest.x,dest.y,dest.z);
        caster.fallDistance=0;
        if(caster instanceof Mob mob)mob.getNavigation().stop();
        c.level().playSound(null,caster.getX(),caster.getY(),caster.getZ(),AcademySounds.TP_TP,SoundSource.HOSTILE,.5f,1f);
        return true;
    }
    @Override public int mobCooldownTicks(float p){return getCooldownTicks(p,0);}

    private static Vec3 mobDestination(MobCastContext c){
        LivingEntity caster=c.caster();ServerLevel level=c.level();
        double range=lerpf(25,60,c.proficiency());
        LivingEntity target=c.target();
        if(target!=null){
            Vec3 away=new Vec3(target.getX()-caster.getX(),0,target.getZ()-caster.getZ());
            if(away.lengthSqr()<1.0e-6)away=new Vec3(c.look().x,0,c.look().z);
            if(away.lengthSqr()<1.0e-6)away=new Vec3(0,0,1);
            away=away.normalize();
            Vec3 side=new Vec3(-away.z,0,away.x);
            double gap=(target.getBbWidth()+caster.getBbWidth())/2+.6;
            for(Vec3 offset:new Vec3[]{away.scale(gap),side.scale(gap),side.scale(-gap),away.scale(-gap)}){
                Vec3 feet=grounded(caster,level,target.position().add(offset));
                if(feet!=null&&feet.distanceTo(caster.position())<=range)return feet;
            }
            return null;
        }
        Vec3 eye=c.eye(),look=c.look();
        BlockHitResult wall=LoadedRaycast.clip(level,new ClipContext(eye,eye.add(look.scale(range)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,caster));
        double max=wall.getType()==HitResult.Type.MISS?range:eye.distanceTo(wall.getLocation());
        for(double d=max-.5;d>=3;d-=.5){
            Vec3 feet=grounded(caster,level,caster.position().add(look.scale(d)));
            if(feet!=null)return feet;
        }
        return null;
    }

    /** The column's nearest safe standing spot at whole-block heights around {@code around}, or null. */
    private static Vec3 grounded(LivingEntity caster,ServerLevel level,Vec3 around){
        double base=Math.floor(around.y);
        for(int dy:new int[]{0,1,-1,-2}){
            Vec3 feet=new Vec3(around.x,base+dy,around.z);
            if(LoadedRaycast.isLoaded(level,BlockPos.containing(feet))&&TeleportDestinations.isSafeGrounded(caster,level,feet))return feet;
        }
        return null;
    }

}

