package com.slizerozik.demoncow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.slizerozik.demoncow.DemonCowMod;
import com.slizerozik.demoncow.entity.DemonCow;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class DemonCowRenderer extends MobRenderer<DemonCow, DemonCowModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(DemonCowMod.MODID, "textures/entity/demon_cow.png");
    private static final RenderType GLOW = RenderType.eyes(ResourceLocation.fromNamespaceAndPath(DemonCowMod.MODID, "textures/entity/demon_cow_glow.png"));

    public DemonCowRenderer(EntityRendererProvider.Context context) {
        super(context, new DemonCowModel(context.bakeLayer(DemonCowModel.LAYER)), 5.0F);
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(DemonCow entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(DemonCow entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(DemonCow.SCALE, DemonCow.SCALE, DemonCow.SCALE);
    }

    @Override
    public boolean shouldRender(DemonCow entity, Frustum camera, double camX, double camY, double camZ) {
        // the model is far bigger than the hitbox, and beams reach even further
        return true;
    }

    @Override
    public void render(DemonCow entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
        int beam = entity.getBeam();
        if (beam == DemonCow.BEAM_NONE || !entity.isAlive()) {
            return;
        }
        // beam coordinates are relative to the entity's interpolated position
        Vec3 pos = entity.getPosition(partialTick);
        Vec3 target = new Vec3(entity.getBeamTarget()).subtract(pos);
        float yaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float stand = Mth.lerp(partialTick, entity.standAnimO, entity.standAnim);
        float pulse = 1.0F + 0.15F * Mth.sin((entity.tickCount + partialTick) * 1.3F);
        if (beam == DemonCow.BEAM_LASER) {
            for (int side = -1; side <= 1; side += 2) {
                Vec3 eye = DemonCow.eyePos(Vec3.ZERO, yaw, stand, side);
                BeamRenderer.beam(poseStack, buffers, eye, target, 0.45F * pulse, 255, 190, 170, 255, 20, 20);
            }
        } else {
            Vec3 udder = DemonCow.udderPos(Vec3.ZERO, yaw, stand);
            BeamRenderer.beam(poseStack, buffers, udder, target, 0.9F * pulse, 255, 255, 255, 215, 215, 225);
        }
    }
}
