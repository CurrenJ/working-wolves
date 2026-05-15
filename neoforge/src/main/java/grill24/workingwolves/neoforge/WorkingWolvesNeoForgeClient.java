package grill24.workingwolves.neoforge;

import grill24.workingwolves.WorkingWolves;
import grill24.workingwolves.client.DogBedScreenData;
import grill24.workingwolves.compat.GelatinScreensCompat;
import grill24.workingwolves.network.WolfDataSyncPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = WorkingWolves.MODID, dist = Dist.CLIENT)
public class WorkingWolvesNeoForgeClient {
    public WorkingWolvesNeoForgeClient(ModContainer container, IEventBus modEventBus) {
        NeoForgePacketRegistrar.wolfDataSyncHandler = this::applyClientData;
        NeoForgePacketRegistrar.bedStateHandler = DogBedScreenData::applyStatePacket;
        NeoForgePacketRegistrar.bedJournalUpdateHandler = DogBedScreenData::applyJournalUpdate;

        GelatinScreensCompat.init();
    }

    private void applyClientData(WolfDataSyncPacket packet) {
        WolfDataSyncPacket.applyToClient(Minecraft.getInstance().level, packet);
    }
}
