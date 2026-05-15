package grill24.workingwolves.fabric;

import grill24.workingwolves.compat.GelatinScreensCompat;
import net.fabricmc.api.ClientModInitializer;

public class WorkingWolvesFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricPacketRegistrar.registerClient();
        GelatinScreensCompat.init();
    }
}
