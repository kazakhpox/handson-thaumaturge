package net.handsonthaumaturge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Textured, tinted squares and strips drawn in a node's pixels, lit as if glowing: the research table's ink and icons. */
final class Quads {
    private Quads() {
    }

    /** A square lying face up, centred on ({@code x}, {@code z}) at height {@code y}; the texture's top towards +Z (away). */
    static void flat(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, float x, float y, float z, float half, int argb) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        // Seen from the front and above: the texture's left is the screen's left (+X), its top the far side (+Z).
        put(out, m, last, x + half, y, z + half, 0, 0, argb, 0, 1, 0);
        put(out, m, last, x + half, y, z - half, 0, 1, argb, 0, 1, 0);
        put(out, m, last, x - half, y, z - half, 1, 1, argb, 0, 1, 0);
        put(out, m, last, x - half, y, z + half, 1, 0, argb, 0, 1, 0);
    }

    /** A rectangle lying face up from ({@code x0}, {@code z0}) to ({@code x1}, {@code z1}), the whole texture on it. */
    static void flatRect(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, float x0, float z0, float x1, float z1,
            float y, int argb) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        float left = Math.max(x0, x1);
        float right = Math.min(x0, x1);
        float far = Math.max(z0, z1);
        float near = Math.min(z0, z1);
        put(out, m, last, left, y, far, 0, 0, argb, 0, 1, 0);
        put(out, m, last, left, y, near, 0, 1, argb, 0, 1, 0);
        put(out, m, last, right, y, near, 1, 1, argb, 0, 1, 0);
        put(out, m, last, right, y, far, 1, 0, argb, 0, 1, 0);
    }

    /** A line of ink lying face up from one point to another, {@code width} wide. */
    static void flatLine(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, float x0, float z0, float x1, float z1,
            float y, float width, int argb) {
        float dx = x1 - x0;
        float dz = z1 - z0;
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4F) {
            return;
        }
        pose.pushPose();
        pose.translate(x0, y, z0);
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(dx, dz)));
        flatRect(pose, buffers, texture, -width / 2.0F, 0.0F, width / 2.0F, length, 0.0F, argb);
        pose.popPose();
    }

    /** A square standing upright facing the front (-Z), centred on ({@code x}, {@code y}) at depth {@code z}. */
    static void upright(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, float x, float y, float z, float half,
            int argb) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        // Seen from the front the screen's left is +X: the texture's left there, its top up.
        put(out, m, last, x + half, y + half, z, 0, 0, argb, 0, 0, -1);
        put(out, m, last, x + half, y - half, z, 0, 1, argb, 0, 0, -1);
        put(out, m, last, x - half, y - half, z, 1, 1, argb, 0, 0, -1);
        put(out, m, last, x - half, y + half, z, 1, 0, argb, 0, 0, -1);
    }

    /** An upright rectangle facing the front, from ({@code x0}, {@code y0}) to ({@code x1}, {@code y1}). */
    static void uprightRect(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, float x0, float y0, float x1, float y1,
            float z, int argb) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        float left = Math.max(x0, x1);
        float right = Math.min(x0, x1);
        float top = Math.max(y0, y1);
        float bottom = Math.min(y0, y1);
        put(out, m, last, left, top, z, 0, 0, argb, 0, 0, -1);
        put(out, m, last, left, bottom, z, 0, 1, argb, 0, 0, -1);
        put(out, m, last, right, bottom, z, 1, 1, argb, 0, 0, -1);
        put(out, m, last, right, top, z, 1, 0, argb, 0, 0, -1);
    }

    private static void put(VertexConsumer out, Matrix4f m, PoseStack.Pose pose, float x, float y, float z, float u, float v, int argb,
            float nx, float ny, float nz) {
        out.addVertex(m, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }
}
