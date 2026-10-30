package com.mohistmc.academy.regression;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Probe-only standard capabilities: no fixture enters the shipped mod JAR. */
@EventBusSubscriber(modid = "academy_audit", bus = EventBusSubscriber.Bus.MOD)
public final class AutomationFluidFixtures {
    static final DataComponentType<SimpleFluidContent> FLUID = DataComponentType.<SimpleFluidContent>builder()
            .persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC).build();

    @SubscribeEvent
    public static void registerComponent(RegisterEvent event) {
        event.register(Registries.DATA_COMPONENT_TYPE,
                ResourceLocation.fromNamespaceAndPath("academy_audit", "test_container_fluid"), () -> FLUID);
    }

    @SubscribeEvent
    public static void registerContainers(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.FluidHandler.ITEM,
                (stack, context) -> new FluidHandlerItemStack(() -> FLUID, stack, 2000), Items.PRISMARINE_SHARD);
        event.registerItem(Capabilities.FluidHandler.ITEM,
                (stack, context) -> new FluidHandlerItemStack.Consumable(() -> FLUID, stack, 2000), Items.NAUTILUS_SHELL);
        event.registerItem(Capabilities.FluidHandler.ITEM,
                (stack, context) -> new FluidHandlerItemStack.SwapEmpty(() -> FLUID, stack,
                        new ItemStack(Items.GLASS_BOTTLE), 2000), Items.PRISMARINE_CRYSTALS);
    }
}
