package grill24.workingwolves.fabric;

import grill24.workingwolves.blockentity.expedition.data.ExpeditionRegistries;
import grill24.workingwolves.blockentity.expedition.data.MobEncounterEntry;
import grill24.workingwolves.blockentity.expedition.data.OreDiscoveryEntry;
import grill24.workingwolves.blockentity.expedition.data.WoodDiscoveryEntry;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;

public class ExpeditionRegistriesFabric {

    public static void register() {
        DynamicRegistries.registerSynced(ExpeditionRegistries.MOB_ENCOUNTER, MobEncounterEntry.CODEC, MobEncounterEntry.CODEC);
        DynamicRegistries.registerSynced(ExpeditionRegistries.ORE_DISCOVERY, OreDiscoveryEntry.CODEC, OreDiscoveryEntry.CODEC);
        DynamicRegistries.registerSynced(ExpeditionRegistries.WOOD_DISCOVERY, WoodDiscoveryEntry.CODEC, WoodDiscoveryEntry.CODEC);
    }
}
