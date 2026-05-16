package grill24.workingwolves.blockentity.expedition;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

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
        String mob = getMobForZone(zone, sim.getBiomeCategory(), sim.getCollarTier(), rng);

        switch (mob) {
            case "skeleton" -> {
                int bones = 1 + rng.nextInt(3) + sim.getLooting();
                int arrows = rng.nextInt(2) + sim.getLooting();
                if (bones > 0) sim.addPendingLoot(new ItemStack(Items.BONE, bones));
                if (arrows > 0) sim.addPendingLoot(new ItemStack(Items.ARROW, arrows));
                String[] lines = {
                    "Skeleton at range. Closed before it could draw again.",
                    "Arrow clipped my ear. Didn't slow me down.",
                    "Two skeletons. Took turns on each.",
                    "Pinned behind a tree for a moment. Then I wasn't.",
                    "Skeleton in the open. No cover for either of us.",
                    "Nocked and ready. I was faster."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "zombie" -> {
                int flesh = 1 + rng.nextInt(2) + sim.getLooting();
                sim.addPendingLoot(new ItemStack(Items.ROTTEN_FLESH, flesh));
                String[] lines = {
                    "Zombie, slow and loud. Over quickly.",
                    "Three zombies. Messy. Done.",
                    "It grabbed my scruff. Regretted it.",
                    "Zombie mob. Picked them off one by one.",
                    "Got swarmed for a moment. Pushed through.",
                    "Undead smell. Three bites, none landed."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "spider" -> {
                int string = rng.nextInt(3) + sim.getLooting();
                int eye = rng.nextInt(2) + sim.getLooting();
                if (string > 0) sim.addPendingLoot(new ItemStack(Items.STRING, string));
                if (eye > 0) sim.addPendingLoot(new ItemStack(Items.SPIDER_EYE, eye));
                String[] lines = {
                    "Spider dropped from above. Nearly had me.",
                    "Webbing on my paws. Slowed the approach.",
                    "Jockey. Skeleton on its back. Harder than expected.",
                    "Spider hit first. Left marks. Still won.",
                    "Cave spider. Smaller. The poison was not.",
                    "Leapt wide. I was already moving."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "creeper" -> {
                if (zone >= 1) {
                    int powder = rng.nextInt(2) + sim.getLooting();
                    if (powder > 0) sim.addPendingLoot(new ItemStack(Items.GUNPOWDER, powder));
                }
                String[] lines = {
                    "Heard it hissing. Backed off. Waited for the click. Then closed in.",
                    "Creeper cornered. Circled until it gave up.",
                    "Close. Too close. Singed. Won't do that again.",
                    "Two creepers tangled together. Gave them wide space.",
                    "It started to glow. I ran. It didn't follow far.",
                    "Killed it quick enough. The trick is not hesitating."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "drowned" -> {
                if (zone >= 1 && rng.nextFloat() < 0.25f + sim.getLooting() * 0.1f) {
                    sim.addPendingLoot(new ItemStack(Items.NAUTILUS_SHELL, 1));
                }
                String[] lines = {
                    "Trident hit the bank beside me. Pulled it out of the water anyway.",
                    "Something waded out of the shallows. Finished it fast.",
                    "Drowned with a trident. Kept moving to stay out of the arc.",
                    "Three drowned. Lured them onto dry ground.",
                    "It was slow on land. That's the trick.",
                    "Caught one emerging. The current made it awkward."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "slime" -> {
                int slimeballs = 1 + rng.nextInt(3) + sim.getLooting();
                if (slimeballs > 0) sim.addPendingLoot(new ItemStack(Items.SLIME_BALL, slimeballs));
                String[] lines = {
                    "Slime. Hit it and it became two problems.",
                    "Four of them by the end. Sticky paws.",
                    "Big one. Split twice before it stopped moving.",
                    "Small slimes. More annoying than dangerous. Cleaned up."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "witch" -> {
                net.minecraft.world.item.Item[] witchDrops = {
                    Items.GLASS_BOTTLE, Items.SUGAR, Items.REDSTONE,
                    Items.GUNPOWDER, Items.SPIDER_EYE, Items.GLOWSTONE_DUST
                };
                int dropCount = 1 + rng.nextInt(3) + sim.getLooting();
                for (int d = 0; d < dropCount; d++) {
                    sim.addPendingLoot(new ItemStack(witchDrops[rng.nextInt(witchDrops.length)], 1));
                }
                String[] lines = {
                    "Witch in a hollow. Took a splash before I closed in.",
                    "Slowed by a hex, then a poison one. Still got her.",
                    "She kept drinking. Wouldn't stop healing. Had to be fast.",
                    "Robe and hat. Cackling. Not anymore."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "pillager" -> {
                int arrows = 2 + rng.nextInt(3) + sim.getLooting();
                sim.addPendingLoot(new ItemStack(Items.ARROW, arrows));
                if (rng.nextFloat() < 0.10f + sim.getLooting() * 0.03f) {
                    sim.addPendingLoot(new ItemStack(Items.EMERALD, 1));
                }
                if (rng.nextFloat() < 0.03f) {
                    sim.addPendingLoot(new ItemStack(Items.CROSSBOW, 1));
                }
                String[] lines = {
                    "Pillager patrol. Crossbows up. Broke their formation.",
                    "Bolt hit the dirt by my paw. Closed the distance before reload.",
                    "Two pillagers at an outpost edge. Didn't go inside.",
                    "Pillager captain. Tougher than the rest."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "enderman" -> {
                if (zone >= 2) {
                    int pearls = rng.nextInt(2) + sim.getLooting();
                    if (pearls > 0) sim.addPendingLoot(new ItemStack(Items.ENDER_PEARL, pearls));
                }
                String[] lines = {
                    "Fought it in the rain. Couldn't teleport.",
                    "Caught it off guard near water. It panicked.",
                    "Three failed lunges. The fourth connected.",
                    "It grabbed a block and watched me. I didn't look at its face.",
                    "Kept blinking. Every lunge, somewhere else. Eventually connected.",
                    "Tall. Fast. Got lucky."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "wither_skeleton" -> {
                if (zone >= 2) {
                    int coal = rng.nextInt(2);
                    if (coal > 0) sim.addPendingLoot(new ItemStack(Items.COAL, coal));
                    if (rng.nextFloat() < 0.05f + sim.getLooting() * 0.01f) {
                        sim.addPendingLoot(new ItemStack(Items.WITHER_SKELETON_SKULL, 1));
                    }
                }
                String[] lines = {
                    "Wither skeleton. Its blade gave me the shakes. Pushed through.",
                    "Taller than expected. Slower than expected. Worked in my favor.",
                    "Wither effect. Vision went black for a moment. Kept fighting.",
                    "Something in the dark. Much taller. Much worse.",
                    "It had a reach advantage. Had to get inside its guard.",
                    "Old bones. Strong swing. Took three clean hits."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "phantom" -> {
                int membranes = 1 + (sim.getCollarTier() >= 3 ? rng.nextInt(3) : rng.nextInt(2)) + sim.getLooting();
                sim.addPendingLoot(new ItemStack(Items.PHANTOM_MEMBRANE, Math.max(1, membranes)));
                String[] lines = {
                    "Something diving from above. No warning. Phantom.",
                    "Wings in the dark. Attacked in passes. Waited for the gap.",
                    "Phantom grabbed my scruff mid-leap. Landed hard. Found it again.",
                    "Three of them, circling. Picked them out of the air one by one."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "blaze" -> {
                if (zone >= 2) {
                    int rods = rng.nextInt(3) + sim.getLooting();
                    if (rods > 0) sim.addPendingLoot(new ItemStack(Items.BLAZE_ROD, rods));
                }
                String[] lines = {
                    "Three fireballs in a row, then a window. Used the window.",
                    "Blaze circling overhead. Waited for it to descend.",
                    "Fireball clipped my flank. The heat was worse than the hit.",
                    "The smoke was thick. Found it by the sound.",
                    "Dodged wide on the first burst. Closed on the second pause.",
                    "Fur still smells like forge smoke."
                };
                sim.addLogLine(lines[rng.nextInt(lines.length)]);
            }
            default -> sim.addLogLine("Something moved. Gone now.");
        }
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

    static String getMobForZone(int zone, String biomeCategory, int collarTier, Random rng) {
        return switch (zone) {
            case 0 -> {
                String[] pool = {"skeleton", "zombie", "spider", "slime"};
                yield pool[rng.nextInt(pool.length)];
            }
            case 1 -> {
                String[] pool = {"skeleton", "zombie", "spider", "creeper", "drowned", "slime", "witch", "pillager"};
                yield pool[rng.nextInt(pool.length)];
            }
            default -> {
                String[] pool = {"enderman", "wither_skeleton", "phantom", "pillager",
                    ("mountain".equals(biomeCategory) || "cave".equals(biomeCategory) || "other".equals(biomeCategory))
                        ? "blaze" : "enderman"};
                yield pool[rng.nextInt(pool.length)];
            }
        };
    }
}
