package grill24.workingwolves.mixin;

import com.mojang.logging.LogUtils;
import com.mojang.logging.LogUtils;
import grill24.workingwolves.api.IWorkingWolf;
import grill24.workingwolves.client.layer.WolfHeldItemLayer;
import grill24.workingwolves.client.layer.WolfHeldItemTracker;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(WolfRenderer.class)
public abstract class WolfRendererMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Method ADD_LAYER;
    private static final Field ITEM_MODEL_RESOLVER;

    static {
        Method addLayer = null;
        Field resolver = null;
        try {
            addLayer = LivingEntityRenderer.class.getDeclaredMethod("addLayer", RenderLayer.class);
            addLayer.setAccessible(true);
            resolver = LivingEntityRenderer.class.getDeclaredField("itemModelResolver");
            resolver.setAccessible(true);
            LOGGER.info("WolfRendererMixin: reflection OK");
        } catch (Exception e) {
            LOGGER.error("WolfRendererMixin: reflection FAILED", e);
        }
        ADD_LAYER = addLayer;
        ITEM_MODEL_RESOLVER = resolver;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void workingwolves$addHeldItemLayer(CallbackInfo ci) {
        if (ADD_LAYER == null) {
            LOGGER.error("WolfRendererMixin: ADD_LAYER is null, cannot add layer");
            return;
        }
        try {
            ADD_LAYER.invoke(this, new WolfHeldItemLayer((WolfRenderer) (Object) this));
            LOGGER.info("WolfRendererMixin: layer added successfully");
        } catch (Exception e) {
            LOGGER.error("WolfRendererMixin: addLayer FAILED", e);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void workingwolves$extractHeldItem(Wolf entity, WolfRenderState state, float partialTicks, CallbackInfo ci) {
        WolfHeldItemTracker.heldItem.clear();
        if (ITEM_MODEL_RESOLVER == null) return;
        ItemStack mouthItem = ((IWorkingWolf) (Object) entity).workingwolves$getMouthItem();
        if (!mouthItem.isEmpty()) {
            try {
                ItemModelResolver resolver = (ItemModelResolver) ITEM_MODEL_RESOLVER.get(this);
                resolver.updateForLiving(WolfHeldItemTracker.heldItem, mouthItem, ItemDisplayContext.GROUND, entity);
            } catch (Exception e) {
                LOGGER.error("WolfRendererMixin: extractRenderState FAILED", e);
            }
        }
    }
}
