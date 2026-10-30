package com.mohistmc.academy.regression;

import com.mohistmc.academy.client.ChargingHudOverlay;
import com.mohistmc.academy.client.ClientSessionState;
import com.mohistmc.academy.client.media.ExternalMediaManager;
import com.mohistmc.academy.client.sound.AbilityLoopSoundManager;
import com.mohistmc.academy.utils.RenderUtils;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.AcademyFluids;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/** Opt-in real render/audio tests. Separate probe JAR; never enabled in a normal client. */
@EventBusSubscriber(modid = "academy_audit", value = Dist.CLIENT)
public final class ClientDeepAuditGate {
    private static final List<String> EVIDENCE = new ArrayList<>();
    private static int ticks, frames, stage;
    private static boolean done, captureRequested;
    private static volatile boolean captured;
    private static boolean worldRequested;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.deepAuditClient") || done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (++ticks > 3600) throw new IllegalStateException("client gate timed out stage=" + stage);
            if (Files.exists(mc.gameDirectory.toPath().resolve("stop-request.txt"))) throw new IllegalStateException("runner requested resource/timeout shutdown");
            if (!worldRequested && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen && mc.getOverlay() == null) {
                worldRequested = true;
                mc.createWorldOpenFlows().createFreshLevel("DeepAudit29",
                        new net.minecraft.world.level.LevelSettings("Deep audit isolated", net.minecraft.world.level.GameType.CREATIVE,
                                false, net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(),
                                net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(20261025L, false, false),
                        registries -> registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(),
                        new net.minecraft.client.gui.screens.TitleScreen());
                return;
            }
            if (mc.player == null || mc.level == null || mc.getOverlay() != null) return;
            if (Boolean.getBoolean("academy.deepAuditClient.bootstrapOnly")) return;
            if (stage == 0) {
                if (!ClientTeleportPreviewChecks.ready(mc)) return;
                if (mc.screen != null) mc.player.closeContainer();
                var fluid = IClientFluidTypeExtensions.of(AcademyFluids.PHASE_LIQUID_TYPE.get());
                var sprite = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(fluid.getStillTexture());
                require(!sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()), "phase liquid atlas sprite missing");
                EVIDENCE.add("PASS phase liquid still/flow atlas sprite=" + sprite.contents().name());
                require(fluid.getStillTexture().equals(fluid.getFlowingTexture()), "flow sprite differs");
                textureRelease(mc);
                railgunFrameIndependence();
                ClientSecondaryAuditChecks.sessions(mc, EVIDENCE);
                ClientTeleportPreviewChecks.run(mc, EVIDENCE);
                ChargingHudOverlay.setChargingState(1, 40, "mag_movement");
                stage = 1;
            } else if (stage == 1 && frames >= 12) {
                require(field(AbilityLoopSoundManager.class, "movement") != null, "positive control: alive charging player has no loop");
                float health = mc.player.getHealth();
                mc.player.setHealth(0);
                ChargingHudOverlay.onClientTick(event);
                AbilityLoopSoundManager.tick(event);
                require(field(AbilityLoopSoundManager.class, "movement") == null, "dead player retained movement loop");
                require(!ChargingHudOverlay.isCharging("mag_movement"), "dead player retained charging HUD");
                mc.player.setHealth(health);
                EVIDENCE.add("PASS alive loop exists; death clears actual loop owner and HUD before server abort");
                ClientSessionState.reset();
                stage = 2;
            } else if (stage == 2 && captured) finish(mc, null);
        } catch (Throwable failure) { finish(mc, failure); }
    }

    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        if (!Boolean.getBoolean("academy.deepAuditClient") || done || stage < 1) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            TrackingGraphics graphics = new TrackingGraphics(mc, event.getGuiGraphics().bufferSource());
            // The old implementation allocated private native builders, never consulting the GUI owner.
            for (int i = 0; i < 32; i++) RenderUtils.renderText(graphics, "Academy audit: text buffer " + i, 8, 8 + i % 8 * 10);
            require(graphics.requests == 32, "renderText bypassed managed GUI buffer (native allocation regression)");
            if (frames == 0) {
                ClientSecondaryAuditChecks.catRender(mc, graphics.pose(), graphics.bufferSource(), EVIDENCE);
                ClientLayoutAuditChecks.run(mc, graphics.bufferSource(), EVIDENCE);
            }
            var types = List.of(AcademyEntities.AERO_VOLCANIC_BALL.get(), AcademyEntities.AERO_AIR_BLADE.get(),
                    AcademyEntities.AERO_BOMBER_LANCE.get(), AcademyEntities.CRUISE_BOMB.get(), AcademyEntities.LIQUID_SHADOW.get());
            int x = 65;
            for (var type : types) {
                Entity entity = type.create(mc.level);
                require(entity != null, "projectile factory failed");
                entity.setPos(4, 0, 0); entity.xo = 4; entity.yo = entity.zo = 0;
                entity.setDeltaMovement(new Vec3(1, .2, .3));
                PoseStack pose = graphics.pose();
                MatrixSnapshot original = new MatrixSnapshot(pose);
                pose.pushPose();
                try {
                    pose.translate(x, 150, 0); pose.scale(20, -20, 20);
                    mc.getEntityRenderDispatcher().getRenderer(entity).render(entity, 0, 0, pose, graphics.bufferSource(), 0xF000F0);
                } finally { pose.popPose(); }
                require(original.matches(pose), "renderer changed caller pose");
                graphics.flush(); x += 110;
            }
            frames++;
            if (stage == 2 && !captureRequested) {
                captureRequested = true;
                EVIDENCE.add("PASS " + frames + " actual render frames; managed text requests=" + frames * 32 + "; five restored entity renderers completed, caller pose preserved");
                Screenshot.grab(mc.gameDirectory, "deep-audit-render.png", mc.getMainRenderTarget(), message -> captured = true);
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }

    @SuppressWarnings("unchecked") private static void textureRelease(Minecraft mc) throws Exception {
        var id = ResourceLocation.fromNamespaceAndPath("academy_audit", "temporary_cover");
        var texture = new DynamicTexture(new NativeImage(2, 2, false));
        mc.getTextureManager().register(id, texture);
        ((Set<ResourceLocation>) field(ExternalMediaManager.class, "DYNAMIC_COVERS")).add(id);
        require(texture.getPixels() != null, "positive control texture absent");
        ExternalMediaManager.invalidateForResourceReload();
        require(texture.getPixels() == null, "resource invalidation leaked cover NativeImage/GPU texture");
        require(((Set<?>) field(ExternalMediaManager.class, "DYNAMIC_COVERS")).isEmpty(), "cover registry retained entries");
        EVIDENCE.add("PASS dynamic cover owns pixels before invalidation, closes pixels and clears registry afterward");
    }

    private static Object field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(null);
    }
    private static void railgunFrameIndependence() throws Exception {
        Class<?> type = com.mohistmc.academy.client.renderer.RailgunBeamRenderer.ArcData.class;
        var constructor = type.getDeclaredConstructor(Vec3.class, double.class, int.class);
        constructor.setAccessible(true);
        Object arcs = constructor.newInstance(new Vec3(1, 0, 0), 6d, 17);
        var tick = type.getDeclaredMethod("tick", int.class); tick.setAccessible(true);
        Field segmentsField = type.getDeclaredField("segments"); segmentsField.setAccessible(true);
        Object segment = ((List<?>) segmentsField.get(arcs)).getFirst();
        Field life = segment.getClass().getDeclaredField("life"); life.setAccessible(true);
        tick.invoke(arcs, 1); int first = life.getInt(segment);
        for (int frame = 0; frame < 144; frame++) tick.invoke(arcs, 1);
        require(life.getInt(segment) == first, "railgun arc lifetime changed with render FPS");
        tick.invoke(arcs, 11);
        require(life.getInt(segment) == first - 10, "railgun arc lifetime did not follow entity ticks");
        EVIDENCE.add("PASS railgun arcs: 144 renders in one tick preserve alpha; 10 game ticks consume 10 lifetime ticks");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void finish(Minecraft mc, Throwable failure) {
        if (done) return; done = true;
        try {
            if (failure != null) { EVIDENCE.add("FAIL " + failure); failure.printStackTrace(); }
            Files.writeString(mc.gameDirectory.toPath().resolve("academy-deep-audit-client.txt"),
                    "status=" + (failure == null ? "PASS" : "FAIL") + "\n" + String.join("\n", EVIDENCE));
        } catch (Exception writeFailure) { writeFailure.printStackTrace(); }
        mc.stop();
    }
    private static final class TrackingGraphics extends GuiGraphics {
        int requests;
        TrackingGraphics(Minecraft mc, MultiBufferSource.BufferSource buffers) { super(mc, buffers); }
        @Override public MultiBufferSource.BufferSource bufferSource() { requests++; return super.bufferSource(); }
    }
    private record MatrixSnapshot(org.joml.Matrix4f matrix) {
        MatrixSnapshot(PoseStack pose) { this(new org.joml.Matrix4f(pose.last().pose())); }
        boolean matches(PoseStack pose) { return matrix.equals(pose.last().pose()); }
    }
}
