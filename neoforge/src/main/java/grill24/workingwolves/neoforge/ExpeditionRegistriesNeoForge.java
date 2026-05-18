package grill24.workingwolves.neoforge;

import grill24.workingwolves.blockentity.expedition.data.ExpeditionLifecycleEntry;
import grill24.workingwolves.blockentity.expedition.data.ExpeditionRegistries;
import grill24.workingwolves.blockentity.expedition.data.MobEncounterEntry;
import grill24.workingwolves.blockentity.expedition.data.OreDiscoveryEntry;
import grill24.workingwolves.blockentity.expedition.data.RareEventEntry;
import grill24.workingwolves.blockentity.expedition.data.RoleEntry;
import grill24.workingwolves.blockentity.expedition.data.WoodDiscoveryEntry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

public class ExpeditionRegistriesNeoForge {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ExpeditionRegistriesNeoForge::onDataPackRegistry);
    }

    private static void onDataPackRegistry(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(ExpeditionRegistries.MOB_ENCOUNTER, MobEncounterEntry.CODEC, MobEncounterEntry.CODEC);
        event.dataPackRegistry(ExpeditionRegistries.ORE_DISCOVERY, OreDiscoveryEntry.CODEC, OreDiscoveryEntry.CODEC);
        event.dataPackRegistry(ExpeditionRegistries.WOOD_DISCOVERY, WoodDiscoveryEntry.CODEC, WoodDiscoveryEntry.CODEC);
        event.dataPackRegistry(ExpeditionRegistries.RARE_EVENT, RareEventEntry.CODEC, RareEventEntry.CODEC);
        event.dataPackRegistry(ExpeditionRegistries.ROLE, RoleEntry.CODEC, RoleEntry.CODEC);
        event.dataPackRegistry(ExpeditionRegistries.EXPEDITION_LIFECYCLE, ExpeditionLifecycleEntry.CODEC, ExpeditionLifecycleEntry.CODEC);
    }
}
