package grill24.workingwolves.blockentity.expedition;

import grill24.workingwolves.blockentity.expedition.data.ExpeditionRegistries;
import grill24.workingwolves.blockentity.expedition.data.MobEncounterEntry;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class HunterEventHandler {

    static void rollDiscovery(Level level, int zone, Random rng, ExpeditionSimulator sim) {
        if (!(level instanceof ServerLevel sl)) return;

        Registry<MobEncounterEntry> reg = sl.registryAccess().lookupOrThrow(ExpeditionRegistries.MOB_ENCOUNTER);

        List<MobEncounterEntry> pool = new ArrayList<>();
        List<Integer> poolWeights = new ArrayList<>();
        int totalWeight = 0;

        for (MobEncounterEntry entry : reg) {
            if (!entry.zones().contains(zone)) continue;
            if (entry.requiredBiomeCategories().isPresent()
                    && !entry.requiredBiomeCategories().get().contains(sim.getBiomeCategory())) continue;
            if (entry.minCollarTier().isPresent() && sim.getCollarTier() < entry.minCollarTier().get()) continue;
            pool.add(entry);
            poolWeights.add(entry.weight());
            totalWeight += entry.weight();
        }

        if (pool.isEmpty()) return;

        int pick = rng.nextInt(totalWeight);
        int cursor = 0;
        MobEncounterEntry chosen = pool.get(pool.size() - 1);
        for (int i = 0; i < pool.size(); i++) {
            cursor += poolWeights.get(i);
            if (pick < cursor) { chosen = pool.get(i); break; }
        }

        List<ItemStack> drops = ExpeditionLootHelper.roll(sl, chosen.lootTable(), sim.getBlockPos());
        sim.addPendingLoot(drops);
        sim.addLogLine(chosen.journalLines().get(rng.nextInt(chosen.journalLines().size())));
    }
}
