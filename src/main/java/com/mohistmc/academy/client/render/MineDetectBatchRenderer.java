package com.mohistmc.academy.client.render;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.ClientPacketBridge;
import com.mohistmc.academy.network.MineDetectResultPacket;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Final-1.12.2 MineDetect renderer. The official handler rebuilt one list and
 * submitted every ore in one tessellator batch; using client entities for the
 * 8400-entry upper bound adds avoidable entity-tick and dispatch overhead.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class MineDetectBatchRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/effects/mineview.png");
    private static final RenderType TYPE = RenderType.create(
            "academy_mine_highlight_batch", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 1 << 20, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TEXTURE, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(false));
    private static final float[][] COLORS = {
            {115 / 255f, 200 / 255f, 227 / 255f},
            {161 / 255f, 181 / 255f, 188 / 255f},
            {87 / 255f, 231 / 255f, 248 / 255f},
            {97 / 255f, 204 / 255f, 94 / 255f},
            {235 / 255f, 109 / 255f, 84 / 255f}
    };

    private MineDetectBatchRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        MineDetectResultPacket snapshot = ClientPacketBridge.mineDetectSnapshot();
        if (snapshot.entries().isEmpty() || snapshot.range() <= 0) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(TYPE);
        Matrix4f matrix = pose.last().pose();
        double range = snapshot.range();
        double invRange = 2.2 / range;
        // alpha <= 0.01f whenever dist / range >= (1 + 0.29 / 0.7) / 2.2 (~0.64286); prune via distSq before sqrt.
        double maxVisibleDistSq = (range * 0.65) * (range * 0.65);
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        for (MineDetectResultPacket.Entry entry : snapshot.entries()) {
            double dx = px - entry.pos().getX();
            double dy = py - entry.pos().getY();
            double dz = pz - entry.pos().getZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > maxVisibleDistSq) continue;
            float alpha = (float) Math.clamp(.3 + (1 - Math.sqrt(distSq) * invRange) * .7, 0, 1);
            if (alpha <= .01f) continue;
            float[] color = COLORS[Math.clamp(entry.harvestLevel(), 0, COLORS.length - 1)];
            box(out, matrix, entry.pos().getX(), entry.pos().getY(), entry.pos().getZ(),
                    color[0], color[1], color[2], alpha);
        }
        buffers.endBatch(TYPE);
        pose.popPose();
    }

    private static void box(VertexConsumer out, Matrix4f matrix, float x, float y, float z,
                            float r, float g, float b, float a) {
        float x0 = x + .05f, x1 = x + .95f;
        float y0 = y + .05f, y1 = y + .95f;
        float z0 = z + .05f, z1 = z + .95f;
        face(out, matrix, r, g, b, a, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
        face(out, matrix, r, g, b, a, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        face(out, matrix, r, g, b, a, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1);
        face(out, matrix, r, g, b, a, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        face(out, matrix, r, g, b, a, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1);
        face(out, matrix, r, g, b, a, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0);
    }

    private static void face(VertexConsumer out, Matrix4f matrix, float r, float g, float b, float a,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        out.addVertex(matrix, x0, y0, z0).setColor(r, g, b, a).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        out.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        out.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        out.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }
}
