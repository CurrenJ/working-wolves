package grill24.workingwolves.blockentity.expedition;

import grill24.workingwolves.blockentity.expedition.data.ExpeditionRegistries;
import grill24.workingwolves.blockentity.expedition.data.WoodDiscoveryEntry;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.Random;

class WoodcutterEventHandler {

    static void rollDiscovery(Level level, int zone, Random rng, ExpeditionSimulator sim) {
        if (!(level instanceof ServerLevel sl)) return;

        Registry<WoodDiscoveryEntry> reg = sl.registryAccess().lookupOrThrow(ExpeditionRegistries.WOOD_DISCOVERY);

        Optional<WoodDiscoveryEntry> entry = reg.stream()
            .filter(e -> e.woodBiomeId().equals(sim.getWoodBiome()))
            .findFirst();

        entry.ifPresent(e -> {
            sim.addPendingLoot(ExpeditionLootHelper.roll(sl, e.lootTable(), sim.getBlockPos()));
            sim.addLogLine(e.journalLines().get(rng.nextInt(e.journalLines().size())));
        });
    }
}
