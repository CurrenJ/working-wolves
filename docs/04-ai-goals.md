# AI Goals

Six custom goals are injected into the wolf's goal selector via `WolfMixin.workingwolves$registerGoals`. They use `@Unique` fields on the wolf mixin for state. All goals gate on `collarTier > 0`.

## Priority ordering

| Prio | Goal | Active states | Purpose |
|---|---|---|---|
| 0 | `AntiStuckGoal` | active, returning | Unstick teleport after 30s no movement |
| 0 | `SelfPreservationGoal` | always (when collared) | Avoid lava/fire/falls/crowding |
| 1 | `ReturnToBaseGoal` | returning | Pathfind to bed and deposit |
| 2 | `RetrieverGoal` | idle + retriever class | Collect items near bed |
| 2 | `HunterGoal` | active + hunter class | Kill hostiles, collect drops |
| 2 | `MinerGoal` | active + miner class | Find and mine ores |

Goals at the same priority don't conflict — they gate on mutually exclusive class/state combinations.

## AntiStuckGoal

Detects if the wolf hasn't moved >2 blocks in 30 seconds (600 ticks). If stuck:
- Tries up to 256 random positions within an expanding radius (base 32, doubles each consecutive failure, cap 128).
- Requires solid ground, air at position and above, no liquids.
- Fallback: teleport 1 block up if clear.
- Consecutive failures increase the search radius for next time; success resets.

Only active when `expeditionState` is "active" or "returning" (not idle retrievers).

## SelfPreservationGoal

Triggers when any threat is detected. Threat checks (in order):

1. **Lava/fire within 2 blocks:** Scans 2-block radius for `BlockTags.FIRE`, `BaseFireBlock`, or `Blocks.LAVA`. Flees to nearest safe position at 1.4 speed for 40 ticks.
2. **Wolf on fire or in lava:** Same flee behavior.
3. **Crowding (3+ hostiles in 3-block melee range):** Computes average hostile position, flees in opposite direction at 1.3 speed.
4. **Dangerous fall (4+ block drop with no ledge):** Avoids unless `expeditionState` is "returning" (return path overrides fall safety).

Flee uses `findSafePosition()` which searches ±5 blocks horizontally and ±1 vertically for solid ground with air above, no fire/lava.

## ReturnToBaseGoal

Active when `expeditionState == "returning"`.

**Destination priority:** `bedPos` if set, otherwise owner position.

**Arrival** (within 2 blocks of destination):
- If bed BE exists → `depositItems()` (iterates bag, tries each stack into `DogBedBlockEntity.tryInsert()`).
- If bed BE missing → `dropAllItems()` on ground, clears `bedPos`.
- Sets state to "idle", orders wolf to sit, syncs data.

**Pathfinding** uses a waypoint system for long distances: if >64 blocks from destination, picks an intermediate waypoint at ground level 64 blocks toward the destination and paths there first. Repaths every 40 ticks or when navigation completes.

## RetrieverGoal

Active: `collarTier > 0`, `wolfClass == "retriever"`, `expeditionState == "idle"`.

**Scan:** Every 20 ticks, scans for `ItemEntity` within `Config.retrieverScanRange` blocks of bed position. Filters by `filterItem` (if filter is empty, all items match; if filter is set, only `ItemStack.isSameItem` matches).

**Unreachable cache:** Failed pathfinding positions are cached for 30 seconds (600 ticks). `findNearestReachableItem` iterates candidates by distance, tests each with `navigation.createPath + canReach()`, caches failures.

**Pursuit:** Paths to target item at speed 1.0. Repaths every 20 ticks. Picks up at 1.5 blocks distance.

**Bag capacity:** If bag is ≥80% full (`usedSlots / size >= 0.8`), sets state to "returning".

**Idle deposit:** If bag has items but no collectible items are found for 10 seconds (200 ticks), triggers return to deposit.

## HunterGoal

Active: `collarTier > 0`, `wolfClass == "hunter"`, `expeditionState == "active"`.

### State machine (tick order):

1. **Drop collection:** After a kill, spends 10 ticks collecting drops in a 4-block radius around the kill position.
2. **Timer check:** If `expeditionStartTime > 0` and elapsed >= `expeditionDuration`, triggers return.
3. **Bag check:** If bag is 100% full, triggers return.
4. **Preemptive retreat:** If 3+ hostiles in 3-block melee range, retreat for 60 ticks.
5. **Health retreat:** If health < 50%, retreat for 100 ticks and eat food from bag.
6. **Re-engage:** During retreat, if health >= 75% and retreat timer expired, resume hunting.
7. **Combat:** Standard wolf melee attack at 2-block range, pursuit at speed 1.2.
8. **Scan:** Every 20 ticks, scans `Config.hunterScanRange`-block radius for `Monster` entities matching the filter.

### Retreat behavior

`fleeFromNearestHostile()`:
- First preference: flee toward bed direction if bed exists.
- Second preference: flee away from nearest hostile's position.
- Fallback: path to bed.

`eatFoodFromBag()`: Finds first stack with `DataComponents.FOOD`, consumes 1 item, heals 6 HP (at half player food heal rate). Has a double `stack.shrink(1)` bug — consumes 2 items per eat instead of 1.

### Mob filtering

`getMobFilter()` maps held filter items to entity type name patterns:

| Filter item | Targets |
|---|---|
| Bone | skeletons, strays, wither |
| Rotten flesh | zombies, drowned, husks |
| String | spiders |
| Gunpowder | creepers |
| Ender pearl | endermen |
| Blaze powder/rod | blazes |
| Ghast tear | ghasts |
| Arrow | skeletons |
| Emerald | vindicators, evokers, pillagers, ravagers |
| Empty | any Monster |

## MinerGoal

Active: `collarTier > 0`, `wolfClass == "miner"`, `expeditionState == "active"`.

### State machine (tick order):

1. Clean expired unreachable cache (>5 seconds old).
2. **Timer check:** Same as HunterGoal.
3. **Pickaxe check:** `hasValidPickaxe()` finds a pickaxe with >10% durability remaining. If none, trigger return.
4. **Bag check:** 100% full → return.
5. If currently mining (`miningPos != null`), tick mining progress.
6. **Scan:** Every 10 ticks, scan for ores.
7. If `targetOre` set, approach and mine.
8. If exploring, continue exploration movement.
9. Otherwise, idle — after 60 ticks (3 seconds) of no activity, start exploring.

### Ore scanning

Scans `oreScanRange` (default 64 blocks from `Config.detectionRange`) centered on wolf. Range is config-wide — shared with pathfinding boost and retriever range.

Filters by `filterItem` (same pattern as retriever: if empty, all ores; if set, specific ore type via `BlockTags.*_ORES`).

**Unreachable cache** with recheck: cached positions are re-tested if the wolf is now within mining distance or has moved to <25% of the cached distance. Cache timeout is 5 seconds (100 ticks) — shorter than retriever since ore positions are farther apart.

**Reachability check:** Tests direct path to ore, then falls back to `findAdjacentStandingPos()` (searches 26 neighbors for a standable spot with solid ground below, air at position). Only the first 16 sorted candidates are path-tested per scan to bound CPU.

### Mining

`startMining()`: Sets `miningPos`, `miningProgress = 0`. Calculates `mineTimeForCurrentOre` based on block hardness and tool speed at half player rate (×2 multiplier): `max(10, hardness * 3.0 / toolSpeed)`.

`tickMiningProgress()`: Increments progress each tick. At progress%5==0, spawns block break particles. Calls `level.destroyBlockProgress()` with progress ratio. On completion:
- Gets drops via `Block.getDrops()` with the pickaxe for enchantment support (fortune, silk touch).
- Calls `level.destroyBlock()` and `levelEvent(2001)`.
- Applies 1 durability damage to tool via `hurtAndBreak()` (Unbreaking-aware).
- Collects drops in 3-block radius.

**Drift protection:** If wolf moves >5 blocks from the mining position, resets progress and starts over.

### Exploration

When no target ores found for 60 ticks:
- Sets `visitedChunks` (tracked from `originPos =` dispatch point).
- `pickExploreTarget()`: picks a direction away from origin, tries 8 angles with slight randomization (±45°), picks unexplored chunks first. Uses `findGround()` to locate solid ground positions.
- `exploreStepRange()` = `detectionRange * 2` (default 128 blocks).
- Explores for `exploreStepRange * 2` ticks, then re-scans for ores.

### Safety

- Avoids drops of 4+ blocks unless on return path (handled by `SelfPreservationGoal`).
- Miner paths to ore or adjacent standing position at speed 1.0.
