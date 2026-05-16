package grill24.workingwolves.blockentity.expedition;

import grill24.workingwolves.blockentity.expedition.data.ExpeditionRegistries;
import grill24.workingwolves.blockentity.expedition.data.RareEventEntry;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class RareEventHandler {

    static boolean roll(Level level, int zone, Random rng, ExpeditionSimulator sim) {
        if (!(level instanceof ServerLevel sl)) return false;

        Registry<RareEventEntry> reg = sl.registryAccess().lookupOrThrow(ExpeditionRegistries.RARE_EVENT);

        List<RareEventEntry> pool = new ArrayList<>();
        int totalWeight = 0;
        for (RareEventEntry e : reg) {
            if (e.crossRole()) continue;
            if (!e.conditions().test(zone, sim.getBiomeCategory(), sim.getCollarTier(),
                    sim.hasMining(), sim.hasHunting(), sim.hasWoodcutting())) continue;
            pool.add(e);
            totalWeight += e.weight();
        }
        if (pool.isEmpty()) return false;

        int pick = rng.nextInt(totalWeight);
        int cursor = 0;
        RareEventEntry chosen = pool.get(pool.size() - 1);
        for (RareEventEntry e : pool) {
            cursor += e.weight();
            if (pick < cursor) { chosen = e; break; }
        }
        applyEvent(sl, rng, sim, chosen);
        return true;
    }

    static boolean rollCrossRole(Level level, Random rng, ExpeditionSimulator sim) {
        if (!(level instanceof ServerLevel sl)) return false;

        Registry<RareEventEntry> reg = sl.registryAccess().lookupOrThrow(ExpeditionRegistries.RARE_EVENT);

        List<RareEventEntry> pool = new ArrayList<>();
        int totalWeight = 0;
        for (RareEventEntry e : reg) {
            if (!e.crossRole()) continue;
            if (!e.conditions().test(0, sim.getBiomeCategory(), sim.getCollarTier(),
                    sim.hasMining(), sim.hasHunting(), sim.hasWoodcutting())) continue;
            pool.add(e);
            totalWeight += e.weight();
        }
        if (pool.isEmpty()) return false;

        int pick = rng.nextInt(totalWeight);
        int cursor = 0;
        RareEventEntry chosen = pool.get(pool.size() - 1);
        for (RareEventEntry e : pool) {
            cursor += e.weight();
            if (pick < cursor) { chosen = e; break; }
        }
        applyEvent(sl, rng, sim, chosen);
        return true;
    }

    private static void applyEvent(ServerLevel sl, Random rng, ExpeditionSimulator sim, RareEventEntry e) {
        sim.addLogLine(e.journalLines().get(rng.nextInt(e.journalLines().size())));
        e.lootTable().ifPresent(key ->
            sim.getPendingLoot().addAll(ExpeditionLootHelper.roll(sl, key, sim.getBlockPos())));
        e.satiationDelta().ifPresent(sim::adjustSatiation);
        e.applyHazard().ifPresent(h ->
            sim.applyHazardCost(sl, rng, ExpeditionSimulator.HazardLevel.valueOf(h)));
        if (e.removeLastLoot() && !sim.getPendingLoot().isEmpty())
            sim.getPendingLoot().remove(sim.getPendingLoot().size() - 1);
    }
}
