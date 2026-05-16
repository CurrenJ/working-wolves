package grill24.workingwolves.blockentity.expedition;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.Random;

class WoodcutterEventHandler {

    static void rollEvent(Level level, int zone, ExpeditionSimulator sim) {
        Random rng = new Random(level.getGameTime() + sim.getElapsedTicks() + 3);
        int[] weights = switch (zone) {
            case 0 -> new int[]{50, 40, 10};
            case 1 -> new int[]{30, 45, 25};
            default -> new int[]{20, 35, 45};
        };
        int roll = rng.nextInt(100);

        if (roll < weights[0]) {
            String[] travelLines = {
                "Deep into the trees.",
                "Bark and pine needles underfoot.",
                "A clearing, then more forest.",
                "Old growth. Roots everywhere.",
                "Light through the branches.",
                "Sound of wind through the canopy.",
                "The path I marked is already closing.",
                "Something moved through the understory. Held still.",
                "Following a deer path. Goes exactly where I needed.",
                "Rain started. The canopy is holding most of it.",
                "The fern layer is thick. Good soil under it.",
                "Something ahead went quiet. Waited. Kept moving."
            };
            sim.addLogLine(travelLines[rng.nextInt(travelLines.length)]);
            if (!ToolDurabilityHelper.applyWoodcutterDurability(level, 1, sim)) {
                sim.complete(level, false, false);
            }
        } else if (roll < weights[0] + weights[1]) {
            rollDiscovery(level, zone, rng, sim);
            if (!ToolDurabilityHelper.applyWoodcutterDurability(level, 3, sim)) {
                sim.complete(level, false, false);
            }
        } else {
            ExpeditionSimulator.HazardLevel hl = ExpeditionSimulator.pickHazardLevel(zone, rng);
            rollHazard(level, rng, hl, sim);
            int dmg = switch (hl) { case LIGHT -> 1; case MODERATE -> 2; case SEVERE -> 3; };
            if (!ToolDurabilityHelper.applyWoodcutterDurability(level, dmg, sim)) {
                sim.complete(level, false, false);
            }
        }
    }

    static void rollDiscovery(Level level, int zone, Random rng, ExpeditionSimulator sim) {
        // Axe speed scales yield: iron axe (speed 6) = baseline 1.0x
        float axeMultiplier = Math.max(0.5f, sim.getAxeSpeed() / 6.0f);

        switch (sim.getWoodBiome()) {
            case "jungle" -> {
                int logs = Math.max(1, (int)((1 + rng.nextInt(4)) * axeMultiplier));
                sim.addPendingLoot(new ItemStack(Items.JUNGLE_LOG, logs));
                if (rng.nextFloat() < 0.4f) sim.addPendingLoot(new ItemStack(Items.BAMBOO, 1 + rng.nextInt(3)));
                if (rng.nextFloat() < 0.25f) sim.addPendingLoot(new ItemStack(Items.COCOA_BEANS, 1 + rng.nextInt(2)));
                if (rng.nextFloat() < 0.2f) sim.addPendingLoot(new ItemStack(Items.JUNGLE_SAPLING, 1));
                String[] lines = {
                    "Dense jungle. Every trunk laced with vines.",
                    "Something large moved through the understory. Held still.",
                    "Orchids growing from a wound in the bark.",
                    "The trees here do not fall cleanly."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "dark_forest" -> {
                int logs = Math.max(1, (int)((1 + rng.nextInt(4)) * axeMultiplier));
                sim.addPendingLoot(new ItemStack(Items.DARK_OAK_LOG, logs));
                if (rng.nextFloat() < 0.2f) sim.addPendingLoot(new ItemStack(Items.DARK_OAK_SAPLING, 1));
                if (rng.nextFloat() < 0.15f) sim.addPendingLoot(new ItemStack(Items.BROWN_MUSHROOM, 1 + rng.nextInt(2)));
                if (rng.nextFloat() < 0.15f) sim.addPendingLoot(new ItemStack(Items.RED_MUSHROOM, 1));
                String[] lines = {
                    "Found a clearing. The trees lean inward around it.",
                    "Mushrooms growing up the trunks, not just the ground.",
                    "Long claw marks on the trunk. Higher than I wanted to think.",
                    "The inside of the dark oak was pale. Did not expect that."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "taiga" -> {
                int logs = Math.max(1, (int)((1 + rng.nextInt(4)) * axeMultiplier));
                sim.addPendingLoot(new ItemStack(Items.SPRUCE_LOG, logs));
                if (rng.nextFloat() < 0.3f) sim.addPendingLoot(new ItemStack(Items.SWEET_BERRIES, 1 + rng.nextInt(3)));
                if (rng.nextFloat() < 0.2f) sim.addPendingLoot(new ItemStack(Items.SPRUCE_SAPLING, 1));
                if (rng.nextFloat() < 0.1f) sim.addPendingLoot(new ItemStack(Items.STICK, 2 + rng.nextInt(4)));
                String[] lines = {
                    "Spruce stands. Cold air. Smells like winter stored inside wood.",
                    "Good straight grain. These trees grow slow and honest.",
                    "A cavity in the trunk. Old axe marks. Someone didn't finish.",
                    "Snow came down all at once when the trunk finally went."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "savanna" -> {
                int logs = Math.max(1, (int)((1 + rng.nextInt(3)) * axeMultiplier));
                sim.addPendingLoot(new ItemStack(Items.ACACIA_LOG, logs));
                if (rng.nextFloat() < 0.2f) sim.addPendingLoot(new ItemStack(Items.ACACIA_SAPLING, 1));
                String[] lines = {
                    "Scattered acacia. Flat light.",
                    "Twisted trunks in the savanna heat.",
                    "Dead acacia still standing. Bark stripped clean by antlers.",
                    "The roots tore the ground when it went over."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "cherry" -> {
                int logs = Math.max(1, (int)((1 + rng.nextInt(3)) * axeMultiplier));
                sim.addPendingLoot(new ItemStack(Items.CHERRY_LOG, logs));
                if (rng.nextFloat() < 0.3f) sim.addPendingLoot(new ItemStack(Items.CHERRY_SAPLING, 1));
                if (rng.nextFloat() < 0.4f) sim.addPendingLoot(new ItemStack(Items.PINK_PETALS, 1 + rng.nextInt(3)));
                String[] lines = {
                    "Petals drifting down without any wind.",
                    "Wood pale and close-grained. Smells faintly sweet.",
                    "This place makes time feel very long.",
                    "A family of rabbits under a fallen log. Worked around them."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "mangrove" -> {
                int logs = Math.max(1, (int)((1 + rng.nextInt(3)) * axeMultiplier));
                sim.addPendingLoot(new ItemStack(Items.MANGROVE_LOG, logs));
                if (rng.nextFloat() < 0.3f) sim.addPendingLoot(new ItemStack(Items.MANGROVE_PROPAGULE, 1));
                if (rng.nextFloat() < 0.2f) sim.addPendingLoot(new ItemStack(Items.MANGROVE_ROOTS, 1 + rng.nextInt(2)));
                String[] lines = {
                    "Roots in the water first, then down to the mud.",
                    "A frog on the root arch I was about to step over. Stepped around it.",
                    "The aerial roots hang like curtains. Patience required.",
                    "Dense, uneven wood. A life lived half in water."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            default -> {
                int logs = Math.max(1, (int)((1 + rng.nextInt(4)) * axeMultiplier));
                boolean birch = rng.nextBoolean();
                sim.addPendingLoot(new ItemStack(birch ? Items.BIRCH_LOG : Items.OAK_LOG, logs));
                if (rng.nextFloat() < 0.25f) sim.addPendingLoot(new ItemStack(Items.APPLE, 1 + rng.nextInt(2)));
                if (rng.nextFloat() < 0.15f) sim.addPendingLoot(new ItemStack(birch ? Items.BIRCH_SAPLING : Items.OAK_SAPLING, 1));
                String[] lines = {
                    "Good stand of oak. Clean work.",
                    "Birch grove, white bark. Quiet.",
                    "The oak at the center is old enough to matter.",
                    "Birch light is different. White bark scatters everything.",
                    "Clean, quick work. These trees have nothing to say about it."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
        }
    }

    static void rollHazard(Level level, Random rng, ExpeditionSimulator.HazardLevel hl, ExpeditionSimulator sim) {
        String[] lines = switch (hl) {
            case LIGHT -> new String[]{
                "Roots tangled the path. Lost time.",
                "Fog rolling in. Slowed down.",
                "Thorns and thick brush.",
                "Rain made the bark slick. Careful work.",
                "The path I marked was already grown over."
            };
            case MODERATE -> new String[]{
                "Tree fell the wrong way. Close call.",
                "Beehive in the branches. Stings.",
                "Hostile mob in the undergrowth.",
                "A root caught my back foot. Went down hard.",
                "Something in the dark forest did not want me there.",
                "Disturbed a nest I did not see. Moved before counting."
            };
            case SEVERE -> new String[]{
                "Bear investigating my woodpile. Then investigating me.",
                "The dry grass caught from a spark. Not so controlled.",
                "Tree came down on top of me. Working out from under it.",
                "The mud swallowed me to the hip. Took time to extract.",
                "Something large, very close, very fast. Dropped everything and ran."
            };
        };
        sim.addLogLine(lines[rng.nextInt(lines.length)]);
        sim.applyHazardCost(level, rng, hl);
    }
}
