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
        flatRect(pose, buffers, texture, x0, z0, x1, z1, y, 1.0F, argb);
    }

    /** As {@link #flatRect}, the texture's top left {@code uv} by {@code uv} of it only (a sheet drawn in a corner of its image). */
    static void flatRect(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, float x0, float z0, float x1, float z1,
            float y, float uv, int argb) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        float left = Math.max(x0, x1);
        float right = Math.min(x0, x1);
        float far = Math.max(z0, z1);
        float near = Math.min(z0, z1);
        put(out, m, last, left, y, far, 0, 0, argb, 0, 1, 0);
        put(out, m, last, left, y, near, 0, uv, argb, 0, 1, 0);
        put(out, m, last, right, y, near, uv, uv, argb, 0, 1, 0);
        put(out, m, last, right, y, far, uv, 0, argb, 0, 1, 0);
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
        uprightRect(pose, buffers, texture, x0, y0, x1, y1, z, 1.0F, argb);
    }

    static void uprightRect(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, float x0, float y0, float x1, float y1,
            float z, float uv, int argb) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        float left = Math.max(x0, x1);
        float right = Math.min(x0, x1);
        float top = Math.max(y0, y1);
        float bottom = Math.min(y0, y1);
        put(out, m, last, left, top, z, 0, 0, argb, 0, 0, -1);
        put(out, m, last, left, bottom, z, 0, uv, argb, 0, 0, -1);
        put(out, m, last, right, bottom, z, uv, uv, argb, 0, 0, -1);
        put(out, m, last, right, top, z, uv, 0, argb, 0, 0, -1);
    }

    /** A box from one corner to the other, every face carrying the same {@code u0,v0}..{@code u1,v1} of the texture, see-through allowed. */
    static void box(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, float x0, float y0, float z0, float x1, float y1,
                    float z1, float u0, float v0, float u1, float v1, int argb) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        float[][] faces = {
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0},
                {x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0},
                {x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, 0, 0, -1},
                {x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, 0, 0, 1},
                {x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, -1, 0, 0},
                {x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, 1, 0, 0},
        };
        for (float[] f : faces) {
            put(out, m, last, f[0], f[1], f[2], u0, v0, argb, f[12], f[13], f[14]);
            put(out, m, last, f[3], f[4], f[5], u0, v1, argb, f[12], f[13], f[14]);
            put(out, m, last, f[6], f[7], f[8], u1, v1, argb, f[12], f[13], f[14]);
            put(out, m, last, f[9], f[10], f[11], u1, v0, argb, f[12], f[13], f[14]);
        }
    }

    /** A roll lying along X from {@code x0} to {@code x1}, its axis at ({@code y}, {@code z}): eight faces of the texture round it, and its ends. */
    static void rollX(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, float x0, float x1, float y, float z, float radius,
                      float uv, int argb) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        int sides = 8;
        for (int i = 0; i < sides; i++) {
            double a0 = 2 * Math.PI * i / sides;
            double a1 = 2 * Math.PI * (i + 1) / sides;
            float y0 = y + radius * (float) Math.cos(a0);
            float z0 = z + radius * (float) Math.sin(a0);
            float y1 = y + radius * (float) Math.cos(a1);
            float z1 = z + radius * (float) Math.sin(a1);
            float ny = (float) Math.cos((a0 + a1) / 2);
            float nz = (float) Math.sin((a0 + a1) / 2);
            float v0 = uv * i / sides;
            float v1 = uv * (i + 1) / sides;
            put(out, m, last, x0, y0, z0, 0, v0, argb, 0, ny, nz);
            put(out, m, last, x0, y1, z1, 0, v1, argb, 0, ny, nz);
            put(out, m, last, x1, y1, z1, uv, v1, argb, 0, ny, nz);
            put(out, m, last, x1, y0, z0, uv, v0, argb, 0, ny, nz);
            // The ends: a fan from the axis, the paper's curl.
            put(out, m, last, x0, y, z, 0.3F * uv, 0.3F * uv, argb, -1, 0, 0);
            put(out, m, last, x0, y0, z0, 0.3F * uv, 0.35F * uv, argb, -1, 0, 0);
            put(out, m, last, x0, y1, z1, 0.35F * uv, 0.35F * uv, argb, -1, 0, 0);
            put(out, m, last, x0, y, z, 0.3F * uv, 0.3F * uv, argb, -1, 0, 0);
            put(out, m, last, x1, y, z, 0.3F * uv, 0.3F * uv, argb, 1, 0, 0);
            put(out, m, last, x1, y1, z1, 0.35F * uv, 0.35F * uv, argb, 1, 0, 0);
            put(out, m, last, x1, y0, z0, 0.3F * uv, 0.35F * uv, argb, 1, 0, 0);
            put(out, m, last, x1, y, z, 0.3F * uv, 0.3F * uv, argb, 1, 0, 0);
        }
    }

    private static void put(VertexConsumer out, Matrix4f m, PoseStack.Pose pose, float x, float y, float z, float u, float v, int argb,
            float nx, float ny, float nz) {
        out.addVertex(m, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }
}
