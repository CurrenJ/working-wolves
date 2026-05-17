package grill24.workingwolves.fabric;

import grill24.workingwolves.client.WolfPreviewFloorRenderer;
import grill24.workingwolves.compat.GelatinScreensCompat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;

public class WorkingWolvesFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricPacketRegistrar.registerClient();
        GelatinScreensCompat.init();

        PictureInPictureRendererRegistry.register(ctx ->
                new WolfPreviewFloorRenderer(ctx.bufferSource(), ctx.minecraft().getEntityRenderDispatcher()));
    }
}
