package grill24.workingwolves.blockentity.expedition;

import grill24.workingwolves.blockentity.expedition.data.ExpeditionRegistries;
import grill24.workingwolves.blockentity.expedition.data.WoodDiscoveryEntry;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Optional;
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
