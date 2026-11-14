package com.mohistmc.academy.world;

import java.util.List;

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

    /** Flat damage added to every ability hit the entity deals, before the multipliers. Base 0. */
    public static final DeferredHolder<Attribute, Attribute> SKILL_POWER = ATTRIBUTES.register("skill_power",
            () -> new net.minecraft.world.entity.ai.attributes.RangedAttribute("attribute.name.academy.skill_power",
                    0.0, 0.0, 1_000_000.0).setSyncable(true));

    /** Multiplier on max CP (level base, usage growth and courses). Base 1.0. */
    public static final DeferredHolder<Attribute, Attribute> MAX_CP = percent("max_cp", 1, 0, 1024);
    /** Multiplier on CP recovery. Base 1.0. */
    public static final DeferredHolder<Attribute, Attribute> CP_REGEN = percent("cp_regen", 1, 0, 1024);
    /** Multiplier on max overload. Base 1.0. */
    public static final DeferredHolder<Attribute, Attribute> MAX_OVERLOAD = percent("max_overload", 1, 0, 1024);
    /** Multiplier on overload recovery. Base 1.0. */
    public static final DeferredHolder<Attribute, Attribute> OVERLOAD_RECOVERY =
            percent("overload_recovery", 1, 0, 1024);
    /** Multiplier on every skill's CP cost; below 1 is cheaper. Base 1.0. */
    public static final DeferredHolder<Attribute, Attribute> CP_COST = percent("cp_cost", 1, 0, 1024);
    /** Multiplier on every skill cooldown; below 1 is shorter. Base 1.0. */
    public static final DeferredHolder<Attribute, Attribute> COOLDOWN = percent("cooldown", 1, 0, 1024);
    /** Multiplier on proficiency gained. Base 1.0. */
    public static final DeferredHolder<Attribute, Attribute> PROFICIENCY_GAIN = percent("proficiency_gain", 1, 0, 1024);

    /** The resource attributes, which only players have. */
    public static final List<DeferredHolder<Attribute, Attribute>> PLAYER_RESOURCES =
            List.of(MAX_CP, CP_REGEN, MAX_OVERLOAD, OVERLOAD_RECOVERY, CP_COST, COOLDOWN, PROFICIENCY_GAIN);

    private static DeferredHolder<Attribute, Attribute> percent(String id, double base, double min, double max) {
        return ATTRIBUTES.register(id, () -> new PercentageAttribute("attribute.name.academy." + id, base, min, max)
                .setSyncable(true));
    }

    private AcademyAttributes() {}

    public static Holder<Attribute> skillDamage() { return SKILL_DAMAGE; }
    public static Holder<Attribute> skillResistance() { return SKILL_RESISTANCE; }

    @SubscribeEvent
    public static void attach(EntityAttributeModificationEvent event) {
        for (EntityType<? extends net.minecraft.world.entity.LivingEntity> type : event.getTypes()) {
            if (!event.has(type, SKILL_DAMAGE)) event.add(type, SKILL_DAMAGE);
            if (!event.has(type, SKILL_RESISTANCE)) event.add(type, SKILL_RESISTANCE);
            if (!event.has(type, SKILL_POWER)) event.add(type, SKILL_POWER);
        }
        for (DeferredHolder<Attribute, Attribute> resource : PLAYER_RESOURCES)
            if (!event.has(EntityType.PLAYER, resource)) event.add(EntityType.PLAYER, resource);
    }
}
