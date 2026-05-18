package grill24.workingwolves.blockentity.expedition;

import grill24.workingwolves.blockentity.expedition.data.ExpeditionRegistries;
import grill24.workingwolves.blockentity.expedition.data.OreDiscoveryEntry;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class MinerEventHandler {

    static void rollDiscovery(Level level, int zone, Random rng, ExpeditionSimulator sim) {
        if (!(level instanceof ServerLevel sl)) return;

        Registry<OreDiscoveryEntry> reg = sl.registryAccess().lookupOrThrow(ExpeditionRegistries.ORE_DISCOVERY);

        List<OreDiscoveryEntry> pool = new ArrayList<>();
        List<Integer> poolWeights = new ArrayList<>();
        int totalWeight = 0;

        for (OreDiscoveryEntry entry : reg) {
            if (!entry.zones().contains(zone)) continue;
            if (entry.minCollarTier().isPresent() && sim.getCollarTier() < entry.minCollarTier().get()) continue;
            pool.add(entry);
            poolWeights.add(entry.weight());
            totalWeight += entry.weight();
        }

        if (pool.isEmpty()) return;

        int pick = rng.nextInt(totalWeight);
        int cursor = 0;
        OreDiscoveryEntry chosen = pool.get(pool.size() - 1);
        for (int i = 0; i < pool.size(); i++) {
            cursor += poolWeights.get(i);
            if (pick < cursor) { chosen = pool.get(i); break; }
        }

        var tableKey = (sim.isSilkTouch() && chosen.silkTouchLootTable().isPresent())
            ? chosen.silkTouchLootTable().get()
            : chosen.lootTable();

        List<ItemStack> drops = ExpeditionLootHelper.roll(sl, tableKey, sim.getBlockPos());
        sim.addPendingLoot(drops);

        boolean biomeBonusApplies = chosen.biomeBonusCategories()
            .map(cats -> cats.contains(sim.getBiomeCategory()))
            .orElse(false);
        if (biomeBonusApplies && chosen.biomeBonusLootTable().isPresent()) {
            sim.addPendingLoot(ExpeditionLootHelper.roll(sl, chosen.biomeBonusLootTable().get(), sim.getBlockPos()));
        }

        sim.addLogLine(chosen.journalLines().get(rng.nextInt(chosen.journalLines().size())));
    }
}
