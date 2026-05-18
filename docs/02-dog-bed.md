# Dog Bed

## Block

`DogBedBlock` (`common/.../block/DogBedBlock.java`) extends `BaseEntityBlock` with `FACING` property (horizontal directional). CODEC = `simpleCodec(DogBedBlock::new)`.

Properties: strength 2.0/3.0, no occlusion, wood sound.

### Right-click behavior (`useWithoutItem`)

- **Shift-right-click:** Starts bed pairing via `WolfBedPairing.startBedPairing(sp, pos, dimension)`. Sends `message.workingwolves.bed_pairing_started`.
- **Normal right-click:** Opens a 3-row `ChestMenu` backed by the BE. GUI title shows `<wolf name>'s Dog Bed` if a wolf is assigned, otherwise `"Dog Bed"`.

### Block destruction

`destroy()` calls `be.dropContents(level, pos)` to scatter all stored items as world entities, then calls super.

The BE overrides `setRemoved()`: if a simulation was running, it rescues the wolf entity (restores visibility, AI, invulnerability, sets expedition state to `"idle"`, clears `bedPos`), drops any `simPendingLoot`, and logs "Bed destroyed. Expedition abandoned."

## Block Entity

`DogBedBlockEntity` (`common/.../blockentity/DogBedBlockEntity.java`) implements `Container` with 27 slots.

### Persistence (ValueInput/ValueOutput)

- `loadAdditional`: reads `"items"` as `ItemStack.OPTIONAL_CODEC.listOf()`, `"assigned_wolf"` as UUID string, `"assigned_wolf_name"` as string.
- `saveAdditional`: stores all three fields.

### Wolf assignment

- `assignWolf(uuid, name)` — stores UUID and name, marks dirty.
- `unassignWolf()` — clears both fields, marks dirty.
- `hasWolf()` / `getAssignedWolfUuid()` / `getAssignedWolfName()` — accessors.

### Inventory management

- `dropContents(level, pos)` — drops every non-empty stack as a world item, clears inventory.
- `tryInsert(stack)` — inserts into first matching slot (empty or same-item-same-components with room). Returns `ItemStack.EMPTY` if fully inserted, or the remainder.

## Bed Pairing

`WolfBedPairing` (`common/.../pairing/WolfBedPairing.java`) is a static utility for the two-step bed assignment flow:

1. Player shift-right-clicks a dog bed → `startBedPairing(player, bedPos, dimension)` stores a `PendingBed` in a `ConcurrentHashMap<UUID, PendingBed>`.
2. Player shift-right-clicks a collared wolf with empty hand (handled in `WolfMixin.mobInteract`) → `consumeBedPairing(player)` retrieves and removes the pending bed. If within the 30-second timeout (600 ticks), the wolf is assigned to that bed.

`PendingBed` is a record: `(BlockPos bedPos, ResourceKey<Level> dimension, long startTime)`.

`hasActiveBedPairing(player)` checks if a non-expired pairing exists (with timeout cleanup).

### Bed assignment in WolfMixin

When `consumeBedPairing` returns a valid bed:
1. Gets the `DogBedBlockEntity` at bedPos.
2. Calls `be.assignWolf(wolf.getUUID(), wolfName)`.
3. Sets `bedPos` on the wolf via mixin.
4. Calls `syncData()`.
5. Sends `message.workingwolves.bed_assigned`.

### Expedition simulation

The BE runs a simulated expedition when a hunter or miner wolf is dispatched. The wolf teleports inside the bed block (invisible, invulnerable, no AI) and the simulation advances via `serverTick`.

**State machine:** `simState`: `"inactive"` → `"running"` → `"complete"`.

**Starting the sim** (`beginExpeditionSimulation`):
- Snapshots wolf class, collar tier, filter item, armor, pickaxe/weapon stats, UUID, biome category.
- **Food:** Items with `DataComponents.FOOD` are moved from the bed inventory into the wolf's bag (merge-first, then empty slots). No items are deleted — leftovers stay in the bag and are deposited on return.
- **Pickaxes (miners):** Items matching `ItemTags.PICKAXES` are moved from bed into wolf's bag the same way.
- `simSatiation` starts at 0. `expeditionDuration` comes from the collar (default 15 min = 18,000 ticks). Fallback 12,000 if zero.
- Logs "Left the warmth of the bed."

**Per-tick** (`serverTick`):
- Increments `simElapsedTicks`. If ≥ `simTotalTicks`, completes normally.
- Every 60–100 ticks (randomized), rolls an event via `rollEvent()`.

**Events** (zone-based, where zone = progress fraction: 0–24% / 25–74% / 75–100%):

| Zone | Travel | Discovery | Hazard |
|---|---|---|---|
| 0 | 50% | 40% | 10% |
| 1 | 30% | 45% | 25% |
| 2 | 20% | 35% | 45% |

- **Travel:** Flavor-only log line.
- **Discovery (hunter):** Picks a mob from the zone pool (weighted by filter item if set), generates loot with Looting multiplier.
- **Discovery (miner):** Picks an ore from the zone pool (weighted by filter item if set, 70% chance when set), generates drops with Fortune/Silk Touch multipliers. Biome yield bonus (×1.25 for mountain/cave).
- **Hazard:** Flavor line + `applyHazardCost()` (shared by both classes) + miner pickaxe durability.

**Satiation and food** (`applyHazardCost`, `eatFoodFromWolfBag`):
- Each hazard drains 2 satiation points.
- When satiation ≤ 0, the wolf eats one food item from its bag, gaining its nutrition value (e.g., steak = 8, rotten flesh = 4). Higher-quality food protects against more hazards.
- If no food remains, injury count increments. 3 injuries → expedition fails (early return or rare death).

**Pickaxe durability (miners only)** (`applyMinerDurability`):

| Event | Durability cost |
|---|---|
| Travel | 1 |
| Discovery | 3 |
| Hazard | 1 |

- Finds the best pickaxe in the wolf's bag (highest destroy speed).
- **Unbreaking** reduces damage: each point has `1/(level+1)` chance of being negated.
- **Enchanted pickaxes** (any enchantment) are never destroyed — damage caps at 1 durability remaining. At 1 durability, no further damage is applied; if a spare exists, the wolf switches to it; otherwise the expedition ends early.
- **Unenchanted pickaxes** break normally when durability reaches 0. Auto-switches to the next best pickaxe if available.
- **Early end:** Pickaxe < 5 durability with no spare, or last pickaxe breaks/gone with no replacement.

**Completion** (`completeSimulation`):
- Guarded: returns early if `simState` is no longer `"running"` (prevents double-completion from hazard + durability both firing in one event).
- **Death:** Loot dropped at bed, wolf killed via `genericKill()`.
- **Failed** (injuries): Pending loot cleared, wolf returns empty.
- **Success:** Pending loot deposited into bed inventory (overflow dropped as world items).
- All paths call `triggerWolfArrival()`: teleports wolf 10–14 blocks from bed, restores visibility/AI/invulnerability, sets state to `"returning"`.
- `recallExpedition()`: Public method called by recall whistle on bed. Same as success completion but logs "Called back early."

**Journal:** Every event line is appended to `expeditionLog` (stored in BE NBT as newline-joined string) and pushed to tracking clients via `WorkingWolvesPackets.pushJournalLine()`.

**Persistence of sim state:**

| Key | Type |
|---|---|
| `sim_state` | String |
| `sim_wolf_class` | String |
| `sim_collar_tier` | int |
| `sim_pickaxe_speed_x100` | int |
| `sim_fortune` | int |
| `sim_silk_touch` | int (0/1) |
| `sim_looting` | int |
| `sim_total_ticks` | int |
| `sim_elapsed_ticks` | int |
| `sim_event_timer` | int |
| `sim_injury_count` | int |
| `sim_satiation` | int |
| `sim_armor_points` | int |
| `sim_wolf_uuid` | String (UUID) |
| `sim_biome_category` | String |
| `sim_filter_item` | `ItemStack.OPTIONAL_CODEC` |
| `expedition_log` | String (newline-joined) |

## Dog Bed GUI — Wolf Preview Panel

`DogBedScreen` renders a fake wolf entity and a procedurally scrolling block floor inside a PiP (picture-in-picture) texture via `WolfPreviewFloorRenderer`.

### Coordinate system

The PiP scene shares a single `PoseStack`. Entity transforms are applied first (translate Y by `boundingBoxHeight/2`, then `rotateZ(π) * rotateX(previewPitch)`), after which both the wolf and the floor group operate in the same transformed space. The wolf is submitted at `(0, 0, 0)` in that space.

The floor group is then positioned by:
1. `translate(previewFloorOffsetX, previewFloorY, previewFloorOffsetZ)` — currently `(0, -1, 1)`
2. `scale(previewFloorScale)` — currently `1.0`
3. `rotateY(previewFloorRotY)` — currently `45°`

A grid cell at `(col, row)` is placed at floor-local coordinates:
```
bx = (col − 4.5) × spacing
bz = (row − 3.0) × spacing
```

After the `rotateY(45°)`, its X position in entity-transform space is:
```
entity_x = (bx − bz) / √2  =  [(col − 4.5) − (row − 3.0)] / √2
                             =  (col − row − 1.5) / √2
```

Because the wolf sits at `entity_x = 0`, its visual **centerline in the grid satisfies `col = row + 1.5`**. This diagonal runs from the front-left corner of the grid (row 0, col ~1.5) to the back-right corner (row 5, col ~6.5).

### Consequence for side objects

Side objects (furnaces, crafting tables, etc.) must not spawn on the wolf's path. The clear zone is therefore a diagonal band, not a fixed column range:

```java
// clear if: Math.abs(col - row - 1.5f) < SIDE_CLEAR_HALF_WIDTH  (currently 2.0)
```

Per row this clears:

| Row | Cleared cols | Eligible (side objects) |
|-----|-------------|------------------------|
| 0 | 0–3 | 4–8 (right flank) |
| 1 | 1–4 | 0, 5–8 |
| 2 | 2–5 | 0–1, 6–8 |
| 3 | 3–6 | 0–2, 7–8 |
| 4 | 4–7 | 0–3, 8 |
| 5 | 5–8 | 0–4 (left flank) |

### Mining zone progression

`randomMineFloorBlock()` picks a block distribution based on `miningZone()`, which maps expedition progress (not collar tier) to zones 1–3:

| Zone | Progress | Floor feel |
|------|----------|------------|
| 1 | 0–33 % | Mostly stone, ~5 % coal ore |
| 2 | 33–67 % | Stone/deepslate mix, ~5 % deepslate iron |
| 3 | 67–100 % | Mostly deepslate, ~2 % each of 5 rare ores |

```java
float progress = (float) DogBedScreenData.simElapsedTicks / DogBedScreenData.simTotalTicks;
```

Zone changes cause `floorTheme()` to return a different string (`"mine_1/2/3"`), which `ensureFloorGrid()` detects and resets the grid.

## Return-to-base deposit

`ReturnToBaseGoal.handleArrival()`:
- If `bedPos` is set and the BE exists, calls `depositItems(dogBed, mixin)` which iterates the bag and calls `dogBed.tryInsert(stack)` for each slot.
- If the BE is missing (bed broken), drops all items on the ground and clears `bedPos`.
- Sets expedition state to `"idle"`, orders wolf to sit, syncs data.
