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
                "Scent of iron in the air. Getting closer.",
                "Rabbit tracks, then larger tracks following them. Noted.",
                "The wind carried smoke. Old and cold. Someone's fire, days back.",
                "A bird sat very still on the branch above. Watched me until I passed.",
                "Deer path well-worn. Not by deer alone.",
                "Moved through tall grass without disturbing the tops.",
                "The hollow in that tree is occupied. Not checking by what.",
                "Scent changed as the ground fell away. Getting close to water.",
                "Spotted something against the hillside. Stayed downwind.",
                "Old kill site. Picked clean. Bones scattered wide.",
                "The undergrowth opened suddenly. Open ground. Exposed briefly.",
                "Sound of wings ahead. Flushed something without meaning to.",
                "A burrow under the root. Fresh-scraped earth around it.",
                "The light was going fast. Picked up the pace.",
                "Still air. Every step loud. Slowed accordingly.",
                "Hoof prints in the mud, deep-set, heavy. Elk or worse.",
                "Something else came through here before dawn. Not long before.",
                "The trail dog-legged south. Followed it.",
                "High ground. Good view of the valley. Catalogued what moved below.",
                "Damp air. The cloud ceiling is low. Good tracking weather.",
                "Scat on the path. Recent. Changed approach.",
                "A screech from the canopy. Some kind of predatory bird watching.",
                "The game trail crossed the stream three times. Crossed it too.",
                "Nothing but the sound of wind and something that wasn't wind.",
                "The forest floor was soft here. Left no sound but left marks.",
                "Crows ahead, circling something. Investigated. Not relevant.",
                "Eyes in the brush at dusk. Both of us froze. They moved first.",
                "The valley narrows here. Good ambush country for both sides.",
                "Frost on the trail. Last night was colder than expected.",
                "Moss grows thick on the north face of these trunks. Orienting.",
                "A piece of fur caught in the bark. Not mine.",
                "Circled wide around the watering hole. Watching first.",
                "The trail went cold twice. Picked it up again twice.",
                "Low scrub with thorns. The passage cost some fur.",
                "The smell of blood somewhere upwind. Old. No need to investigate.",
                "Heard something large breathing nearby. Froze. It moved on.",
                "Open meadow at dusk. Crossed fast. Back under cover."
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
        sim.addPendingLoot(drops);
        sim.addLogLine(chosen.journalLines().get(rng.nextInt(chosen.journalLines().size())));
    }

    static void rollHazard(Level level, Random rng, ExpeditionSimulator.HazardLevel hl, ExpeditionSimulator sim) {
        String[] lines = switch (hl) {
            case LIGHT -> new String[]{
                "Got bit. Not badly. Kept going.",
                "Wrong side of a ravine. Had to double back.",
                "Ambushed. Recovered faster than expected.",
                "Something stirred in the brush. Moved on.",
                "Lava nearby. Backed off.",
                "Surprised it. Surprised me too. Worked out.",
                "Stepped wrong on the slope. Steady.",
                "Something nipped. Not serious.",
                "Tripped on exposed root. No one saw it.",
                "Got cut by something in the brush. Shallow.",
                "Lost the trail briefly. Found it again.",
                "Took a glancing blow. Barely felt it.",
                "Too many at once for a clean shot. Improvised.",
                "Backed into a thorn wall. Forward was safer.",
                "Misjudged the distance. Adjusted.",
                "Bit. The attacker regretted it.",
                "Clipped by something in the dark. A scratch.",
                "Spooked the wrong animal first. Sorted it out.",
                "Brush grabbed the leg. Pulled free.",
                "The ground gave. Caught myself."
            };
            case MODERATE -> new String[]{
                "Three of them at once. Held on.",
                "Cornered briefly. Found a way out.",
                "Took a hit. Kept moving.",
                "Outnumbered. Fought anyway.",
                "Pack of them. Retreated and regrouped.",
                "Larger than expected. Changed the approach mid-fight.",
                "Caught off-guard. Recovered.",
                "Two directions at once. Picked the worse threat first.",
                "They coordinated. Adapted.",
                "Couldn't run. Fought instead.",
                "Backed up as far as I could. Then held.",
                "Hit harder than prepared for. Still going.",
                "Lost ground before gaining it back.",
                "Something circled from behind. Caught it in time.",
                "Pinned briefly. Not long.",
                "Tooth met armor. Worked in my favor.",
                "Outwitted. Temporarily.",
                "Took ground slowly. Kept it.",
                "Close range, no choice. Managed it.",
                "Used the terrain. The terrain helped."
            };
            case SEVERE -> new String[]{
                "Too many. Barely got clear.",
                "Took the worst of it. Still here.",
                "Nearly didn't make it out. Did.",
                "The pack was bigger than it looked. Ran.",
                "Something found me before I found it. Cost me.",
                "Not coming back that way.",
                "The noise ahead went quiet all at once. That's worse.",
                "Claws. Ran. Can describe it later.",
                "Teeth marks on the bag. Not on me. Barely.",
                "Left something behind to make it out.",
                "Everything went wrong. Still here.",
                "Worst of it hit the armor. Mostly.",
                "The pack separated us. Regrouped later.",
                "Can't eat that much. Just run from it.",
                "Came out the wrong side. Came out.",
                "Something enormous in the dark. Moved. Survived.",
                "Fought longer than was smart. Stopped when stopping mattered.",
                "Lost supplies back there. Didn't lose more than that.",
                "The injury isn't bad. The memory of how it happened is.",
                "A fight I didn't plan for. Won anyway."
            };
        };
        sim.addLogLine(lines[rng.nextInt(lines.length)]);
        sim.applyHazardCost(level, rng, hl);
    }
}
