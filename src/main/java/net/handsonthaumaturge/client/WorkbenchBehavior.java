package net.handsonthaumaturge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;

import net.handsonthaumaturge.HandsOnThaumaturge;
import net.handsonthaumaturge.station.WorkbenchStation;

/**
 * The arcane workbench at work: a sigil glows under each floating crystal in its primal's colour, faint while it waits and
 * bright and turning while the recipe on the grid burns that crystal; over the far edge stand the vis in the aura and what the
 * recipe costs, red when the aura cannot pay it. A craft rings and throws sparks of magic from the result.
 */
public final class WorkbenchBehavior implements StationBehavior {
    private static final ResourceLocation SIGIL = HandsOnThaumaturge.id("textures/misc/sigil.png");
    /** Thaumaturge's primal colours, in the crystal slots' order (aer, ignis, aqua, terra, ordo, perditio). */
    private static final int[] COLOURS = {0xFFFF7E, 0xFF5A01, 0x3CD4FC, 0x56C000, 0xD5D4EC, 0x7A4A9A};
    private static final float SIGIL_SIZE = 5.0F;

    /** How lit each sigil is now (0 faint, 1 burning), eased towards what the recipe asks. */
    private final float[] glow = new float[6];
    private float clock;

    public WorkbenchBehavior(StationView view) {
    }

    @Override
    public void onSceneReady(StationView view) {
        for (int i = 0; i < 6; i++) {
            int index = i;
            view.node("sigil_" + i).ifPresent(node -> node.setPainter((pose, buffers, light) -> paintSigil(view, index, pose, buffers)));
        }
        view.node("vis").ifPresent(node -> node.setPainter((pose, buffers, light) -> paintVis(view, pose, buffers)));
    }

    @Override
    public void frame(StationView view, float dt) {
        clock += dt;
        int burns = view.state().data().getInt(WorkbenchStation.BURNS);
        for (int i = 0; i < 6; i++) {
            float target = (burns & 1 << i) != 0 ? 1.0F : 0.0F;
            glow[i] += (target - glow[i]) * Math.min(1.0F, dt * 5.0F);
        }
    }

    private void paintSigil(StationView view, int index, PoseStack pose, MultiBufferSource buffers) {
        float lit = glow[index];
        if (!view.isInUse() && lit < 0.01F) {
            return;
        }
        int alpha = Math.round(70 + 185 * lit);
        int argb = alpha << 24 | COLOURS[index];
        float half = SIGIL_SIZE * (0.9F + 0.1F * lit + 0.04F * lit * Mth.sin(clock * 4.0F)) / 2.0F;
        pose.pushPose();
        // Faint ones rest; burning ones turn, each its own way.
        pose.mulPose(Axis.YP.rotationDegrees(index * 60.0F + clock * 40.0F * lit * (index % 2 == 0 ? 1 : -1)));
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucentEmissive(SIGIL));
        Matrix4f m = pose.last().pose();
        PoseStack.Pose last = pose.last();
        // Both windings: seen from above and, as the camera swoops in, from wherever it is.
        vertex(out, m, last, -half, -half, 0, 0, argb);
        vertex(out, m, last, -half, half, 0, 1, argb);
        vertex(out, m, last, half, half, 1, 1, argb);
        vertex(out, m, last, half, -half, 1, 0, argb);
        vertex(out, m, last, half, -half, 1, 0, argb);
        vertex(out, m, last, half, half, 1, 1, argb);
        vertex(out, m, last, -half, half, 0, 1, argb);
        vertex(out, m, last, -half, -half, 0, 0, argb);
        pose.popPose();
    }

    private static void vertex(VertexConsumer out, Matrix4f m, PoseStack.Pose pose, float x, float z, float u, float v, int argb) {
        out.addVertex(m, x, 0.0F, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private void paintVis(StationView view, PoseStack pose, MultiBufferSource buffers) {
        if (!view.isInUse()) {
            return;
        }
        CompoundTag data = view.state().data();
        int vis = data.getInt(WorkbenchStation.VIS);
        int cost = data.contains(WorkbenchStation.COST) ? data.getInt(WorkbenchStation.COST) : -1;
        boolean short_ = cost > vis;
        TextPainter.draw(pose, buffers, Component.translatable("handson_thaumaturge.workbench.vis", vis), 1.2F, 0.9F,
                short_ ? 0xFFEE6E6E : 0xFFB4B4FF);
        if (cost > 0) {
            TextPainter.draw(pose, buffers, Component.translatable("handson_thaumaturge.workbench.cost", cost), 1.2F, -0.7F,
                    0xFFC0FFFF);
        }
    }

    @Override
    public void onEffect(StationView view, ResourceLocation effect, String node, CompoundTag data) {
        if (!WorkbenchStation.EFFECT_CRAFTED.equals(effect)) {
            return;
        }
        view.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, "result", 1.0F, 1.2F);
        view.spawnParticles(com.pockyl.diegesis.api.client.DiegesisParticles.GLINT, "result", 14, 0xFFC890FF);
    }
}
