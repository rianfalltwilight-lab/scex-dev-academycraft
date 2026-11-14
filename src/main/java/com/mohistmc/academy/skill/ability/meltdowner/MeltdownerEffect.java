package com.mohistmc.academy.skill.ability.meltdowner;

import com.mohistmc.academy.skill.ability.LoadedRaycast;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.api.mob.MobCastContext;
import com.mohistmc.academy.api.mob.MobCastable;

import com.mohistmc.academy.entity.MeltdownBeamEntity;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.passive.PassiveDamageHelper;
import com.mohistmc.academy.skill.passive.PassiveSkillEventHandler;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.AcademySounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;
import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** Legacy charged, widening and block-destructive Meltdowner ray. */
public final class MeltdownerEffect implements ChargingSkillEffect, MobCastable {
 private static final Map<UUID,Float> OVERLOAD_FLOORS=new ConcurrentHashMap<>();
 private static final ResourceLocation CHARGE_SLOW=ResourceLocation.fromNamespaceAndPath("academy","meltdowner_charge_slow");
 @Override public String getId(){return "meltdowner";} @Override public boolean appliesBaseResourceCost(){return false;}
 @Override public boolean grantsActivationProficiency(){return false;}
 @Override public int getMinChargeTicks(){return 20;} @Override public int getMaxChargeTicks(){return 40;}
 @Override public int getSessionTimeoutTicks(PlayerAbilityData d){return 101;}
 @Override public TickResult getSessionTimeoutResult(ServerPlayer p,PlayerAbilityData d,int t){return TickResult.ABORT_RESOURCE;}
 private float startOl(PlayerAbilityData d){return lerpf(200,170,d.getProficiency(getId()));}
 @Override public boolean canStartCharging(ServerPlayer p,PlayerAbilityData d){return DynamicSkillRules.canPay(d,getId(),0,startOl(d));}
 @Override public void onChargingStart(ServerPlayer p,PlayerAbilityData d){if(DynamicSkillRules.tryPay(d,getId(),0,startOl(d)))OVERLOAD_FLOORS.put(p.getUUID(),d.getCurrentOverload());}
 @Override public boolean onChargingTick(ServerPlayer p,PlayerAbilityData d,int t){Float floor=OVERLOAD_FLOORS.get(p.getUUID());if(floor==null)return false;if(!d.isDevMode()&&d.getCurrentOverload()<floor)d.setCurrentOverload(floor);float cp=lerpf(10,15,d.getProficiency(getId()));if(!DynamicSkillRules.tryPay(d,getId(),cp,0))return false;applyChargeSlow(p,t);if((t&1)==0)com.mohistmc.academy.world.effect.EffectHelper.meltdownBurst(p.serverLevel(),p.getX(),p.getY()+.8,p.getZ(),3,.7);return true;}
 @Override public TickResult getTickResult(ServerPlayer p,PlayerAbilityData d,int t){return onChargingTick(p,d,t)?TickResult.CONTINUE:TickResult.ABORT_RESOURCE;}
 @Override public boolean canRelease(ServerPlayer p,PlayerAbilityData d,int t){return t>=20&&t<=100;}
 @Override public void onChargingRelease(ServerPlayer p,PlayerAbilityData d,int ticks){OVERLOAD_FLOORS.remove(p.getUUID());clearChargeSlow(p);fire(p,d,Math.min(40,ticks));}
 @Override public boolean tryRelease(ServerPlayer p,PlayerAbilityData d,int t){if(!canRelease(p,d,t))return false;onChargingRelease(p,d,t);return true;}
 @Override public void onChargingAbort(ServerPlayer p,PlayerAbilityData d){OVERLOAD_FLOORS.remove(p.getUUID());clearChargeSlow(p);} @Override public void execute(ServerPlayer p,PlayerAbilityData d){}
 private void fire(ServerPlayer player,PlayerAbilityData data,int charge){
  float exp=data.getProficiency(getId()),rate=lerpf(.8f,1.2f,(charge-20)/20f);
  ServerLevel level=player.serverLevel();
  ray(player,level,exp,charge,player.getEyePosition(),player.getLookAngle(),DynamicSkillRules.destroysBlocks(level,getId()));
  if(!data.isDevMode()){DynamicSkillRules.addExp(player,data,getId(),rate*.002f);data.setCooldown(getId(),(int)(rate*20*lerpf(15,7,exp)));}
 }

 /** Mob cast: a full 40-tick charge (slowed like a player), then the same ray; blocks only if it may grief. */
 @Override public boolean mobCast(MobCastContext c){clearChargeSlow(c.caster());
  ray(c.caster(),c.level(),c.proficiency(),Math.clamp(c.chargeTicks(),20,40),c.eye(),c.look(),c.mayDestroyBlocks());return true;}
 @Override public int mobChargeTicks(float p){return 40;}
 @Override public boolean mobChargeTick(MobCastContext c,int t){LivingEntity m=c.caster();applyChargeSlow(m,t);
  if((t&1)==0)com.mohistmc.academy.world.effect.EffectHelper.meltdownBurst(c.level(),m.getX(),m.getY()+.8,m.getZ(),3,.7);return true;}
 @Override public void mobChargeAborted(MobCastContext c){clearChargeSlow(c.caster());}
 /** The player's cooldown formula at the full 40-tick charge mobs use. */
 @Override public int mobCooldownTicks(float p){return (int)(lerpf(.8f,1.2f,1f)*20*lerpf(15,7,p));}

 /** The released ray, shared by players and mob casters. */
 private void ray(LivingEntity caster,ServerLevel level,float exp,int charge,Vec3 eye,Vec3 look,boolean mayDestroyBlocks){
  float rate=lerpf(.8f,1.2f,(charge-20)/20f);
  float startDamage=DynamicSkillRules.damage(getId(),rate*lerpf(18,50,exp));
  float energy=rate*lerpf(300,700,exp);double radius=lerpf(2,3,exp);
  Vec3 start=eye.add(look.scale(.1));Vec3 dir=look.normalize();

  double stopDistance=50;Set<Integer> hit=new HashSet<>();
  AABB bounds=new AABB(start,start.add(dir.scale(50))).inflate(radius*1.2);
  List<Entity> targets=new ArrayList<>(level.getEntities(caster,bounds,
          target->target!=caster&&target.isAlive()));
  targets.removeIf(target->{Vec3 delta=target.position().subtract(start);double axial=delta.dot(dir);return axial<0||axial>50||delta.subtract(dir.scale(axial)).length()>=radius*1.2;});
  targets.sort(Comparator.comparingDouble(target->target.distanceToSqr(caster)));
  for(Entity target:targets){
   Vec3 delta=target.position().subtract(start);float perpendicular=(float)delta.cross(dir).length();
   float damage=startDamage*lerpf(1,.2f,Math.min(50,perpendicular)/50f);
   if(target instanceof ServerPlayer reflector&&PassiveSkillEventHandler.reflectSpecialRay(reflector,caster,damage)){
    stopDistance=Math.min(stopDistance,Math.sqrt(target.distanceToSqr(caster)));
    float reflectedDamage=DynamicSkillRules.damage(getId(),.5f*lerpf(20,50,exp));
    reflected(level,caster,dir,reflector,reflectedDamage,hit);break;
   }
   if(hit.add(target.getId()))com.mohistmc.academy.skill.AcademyDamageHelper.hurt(caster,target,
           com.mohistmc.academy.skill.SkillDamageSource.attack(caster),damage);
  }

  double visualLength=Math.min(30,stopDistance);
  MeltdownBeamEntity beam=new MeltdownBeamEntity(AcademyEntities.MELTDOWN_BEAM.get(),level);
  beam.setPos(start.x,start.y,start.z);beam.setBeam(start,dir,visualLength);level.addFreshEntity(beam);
  level.playSound(null,caster.getX(),caster.getY(),caster.getZ(),AcademySounds.MD_MELTDOWNER,
          caster instanceof Player?SoundSource.PLAYERS:SoundSource.HOSTILE,.5f,1f);
  if(mayDestroyBlocks)destroyCylinder(level,caster,start,dir,radius,energy,stopDistance);
 }

 private void destroyCylinder(ServerLevel level,LivingEntity caster,Vec3 start,Vec3 dir,double radius,
                              float totalEnergy,double stopDistance){
  Vec3 reference=Math.abs(dir.y)<.98?new Vec3(0,1,0):new Vec3(1,0,0);
  Vec3 axis0=dir.cross(reference).normalize(),axis1=dir.cross(axis0).normalize();
  List<Vec3> origins=new ArrayList<>();
  for(double s=-radius;s<=radius;s+=.9)for(double t=-radius;t<=radius;t+=.9){
   double rr=radius*(.9+level.random.nextDouble()*.2);if(s*s+t*t<=rr*rr)origins.add(start.add(axis0.scale(s)).add(axis1.scale(t)));
  }
  if(origins.isEmpty())origins.add(start);
  float perLine=totalEnergy/origins.size();
  for(Vec3 origin:origins){float left=perLine;BlockPos previous=null;
   for(int step=0;step<=50&&left>0&&step<=stopDistance;step++){
    BlockPos pos=BlockPos.containing(origin.add(dir.scale(step)));if(pos.equals(previous))continue;previous=pos;
    left=destroyAlongRay(level,caster,pos,left);
    if(left>0&&level.random.nextFloat()<.05f)left=destroyAlongRay(level,caster,pos.relative(Direction.getRandom(level.random)),left);
   }
  }
 }

 private float destroyAlongRay(ServerLevel level,LivingEntity caster,BlockPos pos,float energy){
  if(!LoadedRaycast.isLoaded(level, pos)||!level.getWorldBorder().isWithinBounds(pos))return 0;
  var state=level.getBlockState(pos);if(state.isAir())return energy;
  if(!(caster instanceof ServerPlayer player)){
   // Mob caster: mayDestroyBlocks already ruled; mods may still veto through LivingDestroyBlockEvent. No drops.
   float hardness=state.getDestroySpeed(level,pos);if(hardness<0||energy<hardness)return 0;
   if(!EventHooks.onEntityDestroyBlock(caster,pos,state)||level.getBlockState(pos)!=state)return 0;
   return level.destroyBlock(pos,false,caster)?energy-hardness:0;
  }
  float hardness=state.getDestroySpeed(level,pos);if(hardness<0||energy<hardness||!level.mayInteract(player,pos))return 0;
  BlockEvent.BreakEvent event=new BlockEvent.BreakEvent(level,pos,state,player);NeoForge.EVENT_BUS.post(event);
  if(event.isCanceled()||level.getBlockState(pos)!=state)return 0;
  if(!level.destroyBlock(pos,level.random.nextFloat()<.05f,player))return 0;
  return energy-hardness;
 }

 private static void reflected(ServerLevel level,LivingEntity attacker,Vec3 attackerLook,ServerPlayer reflector,float damage,Set<Integer> hit){
  Vec3 start=reflector.getEyePosition(),direction=reflector.getLookAngle().normalize(),intended=start.add(direction.scale(10));
  HitResult wall=LoadedRaycast.clip(level, new ClipContext(start,intended,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,reflector));
  Vec3 end=wall.getType()==HitResult.Type.MISS?intended:wall.getLocation();LivingEntity nearest=null;double best=Double.MAX_VALUE;
  for(LivingEntity target:level.getEntitiesOfClass(LivingEntity.class,new AABB(start,end).inflate(1),
          target->target!=reflector&&target.isAlive())){var intercept=target.getBoundingBox().inflate(.3).clip(start,end);if(intercept.isPresent()&&start.distanceToSqr(intercept.get())<best){best=start.distanceToSqr(intercept.get());nearest=target;}}
  if(nearest!=null&&hit.add(nearest.getId()))com.mohistmc.academy.skill.AcademyDamageHelper.hurt(attacker,nearest,
          com.mohistmc.academy.skill.SkillDamageSource.attack(attacker),damage);
  // MDContextC.c_reflected in 1.0.7 rendered a separate ten-block ray
  // from the reflection point, even when it did not acquire a living target.
  double distance=attacker.getEyePosition().distanceTo(reflector.getEyePosition());
  Vec3 visualStart=attacker.getEyePosition().add(attackerLook.normalize().scale(distance));
  MeltdownBeamEntity reflectedBeam=new MeltdownBeamEntity(AcademyEntities.MELTDOWN_BEAM.get(),level);
  reflectedBeam.setPos(visualStart.x,visualStart.y,visualStart.z);reflectedBeam.setBeam(visualStart,direction,10);level.addFreshEntity(reflectedBeam);
 }
 private static void applyChargeSlow(LivingEntity player,int ticks){var movement=player.getAttribute(Attributes.MOVEMENT_SPEED);if(movement==null)return;movement.removeModifier(CHARGE_SLOW);double scale=Math.clamp((100-ticks)/100d,0d,1d);movement.addTransientModifier(new AttributeModifier(CHARGE_SLOW,scale-1d,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));}
 private static void clearChargeSlow(LivingEntity player){var movement=player.getAttribute(Attributes.MOVEMENT_SPEED);if(movement!=null)movement.removeModifier(CHARGE_SLOW);}
 @Override public int getCooldownTicks(float e){return 0;}
}

