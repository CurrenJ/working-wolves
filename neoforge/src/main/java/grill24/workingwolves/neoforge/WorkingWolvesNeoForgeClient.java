package grill24.workingwolves.neoforge;

import grill24.workingwolves.WorkingWolves;
import grill24.workingwolves.client.DogBedScreenData;
import grill24.workingwolves.client.WolfPreviewFloorRenderState;
import grill24.workingwolves.client.WolfPreviewFloorRenderer;
import grill24.workingwolves.compat.GelatinScreensCompat;
import grill24.workingwolves.network.WolfDataSyncPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

@Mod(value = WorkingWolves.MODID, dist = Dist.CLIENT)
public class WorkingWolvesNeoForgeClient {
    public WorkingWolvesNeoForgeClient(ModContainer container, IEventBus modEventBus) {
        NeoForgePacketRegistrar.wolfDataSyncHandler = this::applyClientData;
        NeoForgePacketRegistrar.bedStateHandler = DogBedScreenData::applyStatePacket;
        NeoForgePacketRegistrar.bedJournalUpdateHandler = DogBedScreenData::applyJournalUpdate;

        modEventBus.addListener(this::onRegisterPipRenderers);

        GelatinScreensCompat.init();
    }

    private void onRegisterPipRenderers(RegisterPictureInPictureRenderersEvent event) {
        event.register(WolfPreviewFloorRenderState.class,
                bufferSource -> new WolfPreviewFloorRenderer(bufferSource, Minecraft.getInstance().getEntityRenderDispatcher()));
    }

    private void applyClientData(WolfDataSyncPacket packet) {
        WolfDataSyncPacket.applyToClient(Minecraft.getInstance().level, packet);
    }
}
