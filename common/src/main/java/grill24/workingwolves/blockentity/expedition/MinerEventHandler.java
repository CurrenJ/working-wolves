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

    static void rollEvent(Level level, int zone, ExpeditionSimulator sim) {
        Random rng = new Random(level.getGameTime() + sim.getElapsedTicks() + 2);
        int[] weights = switch (zone) {
            case 0 -> new int[]{50, 40, 10};
            case 1 -> new int[]{30, 45, 25};
            default -> new int[]{20, 35, 45};
        };
        int roll = rng.nextInt(100);

        if (roll < weights[0]) {
            String[] travelLines = zone == 0 ? new String[]{
                "Entrance shaft. Air still tastes of surface.",
                "Thin seam of chalk in the wall. No ore, but promising.",
                "Old torch stub in the stone. Someone dug here before.",
                "Rootlets pushing through the ceiling. Still near the surface.",
                "Pebbles underfoot. Smooth, water-worn. Ancient stream bed.",
                "Low ceiling. Had to press close to the wall.",
                "Patch of moss on the north face. Moisture in the rock.",
                "Faint dripping somewhere ahead. Getting deeper.",
                "Gravel chute. Held still until it settled.",
                "Soil giving way to true stone. The real work starts here."
            } : zone == 1 ? new String[]{
                "Aquifer seeping through a crack. Floor slick with it.",
                "Gallery opens up. Ceiling lost in the dark above.",
                "Calcite formations on the wall. Smoother than the stone beside them.",
                "Smell of sulfur. Distant, but real.",
                "Bones of something in a side passage. Old. Very old.",
                "Fossil half-emerged from the limestone. Strange fish-shape.",
                "Dripstone columns ahead, floor to ceiling. Navigated through carefully.",
                "Sound of water under the floor. A buried river.",
                "The rock changed color here. Banded layers. Thousands of years of quiet.",
                "Wind from somewhere. No way to know which direction."
            } : new String[]{
                "Absolute darkness below the ledge. Dropped a stone. Did not hear it land.",
                "The walls are warm here. Not from the stone.",
                "Ancient city brickwork in the distance. Did not go closer.",
                "Magma block underfoot. Backed off and found another way.",
                "Ceiling dripping slowly, one drop every few seconds. Counted them.",
                "No wind. No sound. The rock absorbs everything.",
                "Sculk patches on the floor. Stepped around each one.",
                "Vein of deepslate running floor-to-ceiling. Millions of years compressed into a seam.",
                "Glow of magma through a crack in the wall. Held paw to it. Warm.",
                "Passage narrowed to nothing. Backtracked. Found another way down."
            };
            sim.addLogLine(travelLines[rng.nextInt(travelLines.length)]);
            if (!ToolDurabilityHelper.applyMinerDurability(level, 1, sim)) {
                sim.complete(level, false, false);
            }
        } else if (roll < weights[0] + weights[1]) {
            rollDiscovery(level, zone, rng, sim);
            if (!ToolDurabilityHelper.applyMinerDurability(level, 3, sim)) {
                sim.complete(level, false, false);
            }
        } else {
            ExpeditionSimulator.HazardLevel hl = ExpeditionSimulator.pickHazardLevel(zone, rng);
            rollHazard(level, rng, hl, sim);
            int dmg = switch (hl) { case LIGHT -> 1; case MODERATE -> 2; case SEVERE -> 3; };
            if (!ToolDurabilityHelper.applyMinerDurability(level, dmg, sim)) {
                sim.complete(level, false, false);
            }
        }
    }

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

        // Pick normal or silk-touch table
        var tableKey = (sim.isSilkTouch() && chosen.silkTouchLootTable().isPresent())
            ? chosen.silkTouchLootTable().get()
            : chosen.lootTable();

        List<ItemStack> drops = ExpeditionLootHelper.roll(sl, tableKey, sim.getBlockPos());
        sim.addPendingLoot(drops);

        // Biome bonus rolls
        boolean biomeBonusApplies = chosen.biomeBonusCategories()
            .map(cats -> cats.contains(sim.getBiomeCategory()))
            .orElse(false);
        if (biomeBonusApplies && chosen.biomeBonusLootTable().isPresent()) {
            sim.addPendingLoot(ExpeditionLootHelper.roll(sl, chosen.biomeBonusLootTable().get(), sim.getBlockPos()));
        }

        sim.addLogLine(chosen.journalLines().get(rng.nextInt(chosen.journalLines().size())));
    }

    static void rollHazard(Level level, Random rng, ExpeditionSimulator.HazardLevel hl, ExpeditionSimulator sim) {
        String[] lines = switch (hl) {
            case LIGHT -> new String[]{
                "Passage looped. Spent time finding north again.",
                "Wrong tunnel for a while. Doubled back. Lost time.",
                "Sound of many feet. Stayed still until it passed.",
                "Groaning in the walls. Zombie trapped in the stone somewhere.",
                "Something moved in the chamber below. Did not go down."
            };
            case MODERATE -> new String[]{
                "Ceiling cracked. Held still. It held.",
                "Gravel pour from above. Moved before it filled the corridor.",
                "Water flooded the lower corridor fast. Climbed out.",
                "Deep water ahead, no bottom. Found another way around.",
                "Clicking in the dark behind me. Picked up the pace.",
                "Breath in the dark that wasn't mine. Not going back that way.",
                "Heat rising through the floor. Magma below. Found a way around it."
            };
            case SEVERE -> new String[]{
                "Lava pocket opened up mid-swing. Retreated fast. Singed.",
                "Cave-in above. Buried the passage. Had to dig back out.",
                "Support pillar gone. The whole section leaned. Left quickly.",
                "Aquifer burst through the wall. Cold and sudden. Lost the passage.",
                "Something in the dark that wasn't afraid. Moved fast."
            };
        };
        sim.addLogLine(lines[rng.nextInt(lines.length)]);
        sim.applyHazardCost(level, rng, hl);
    }
}
