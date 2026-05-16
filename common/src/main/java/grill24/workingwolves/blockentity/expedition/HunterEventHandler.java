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

    static void rollEvent(Level level, int zone, ExpeditionSimulator sim) {
        Random rng = new Random(level.getGameTime() + sim.getElapsedTicks() + 1);
        int[] weights = switch (zone) {
            case 0 -> new int[]{50, 40, 10};
            case 1 -> new int[]{30, 45, 25};
            default -> new int[]{20, 35, 45};
        };
        int roll = rng.nextInt(100);

        if (roll < weights[0]) {
            String[] travelLines = {
                "Followed a scent through the trees.",
                "Something watched from the ridge. Did not follow.",
                "Tracks in the dirt. Recent.",
                "The forest grew quiet.",
                "Distant howl. Not ours.",
                "Old campfire. Cold for weeks. Someone was here.",
                "Wind shifted. Catalogued seven new smells.",
                "Fog sat low. Walked through it anyway.",
                "A sound like digging, far off. Not me.",
                "The trees here grow wrong. Filed for later.",
                "Crossed the creek twice. Lost count after that.",
                "Scent of iron in the air. Getting closer."
            };
            sim.addLogLine(travelLines[rng.nextInt(travelLines.length)]);
        } else if (roll < weights[0] + weights[1]) {
            rollDiscovery(level, zone, rng, sim);
        } else {
            ExpeditionSimulator.HazardLevel hl = ExpeditionSimulator.pickHazardLevel(zone, rng);
            rollHazard(level, rng, hl, sim);
        }
    }

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

        List<ItemStack> drops = ExpeditionLootHelper.roll(sl, chosen.lootTable(), sl.getServer() != null ? sim.getBlockPos() : sim.getBlockPos());
        sim.getPendingLoot().addAll(drops);
        sim.addLogLine(chosen.journalLines().get(rng.nextInt(chosen.journalLines().size())));
    }

    static void rollHazard(Level level, Random rng, ExpeditionSimulator.HazardLevel hl, ExpeditionSimulator sim) {
        String[] lines = switch (hl) {
            case LIGHT -> new String[]{
                "Got bit. Not badly. Kept going.",
                "Wrong side of a ravine. Had to double back.",
                "Ambushed. Recovered faster than expected.",
                "Something stirred in the brush. Moved on.",
                "Lava nearby. Backed off."
            };
            case MODERATE -> new String[]{
                "Three of them at once. Held on.",
                "Cornered briefly. Found a way out.",
                "Took a hit. Kept moving.",
                "Outnumbered. Fought anyway.",
                "Pack of them. Retreated and regrouped."
            };
            case SEVERE -> new String[]{
                "Too many. Barely got clear.",
                "Took the worst of it. Still here.",
                "Nearly didn't make it out. Did.",
                "The pack was bigger than it looked. Ran.",
                "Something found me before I found it. Cost me."
            };
        };
        sim.addLogLine(lines[rng.nextInt(lines.length)]);
        sim.applyHazardCost(level, rng, hl);
    }
}
