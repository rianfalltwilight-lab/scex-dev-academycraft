package com.mohistmc.academy.api.mob;

import com.mohistmc.academy.AcademyCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jetbrains.annotations.Nullable;

/** Data map {@code academy:mob_skills} on entity types; AcademyCraft ships no entries. */
@EventBusSubscriber(modid = AcademyCraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class AcademyMobDataMaps {
    private AcademyMobDataMaps() {}

    public static final DataMapType<EntityType<?>, MobSkills> MOB_SKILLS = DataMapType
            .builder(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "mob_skills"),
                    Registries.ENTITY_TYPE, MobSkills.CODEC)
            .synced(MobSkills.CODEC, false)
            .build();

    @SubscribeEvent
    public static void register(RegisterDataMapTypesEvent event) {
        event.register(MOB_SKILLS);
    }

    public static @Nullable MobSkills get(EntityType<?> type) {
        return type.builtInRegistryHolder().getData(MOB_SKILLS);
    }
}
