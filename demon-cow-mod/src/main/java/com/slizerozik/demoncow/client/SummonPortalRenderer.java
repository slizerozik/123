package com.slizerozik.demoncow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.slizerozik.demoncow.DemonCowMod;
import com.slizerozik.demoncow.entity.SummonPortal;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public class SummonPortalRenderer extends EntityRenderer<SummonPortal> {
    private static final ResourceLocation SWIRL = ResourceLocation.fromNamespaceAndPath(DemonCowMod.MODID, "textures/entity/portal_swirl.png");
    private static final ResourceLocation RING = ResourceLocation.fromNamespaceAndPath(DemonCowMod.MODID, "textures/entity/portal_ring.png");
    private static final int RAYS = 10;

    public SummonPortalRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SummonPortal entity) {
        return SWIRL;
    }

    @Override
    public boolean shouldRender(SummonPortal entity, Frustum camera, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public void render(SummonPortal entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float time = entity.getAge() + partialTick;
        float open = entity.getOpenScale(partialTick);

        this.renderSkyRays(entity, time, poseStack, buffers);

        if (open > 0.001F) {
            // ease out with a little overshoot so the portal "pops" open
            float eased = 1.0F - (1.0F - open) * (1.0F - open);
            float radius = SummonPortal.RADIUS * eased;
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
            disc(poseStack, buffers.getBuffer(RenderType.entityTranslucent(SWIRL)), radius * 0.86F, time * 1.6F, 0.0F, 255);
            disc(poseStack, buffers.getBuffer(RenderType.entityTranslucent(SWIRL)), radius * 0.86F, -time * 0.9F + 40.0F, 0.06F, 120);
            disc(poseStack, buffers.getBuffer(RenderType.entityTranslucent(RING)), radius, -time * 0.5F, 0.12F, 255);
            poseStack.popPose();
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    /** Upright textured disc facing local Z, spun around its centre. */
    private static void disc(PoseStack poseStack, VertexConsumer consumer, float radius, float spinDegrees, float zOffset, int alpha) {
        poseStack.pushPose();
        poseStack.translate(0, 0, zOffset);
        poseStack.mulPose(Axis.ZP.rotationDegrees(spinDegrees));
        PoseStack.Pose pose = poseStack.last();
        vertex(consumer, pose, -radius, -radius, 0, 1, alpha);
        vertex(consumer, pose, radius, -radius, 1, 1, alpha);
        vertex(consumer, pose, radius, radius, 1, 0, alpha);
        vertex(consumer, pose, -radius, radius, 0, 0, alpha);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v, int alpha) {
        consumer.addVertex(pose, x, y, 0)
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0, 0, 1);
    }

    /** Thin red rays from the sky around the ritual site; bright while summoning, faint afterwards. */
    private void renderSkyRays(SummonPortal entity, float time, PoseStack poseStack, MultiBufferSource buffers) {
        float open = entity.getOpenScale(0);
        boolean summoning = entity.getAge() < SummonPortal.OPEN_END;
        if (!summoning && open <= 0.001F) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
        RandomSource random = RandomSource.create(entity.getId() * 31L + 7);
        for (int i = 0; i < RAYS; i++) {
            double x = random.triangle(0, 34);
            double z = random.triangle(0, 34);
            float phase = random.nextFloat() * 100.0F;
            float flicker = 0.5F + 0.5F * Mth.sin(time * 0.15F + phase);
            float strength = summoning ? Mth.clamp(time / 40.0F, 0, 1) * flicker : 0.25F * flicker * open;
            int alpha = (int) (170 * strength);
            if (alpha < 8) {
                continue;
            }
            Vec3 bottom = new Vec3(x, -SummonPortal.CENTER_HEIGHT - 10, z);
            Vec3 top = new Vec3(x, 160, z);
            BeamRenderer.prism(poseStack, consumer, bottom, top, 0.9F, 200, 30, 40, alpha / 3);
            BeamRenderer.prism(poseStack, consumer, bottom, top, 0.3F, 255, 90, 90, alpha);
        }
    }
}
