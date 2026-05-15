# AI Goals

Custom goals are injected into the wolf's goal selector via `WolfMixin.workingwolves$registerGoals`. All goals gate on `collarTier > 0`.

Hunter and Miner wolves use **simulated expeditions** — when dispatched, their AI is suppressed and a server-side simulation runs in `DogBedBlockEntity`. The goals below only apply to **physical** wolf behaviors: retriever operation and companion mode.

## Priority ordering

| Prio | Goal | Active states | Purpose |
|---|---|---|---|
| 0 | `AntiStuckGoal` | retriever idle, companion mode | Unstick teleport after 30s no movement |
| 1 | `ReturnToBaseGoal` | returning (retriever bag full) | Pathfind to bed and deposit |
| 2 | `RetrieverGoal` | idle + retriever class | Collect items near bed or owner |
| 2 | `HunterGoal` | idle + hunter class (companion mode) | Kill nearby hostiles while following owner |
| 2 | `MinerGoal` | idle + miner class (companion mode) | Find and mine ores near owner |

Self-preservation runs outside the goal system — see `WolfSelfPreservationMixin`.

---

## AntiStuckGoal

Detects if the wolf hasn't moved >2 blocks in 30 seconds (600 ticks). If stuck:
- Tries up to 256 random positions within an expanding radius (base 32, doubles each consecutive failure, cap 128).
- Requires solid ground, air at position and above, no liquids.
- Fallback: teleport 1 block up if clear.
- Consecutive failures increase the search radius for next time; success resets.

Active when `expeditionState` is `"idle"` (not during simulated expedition).

---

## ReturnToBaseGoal

Active when `expeditionState == "returning"` (retriever bag full).

**Flags:** `noneOf` — does not claim MOVE flag, allowing other movement to coexist.

**Destination priority:** `bedPos` if set, otherwise owner position.

**Arrival** (within 2 blocks of destination):
- If bed BE exists → `depositItems()`.
- If bed BE missing → `dropAllItems()` on ground, clears `bedPos`.
- Sets state to `"idle"`, orders wolf to sit, syncs data.

**Pathfinding:** Waypoint system for long distances — if >64 blocks from destination, paths to an intermediate waypoint 64 blocks toward the destination. Repaths every 40 ticks or when navigation completes.

---

## RetrieverGoal

Active: `collarTier > 0`, `wolfClass == "retriever"`, `expeditionState == "idle"`, `!isOrderedToSit`.

**Scan center:** `bedPos` if assigned; otherwise owner position (companion mode, requires owner within 32 blocks).

**Scan:** Every 20 ticks, scans for `ItemEntity` within `Config.retrieverScanRange` blocks of the scan center. Filters by `filterItem`.

**Unreachable cache:** Failed pathfinding positions cached for 30 seconds (600 ticks). `findNearestReachableItem` iterates by distance, tests each with `navigation.createPath + canReach()`, caches failures.

**Pursuit:** Paths to target item at speed 1.0. Repaths every 20 ticks. Picks up at 1.5 blocks.

**Bag capacity:** ≥80% full → sets state to `"returning"`.

**Idle deposit:** No collectible items for 10 seconds (200 ticks) while bag has items → triggers return.

---

## HunterGoal (companion mode only)

Active: `collarTier > 0`, `wolfClass == "hunter"`, `expeditionState == "idle"`, `!isOrderedToSit`, owner within 32 blocks.

Scans within 24 blocks of the owner for hostile mobs. Engages the nearest **pathfindable** target (reachability check via `navigation.createPath + canReach()`). Follows owner when no target found and >16 blocks away.

**Weapon selection:** Picks best weapon from bag. Prefers melee when target is within 3 blocks even if ranged weapon is equipped. Won't start bow pull if bag has no ammo.

**Nav budget:** Applied at `start()` — companion range (24).

**Retreat:** Health < 50% → retreat for 100 ticks, eat food from bag. Re-engage at 75%.

---

## MinerGoal (companion mode only)

Active: `collarTier > 0`, `wolfClass == "miner"`, `expeditionState == "idle"`, `!isOrderedToSit`, owner within 32 blocks.

Scans within 16 blocks of the owner for ores. Mines them at half player speed using the best pickaxe from the bag (via `hurtAndBreak`, Unbreaking-aware). Follows owner when no ore found for 30 seconds (600 ticks) or when owner is >8 blocks away.

**Scan center:** Owner position (not wolf position) — mines near the player, not near itself.

**Mining:** Progress ticks with block break particles. On completion: `Block.getDrops()` with the pickaxe (Fortune/Silk Touch apply), `level.destroyBlock()`.

**Drift protection:** If wolf moves >5 blocks from the mining position during mining, resets progress.

---

## WolfSelfPreservationMixin

Not a goal — injected at `Wolf.aiStep()` HEAD. Runs every tick for collared wolves. Runs regardless of expedition state, but wolves on simulated expeditions have AI fully suppressed so this injection still fires but `workingwolves$getCollarTier() <= 0` check... actually: the mixin checks collar tier, not expedition state, so it runs for all collared wolves including those on expedition.

**Behavior order:**
1. Corner tracking — accumulates hurt count; resets if wolf moves or time window expires.
2. Flee ticker — if active, skip all further processing.
3. **Healing** — if health < 75% and cooldown elapsed: show food in mouth, spawn eating particles, consume food from bag (proportional heal).
4. **Corner escape** — 3+ hits without moving triggers flee to reachable position.
5. **Lava/fire avoidance** — nearby hazard or wolf on fire → flee.
6. **Crowding** — 3+ hostiles in 3-block melee range → disengage.
7. **Falls** — 4+ block drop ahead, not returning → avoid.
8. Mouth food timer — clears mouth item after eating animation.

---

## Simulated Expedition (not a goal)

The expedition simulation lives in `DogBedBlockEntity`. See `SPEC.md §Simulated Expedition` for the full design. Summary:

- Dispatch Whistle → wolf walks ~12 blocks away, vanishes (invisible, teleported inside bed block, AI off).
- `DogBedBlockEntity.tick()` advances the simulation every 60–100 ticks, generating log lines and loot.
- Log lines pushed to open clients via packet.
- On completion: wolf reappears ~12 blocks from bed, walks home, deposits loot.
- Failure paths: return empty (common) or death / collar destruction (rare, config-tunable).
