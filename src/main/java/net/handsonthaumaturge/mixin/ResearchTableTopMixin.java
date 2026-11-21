package net.handsonthaumaturge.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * While the research table is worked as a station, Thaumaturge's renderer of it draws only its inkwell and quill (when the scribing
 * tools are in): the papers and the scroll it would lay on the table give way to the station's own sheet.
 */
@Pseudo
@Mixin(targets = "com.leclowndu93150.thaumaturge.client.render.blockentity.ResearchTableRenderer", remap = false)
abstract class ResearchTableTopMixin {
    private static final String RENDER = "render(Lcom/leclowndu93150/thaumaturge/content/research/table/BlockEntityResearchTable;"
            + "FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V";

    @WrapWithCondition(method = RENDER, require = 0, at = @At(value = "INVOKE",
            target = "Lcom/leclowndu93150/thaumaturge/client/render/blockentity/ResearchTableTop;renderSheets(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private boolean handson$papers(PoseStack pose, MultiBufferSource buffers, int light) {
        return !net.handsonthaumaturge.client.TableRendering.WORKED.get();
    }

    @WrapWithCondition(method = RENDER, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"))
    private boolean handson$scroll(ModelPart part, PoseStack pose, VertexConsumer consumer, int light, int overlay) {
        return !net.handsonthaumaturge.client.TableRendering.WORKED.get();
    }

    @WrapWithCondition(method = RENDER, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private boolean handson$ribbon(ModelPart part, PoseStack pose, VertexConsumer consumer, int light, int overlay, int colour) {
        return !net.handsonthaumaturge.client.TableRendering.WORKED.get();
    }
}
