package com.mohistmc.academy.client.renderer;

import com.mohistmc.academy.world.entity.CruiseBombEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Upstream RenderBomb's untextured blue sphere, radius 0.1. */
public final class CruiseBombRenderer extends EntityRenderer<CruiseBombEntity> {
    public CruiseBombRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0; }
    @Override public void render(CruiseBombEntity entity, float yaw, float partial, PoseStack pose,
                                 MultiBufferSource buffers, int light) {
        AeroProjectileRenderer.sphere(buffers.getBuffer(AeroProjectileRenderer.TYPE), pose.last().pose(), .1f, .5f, 0, 0, 1);
        super.render(entity, yaw, partial, pose, buffers, light);
    }
    @Override public ResourceLocation getTextureLocation(CruiseBombEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    }
}
