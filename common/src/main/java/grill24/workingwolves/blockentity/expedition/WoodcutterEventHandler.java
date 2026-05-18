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
                "Something ahead went quiet. Waited. Kept moving.",
                "A woodpecker was working ahead. Good sign the timber is healthy.",
                "The canopy closed again behind me. No longer sure which way is east.",
                "Fallen giant across the path. Climbed over. Noted the size.",
                "Saplings coming up in the gap where a big one came down.",
                "The leaf litter is thick enough to lose a foot in.",
                "A stand of old birch, paper bark curling. Took the straightest.",
                "Nettles along the path. Found the gaps.",
                "The timber here shows signs of previous cutting. Old stumps, mossy.",
                "Bark beetles in the outer layer. The wood beneath is sound.",
                "Smell of pine resin from somewhere upslope. Following it.",
                "The slope steepened. The trees held their own.",
                "Birdsong stopped all at once. Waited. Started again. Continued.",
                "A clearing not marked on the path. Useable.",
                "Two trees growing from the same stump. The old one is gone.",
                "Heartwood exposed by old damage. Still solid.",
                "The wind came through the canopy in waves. Timed the cuts between gusts.",
                "Found a stand of spruce on the north face. Slower growing, better grain.",
                "Moss two inches deep on the forest floor. Silent going.",
                "The deer was watching from the ridge before I noticed. Both of us paused.",
                "A twisted oak that grew into the shape of a question. Left it.",
                "The path closed in from both sides. Made my own.",
                "Ground was soft after rain. Roots showing. Careful footing.",
                "The mark I cut three trips ago was overgrown but visible.",
                "Crows following at a distance. Not a concern.",
                "The air here is dim and green and cool even at noon.",
                "A break in the canopy lets in one column of light. The rest is shade.",
                "Found a stand where the spacing is just right. Easy work.",
                "Gnarled oak with a hollow large enough to shelter in. Noted.",
                "Smell of woodsmoke from far away. Old fire, days ago.",
                "Heard an axe ahead. Another cutter. We passed without speaking.",
                "The undergrowth had been grazed. Something large came through recently.",
                "The bark here is thick and soft, riven with cracks. Old timber.",
                "A long straight section of the trail with good sight lines. Moved faster.",
                "The trees thin near the ridge. Different air up there.",
                "A dead tree still standing. Passed carefully.",
                "The canopy is unbroken here. Like walking under water."
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
                "The path I marked was already grown over.",
                "Slipped in the mud. Kept the axe clear.",
                "Loose bark underfoot. Expected firm ground.",
                "The trail narrowed to nothing. Made a new one.",
                "Wrong tree fell first. Worked around it.",
                "Lost the marked path. Reoriented by the slope.",
                "A branch came down without warning. Close.",
                "The ground soft enough to sink an ankle.",
                "Misjudged the tree's lean. Stepped wide.",
                "Stumbled on a hidden root. Found footing again.",
                "Something startled close by. No contact.",
                "Fog thickened faster than expected. Slowed.",
                "The axe slipped on wet bark. Adjusted grip.",
                "Tangled in old vine. Took time to sort.",
                "The path I remembered was not the path that was there.",
                "Chips of bark in the eyes. Stopped briefly."
            };
            case MODERATE -> new String[]{
                "Tree fell the wrong way. Close call.",
                "Beehive in the branches. Stings.",
                "Hostile mob in the undergrowth.",
                "A root caught my back foot. Went down hard.",
                "Something in the dark forest did not want me there.",
                "Disturbed a nest I did not see. Moved before counting.",
                "The tree twisted on the way down. Not the direction intended.",
                "Something large in the undergrowth, displeased. Left the area.",
                "Bees found the cut wood before I'd finished. Negotiated a departure.",
                "A root caught the axe head on the follow-through.",
                "Slid down the wet slope further than planned.",
                "The hive was bigger than it looked from above.",
                "Got cut on a briar backing away from something.",
                "A falling branch knocked me clear off the stump.",
                "Two trees tangled overhead; the second fell first.",
                "The hollow trunk had something living inside it. Adjusted plans.",
                "Tripped over the root that was clearly there if I'd looked down.",
                "Cut wrong and the bark came off in a spray. Avoided most of it.",
                "Something territorial in the undergrowth. Exchanged intentions. Left.",
                "The weight of the cut timber exceeded expectations. Managed.",
                "Ivy had worked into the trunk structure. Dense interior. More work.",
                "Cornered by the fallen tree on one side and dense brush on the other.",
                "A branch that should have stayed up fell directly across the path out.",
                "The wasps found my lunch before I did."
            };
            case SEVERE -> new String[]{
                "Bear investigating my woodpile. Then investigating me.",
                "The dry grass caught from a spark. Not so controlled.",
                "Tree came down on top of me. Working out from under it.",
                "The mud swallowed me to the hip. Took time to extract.",
                "Something large, very close, very fast. Dropped everything and ran.",
                "The root system pulled up half the hillside when the tree went.",
                "Something charged from the dark end of the forest. Dropped the load and ran.",
                "The fire spread to the standing timber. Left fast.",
                "A tree came down across the one I'd just cut. Trapped for a while.",
                "The ground gave under the weight of the fall. Took me with it partially.",
                "Flood-cut soil where the bank was. The tree took the bank with it.",
                "Hornet colony, full size, in the trunk. Discovered on the third swing.",
                "The cut tree pinned me by the leg. Freed myself eventually.",
                "Singed more than expected from the spark. Found water.",
                "A second bear. The first one was already a problem.",
                "The axe came apart on a knot. Left without the blade.",
                "Fell down the ravine. No serious damage. Lost most of the cut wood.",
                "A flash flood. Left the woodpile. Prioritized leaving.",
                "The trunk was rotten through to the center. Came down wrong.",
                "Something bit through the carrying rope and the load dispersed."
            };
        };
        sim.addLogLine(lines[rng.nextInt(lines.length)]);
        sim.applyHazardCost(level, rng, hl);
    }
}
