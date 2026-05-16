package grill24.workingwolves.blockentity.expedition;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

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
        float biomeMultiplier = ("mountain".equals(sim.getBiomeCategory()) || "cave".equals(sim.getBiomeCategory())) ? 1.25f : 1.0f;

        String ore = switch (zone) {
            case 0 -> {
                String[] pool = {"coal", "iron", "flint"};
                yield pool[rng.nextInt(pool.length)];
            }
            case 1 -> {
                String[] pool = {"iron", "copper", "gold", "lapis"};
                yield pool[rng.nextInt(pool.length)];
            }
            default -> {
                String[] pool = {"gold", "redstone",
                    (sim.getCollarTier() >= 3 ? "diamond" : "redstone"),
                    "amethyst"};
                yield pool[rng.nextInt(pool.length)];
            }
        };

        float fortuneMultiplier = 1.0f + sim.getFortune() * 0.5f;

        switch (ore) {
            case "coal" -> {
                int base = 2 + rng.nextInt(5);
                int count = (int)(base * fortuneMultiplier * biomeMultiplier);
                sim.addPendingLoot(new ItemStack(sim.isSilkTouch() ? Items.COAL_ORE : Items.COAL, Math.max(1, count)));
                String[] lines = {
                    "Coal seam in the wall. Wide as my paw.",
                    "Black vein, three layers deep. Long work but worth it.",
                    "Coal dust already in the air here. Good sign.",
                    "Pocket of coal behind a thin slate wall. Lucky find.",
                    "Large seam, runs deeper than I can see. Took what I could reach."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "iron" -> {
                int base = zone == 0 ? 1 + rng.nextInt(3) : 2 + rng.nextInt(4);
                int count = (int)(base * biomeMultiplier);
                sim.addPendingLoot(new ItemStack(sim.isSilkTouch() ? Items.IRON_ORE : Items.RAW_IRON, Math.max(1, count)));
                String[] lines = {
                    "Iron ore behind the stone. Raw and rough.",
                    "Caught scent of iron before I saw it. Dug in.",
                    "Red-brown streak through grey granite. Classic iron.",
                    "Cluster of iron nodules in the ceiling. Had to work at an angle.",
                    "Ochre staining the rock face. Scraped it back and found the vein."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "copper" -> {
                int base = 1 + rng.nextInt(4);
                int count = (int)(base * fortuneMultiplier * biomeMultiplier);
                sim.addPendingLoot(new ItemStack(sim.isSilkTouch() ? Items.COPPER_ORE : Items.RAW_COPPER, Math.max(1, count)));
                String[] lines = {
                    "Copper, oxidized green. Unmistakable.",
                    "Teal streak bleeding through the limestone. A big pocket.",
                    "Copper blooms across the rock face like frozen flames.",
                    "Green dust on the floor. Looked up and found the source.",
                    "Verdigris on everything here. Rich copper deposit, weathered through."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "gold" -> {
                int base = 1 + rng.nextInt(3);
                int count = (int)(base * biomeMultiplier);
                sim.addPendingLoot(new ItemStack(sim.isSilkTouch() ? Items.GOLD_ORE : Items.RAW_GOLD, Math.max(1, count)));
                String[] lines = {
                    "Gold glint in the torchlight. Checked twice to be sure.",
                    "A vein of gold. Small but rich. Careful extraction.",
                    "Gold in the deepslate. Deep enough to mean it.",
                    "Yellow fleck, then more flecks, then a full seam. Good day.",
                    "Heavy ore. Had to shift the weight in the bag."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "lapis" -> {
                int base = 2 + rng.nextInt(7);
                int count = (int)(base * fortuneMultiplier * biomeMultiplier);
                sim.addPendingLoot(new ItemStack(sim.isSilkTouch() ? Items.LAPIS_ORE : Items.LAPIS_LAZULI, Math.max(1, count)));
                String[] lines = {
                    "Lapis. Bright blue in the grey rock. Striking.",
                    "Lapis dust already on the floor where a chunk fell out.",
                    "Deep blue vein, wider than expected. Took a long time.",
                    "The stone split and the lapis inside was almost luminous.",
                    "Ultramarine scatter through the limestone. Beautiful, even down here."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "redstone" -> {
                int base = 2 + rng.nextInt(7);
                int count = (int)(base * fortuneMultiplier * biomeMultiplier);
                sim.addPendingLoot(new ItemStack(sim.isSilkTouch() ? Items.REDSTONE_ORE : Items.REDSTONE, Math.max(1, count)));
                String[] lines = {
                    "Redstone. The rock hums faintly where it runs.",
                    "Deep red dust on the paw. Vein in the ceiling.",
                    "Redstone seam lit up when I scraped it. Strange light in the dark.",
                    "The walls pulsed dim red as I worked. Kept going.",
                    "Redstone ore, dense and deep. The deepslate soaked it up over millennia."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "diamond" -> {
                int base = 1 + rng.nextInt(3);
                int count = (int)(base * fortuneMultiplier * biomeMultiplier);
                sim.addPendingLoot(new ItemStack(sim.isSilkTouch() ? Items.DIAMOND_ORE : Items.DIAMOND, Math.max(1, count)));
                String[] lines = {
                    "Diamond. Stopped breathing for a moment. Then dug.",
                    "Blue-white glint in the deepslate. Heart hammering.",
                    "Diamond vein. Small. Did not care. Worked until my paws ached.",
                    "Found it by accident, feeling along the wall in the dark. Diamond.",
                    "The pickaxe rang differently against this stone. Looked closer. Diamond."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "amethyst" -> {
                int base = 1 + rng.nextInt(4);
                int count = (int)(base * fortuneMultiplier * biomeMultiplier);
                sim.addPendingLoot(new ItemStack(Items.AMETHYST_SHARD, Math.max(1, count)));
                String[] lines = {
                    "Geode pocket. Amethyst clusters inside, perfect and untouched.",
                    "The rock opened into a hollow lined with purple crystal. Unexpected.",
                    "Amethyst. Chimed softly when the pickaxe hit it.",
                    "A small geode, cracked. Violet shards caught whatever light there was.",
                    "Walls of amethyst around me for a moment. A private cathedral."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "flint" -> {
                int base = 2 + rng.nextInt(3);
                sim.addPendingLoot(new ItemStack(Items.FLINT, Math.max(1, base)));
                String[] lines = {
                    "Gravel seam. Sifted through it carefully. Good flint.",
                    "Flint nodules in the limestone. Ancient sea floor, once.",
                    "Black flint, sharp-edged. Sorted the best pieces.",
                    "Gravel pocket collapsed. Picked through the pile. Worth it.",
                    "Flint-rich band running through the chalk. Took what would carry."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            default -> sim.addLogLine("Odd mineral. Took what was worth taking.");
        }
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
