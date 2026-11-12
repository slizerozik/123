package com.slizerozik.demoncow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Untextured glowing beams: a solid core inside a translucent halo. */
public final class BeamRenderer {
    private BeamRenderer() {
    }

    public static void beam(PoseStack poseStack, MultiBufferSource buffers, Vec3 from, Vec3 to, float width,
            int coreR, int coreG, int coreB, int haloR, int haloG, int haloB) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
        prism(poseStack, consumer, from, to, width * 2.4F, haloR, haloG, haloB, 50);
        prism(poseStack, consumer, from, to, width * 1.6F, haloR, haloG, haloB, 100);
        prism(poseStack, consumer, from, to, width, coreR, coreG, coreB, 255);
    }

    /** Square prism between two points. */
    public static void prism(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 to, float width, int r, int g, int b, int a) {
        Vector3f axis = to.subtract(from).toVector3f();
        if (axis.lengthSquared() < 1.0E-4F) {
            return;
        }
        axis.normalize();
        Vector3f helper = Math.abs(axis.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f u = new Vector3f(axis).cross(helper).normalize().mul(width * 0.5F);
        Vector3f v = new Vector3f(axis).cross(u).normalize().mul(width * 0.5F);
        Vector3f p0 = from.toVector3f(), p1 = to.toVector3f();
        PoseStack.Pose pose = poseStack.last();
        float[][] corners = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
        for (int i = 0; i < 4; i++) {
            float[] c0 = corners[i], c1 = corners[(i + 1) % 4];
            vertex(consumer, pose, p0, u, v, c0, r, g, b, a);
            vertex(consumer, pose, p0, u, v, c1, r, g, b, a);
            vertex(consumer, pose, p1, u, v, c1, r, g, b, a);
            vertex(consumer, pose, p1, u, v, c0, r, g, b, a);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vector3f p, Vector3f u, Vector3f v, float[] c, int r, int g, int b, int a) {
        consumer.addVertex(pose, p.x + u.x * c[0] + v.x * c[1], p.y + u.y * c[0] + v.y * c[1], p.z + u.z * c[0] + v.z * c[1]).setColor(r, g, b, a);
    }
}
