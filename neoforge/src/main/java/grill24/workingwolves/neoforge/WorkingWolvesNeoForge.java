package grill24.workingwolves.neoforge;

import grill24.workingwolves.ModBlockEntityTypes;
import grill24.workingwolves.ModBlocks;
import grill24.workingwolves.ModCreativeTabs;
import grill24.workingwolves.ModDataComponents;
import grill24.workingwolves.ModItems;
import grill24.workingwolves.ModMenuTypes;
import grill24.workingwolves.ModSoundEvents;
import grill24.workingwolves.WorkingWolves;
import grill24.workingwolves.architectury.RegistrationApiSided;
import grill24.workingwolves.chunk.WolfChunkManager;
import grill24.workingwolves.command.DebugCommand;
import grill24.workingwolves.network.BedPacketHandlers;
import grill24.workingwolves.neoforge.NeoForgePacketRegistrar;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.world.item.CreativeModeTabs;

@Mod(WorkingWolves.MODID)
public class WorkingWolvesNeoForge {

    public WorkingWolvesNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        RegistrationApiSided.set(NeoForgeRegistrationApi.getInstance());

        // Config
        modContainer.registerConfig(ModConfig.Type.COMMON, NeoForgeConfig.SPEC);
        modEventBus.addListener(NeoForgeConfig::onLoad);
        modEventBus.addListener(NeoForgeConfig::onReload);

        // Register sound events first
        ModSoundEvents.registerSoundEvents();
        WorkingWolvesRegistriesNeoForge.SOUND_EVENTS.register(modEventBus);

        // Register data components second (order matters)
        ModDataComponents.registerDataComponents();
        WorkingWolvesRegistriesNeoForge.DATA_COMPONENT_TYPES.register(modEventBus);

        // Register items
        ModItems.registerItems();
        WorkingWolvesRegistriesNeoForge.ITEMS.register(modEventBus);

        // Register blocks
        ModBlocks.registerBlocks();
        WorkingWolvesRegistriesNeoForge.BLOCKS.register(modEventBus);

        // Register block entity types
        ModBlockEntityTypes.registerBlockEntityTypes();
        WorkingWolvesRegistriesNeoForge.BLOCK_ENTITY_TYPES.register(modEventBus);

        // Register creative tabs
        ModCreativeTabs.registerCreativeTabs();
        WorkingWolvesRegistriesNeoForge.CREATIVE_MODE_TABS.register(modEventBus);

        // Register commands
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> {
            DebugCommand.register(event.getDispatcher());
        });

        // Register menu types
        ModMenuTypes.DOG_BED_MENU_SUPPLIER = () -> WorkingWolvesRegistriesNeoForge.DOG_BED_MENU.value();
        WorkingWolvesRegistriesNeoForge.MENU_TYPES.register(modEventBus);

        // Register networking
        NeoForgePacketRegistrar.init(modEventBus);
        NeoForgePacketRegistrar.dispatchFromBedHandler = BedPacketHandlers::handleDispatch;
        NeoForgePacketRegistrar.recallFromBedHandler = BedPacketHandlers::handleRecall;

        // Add items to the vanilla TOOLS_AND_UTILITIES tab
        modEventBus.addListener(this::addCreative);

        // Register chunk loading for working wolves (every 20 ticks / 1 second)
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (event.getServer().getTickCount() % 20 == 0) {
                WolfChunkManager.getInstance().tick(event.getServer());
            }
        });

        WorkingWolves.LOGGER.info("Working Wolves initialized on NeoForge");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.LEATHER_COLLAR.value());
            event.accept(ModItems.IRON_STUDDED_COLLAR.value());
            event.accept(ModItems.GOLD_TRIMMED_COLLAR.value());
            event.accept(ModItems.DISPATCH_WHISTLE.value());
            event.accept(ModItems.RECALL_WHISTLE.value());
        }
    }
}
