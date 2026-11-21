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
 * While a player works at the research table or the arcane workbench, the station draws what lies on it itself: Thaumaturge's
 * own renderers of them (the table's papers, inkwell and scroll; the wand shown over the workbench) would lie over that, so they
 * rest until the station is left.
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
        DiegesisClient.focusedStation().ifPresent(view -> {
            if (view.isInUse() && view.pos().equals(table.getBlockPos())) {
                ci.cancel();
            }
        });
    }
}
