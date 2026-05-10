package grill24.workingwolves.client.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import grill24.workingwolves.Config;
import grill24.workingwolves.mixin.WolfModelAccessor;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class WolfHeldItemLayer extends RenderLayer<WolfRenderState, WolfModel> {

    public WolfHeldItemLayer(RenderLayerParent<WolfRenderState, WolfModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(
        PoseStack poseStack,
        SubmitNodeCollector collector,
        int lightCoords,
        WolfRenderState state,
        float yRot,
        float xRot
    ) {
        var heldItem = WolfHeldItemTracker.heldItem;
        if (heldItem.isEmpty()) return;

        boolean isBaby = state.isBaby;
        ModelPart head = ((WolfModelAccessor) this.getParentModel()).getHead();
        poseStack.pushPose();
        poseStack.translate(head.x / 16.0F, head.y / 16.0F, head.z / 16.0F);
        if (isBaby) {
            poseStack.scale(0.75F, 0.75F, 0.75F);
        }
        poseStack.mulPose(Axis.ZP.rotation(state.headRollAngle));
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
        poseStack.mulPose(Axis.XP.rotationDegrees(xRot));
        if (isBaby) {
            poseStack.translate(
                Config.mouthOffsetX + 0.01F,
                Config.mouthOffsetY - 0.02F,
                Config.mouthOffsetZ + 0.05F);
        } else {
            poseStack.translate(Config.mouthOffsetX, Config.mouthOffsetY, Config.mouthOffsetZ);
        }
        poseStack.mulPose(Axis.XP.rotationDegrees(Config.mouthRotX));
        poseStack.mulPose(Axis.YP.rotationDegrees(Config.mouthRotY));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Config.mouthRotZ));
        heldItem.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}
