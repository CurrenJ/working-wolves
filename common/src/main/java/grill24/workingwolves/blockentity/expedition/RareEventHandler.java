package grill24.workingwolves.blockentity.expedition;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Random;

class RareEventHandler {

    static boolean roll(Level level, int zone, Random rng, ExpeditionSimulator sim) {
        float p = rng.nextFloat();
        float cursor = 0f;
        String biome = sim.getBiomeCategory();

        // WARDEN'S_BREATH — cave zone 2 only (0.5%)
        if ("cave".equals(biome) && zone == 2) {
            cursor += 0.005f;
            if (p < cursor) {
                sim.addLogLine("Did not make a sound. Not one.");
                sim.addLogLine("Three hours of perfect silence.");
                sim.addLogLine("Emerged with mud on the paws and a look you don't ask about.");
                sim.addPendingLoot(new ItemStack(Items.ECHO_SHARD, 1 + rng.nextInt(2)));
                if (rng.nextFloat() < 0.25f) sim.applyHazardCost(level, rng, ExpeditionSimulator.HazardLevel.MODERATE);
                return true;
            }
        }

        // WOLF_PACK_STANDOFF — forest/plains (2%)
        if ("forest".equals(biome) || "plains".equals(biome)) {
            cursor += 0.02f;
            if (p < cursor) {
                sim.addLogLine("A pack emerged from the tree line.");
                sim.addLogLine("Did not run. Neither did they.");
                sim.addLogLine("When the moon moved, both groups went their separate ways.");
                return true;
            }
        }

        // ECHO_OF_THE_DEEP — underground zone 2 (1%)
        if (zone == 2 && (sim.hasMining() || "cave".equals(biome))) {
            cursor += 0.01f;
            if (p < cursor) {
                sim.addLogLine("The silence changed quality.");
                List<ItemStack> loot = sim.getPendingLoot();
                if (!loot.isEmpty()) {
                    loot.remove(loot.size() - 1);
                    sim.addLogLine("Came back without something. Won't say why.");
                } else {
                    sim.addLogLine("Something down there. Left quickly.");
                }
                return true;
            }
        }

        // GEODE_CRACK — underground/mountain, miner preferred (3% miner, 1% other)
        if ("cave".equals(biome) || "mountain".equals(biome) || sim.hasMining()) {
            cursor += sim.hasMining() ? 0.03f : 0.01f;
            if (p < cursor) {
                sim.addLogLine("The stone rang like a bell when struck.");
                sim.addLogLine("Inside: violet. Still growing.");
                sim.addPendingLoot(new ItemStack(Items.AMETHYST_SHARD, 4 + rng.nextInt(5)));
                return true;
            }
        }

        // STRONGHOLD_LIBRARY — underground zone 1+, collar 2+ (0.75%)
        if (zone >= 1 && sim.getCollarTier() >= 2 && (sim.hasMining() || "cave".equals(biome))) {
            cursor += 0.0075f;
            if (p < cursor) {
                sim.addLogLine("The passage opened into something vast and paper-smelling.");
                sim.addLogLine("Cannot read. Brought back a page anyway.");
                sim.addPendingLoot(new ItemStack(Items.BOOK, 1));
                if (sim.getCollarTier() >= 3) {
                    sim.addPendingLoot(new ItemStack(Items.LAPIS_LAZULI, 4 + rng.nextInt(8)));
                }
                return true;
            }
        }

        // ABANDONED_MINESHAFT_FIND — cave, miner preferred (4% miner, 2% other)
        if ("cave".equals(biome) || sim.hasMining()) {
            cursor += sim.hasMining() ? 0.04f : 0.02f;
            if (p < cursor) {
                sim.addLogLine("The shaft went deeper than it should have.");
                sim.addLogLine("Came back with mud on the paws and something extra.");
                if (sim.getCollarTier() >= 3 && rng.nextFloat() < 0.2f) {
                    sim.addPendingLoot(new ItemStack(Items.EMERALD, 1));
                } else {
                    int ore = rng.nextInt(3);
                    if (ore == 0) sim.addPendingLoot(new ItemStack(Items.RAW_GOLD, 2 + rng.nextInt(3)));
                    else if (ore == 1) sim.addPendingLoot(new ItemStack(Items.RAW_IRON, 3 + rng.nextInt(4)));
                    else sim.addPendingLoot(new ItemStack(Items.COAL, 4 + rng.nextInt(6)));
                }
                return true;
            }
        }

        // ELDER_GUARDIAN_SHADOW — ocean biome (1.5%)
        if ("ocean".equals(biome)) {
            cursor += 0.015f;
            if (p < cursor) {
                sim.addLogLine("The water darkened beneath.");
                sim.addLogLine("Something vast passed. It did not stop.");
                if (rng.nextFloat() < 0.4f) sim.addPendingLoot(new ItemStack(Items.PRISMARINE_CRYSTALS, 1 + rng.nextInt(3)));
                if (sim.getCollarTier() >= 3 && rng.nextFloat() < 0.3f) sim.addPendingLoot(new ItemStack(Items.NAUTILUS_SHELL, 1));
                return true;
            }
        }

        // WANDERING_TRADER_DEAL — surface (2%)
        if (!"cave".equals(biome)) {
            cursor += 0.02f;
            if (p < cursor) {
                sim.addLogLine("A man with two llamas. Offered something wrapped in cloth.");
                sim.addLogLine("Gave him a ration. He seemed satisfied.");
                sim.adjustSatiation(-2);
                Item[] traderGoods = {Items.BLUE_DYE, Items.KELP, Items.PUMPKIN_SEEDS, Items.CACTUS, Items.DEAD_BUSH, Items.FERN};
                sim.addPendingLoot(new ItemStack(traderGoods[rng.nextInt(traderGoods.length)], 1 + rng.nextInt(3)));
                return true;
            }
        }

        // BURIED_CHEST — plains/ocean (1.5%)
        if ("plains".equals(biome) || "ocean".equals(biome)) {
            cursor += 0.015f;
            if (p < cursor) {
                sim.addLogLine("Dug because of the smell of iron. Was right.");
                if (sim.getCollarTier() >= 4 && rng.nextInt(10) == 0) {
                    sim.addPendingLoot(new ItemStack(Items.HEART_OF_THE_SEA, 1));
                } else if (rng.nextInt(3) == 0) {
                    sim.addPendingLoot(new ItemStack(Items.RAW_GOLD, 2 + rng.nextInt(4)));
                } else {
                    sim.addPendingLoot(new ItemStack(Items.RAW_IRON, 3 + rng.nextInt(5)));
                    sim.addPendingLoot(new ItemStack(Items.COAL, 2 + rng.nextInt(3)));
                }
                return true;
            }
        }

        // ANCIENT_INSCRIPTION — collar 3+ (1%)
        if (sim.getCollarTier() >= 3) {
            cursor += 0.01f;
            if (p < cursor) {
                sim.addLogLine("Paused at a moss-covered stone. Strange marks.");
                sim.addLogLine("Sat with it a long while before moving on.");
                sim.adjustSatiation(4);
                return true;
            }
        }

        // BONE_CROWN — collar 4 only (0.5%)
        if (sim.getCollarTier() >= 4) {
            cursor += 0.005f;
            if (p < cursor) {
                sim.addLogLine("Wedged in a root: a circlet of bone and river-iron.");
                sim.addLogLine("Old and deliberate. Brought it home.");
                sim.addPendingLoot(new ItemStack(Items.BONE, 1));
                return true;
            }
        }

        // SPIDER_NEST — forest/cave (2%)
        if ("forest".equals(biome) || "cave".equals(biome)) {
            cursor += 0.02f;
            if (p < cursor) {
                sim.addLogLine("The web was thick as rope. The eggs were not all hatched.");
                boolean success = sim.getCollarTier() >= 2 || rng.nextFloat() < 0.5f;
                if (success) {
                    sim.addLogLine("Destroyed what was there. Took time.");
                    sim.addPendingLoot(new ItemStack(Items.STRING, 2 + rng.nextInt(4) + sim.getLooting()));
                    if (rng.nextFloat() < 0.5f) sim.addPendingLoot(new ItemStack(Items.SPIDER_EYE, rng.nextInt(3) + sim.getLooting()));
                    sim.applyHazardCost(level, rng, ExpeditionSimulator.HazardLevel.LIGHT);
                } else {
                    sim.addLogLine("Too many. Retreated.");
                    sim.applyHazardCost(level, rng, ExpeditionSimulator.HazardLevel.MODERATE);
                }
                return true;
            }
        }

        // THE_VEIN_THAT_SHOULDNT_BE — miner only, underground (1%)
        if (sim.hasMining() && ("cave".equals(biome) || "mountain".equals(biome))) {
            cursor += 0.01f;
            if (p < cursor) {
                sim.addLogLine("Copper. Then iron. Then something that wasn't either.");
                sim.addLogLine("Brought back what could be carried.");
                sim.addPendingLoot(new ItemStack(Items.RAW_COPPER, 2 + rng.nextInt(3)));
                sim.addPendingLoot(new ItemStack(Items.RAW_IRON, 1 + rng.nextInt(3)));
                if (sim.getCollarTier() >= 4 && rng.nextFloat() < 0.15f) {
                    sim.addPendingLoot(new ItemStack(Items.ANCIENT_DEBRIS, 1));
                } else {
                    sim.addPendingLoot(new ItemStack(Items.GOLD_INGOT, 1 + rng.nextInt(2)));
                }
                return true;
            }
        }

        return false;
    }

    static boolean rollCrossRole(Level level, Random rng, ExpeditionSimulator sim) {
        if (sim.hasHunting() && sim.hasWoodcutting() && !sim.hasMining()) {
            String[] lines = {
                "Was lining up a cut when a boar appeared at the next tree. Held still. Eventually continued.",
                "The tree came down wrong and flushed something large from the understory. It was a deer. Both equally startled.",
                "Took a boar while the brush fire was still burning. The smoke slowed it down.",
                "A wolf den under the root mass of a fallen oak. No adults home. Noted the location. Moving on."
            };
            sim.addLogLine(lines[rng.nextInt(lines.length)]);
            return true;
        }
        if (sim.hasHunting() && sim.hasMining() && !sim.hasWoodcutting()) {
            String[] lines = {
                "The tunnel opened into a cavity. Something had been denning there. Backed out. Some places choose their tenants.",
                "Killed a spider in the tunnels and found iron wrapped in silk behind it.",
                "A seam of coal in the wall. On the other side: something scratching. Rhythmic. Deliberate.",
                "The miner in me said forty feet underground with nothing ahead. The miner won. Today."
            };
            sim.addLogLine(lines[rng.nextInt(lines.length)]);
            return true;
        }
        if (sim.hasMining() && sim.hasWoodcutting() && !sim.hasHunting()) {
            String[] lines = {
                "Been underground long enough that wood smells strange now. Too bright. Too alive.",
                "Came up early. Spent the afternoon cutting oak just to remember daylight.",
                "The tunnel roof was lined with roots. Careful work not to collapse the whole thing.",
                "Dug through to a hollow full of roots. The forest above had no idea."
            };
            sim.addLogLine(lines[rng.nextInt(lines.length)]);
            return true;
        }
        if (sim.hasHunting() && sim.hasMining() && sim.hasWoodcutting()) {
            String[] lines = {
                "Too much to do in one place. Prioritised. Got most of it done.",
                "Good day. Everything went roughly according to plan.",
                "Three jobs. Managed two properly. Filed the third for later."
            };
            sim.addLogLine(lines[rng.nextInt(lines.length)]);
            return true;
        }
        return false;
    }
}
