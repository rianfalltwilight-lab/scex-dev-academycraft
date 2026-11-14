package com.mohistmc.academy.world;

import com.mohistmc.academy.AcademyCraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.PercentageAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Attributes modpacks can modify with ordinary attribute modifiers (gear, effects, KubeJS, curios).
 * Every living entity type gets them, so mobs and future mob casters scale the same way as players.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class AcademyAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, AcademyCraft.MODID);

    /** Multiplier on all ability damage the entity deals. Base 1.0 (100%). */
    public static final DeferredHolder<Attribute, Attribute> SKILL_DAMAGE = ATTRIBUTES.register("skill_damage",
            () -> new PercentageAttribute("attribute.name.academy.skill_damage", 1.0, 0.0, 1024.0)
                    .setSyncable(true));

    /** Fraction of incoming ability damage removed: damage x (1 - value). Base 0; negative means vulnerable. */
    public static final DeferredHolder<Attribute, Attribute> SKILL_RESISTANCE = ATTRIBUTES.register("skill_resistance",
            () -> new PercentageAttribute("attribute.name.academy.skill_resistance", 0.0, -1.0, 1.0)
                    .setSyncable(true));

    private AcademyAttributes() {}

    public static Holder<Attribute> skillDamage() { return SKILL_DAMAGE; }
    public static Holder<Attribute> skillResistance() { return SKILL_RESISTANCE; }

    @SubscribeEvent
    public static void attach(EntityAttributeModificationEvent event) {
        for (EntityType<? extends net.minecraft.world.entity.LivingEntity> type : event.getTypes()) {
            if (!event.has(type, SKILL_DAMAGE)) event.add(type, SKILL_DAMAGE);
            if (!event.has(type, SKILL_RESISTANCE)) event.add(type, SKILL_RESISTANCE);
        }
    }
}
