# Working Wolves — Design Spec

## Overview

Working Wolves adds sophisticated roles and logistics to tamed wolves via craftable collars. Each collar tier unlocks wolf classes: Retriever, Hunter, and Miner. Wolves are "first-class citizens" — they load chunks like players, enabling physical expeditions through the world, autonomous combat, and item collection.

---

## Core Systems

### Chunk Loading

Wolves hold chunk tickets, keeping their surroundings loaded for pathfinding and world interaction. Entity spawn/despawn rules apply in the wolf's chunk radius, making wolves useful for mob farms.

| Role state | Chunk radius |
|---|---|
| Expedition (miner/hunter actively working) | 5×5 chunks |
| Idle / Retriever | 3×3 chunks |

Overlapping zones from nearby wolves merge into a single ticket to reduce server load.

**Wolf cap:** Configurable per player, default 5. Prevents server overload from wolf spam.

### Collar Progression

Collars are crafted items applied to a tamed wolf to grant it a class and inventory. Higher tiers unlock more classes and bag space. Upgrading a collar preserves the wolf's current class assignment.

| Tier | Item | Classes unlocked | Bag slots | Expedition timer |
|---|---|---|---|---|
| Leather collar | Leather + string | Retriever | 5 | 5 min |
| Iron-studded collar | Leather collar + iron ingots | Retriever, Hunter | 9 | 10 min |
| Gold-trimmed collar | Iron-studded collar + gold ingots | Retriever, Hunter, Miner | 15 | 15 min |

Collar color on the wolf model changes to indicate tier. No saddlebag model — visual feedback is the collar color alone.

### Wolf Bag

Each collar provides an inventory accessible by right-clicking the wolf. Wolves autonomously use items from this bag:
- **Food**: consumed to heal during retreat
- **Pickaxes**: equipped by miner wolves to break ores
- **Filter items**: held in mouth to set behavior target

Pickaxes consume durability. When a pickaxe drops to 10%, the miner wolf auto-returns to base. If no usable pickaxe remains in the bag, the wolf aborts the expedition and returns.

### Wolf Armor Interaction

Vanilla wolf armor works as normal. Collar tier defense bonus stacks with armor, making a well-equipped wolf significantly tankier.

---

## Dog Bed

The dog bed is a placed block that serves as a wolf's home point, expedition start, and item deposit target.

### Assignment

Right-click a tamed wolf onto a dog bed to assign it. Only one wolf per bed. The wolf remembers the bed's location.

### Auto-Deposit

When a miner or hunter wolf returns from expedition, or when a retriever wolf's bag is full, the wolf navigates to its bed and deposits all bag contents into the bed's internal storage (27 slots). The player collects items from the bed GUI.

### Expedition Timer

The maximum expedition duration is fixed by collar tier (5/10/15 min). The wolf tracks elapsed time and initiates return with enough buffer to pathfind home. If the timer expires before the wolf reaches the bed, it still attempts to complete the return — it simply won't gather more resources.

### Bed Destruction

If the bed block is broken:
- The wolf remembers the last bed location and continues attempting to return there
- Upon reaching the spot, it drops all bag contents on the ground (deposit target is gone)
- To resume expeditions, the player must place a new bed and reassign the wolf

---

## Classes

### Retriever Wolf

**Unlocked at:** Leather collar  
**Role:** Idles near bed, collects nearby dropped items and deposits them.

| Property | Value |
|---|---|
| Pickup range | 64 blocks from bed |
| Idle behavior | Sits by bed when no items to collect |
| Filter | Held item in mouth filters which items to pick up (empty mouth = all items) |
| Pathfinding | Skips items unreachable by pathfinding; caches failures for 30 seconds |

The retriever does not go on timed expeditions — it operates continuously within its range. Boxing the wolf in with walls or fences effectively controls its collection area, since it won't pathfind to unreachable items.

### Hunter Wolf

**Unlocked at:** Iron-studded collar  
**Role:** Dispatched on expedition to kill hostile mobs and collect their drops.

| Property | Value |
|---|---|
| Filter | Held item in mouth sets target mob type (e.g., bone → skeletons, gunpowder → creepers). Empty mouth = any hostile mob |
| Retreat threshold | 50% health |
| Re-engage threshold | 75% health (heals, then resumes hunting) |
| Healing | Eats food from bag at half player speed during retreat |
| Combat | Standard wolf melee attack; wolves are already effective vs most common hostiles |

**Retreat behavior:** When health drops to 50%, the wolf disengages, flees a minimum distance, and eats to heal. If multiple hostile mobs (3+) are within melee range, the wolf disengages preemptively regardless of health.

**Expedition flow:** Dispatch → search for target mobs → engage → collect drops → repeat until bag full or timer low → return to bed → deposit.

### Miner Wolf

**Unlocked at:** Gold-trimmed collar  
**Role:** Dispatched on expedition to locate and mine ores.

| Property | Value |
|---|---|
| Mining speed | 0.5× player speed |
| Ore scan range | 12 blocks (through walls) |
| Filter | Held pickaxe type determines target; held ore item in mouth further filters (e.g., hold diamond → only mine diamond ore) |
| Pickaxe durability | Consumed; auto-return at 10% durability remaining |
| Mining animation | Player-style swing (pickaxe held in mouth, head swings at block) |

**Mining behavior:** The wolf scans within 12 blocks for target ores. If found, it navigates to the ore and mines it. If no ores are found, it wanders further from the last scan point and re-scans, prioritizing unexplored areas. The wolf navigates caves but avoids drops of 4+ blocks unless it's the direct return path.

**Expedition flow:** Dispatch → scan for ores → pathfind to ore → mine → collect drops → repeat until bag full, pickaxe low, or timer low → return to bed → deposit.

---

## Whistles

Two separate items for controlling wolves at range.

### Dispatch Whistle

| Property | Value |
|---|---|
| Recipe | Iron ingot + note block |
| Use | Right-click a wolf to toggle between idle and expedition-ready |

### Recall Whistle

| Property | Value |
|---|---|
| Recipe | Dispatch whistle + echo shard |
| Use | Right-click air to recall all owned wolves globally |
| Cooldown | 2 minutes |
| Cross-dimension | Yes — works across Overworld, Nether, and End |

On recall, all owned wolves immediately abort their current task and pathfind to their assigned bed. A howl sound plays, and brief particle trails show each wolf's direction.

---

## Death & Self-Preservation

### Death

When a wolf dies:
- The collar is destroyed
- All bag contents drop as world items at the death location
- The wolf must be replaced with a new tamed wolf and collar

### Self-Preservation AI

All working wolves share baseline survival behaviors:

| Threat | Response |
|---|---|
| Lava / fire (within 2 blocks) | Immediately path away, hard avoid |
| Falls of 4+ blocks | Avoid unless it's the direct path home |
| 3+ hostile mobs in melee range | Disengage and reposition |
| Deep water | Will cross if on path to objective, prefer land routes |

---

## Owner System

Wolves use vanilla ownership (player who tamed them). Only the owner can:
- Apply/remove/upgrade a collar
- Assign the wolf to a bed
- Open the wolf's bag
- Use dispatch whistle on the wolf
- Recall whistle affects only wolves owned by the user

---

## Key Design Principles

1. **Wolves are helpers, not replacements** — mining is slower than a player, hunting is risk-limited, retrieving is area-controlled
2. **Physical presence matters** — wolves exist in the world, load chunks, and can be seen working. No abstraction.
3. **Minecraft-native UX** — held-item filters, right-click interactions, block-based beds. No GUIs where a world interaction works.
4. **Loss is meaningful but fair** — collar destruction on death hurts, but self-preservation AI and wolf armor make death uncommon for well-equipped wolves.
5. **Progression is gated, not blocked** — each tier adds capabilities; upgraded collars preserve investment.
