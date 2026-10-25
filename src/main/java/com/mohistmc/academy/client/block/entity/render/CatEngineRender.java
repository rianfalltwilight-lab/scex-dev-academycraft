package com.mohistmc.academy.client.block.entity.render;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.world.block.entity.CatEngineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/** Upstream's camera-facing, double-sided one-block cat quad. */
public class CatEngineRender implements BlockEntityRenderer<CatEngineBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/block/cat_engine.png");

    public CatEngineRender(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(CatEngineBlockEntity engine, float partialTick, PoseStack pose,
                       @NotNull MultiBufferSource buffers, int light, int overlay) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var pos = engine.getBlockPos();
        float yaw = (float) Math.toDegrees(Math.atan2(pos.getX() + .5 - camera.x,
                pos.getZ() + .5 - camera.z)) + 180;
        double timeSeconds = engine.getLevel() == null ? 0
                : (engine.getLevel().getGameTime() + partialTick) / 20d;
        pose.pushPose();
        pose.translate(.5, .5 + .03 * Math.sin(timeSeconds * .006), .5);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(engine.interpolatedSpinDegrees(partialTick)));
        var out = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        var matrix = pose.last().pose();
        var normal = pose.last();
        out.addVertex(matrix, -.5f, -.5f, 0).setColor(-1).setUv(0, 0)
                .setOverlay(overlay).setLight(light).setNormal(normal, 0, 0, 1);
        out.addVertex(matrix, .5f, -.5f, 0).setColor(-1).setUv(1, 0)
                .setOverlay(overlay).setLight(light).setNormal(normal, 0, 0, 1);
        out.addVertex(matrix, .5f, .5f, 0).setColor(-1).setUv(1, 1)
                .setOverlay(overlay).setLight(light).setNormal(normal, 0, 0, 1);
        out.addVertex(matrix, -.5f, .5f, 0).setColor(-1).setUv(0, 1)
                .setOverlay(overlay).setLight(light).setNormal(normal, 0, 0, 1);
        pose.popPose();
    }
}
