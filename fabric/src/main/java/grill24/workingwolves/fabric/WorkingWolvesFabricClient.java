package grill24.workingwolves.fabric;

import net.fabricmc.api.ClientModInitializer;

public class WorkingWolvesFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricPacketRegistrar.registerClient();
    }
}
