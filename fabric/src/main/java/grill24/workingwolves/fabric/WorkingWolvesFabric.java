package grill24.workingwolves.fabric;

import grill24.workingwolves.ModBlockEntityTypes;
import grill24.workingwolves.ModBlocks;
import grill24.workingwolves.ModCreativeTabs;
import grill24.workingwolves.ModDataComponents;
import grill24.workingwolves.ModItems;
import grill24.workingwolves.ModSoundEvents;
import grill24.workingwolves.WorkingWolves;
import grill24.workingwolves.architectury.RegistrationApiSided;
import grill24.workingwolves.chunk.WolfChunkManager;
import grill24.workingwolves.command.DebugCommand;
import grill24.workingwolves.fabric.FabricPacketRegistrar;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class WorkingWolvesFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        RegistrationApiSided.set(FabricRegistrationApi.getInstance());

        // Config - load from Fabric config file
        FabricConfig.load();

        // Register sound events first
        ModSoundEvents.registerSoundEvents();

        // Register data components second (order matters)
        ModDataComponents.registerDataComponents();

        // Register items, blocks, block entity types, and creative tabs
        ModItems.registerItems();
        ModBlocks.registerBlocks();
        ModBlockEntityTypes.registerBlockEntityTypes();
        ModCreativeTabs.registerCreativeTabs();

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
            DebugCommand.register(dispatcher);
        });

        // Register networking
        FabricPacketRegistrar.registerServer();

        // Register chunk loading for working wolves (every 20 ticks / 1 second)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                WolfChunkManager.getInstance().tick(server);
            }
        });

        WorkingWolves.LOGGER.info("Working Wolves initialized on Fabric");
    }
}
