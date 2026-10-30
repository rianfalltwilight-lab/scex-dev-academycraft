package com.mohistmc.academy.client.renderer;

import com.mohistmc.academy.world.entity.AeroProjectileEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** ExtraAcC's untextured cyan sphere, curved blade and trailing lance geometry. */
public final class AeroProjectileRenderer extends EntityRenderer<AeroProjectileEntity> {
    static final RenderType TYPE = AirRenderType.createType();
    private static final ResourceLocation UNUSED = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public AeroProjectileRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0; }

    @Override public void render(AeroProjectileEntity entity, float yaw, float partial, PoseStack pose,
                                 MultiBufferSource buffers, int light) {
        // One buffer for the whole entity; never retain a consumer across a RenderType switch.
        VertexConsumer out = buffers.getBuffer(TYPE);
        Matrix4f matrix = pose.last().pose();
        switch (entity.kind()) {
            case VOLCANIC_BALL -> sphere(out, matrix, entity.getBbWidth() / 2,
                    Math.max(.1f, .5f - .4f * (entity.legacyAge() + partial * 2) / 80));
            case AIR_BLADE -> blade(out, matrix, entity.getDeltaMovement().normalize());
            case BOMBER_LANCE -> lance(out, matrix, entity.getStartPos().subtract(entity.getPosition(partial)));
        }
        super.render(entity, yaw, partial, pose, buffers, light);
    }

    static void sphere(VertexConsumer out, Matrix4f matrix, float radius, float alpha) {
        sphere(out, matrix, radius, alpha, .7f, 1f, 1f);
    }

    static void sphere(VertexConsumer out, Matrix4f matrix, float radius, float alpha, float red, float green, float blue) {
        for (int latitude = 0; latitude < 30; latitude++) {
            double a = Math.PI * latitude / 30, b = Math.PI * (latitude + 1) / 30;
            for (int longitude = 0; longitude < 30; longitude++) {
                double c = Math.PI * 2 * longitude / 30, d = Math.PI * 2 * (longitude + 1) / 30;
                vertex(out, matrix, polar(radius, a, c), alpha, red, green, blue);
                vertex(out, matrix, polar(radius, b, c), alpha, red, green, blue);
                vertex(out, matrix, polar(radius, b, d), alpha, red, green, blue);
                vertex(out, matrix, polar(radius, a, d), alpha, red, green, blue);
            }
        }
    }

    private static Vec3 polar(float radius, double latitude, double longitude) {
        return new Vec3(radius * Math.sin(latitude) * Math.cos(longitude),
                radius * Math.cos(latitude), radius * Math.sin(latitude) * Math.sin(longitude));
    }

    private static void blade(VertexConsumer out, Matrix4f matrix, Vec3 forward) {
        Vec3 side = forward.cross(new Vec3(0, 1, 0)).normalize();
        if (side.lengthSqr() < .001) side = new Vec3(1, 0, 0);
        Vec3 up = side.cross(forward).normalize().scale(.01);
        for (int i = 0; i < 40; i++) {
            double z = i * .1 - 2, next = z + .1;
            Vec3 a = forward.scale(Math.sqrt(6.25 - z * z) - 1.5).add(side.scale(z));
            Vec3 b = forward.scale(Math.sqrt(6.25 - next * next) - 1.5).add(side.scale(next));
            Vec3 c = forward.scale(Math.sqrt(18.0625 - next * next) - 3.75).add(side.scale(next));
            Vec3 d = forward.scale(Math.sqrt(18.0625 - z * z) - 3.75).add(side.scale(z));
            for (int sign : new int[]{-1, 1}) {
                Vec3 offset = up.scale(sign);
                vertex(out, matrix, a.add(offset), .5f); vertex(out, matrix, b.add(offset), .5f);
                vertex(out, matrix, c.add(offset), .3f); vertex(out, matrix, d.add(offset), .3f);
            }
            vertex(out, matrix, a.add(up), .5f); vertex(out, matrix, b.add(up), .5f);
            vertex(out, matrix, b.subtract(up), .5f); vertex(out, matrix, a.subtract(up), .5f);
        }
    }

    private static void lance(VertexConsumer out, Matrix4f matrix, Vec3 start) {
        Vec3 direction = start.normalize();
        Vec3 side = direction.cross(Math.abs(direction.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 up = side.cross(direction).normalize();
        Vec3 tail = start.add(direction.scale(-.5));
        if (start.lengthSqr() <= .25) return;
        for (int i = 0; i < 30; i++) {
            double a = i * Math.PI * 2 / 30, b = (i + 1) * Math.PI * 2 / 30;
            Vec3 p = side.scale(Math.cos(a) * .2).add(up.scale(Math.sin(a) * .2));
            Vec3 q = side.scale(Math.cos(b) * .2).add(up.scale(Math.sin(b) * .2));
            vertex(out, matrix, p, .5f); vertex(out, matrix, q, .5f);
            vertex(out, matrix, q.add(tail), .5f); vertex(out, matrix, p.add(tail), .5f);
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 point, float alpha) {
        vertex(out, matrix, point, alpha, .7f, 1f, 1f);
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 point, float alpha, float red, float green, float blue) {
        out.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z).setColor(red, green, blue, alpha);
    }

    @Override public ResourceLocation getTextureLocation(AeroProjectileEntity entity) { return UNUSED; }

    private abstract static class AirRenderType extends RenderType {
        private AirRenderType() { super("unused", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false, () -> {}, () -> {}); }
        static RenderType createType() {
            return create("academy_air_projectile", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                    16384, false, true, CompositeState.builder().setShaderState(POSITION_COLOR_SHADER)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                            .setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
        }
    }
}
