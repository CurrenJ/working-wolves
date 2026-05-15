# Working Wolves — Design Spec

## Overview

Working Wolves adds roles and logistics to tamed wolves via craftable collars. Each collar tier unlocks wolf classes: Retriever, Hunter, and Miner.

Retriever wolves operate physically in the world near their bed. Hunter and Miner wolves go on **simulated expeditions** — they appear to leave, the server runs a tick-based simulation that produces loot and a live narrative log, then they return. This gives the feel of a physical expedition without the fragility of long-range pathfinding and chunk loading.

Separately, all collared wolves support **companion mode**: when idle and near their owner, they physically assist — hunting nearby mobs, mining nearby ores — without going on expedition.

---

## Core Systems

### Chunk Loading

Only retriever wolves hold chunk tickets. Hunter and Miner wolves on simulated expeditions have their AI suppressed and do not load chunks.

| Role state | Chunk radius |
|---|---|
| Retriever (active) | 3×3 chunks |
| Hunter / Miner on expedition | None |
| Any wolf (idle / companion mode) | None |

Overlapping zones from nearby wolves merge into a single ticket to reduce server load.

**Wolf cap:** Configurable per player, default 5.

### Collar Progression

Collars are crafted items applied to a tamed wolf to grant it a class and inventory. Higher tiers unlock more classes and bag space. Upgrading a collar preserves the wolf's current class assignment.

| Tier | Item | Classes unlocked | Bag slots | Expedition duration |
|---|---|---|---|---|
| Leather collar | Leather + string | Retriever | 5 | — |
| Iron-studded collar | Leather collar + iron ingots | Retriever, Hunter | 9 | 10 min |
| Gold-trimmed collar | Iron-studded collar + gold ingots | Retriever, Hunter, Miner | 15 | 15 min |

Collar color on the wolf model changes to indicate tier.

### Wolf Bag

Each collar provides an inventory accessible by right-clicking the wolf. Wolves autonomously use items from this bag:
- **Food**: consumed to heal (proportional to nutrition value). Also acts as expedition endurance — low food increases injury risk during simulation.
- **Pickaxes**: equipped by miner wolves in companion mode; pickaxe quality and enchants scale simulated mining yield.
- **Weapons**: weapon damage and Looting enchant scale simulated combat yield for hunters.
- **Filter items**: held in mouth to set behavior target (companion mode and retriever) or focus simulated expedition loot.

### Wolf Armor Interaction

Vanilla wolf armor works as normal. Collar tier defense bonus stacks with armor. Higher armor reduces injury probability during simulated expeditions.

---

## Dog Bed

The dog bed is a placed block that serves as a wolf's home point, expedition origin, and item deposit target.

### Assignment

Right-click a tamed wolf onto a dog bed to assign it. Only one wolf per bed. The wolf remembers the bed's location.

### Auto-Deposit

When a wolf returns from expedition, it navigates to its bed and deposits all pending loot into the bed's internal storage (27 slots). The player collects items from the bed GUI.

### Expedition Journal

The bed stores a full text log of the wolf's most recent expedition. The log is displayed in the bed GUI alongside the inventory. New lines append in real time — any player with the bed GUI open sees them appear as the expedition progresses. The log persists after the wolf returns until the next expedition begins (full log, no truncation).

### Bed Destruction

If the bed block is broken while a wolf is on expedition:
- The simulation aborts; the wolf reappears near the bed's last location and drops all pending loot on the ground.
- To resume expeditions, the player must place a new bed and reassign the wolf.

---

## Simulated Expedition

### Dispatch

The Dispatch Whistle triggers expedition start when used on an assigned, idle Hunter or Miner wolf. *(Future: a GUI button in the bed screen will replace or supplement this.)*

### Departure & Arrival

**Departure:**
1. Wolf's AI is suppressed. It walks ~12 blocks in a random direction from the bed.
2. At the destination: howl sound + brief particle puff. Wolf becomes invisible and teleports to inside the bed block (hidden, AI off).
3. First log line appears in the bed journal.

**Arrival:**
1. Simulation ends (timer elapsed, bag full, or failure resolved).
2. Wolf teleports to a random point ~12 blocks from the bed in a different direction and becomes visible.
3. Wolf walks to the bed and deposits loot normally.
4. Final log line appears.

### Simulation Engine

The simulation runs inside `DogBedBlockEntity` on the server tick. It advances one event every 60–100 ticks (~3–5 seconds real time). Each event appends a log line, optionally adds to a loot buffer, and optionally triggers a state change.

**Simulation state machine:**
```
DEPARTING → TRAVELING → RETURNING
```

In `TRAVELING`, the sim rolls against a weighted event table each step. Weights are set by the wolf's factors (see below). The simulation cycles through "zones" that shift the event table over time (surface → cave → deep cave for miners; patrol area → combat zone → recovery for hunters).

### Loot Factors

| Factor | Effect |
|---|---|
| **Collar tier** | Unlocks event categories; higher tier = access to rarer loot tables |
| **Biome at bed** | Primary loot flavor — biome determines available event pools (e.g., plains gives common mob drops and crops; underground/cave gives ores; dangerous biomes increase hazard weight) |
| **Filter item** | Focuses rolls toward one loot category; increases quantity of that type, reduces variety |
| **Pickaxe in bag** *(miner)* | Tool speed stat scales ore yield; Fortune increases drop count; Silk Touch changes drop type |
| **Weapon in bag** *(hunter)* | Damage stat scales combat success chance; Looting increases mob drop rolls |
| **Food in bag** | Expedition endurance — low food increases injury event probability; no food makes severe injury likely |
| **Wolf armor** | Reduces injury and death probability |
| **Expedition duration** | More time = more event rolls, with diminishing returns after ~⅔ of the timer has elapsed |

### Event Types & Sample Log

**Travel (flavor, no loot):**
> *"Left the warmth of the bed."*
> *"Dark corridor. Dripping stone."*
> *"Something moved ahead. Paused. Continued."*

**Discovery (loot):**
> *"Caught scent of iron. Dug in."* → +2–4 iron ore
> *"A skeleton, patrolling alone. Engaged."* → +0–2 bones, +0–1 arrow
> *"Vein of coal behind the wall. Long work."* → +4–8 coal

**Hazard (endurance cost, no loot):**
> *"Lava nearby. Retreated."*
> *"Three of them at once. Bit and held on."* → –food charge
> *"Took a hit. Not good."*

**Return triggers:**
> *"Bag heavy. Time to head back."* (bag full)
> *"Getting tired. Turning around."* (timer elapsed)
> *"Injured. Heading home."* (injury chain)

### Expedition Failure

Hazard events accumulate an injury counter. Two failure outcomes exist, both rare:

**Return empty (more common):** Simulation aborts. Wolf returns with no loot. Log ends:
> *"Came back with nothing. Sat by the bed for a long time."*

**Death (rare):** Wolf does not return. Collar is destroyed, bag contents drop at the bed. Log ends:
> *"Didn't come back."*

Death probability is a function of: food level at injury, armor, biome danger, and expedition duration. Configurable in Config (`expeditionDeathChance`, default low). Return-empty is always more likely than death for a wolf with any food and any armor.

---

## Classes

### Retriever Wolf

**Unlocked at:** Leather collar
**Role:** Idles near bed, collects nearby dropped items and deposits them. No expedition.

| Property | Value |
|---|---|
| Pickup range | Configured scan range from bed |
| Idle behavior | Sits by bed when no items to collect |
| Filter | Held item in mouth filters which items to pick up (empty = all) |
| Pathfinding | Skips unreachable items; caches failures for 30 seconds |

Retriever operates continuously and indefinitely. It does not have a dispatch flow.

### Hunter Wolf

**Unlocked at:** Iron-studded collar
**Role:** Simulated expedition to hunt hostile mobs and collect their drops.

Loot flavor is driven by the filter item and bed biome. Log events describe tracking, engaging mobs, and collecting drops. Companion mode (idle, near owner): physically hunts nearby hostile mobs while following the owner.

| Property | Value |
|---|---|
| Filter | Held item sets target mob type; empty = any hostile |
| Retreat threshold | 50% health (companion mode) |
| Re-engage threshold | 75% health (companion mode) |
| Expedition death risk | Low, scales with food and armor |

### Miner Wolf

**Unlocked at:** Gold-trimmed collar
**Role:** Simulated expedition to locate and mine ores.

Loot flavor is driven by filter item, pickaxe quality, and bed biome. Log events describe navigating caves, finding veins, and mining. Companion mode (idle, near owner): physically mines ores near the owner.

| Property | Value |
|---|---|
| Filter | Held ore item sets target ore type; empty = any ore |
| Pickaxe | Required in bag; quality and enchants influence simulated yield |
| Expedition death risk | Low, scales with food and armor |

---

## Whistles

### Dispatch Whistle

**Status: Deprecated (functional).** Right-click an assigned, idle Hunter or Miner wolf to begin a simulated expedition. Will be supplemented by a bed GUI button in a future update.

### Recall Whistle

**Status: Deprecated.** Behavior for simulated expeditions TBD.

---

## Death & Self-Preservation

### Physical Death

When a wolf dies in the world (companion mode or retriever):
- Collar is destroyed
- Bag contents drop at the death location
- Wolf must be replaced with a new tamed wolf and collar

### Expedition Failure Death

When a simulated expedition ends in death:
- Wolf entity is removed
- Collar is destroyed
- Bag contents (pending loot) drop at the bed location
- Log ends with *"Didn't come back."*

### Self-Preservation (Physical)

Handled by `WolfSelfPreservationMixin` (injected at `Wolf.aiStep()`). Active for all physical wolves (retriever, companion mode). Behaviors:

| Threat | Response |
|---|---|
| Health < 75% | Eats food from bag (proportional heal, eating particles + mouth animation) |
| Cornered (3+ hits without moving) | Flees to a reachable position away from hostiles |
| Lava / fire within 2 blocks | Paths away at high speed |
| Wolf in lava or on fire | Paths to safe position |
| 3+ hostile mobs in melee range | Disengages, repositions |
| Falls of 4+ blocks | Avoids unless on return path |

Wolves on simulated expeditions have AI suppressed — self-preservation does not run during expedition.

---

## Owner System

Wolves use vanilla ownership (the player who tamed them). Only the owner can:
- Apply / remove / upgrade a collar
- Assign the wolf to a bed
- Open the wolf's bag
- Use the Dispatch Whistle on the wolf
- Recall Whistle affects only wolves owned by the user

---

## Key Design Principles

1. **Wolves are helpers, not replacements** — expedition results are meaningful but not overpowered; mining is slower than a player, hunting is risk-limited
2. **Expeditions feel real** — departure and arrival animations, a live narrative log, and multi-factor loot create immersion without the fragility of physical long-range AI
3. **Companion mode is always available** — collared wolves physically assist nearby owners regardless of expedition capability
4. **Minecraft-native UX** — held-item filters, right-click interactions, block-based beds; no GUIs where a world interaction works
5. **Loss is meaningful but fair** — collar destruction is possible but rare for a prepared wolf; self-preservation AI and wolf armor reduce physical risk; food and armor reduce expedition death risk
6. **Progression is gated, not blocked** — each collar tier adds capabilities; upgrading preserves investment
