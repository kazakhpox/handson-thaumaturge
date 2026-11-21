package net.handsonthaumaturge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.api.client.DiegesisClient;

/**
 * While a player works at the arcane workbench, the station draws its wand itself: Thaumaturge's wand display over it rests. At
 * the research table its renderer goes on drawing the inkwell (see ResearchTableTopMixin for the papers and scroll it leaves out).
 */
@Pseudo
@Mixin(targets = {"com.leclowndu93150.thaumaturge.client.render.blockentity.ResearchTableRenderer",
        "com.leclowndu93150.thaumaturge.client.render.blockentity.WorkbenchWandDisplayRenderer"}, remap = false)
abstract class StationRenderersMixin {
    // The bridge every caller goes through (BlockEntityRenderer's own signature), so no Thaumaturge type is named here.
    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void handson$restWhileWorked(BlockEntity table, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
            int overlay, CallbackInfo ci) {
        boolean worked = DiegesisClient.focusedStation()
                .filter(view -> view.isInUse() && view.pos().distManhattan(table.getBlockPos()) <= 1).isPresent();
        if (!worked) {
            net.handsonthaumaturge.client.TableRendering.WORKED.set(false);
        } else if (table.getClass().getSimpleName().contains("ResearchTable")) {
            // The table's own inkwell stays; ResearchTableTopMixin leaves out its papers and scroll.
            net.handsonthaumaturge.client.TableRendering.WORKED.set(true);
        } else {
            ci.cancel();
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At("RETURN"), require = 0)
    private void handson$done(BlockEntity table, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
            int overlay, CallbackInfo ci) {
        net.handsonthaumaturge.client.TableRendering.WORKED.set(false);
    }
}
