package com.mohistmc.academy.config;

import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.AbilityMutationService;
import net.minecraft.server.level.ServerPlayer;

/** Single settlement boundary for effects which intentionally bypass registry base costs. */
public final class DynamicSkillRules {
 private DynamicSkillRules(){}
 public static LegacyAbilityRules.SkillTuning tuning(String id){return ACConfig.Server.skill(id);}
 public static boolean enabled(String id){return tuning(id).enabled();}
 public static float cp(String id,float raw){return scale(raw,tuning(id).cp());}
 public static float overload(String id,float raw){return scale(raw,tuning(id).overload());}
 public static float damage(String id,float raw){return scale(scale(raw,tuning(id).damage()),(float)ACConfig.Server.damageMul());}
 public static float exp(String id,float raw){return scale(scale(raw,tuning(id).exp()),ACConfig.Server.legacyRules().proficiencyGrowth());}
 public static boolean destroysBlocks(String id){return tuning(id).destroyBlocks();}
 /** Cooldown ticks scaled by the {@code cooldown} tuning (config x datapack); 0 or less stays unchanged. */
 public static int cooldown(String id,int rawTicks){
  if(rawTicks<=0)return rawTicks;float m=tuning(id).cooldown();if(m==1f)return rawTicks;
  double v=(double)rawTicks*m;return Double.isFinite(v)?(int)Math.min(Integer.MAX_VALUE,Math.max(0,Math.round(v))):rawTicks;
 }
 public static boolean destroysBlocks(net.minecraft.world.level.Level level,String id){
  return tuning(id).destroyBlocks()&&ACConfig.Server.mayDestroyBlocks(level);
 }
 /** CP a player pays: the tuned cost times the player's cp_cost attribute and proficiency discount. */
 public static float playerCp(PlayerAbilityData data,String id,float raw){return scale(cp(id,raw),data.cpCostFactor(id));}
 public static boolean canPay(PlayerAbilityData data,String id,float rawCp,float rawOverload){
  if(!enabled(id))return false;if(data.isDevMode())return true;float cp=playerCp(data,id,rawCp),ol=overload(id,rawOverload);
  // CPData.perform: only CP gates a cast; overload clamps at its cap and then locks every skill.
  if(cp>=0&&ol>=0&&!data.isOverloadLocked()&&data.canAffordCp(cp))return true;
  if(cp>0&&!data.canAffordCp(cp))data.noteCpShortfall(cp);
  return false;
 }
 public static void addExp(ServerPlayer player,PlayerAbilityData data,String id,float raw){
  AbilityMutationService.addSkillExp(player,data,id,exp(id,raw));
 }
 public static boolean tryPay(PlayerAbilityData data,String id,float rawCp,float rawOverload){
  if(!canPay(data,id,rawCp,rawOverload))return false;if(data.isDevMode())return true;float cp=playerCp(data,id,rawCp),ol=overload(id,rawOverload);
  return data.tryConsumeDynamic(cp,ol);
 }
 public static boolean payForced(PlayerAbilityData data,String id,float rawCp,float rawOverload){
  if(!enabled(id)||!Float.isFinite(rawCp)||!Float.isFinite(rawOverload)||rawCp<0||rawOverload<0)return false;
  if(data.isDevMode())return true;
  return data.consumeDynamicForced(playerCp(data,id,rawCp),overload(id,rawOverload));
 }
 static float scale(float raw,float multiplier){if(!Float.isFinite(raw)||raw<0)return 0;float v=raw*multiplier;return Float.isFinite(v)?Math.max(0,v):0;}
}
