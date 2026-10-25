package com.mohistmc.academy.advancement;

import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Server-authoritative bridge for the 1.0.7 achievement graph. */
@EventBusSubscriber(modid = "academy")
public final class LegacyAdvancementBridge {
    private LegacyAdvancementBridge() {}
    private static final String TP_COUNT="academy_legacy_tp_count";
    private static final Map<String,String> USE=Map.ofEntries(
      Map.entry("mag_movement","electromaster/mag_movement"),
      Map.entry("body_intensify","electromaster/body_intensify"),Map.entry("mine_detect","electromaster/mine_detect"),
      Map.entry("thunder_bolt","electromaster/thunder_bolt"),Map.entry("railgun","electromaster/railgun"),Map.entry("thunder_clap","electromaster/thunder_clap"),
      Map.entry("jet_engine","meltdowner/jet_engine"),Map.entry("threatening_teleport","teleporter/threatening_teleport"),
      Map.entry("penetrate_teleport","teleporter/ignore_barrier"),Map.entry("flashing","teleporter/flashing"),
      Map.entry("ground_shock","vecmanip/ground_shock"),Map.entry("dir_blast","vecmanip/dir_blast"),
      Map.entry("storm_wing","vecmanip/storm_wing"));
    private static final Map<String,String> LEARN=Map.ofEntries(
      Map.entry("rad_intensify","meltdowner/rad_intensify"),Map.entry("light_shield","meltdowner/light_shield"),
      Map.entry("meltdowner","meltdowner/meltdowner"),Map.entry("mine_ray_basic","meltdowner/mine_ray"),
      Map.entry("electron_missile","meltdowner/electron_missile"));

    public static void learned(ServerPlayer player, PlayerAbilityData data, Skill skill) {
        String id=LEARN.get(skill.getId()); if(id!=null) award(player,id);
        levels(player,data);
    }
    public static void levels(ServerPlayer player,PlayerAbilityData data){if(data.getCurrentAbility()!=null){int level=Math.clamp(data.getPlayerLevel(),0,5);for(int i=1;i<=level;i++)award(player,data.getCurrentAbility().id()+"/lv"+i);}}
    public static void used(ServerPlayer player, Skill skill) {
        String id=USE.get(skill.getId()); if(id!=null) award(player,id);
    }
    public static void teleported(ServerPlayer player) {
        var tag=teleportProgress(player); int count=Math.min(400,tag.getInt(TP_COUNT)+1); tag.putInt(TP_COUNT,count);
        if(count>=400) award(player,"teleporter/mastery");
    }
    private static CompoundTag teleportProgress(Player player) {
        var data=player.getPersistentData();
        if(!data.contains(Player.PERSISTED_NBT_TAG,10)) data.put(Player.PERSISTED_NBT_TAG,new CompoundTag());
        var persisted=data.getCompound(Player.PERSISTED_NBT_TAG);
        int count=Math.clamp(Math.max(persisted.getInt(TP_COUNT),data.getInt(TP_COUNT)),0,400);
        persisted.putInt(TP_COUNT,count);
        data.remove(TP_COUNT);
        return persisted;
    }
    @SubscribeEvent
    public static void cloned(PlayerEvent.Clone event) {
        if(!(event.getEntity() instanceof ServerPlayer player)) return;
        // Old saves may die before their next teleport has migrated the top-level key.
        int previous=teleportProgress(event.getOriginal()).getInt(TP_COUNT);
        var current=teleportProgress(player);
        current.putInt(TP_COUNT,Math.max(previous,current.getInt(TP_COUNT)));
    }
    public static void teleporterCritical(ServerPlayer player){award(player,"teleporter/critical_attack");}
    public static void electromasterChargedCreeper(ServerPlayer player){award(player,"electromaster/attack_creeper");}
    public static void obtained(ServerPlayer player,String itemId,boolean crafted) {
        String path=switch(itemId) {
            case "academy:crystal_low" -> "default/crystal";
            case "academy:matrix" -> crafted?"default/matrix1":null;
            case "academy:mat_core_0","academy:mat_core_1","academy:mat_core_2" -> crafted?"default/matrix2":null;
            case "academy:node_basic" -> crafted?"default/node":null;
            case "academy:developer_portable" -> crafted?"default/developer1":null;
            case "academy:dev_normal" -> crafted?"default/developer2":null;
            case "academy:dev_advanced" -> crafted?"default/developer3":null;
            case "academy:phase_gen" -> crafted?"default/phasegen":null;
            case "academy:solar_gen" -> crafted?"default/solargen":null;
            case "academy:windgen_main" -> crafted?"default/windgen":null;
            case "academy:terminal_installer" -> crafted?"default/terminal":null;
            default -> null;
        }; if(path!=null)award(player,path);
    }
    public static boolean award(ServerPlayer player,String id) {
        var holder=player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("academy","legacy/"+id));
        return holder!=null && player.getAdvancements().award(holder,"earned");
    }
}
