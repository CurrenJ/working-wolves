# Working Wolves — Design Spec

## Overview

Working Wolves adds roles and logistics to tamed wolves via craftable collars. Wolf roles are determined automatically by what tools and weapons the wolf carries in its bag — no manual class assignment.

Wolves with expedition tools go on **simulated expeditions** — they appear to leave, the server runs a tick-based simulation that produces loot and a live narrative log, then they return. This gives the feel of a physical expedition without the fragility of long-range pathfinding and chunk loading.

Separately, all collared wolves support **companion mode**: when idle and near their owner, they physically assist based on their bag contents — hunting nearby mobs, mining nearby ores, chopping nearby logs — without going on expedition. Wolves with no expedition tools simply follow their owner with vanilla wolf behavior and cannot be dispatched.

### Role detection

| Tool in bag | Expedition role | Companion behavior |
|---|---|---|
| Pickaxe | Mining | `MinerGoal` — mines ores near owner |
| Sword / Bow / Crossbow / Mace | Hunting | `HunterGoal` — hunts hostiles near owner |
| Axe | Woodcutting | `WoodcutterGoal` — chops logs near owner |

**Mixed tools:** a wolf with multiple tool types splits expedition events proportionally between active roles (one type = one equal share). Total event count is fixed by expedition duration, so a mixed wolf gets similar total yield as a specialized one — just with variety. Quality scaling (pickaxe speed, axe speed, looting level) still applies within each role's events.

---

## Core Systems

### Chunk Loading

Collared wolves hold chunk tickets to keep their work area loaded. Wolves on simulated expeditions have their AI suppressed and do not load chunks.

| Role state | Chunk radius |
|---|---|
| Active expedition (physical) | 5×5 chunks |
| Idle / companion mode | 3×3 chunks |
| On simulated expedition | None |

Overlapping zones from nearby wolves merge into a single ticket to reduce server load.

**Wolf cap:** Configurable per player, default 5.

### Collar Progression

Collars are crafted items applied to a tamed wolf to grant it an inventory and set expedition duration. Higher tiers provide more bag space and longer expeditions. All collar tiers allow all expedition types — the collar determines how much the wolf can carry and how long it stays out, not what it does.

| Tier | Item | Bag slots | Expedition duration |
|---|---|---|---|
| Leather collar | Leather + string | 5 | 10 min |
| Iron-studded collar | Leather collar + iron ingots | 9 | 15 min |
| Gold-trimmed collar | Iron-studded collar + gold ingots | 15 | 15 min |

Collar color on the wolf model changes to indicate tier.

### Wolf Bag

Each collar provides an inventory accessible by right-clicking the wolf. Wolves autonomously use items from this bag:
- **Food**: consumed to heal (proportional to nutrition value). Also acts as expedition endurance — low food increases injury risk during simulation.
- **Pickaxes**: activate mining role; quality and Fortune/Silk Touch scale simulated ore yield.
- **Axes**: activate woodcutting role; quality scales simulated wood yield.
- **Weapons** (swords, bows, mace): activate hunting role; Looting scales simulated combat yield.

At dispatch, pickaxes and axes are automatically moved from the dog bed inventory into the wolf's bag (the player can pre-stock the bed). Weapons must already be in the bag.

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
| **Active roles** | Each tool type (pickaxe, weapon, axe) adds one role; event rolls are split equally among active roles |
| **Collar tier** | Higher tier = more bag space + longer expeditions; mining rarity scales with tier (diamonds require gold collar) |
| **Biome at bed** | Drives hunting mob pools, mining yield multiplier, and woodcutting wood type |
| **Pickaxe quality** | Speed scales ore yield; Fortune increases drop count; Silk Touch changes drop type |
| **Axe quality** | Speed scales wood yield (iron axe = baseline) |
| **Looting enchant** | Increases mob drop rolls (hunting role) |
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

## Expedition Roles

Roles are determined by bag contents, not a class setting. Multiple roles can be active simultaneously; the simulation splits event rolls proportionally.

### Hunter (sword / bow / crossbow / mace in bag)

**Role:** Simulated expedition to hunt hostile mobs and collect their drops.

Loot flavor is driven by bed biome and collar tier depth. Log events describe tracking, engaging mobs, and collecting drops. Companion mode: physically hunts nearby hostile mobs while following the owner.

| Property | Value |
|---|---|
| Retreat threshold | 50% health (companion mode) |
| Re-engage threshold | 75% health (companion mode) |
| Looting | Scales mob drop count |
| Expedition death risk | Low, scales with food and armor |

### Miner (pickaxe in bag)

**Role:** Simulated expedition to locate and mine ores.

Loot is driven by pickaxe quality, Fortune/Silk Touch, bed biome, and collar tier. Log events describe navigating caves, finding veins, and mining. Companion mode: physically mines ores near the owner.

| Property | Value |
|---|---|
| Pickaxe | Quality and enchants influence simulated yield |
| Expedition death risk | Low, scales with food and armor |

### Woodcutter (axe in bag)

**Role:** Simulated expedition to chop trees and collect wood. Loot is biome-driven — the bed biome determines which wood types are available. Collar tier affects bag capacity and expedition duration only.

| Biome | Wood loot |
|---|---|
| Forest / plains | Oak, birch, saplings, apples |
| Taiga / mountain | Spruce, sweet berries |
| Jungle | Jungle wood, bamboo, cocoa beans |
| Dark forest | Dark oak, mushrooms |
| Savanna | Acacia |
| Cherry grove | Cherry wood, pink petals |
| Mangrove swamp | Mangrove, propagule |

Axe quality scales log yield (iron axe = baseline). Companion mode: physically chops logs near the owner.

| Property | Value |
|---|---|
| Axe | Quality scales simulated yield |
| Expedition death risk | Low, scales with food and armor |

---

## Whistles

### Dispatch Whistle

**Status: Deprecated (functional).** Right-click an assigned, idle wolf with expedition tools to begin a simulated expedition. Will be supplemented by a bed GUI button in a future update.

### Recall Whistle

**Status: Deprecated.** Behavior for simulated expeditions TBD.

---

## Death & Self-Preservation

### Physical Death

When a wolf dies in the world (companion mode):
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

Handled by `WolfSelfPreservationMixin` (injected at `Wolf.aiStep()`). Active for all physical wolves in companion mode. Behaviors:

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
