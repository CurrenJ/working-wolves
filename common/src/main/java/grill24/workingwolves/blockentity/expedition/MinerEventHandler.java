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
                "Soil giving way to true stone. The real work starts here.",
                "Limestone shelf at chest height. Had to edge past.",
                "The walls here have old pick marks in them. Layered over time.",
                "Good headroom near the entrance. Narrows ahead.",
                "Sand in the joints of the rock. Still shifting.",
                "Water staining on the wall but no active seep. Old runoff.",
                "Packed earth gives way to flagstone. Natural floor, like a path.",
                "The air moves here. Ventilation from somewhere above.",
                "Scratching in the wall. Pocket of air or something nesting.",
                "Light seeping from a crack up high. Still close to surface.",
                "Wrist-thick tree roots pushing through the ceiling in a cluster.",
                "White calcite crust on the rock face. Recent deposition.",
                "Smell of wet clay. The sediment layer is nearby.",
                "The passage opens then closes. Pressed through.",
                "Grit in the air here. Recent collapse somewhere above.",
                "The stone shifts underfoot. Loose-laid, not bedrock. Testing each step.",
                "Found an old boot sole embedded in the wall. Didn't ask.",
                "The shaft is hand-cut. Not natural. Someone started here.",
                "Soil seam two hand-widths wide running diagonal through the stone.",
                "Water trickling underfoot but the floor holds.",
                "A shelf of slate: natural, layered, tilted. Shears cleanly.",
                "Bat roost ahead. Kept to the other wall.",
                "The torch smoke went up cleanly here. Good air flow.",
                "Echo quality changed. Chamber ahead.",
                "Loose gravel behind a stone face. Careful around it.",
                "The taste of the air is mineral and old. Deeper from here.",
                "Old charcoal mark on the wall. Someone's survey.",
                "The passage levels off here. Ground water table, maybe.",
                "Stone face shows rust staining. Iron nearby.",
                "Two tunnels. One smells of animal. Took the other.",
                "Root barrier across the passage. Pushed through. Thorny."
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
                "Wind from somewhere. No way to know which direction.",
                "The gallery ceiling slopes downward to a low crawl. Pushed through.",
                "Flowstone coating everything. Slick underfoot.",
                "Sound of something dripping into a deep pool. Never found the pool.",
                "The rock here is layered in thin sheets. Flagged it.",
                "An unusual smell. Not sulfur. Not organic. Something chemical.",
                "Shadows on the wall from nothing. The torch is steady. The shadows aren't.",
                "The passage forks and both forks close again fifty paces ahead.",
                "Water seep so slow it seems like sweating rock. Ancient moisture.",
                "The column of stone in the center of the chamber was natural. Marked it.",
                "Scratching in the ceiling. Something small and fast. Left it.",
                "Mushrooms in a ring on the cave floor. Perfect circle. Walked around it.",
                "The stone here rings differently when tapped. Hollow behind it.",
                "A natural arch, corbelled by time. Waited before walking under it.",
                "Veins of white quartz running parallel through the stone face.",
                "The cave breathes. Inflow, then outflow, over a slow cycle.",
                "A curtain of thin flowstone translucent to the torch. Light through rock.",
                "Ceiling height varies unpredictably. Learned to check.",
                "Old cart tracks, wooden rails long rotted, embedded in the floor.",
                "Spider silk across the passage. The spider was somewhere ahead.",
                "The passage turns sharply and descends fast. Followed it.",
                "A wall of quartz crystal, small formations, lit from behind by nothing.",
                "The floor is damp but the walls are dry. Aquifer at exactly this depth.",
                "Low hum from somewhere far below. The rock carries it.",
                "Found the edge of a deep pit. Unmarked. Very close to the path.",
                "Ancient mine timber, black with age. Still bearing load.",
                "A seam of dark green serpentine in the wall. No ore. Just striking.",
                "The draft here comes from below. Something is open down there.",
                "Granite intrusion cutting through limestone. Hard work to navigate.",
                "Phosphorescent lichen on the north face of the chamber. Faint blue.",
                "The passage widens into a chamber and the sound changes entirely."
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
                "Passage narrowed to nothing. Backtracked. Found another way down.",
                "The walls are black here. Not basalt. Something else.",
                "No air movement at all. The torch burns straight.",
                "A body of standing water, perfectly still, reflecting the dark.",
                "The sculk blooms in patches across the floor like a spilled dye.",
                "Deepslate faces in all directions. This is the old earth, slow stone.",
                "Heat seeps from below. Not sharp. Background and constant.",
                "A sound like breathing from the stone. Regular intervals. Not mine.",
                "Ancient mineral deposits crystal-clear in the rock. Left alone.",
                "The passage lowers to a flat crawl for twenty paces. Emerged dusty.",
                "The smell down here is not like anything aboveground. Ancient and still.",
                "A fissure in the floor, deep enough to drop stone forever.",
                "The echo of footsteps comes back from three directions.",
                "Bioluminescent patch on the ceiling. Small, faint, undisturbed.",
                "Deep city archwork in the corridor ahead. Old. Very purposeful. Stayed quiet.",
                "The rock temperature is above what the air explains. Warm stone.",
                "A dried channel in the floor. Ancient riverbed, long underground.",
                "No fossils down this deep. The stone is older than what made fossils.",
                "Magma channel visible through a crack no wider than a paw.",
                "The tunnel bent at an exact angle. Someone made this.",
                "Sculk sensor in the passage. Moved over it with great attention.",
                "The ceiling shed a thin layer of dust. Waited a long time.",
                "Every sound absorbed within five paces. The quiet is total.",
                "Darkness that the torch barely pushed back. Each step deliberate.",
                "Blue-black mineral sheening in the wall. Not sure what it is.",
                "The passage opened into a space too large to map in the dark.",
                "Deep crack across the floor. Bridged it. Didn't look down.",
                "Footsteps before me in the dust. Very old. Very clear.",
                "The stone here grows darker the deeper you go. No floor visible ahead.",
                "The deepslate here has pressure ridges. The earth moved at great depth.",
                "Complete silence. Even the torch seems quieter."
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
                "Something moved in the chamber below. Did not go down.",
                "Ceiling dripped on the torch. Went dark briefly. Relit.",
                "The passage looked clear. Wasn't.",
                "Something rattled in the shaft above. Dust and small stone. Moved.",
                "Took the wrong branch twice before correcting.",
                "The slope was steeper than it looked. Slipped. Caught the wall.",
                "Lost the marked trail briefly. Backtracked. Found it.",
                "Fissure hidden by shadow. Tested each step after that.",
                "The torch went dim in a pocket of bad air. Got through fast.",
                "A loose stone in the ceiling. Watched it. Went around.",
                "Went deeper than intended. Added time.",
                "The passage was flooded to the ankle. Got wet. Moved on.",
                "Missed a turn. Added distance. Not time.",
                "The echoes confused the direction. Paused and recalculated.",
                "Rock dust in the eyes. Stopped briefly.",
                "A step gave way underfoot. Caught balance. Continued."
            };
            case MODERATE -> new String[]{
                "Ceiling cracked. Held still. It held.",
                "Gravel pour from above. Moved before it filled the corridor.",
                "Water flooded the lower corridor fast. Climbed out.",
                "Deep water ahead, no bottom. Found another way around.",
                "Clicking in the dark behind me. Picked up the pace.",
                "Breath in the dark that wasn't mine. Not going back that way.",
                "Heat rising through the floor. Magma below. Found a way around it.",
                "The walls narrowed unexpectedly. Had to work back out.",
                "A deep crack appeared in the floor mid-passage. Circumnavigated.",
                "Heard collapse behind me after I'd cleared it. Close.",
                "The water came quickly. Gained the high ground in time.",
                "Got turned around in the branching tunnels. Cost an hour.",
                "Something in the passage I couldn't see clearly. Didn't need to. Left.",
                "A pocket of gas. Burning throat. Got through fast.",
                "The scaffold underfoot gave. Caught the wall.",
                "Trapped in a chamber as the passage behind me filled with gravel. Dug.",
                "Shaft dropped vertically without warning. Caught the edge.",
                "The hissing from the wall was not mechanical. Moved quickly.",
                "Tunnel partially collapsed. Cleared enough to pass.",
                "Large cave spider in the low ceiling. Kept low. Moved fast.",
                "Map was wrong. Lost three hours.",
                "A skeleton in armature. Freshly armed.",
                "Climbing up out of the flooded section took longer than going down.",
                "Water level rose while working. Moved before it mattered more.",
                "Cornered by two directions of noise. Picked the lesser one. Correct.",
                "Lost the tool belt in the scramble. Found most of it.",
                "Flint struck sparks near the gas seep. Not well-placed. Fine.",
                "The hive of cave spiders was larger than the entrance suggested."
            };
            case SEVERE -> new String[]{
                "Lava pocket opened up mid-swing. Retreated fast. Singed.",
                "Cave-in above. Buried the passage. Had to dig back out.",
                "Support pillar gone. The whole section leaned. Left quickly.",
                "Aquifer burst through the wall. Cold and sudden. Lost the passage.",
                "Something in the dark that wasn't afraid. Moved fast.",
                "The cave-in took the lit section. Worked in darkness for a while.",
                "Lava channel broke open between me and the exit. Went around the long way.",
                "The floor gave entirely. Fell. Made it.",
                "Deep water rushed in through the south wall. Climbed. Survived.",
                "The warden was close enough to feel the vibration. Did not move for a long time.",
                "Magma ignited the torch. Moved fast in the dark.",
                "The deep rock shifted. The whole section unstable. Evacuated.",
                "Cave-in buried the pickaxe. Had to choose. Left it. Kept moving.",
                "Cornered at a ledge with no path except through. Went through.",
                "Something old and vast moved in the flooded chamber. Watched from the ledge.",
                "The heat reached through the stone. Skin hot through the fur. Moved.",
                "Barely found the exit before the water hit ceiling level.",
                "A fall that should have been worse. Wasn't. Lucky.",
                "Came out burned along one side. Came out.",
                "Lost the map to the lava. Navigated by memory. Correct."
            };
        };
        sim.addLogLine(lines[rng.nextInt(lines.length)]);
        sim.applyHazardCost(level, rng, hl);
    }
}
