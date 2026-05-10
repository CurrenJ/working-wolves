package grill24.workingwolves.client.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import grill24.workingwolves.Config;
import grill24.workingwolves.mixin.WolfModelAccessor;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.IdentityHashMap;
import java.util.Map;

public class WolfHeldItemLayer extends RenderLayer<WolfRenderState, WolfModel> {

    private static final Map<WolfRenderer, WolfHeldItemLayer> layers = new IdentityHashMap<>();

    public static WolfHeldItemLayer getLayer(WolfRenderer renderer) {
        return layers.get(renderer);
    }

    // Allocated fresh each extractRenderState to avoid cross-wolf contamination
    // since ItemStackRenderState stores mutable quads rendered by reference
    public ItemStackRenderState heldItem = new ItemStackRenderState();
    public boolean isCrossbowMouthItem;

    public WolfHeldItemLayer(RenderLayerParent<WolfRenderState, WolfModel> renderer) {
        super(renderer);
        layers.put((WolfRenderer) renderer, this);
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
        } else if (isCrossbowMouthItem) {
            poseStack.translate(0.000F, 0.094F, -0.375F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(0.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0F));
            heldItem.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
            return;
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
