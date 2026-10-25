package com.mohistmc.academy.world.fluidtype;

import com.mohistmc.academy.AcademyCraft;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.NotNull;

public class PhaseLiquidType extends FluidType {
    public PhaseLiquidType() {
        super(FluidType.Properties.create()
                // Keep the fluid and its world block on the same stable
                // translation key.  The old key (block.academy.phase) was
                // never present in the shipped language tables, so Jade/JEI
                // exposed the raw registry key instead of a name.
                .descriptionId("block.academy.phase_liquid")
                .lightLevel(8)
                .viscosity(6000)
                .temperature(0)
                .supportsBoating(false)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
                .sound(SoundActions.FLUID_VAPORIZE, SoundEvents.FIRE_EXTINGUISH)
                .density(1)
        );
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            private static final ResourceLocation UNDERWATER_LOCATION = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/block/black.png");
            private static final ResourceLocation FLUID_SPRITE = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "block/black");

            @Override
            public @NotNull ResourceLocation getStillTexture() {
                return FLUID_SPRITE;
            }

            @Override
            public @NotNull ResourceLocation getFlowingTexture() {
                return FLUID_SPRITE;
            }

            @Override
            public ResourceLocation getRenderOverlayTexture(Minecraft mc) {
                return UNDERWATER_LOCATION;
            }

            @Override
            public int getTintColor() {
                return 0xFF000000;
            }

            @Override
            public int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
                return 0xFF000000;
            }

        });
    }
}
