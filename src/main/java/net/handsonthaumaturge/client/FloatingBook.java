package net.handsonthaumaturge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * A book open in the air beside a station, its pages turned one leaf at a time, after the floating recipe books of Pocky's
 * Hands-On Vanilla, Delight and Let's Do (MIT): it opens out of nothing, lies open in a shallow V, and a turn lifts the leaf off one
 * side and lays it on the other, the old page on its front and the new one on its back.
 *
 * <p>Drawn in a node's pixels, facing the front (the viewer at -Z), the spine on X = 0: the left page on +X, the right page on -X.
 * What is written on a page is the caller's ({@link PagePainter}), drawn centred on the page.
 */
final class FloatingBook {
    /** Draws page {@code page} (0 is the first left page), centred on the origin, facing the front. */
    interface PagePainter {
        void paint(PoseStack pose, MultiBufferSource buffers, int page);
    }

    private static final ResourceLocation COVER = ResourceLocation.withDefaultNamespace("textures/entity/enchanting_table_book.png");
    private static final ResourceLocation PAPER = ResourceLocation.fromNamespaceAndPath("thaumaturge", "textures/misc/parchment3.png");
    private static final float PAPER_UV = 150.0F / 256.0F;
    /** Page size, node pixels. */
    static final float PAGE_WIDE = 6.4F;
    static final float PAGE_HIGH = 9.0F;
    private static final float COVER_RIM = 0.45F;
    /** How far the open pages fold back from flat, radians: an open book is a shallow V, not a plank. */
    private static final float REST_FOLD = 0.16F;
    private static final float OPEN_TIME = 0.35F;
    private static final float FLIP_TIME = 0.4F;

    private float open;
    private float flip;
    private int flipWay;
    private int spread;
    private int fromSpread;

    /** The spread (two pages) open now, or being turned to. */
    int spread() {
        return spread;
    }

    /** Turns the leaves to this spread (one leaf's movement, whatever the distance). */
    void turnTo(int target) {
        if (target == spread) {
            return;
        }
        fromSpread = spread;
        flipWay = target > spread ? 1 : -1;
        spread = target;
        flip = 0.0001F;
    }

    void jumpTo(int target) {
        spread = target;
        flip = 0;
    }

    /** Opens or closes the book a frame's worth. */
    void frame(float dt, boolean wanted) {
        open = Mth.clamp(open + (wanted ? dt : -dt) / OPEN_TIME, 0.0F, 1.0F);
        if (flip > 0) {
            flip += dt / FLIP_TIME;
            if (flip >= 1) {
                flip = 0;
            }
        }
    }

    boolean shown() {
        return open > 0;
    }

    void render(PoseStack pose, MultiBufferSource buffers, PagePainter painter, float bob) {
        if (open <= 0) {
            return;
        }
        float eased = 1 - (1 - open) * (1 - open);
        pose.pushPose();
        pose.translate(0.0F, bob, 0.0F);
        pose.scale(0.35F + 0.65F * eased, 0.35F + 0.65F * eased, 0.35F + 0.65F * eased);
        // Closed, both halves fold away to the back; open, they lie a shallow V towards the viewer.
        float fold = REST_FOLD + (1 - eased) * (Mth.HALF_PI - REST_FOLD);
        int alpha = (int) (255 * Math.min(1.0F, open * 2.0F)) << 24;

        cover(pose, buffers, 1, fold, alpha);
        cover(pose, buffers, -1, fold, alpha);

        boolean turning = flip > 0 && eased >= 1;
        int leftPage = 2 * spread;
        int rightPage = 2 * spread + 1;
        if (turning && flipWay > 0) {
            leftPage = 2 * fromSpread;
        } else if (turning) {
            rightPage = 2 * fromSpread + 1;
        }
        page(pose, buffers, painter, 1, -fold, leftPage, 0.0F, alpha);
        page(pose, buffers, painter, -1, fold, rightPage, 0.0F, alpha);

        if (turning) {
            // The leaf: from the right side over the spine to the left (forward), or back again; the old page on its front.
            float t = flip * flip * (3 - 2 * flip);
            float angle = fold + t * (Mth.PI - 2 * fold);
            if (flipWay < 0) {
                angle = Mth.PI - angle;
            }
            float lift = -0.25F * Mth.sin(t * Mth.PI);
            if (angle < Mth.HALF_PI) {
                page(pose, buffers, painter, -1, angle, flipWay > 0 ? 2 * fromSpread + 1 : 2 * spread + 1, lift, alpha);
            } else {
                page(pose, buffers, painter, 1, angle - Mth.PI, flipWay > 0 ? 2 * spread : 2 * fromSpread, lift, alpha);
            }
        }
        pose.popPose();
    }

    /**
     * The book lying shut on the table, drawn in its node's pixels: leather boards top and bottom, the block of pages between
     * them, the spine along -X. Hidden while it is open in the air.
     */
    void renderClosed(PoseStack pose, MultiBufferSource buffers) {
        if (open > 0) {
            return;
        }
        float w = 3.6F;
        float d = 4.6F;
        float board = 0.3F;
        float pages = 1.1F;
        int leather = 0xFFFFFFFF;
        int edge = 0xFFE8DDB8;
        // Boards: the leather of the enchanting book's lid.
        box(pose, buffers, COVER, -w, 0.0F, -d, w, board, d, 0.0F, 0.0F, 6.0F / 64.0F, 10.0F / 32.0F, leather);
        box(pose, buffers, COVER, -w, board + pages, -d, w, 2 * board + pages, d, 0.0F, 0.0F, 6.0F / 64.0F, 10.0F / 32.0F, leather);
        // The pages, a little inside the boards, and the spine closing them on -X.
        box(pose, buffers, PAPER, -w + 0.3F, board, -d + 0.25F, w - 0.25F, board + pages, d - 0.25F, 0.0F, 0.0F, 0.1F, 0.6F, edge);
        box(pose, buffers, COVER, -w - 0.15F, 0.0F, -d, -w + 0.3F, 2 * board + pages, d, 0.0F, 0.0F, 1.0F / 64.0F, 10.0F / 32.0F, leather);
        // A ribbon hanging out at the foot, as the Thaumonomicon's.
        box(pose, buffers, PAPER, 1.0F, board + pages * 0.5F, -d - 0.9F, 1.6F, board + pages * 0.5F + 0.08F, -d + 0.3F,
                0.0F, 0.0F, 0.1F, 0.1F, 0xFF9A1E1E);
    }

    /** A box, every face carrying the same piece of texture. */
    private static void box(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, float x0, float y0, float z0, float x1,
                            float y1, float z1, float u0, float v0, float u1, float v1, int argb) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        float[][] faces = {
                {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0},
                {x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0},
                {x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1},
                {x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1},
                {x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0},
                {x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0},
        };
        for (float[] f : faces) {
            shade(out, m, last, f[0], f[1], f[2], u0, v1, argb, f[12], f[13], f[14]);
            shade(out, m, last, f[3], f[4], f[5], u1, v1, argb, f[12], f[13], f[14]);
            shade(out, m, last, f[6], f[7], f[8], u1, v0, argb, f[12], f[13], f[14]);
            shade(out, m, last, f[9], f[10], f[11], u0, v0, argb, f[12], f[13], f[14]);
        }
    }

    private static void shade(VertexConsumer out, Matrix4f m, PoseStack.Pose pose, float x, float y, float z, float u, float v, int argb,
                              float nx, float ny, float nz) {
        out.addVertex(m, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }

    /** One half of the cover behind its page: {@code side} 1 the left (+X), -1 the right. */
    private static void cover(PoseStack pose, MultiBufferSource buffers, int side, float fold, int alpha) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(side > 0 ? -fold : fold));
        float x0 = 0.0F;
        float x1 = side * (PAGE_WIDE + COVER_RIM);
        float h = PAGE_HIGH / 2 + COVER_RIM;
        // The enchanting book's leather: the lid's face on its texture (6 by 10 of 64 by 32).
        quad(pose, buffers, COVER, x0, -h, x1, h, 0.12F, 0.0F, 0.0F, 6.0F / 64.0F, 10.0F / 32.0F, alpha | 0xFFFFFF);
        pose.popPose();
    }

    /** One page: {@code side} 1 lies on +X from the spine, -1 on -X; turned {@code angle} about the spine. */
    private static void page(PoseStack pose, MultiBufferSource buffers, PagePainter painter, int side, float angle, int number,
                             float lift, int alpha) {
        pose.pushPose();
        pose.translate(0.0F, 0.0F, lift);
        pose.mulPose(Axis.YP.rotation(angle));
        quad(pose, buffers, PAPER, 0.0F, -PAGE_HIGH / 2, side * PAGE_WIDE, PAGE_HIGH / 2, 0.0F, 0.0F, 0.0F, PAPER_UV, PAPER_UV,
                alpha | 0xFFFFFF);
        if (alpha >>> 24 > 200) {
            pose.translate(side * PAGE_WIDE / 2, 0.0F, -0.03F);
            painter.paint(pose, buffers, number);
        }
        pose.popPose();
    }

    private static void quad(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, float x0, float y0, float x1, float y1,
                             float z, float u0, float v0, float u1, float v1, int argb) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        float left = Math.max(x0, x1);
        float right = Math.min(x0, x1);
        float top = Math.max(y0, y1);
        float bottom = Math.min(y0, y1);
        put(out, m, last, left, top, z, u0, v0, argb);
        put(out, m, last, left, bottom, z, u0, v1, argb);
        put(out, m, last, right, bottom, z, u1, v1, argb);
        put(out, m, last, right, top, z, u1, v0, argb);
        // And its back, so a leaf seen from behind while it turns is paper too.
        put(out, m, last, right, top, z, u1, v0, argb);
        put(out, m, last, right, bottom, z, u1, v1, argb);
        put(out, m, last, left, bottom, z, u0, v1, argb);
        put(out, m, last, left, top, z, u0, v0, argb);
    }

    private static void put(VertexConsumer out, Matrix4f m, PoseStack.Pose pose, float x, float y, float z, float u, float v, int argb) {
        out.addVertex(m, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0, 0, -1);
    }
}
