package com.mohistmc.academy.client.renderer;

import com.mohistmc.academy.skill.ability.telekinesis.LiquidShadowEntity;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Original Waterman's zombie-shaped water skin, without Drowned overlays or zombie behavior. */
public final class LiquidShadowRenderer extends MobRenderer<LiquidShadowEntity, LiquidShadowRenderer.WaterModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("academy", "textures/entity/extraacc/waterman.png");
    public LiquidShadowRenderer(EntityRendererProvider.Context context) {
        super(context, new WaterModel(context.bakeLayer(ModelLayers.ZOMBIE)), .5f);
    }
    @Override public ResourceLocation getTextureLocation(LiquidShadowEntity entity) { return TEXTURE; }

    public static final class WaterModel extends HumanoidModel<LiquidShadowEntity> {
        WaterModel(ModelPart root) { super(root); }
        @Override public void setupAnim(LiquidShadowEntity entity, float limbSwing, float limbSwingAmount,
                                        float ageInTicks, float headYaw, float headPitch) {
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch);
            // ModelZombie in 1.12 used its non-EntityZombie pose for EntityWaterman.
            AnimationUtils.animateZombieArms(leftArm, rightArm, false, attackTime, ageInTicks);
        }
    }
}
