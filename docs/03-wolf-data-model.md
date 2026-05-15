# Wolf Data Model

## IWorkingWolf Interface

`common/.../api/IWorkingWolf.java` — the contract between AI goals, items, and the mixin. Every field uses the `workingwolves$` prefix convention (Mixin AP requirement for `@Unique`).

Fields exposed:
- `collarTier` (int, 0 = no collar)
- `bedPos` (nullable BlockPos)
- `expeditionState` (String: "idle", "departing", "on_expedition", "returning")
- `expeditionStartTime` (long, game ticks)
- `expeditionDuration` (int, ticks)
- `bagInventory` (NonNullList\<ItemStack\>, sized dynamically)
- `unlockedSlots` (int, bonus bag slots from netherite ingots)
- `miningPos` / `miningProgress` (transient mining/chopping state, not persisted)

Note: `wolfClass` and `filterItem` were removed. Role is now auto-detected from bag contents at dispatch and at goal activation. Use `WolfBagHelper.has*Tool/Weapon` helpers for role checks.

Helper methods:
- `resizeBag()` — rebuilds bag NonNullList to new base size + unlocked slots
- `unlockSlot()` — increments unlockedSlots, then resizes
- `syncData()` — sends `WolfDataSyncPacket` to tracking clients
- `setCollarColorFromTier(color)` — sets vanilla wolf collar color

## WolfMixin

`common/.../mixin/WolfMixin.java` — `@Mixin(Wolf.class)`, extends `TamableAnimal`, implements `IWorkingWolf`.

### Persistence injection

`@Inject` at `@At("TAIL")` for both `addAdditionalSaveData` and `readAdditionalSaveData`:

| Key | Type |
|---|---|
| `ww_collar_tier` | int |
| `ww_bed_pos` | BlockPos (nullable) |
| `ww_expedition_state` | String |
| `ww_expedition_start_time` | long |
| `ww_expedition_duration` | int |
| `ww_bag` | `ItemStack.OPTIONAL_CODEC.listOf()` |
| `ww_unlocked_slots` | int |
| `ww_departure_timer` | int |
| `ww_dep_x` / `ww_dep_y` / `ww_dep_z` | int (only stored when non-null) |

On load, the wolf's collar color is restored based on `collarTier` (BROWN/GRAY/YELLOW). If `expeditionState == "on_expedition"`, the wolf's hidden state is defensively re-applied (`setInvisible(true)`, `setNoAi(true)`, `setInvulnerable(true)`) to prevent visibility leaks after chunk unload/reload.

### mobInteract interception

`@Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)`:

1. If held item is `CollarItem` → delegates to `collar.interactLivingEntity(...)`.
2. If held item is `DispatchWhistleItem` or `RecallWhistleItem` → delegates to the item's `interactLivingEntity`.
3. Shift-right-click with empty hand on owned collared wolf:
   - If player has active bed pairing → assign wolf to bed.
   - Otherwise → open bag GUI via `WolfBagContainer`.

### Pathfinding budget

`IWorkingWolf.workingwolves$applyNavBudget(int range)` is a default method that sets `pathFinder.maxVisitedNodes = range² / 4` and `navigation.requiredPathLength = range`. Each goal calls it in `start()` with its own config range.

### AI goal registration

`@Inject(method = "registerGoals", at = @At("TAIL"))` adds 5 goals:

| Priority | Goal | Purpose |
|---|---|---|
| 0 | `AntiStuckGoal` | Teleport if stuck 30s |
| 0 | `SelfPreservationGoal` | Lava/fire/fall/crowding avoidance |
| 1 | `ReturnToBaseGoal` | Navigate back to bed |
| 2 | `HunterGoal` | Kill hostiles near owner (bag has hunting weapon) |
| 2 | `MinerGoal` | Mine ores near owner (bag has pickaxe) |
| 2 | `WoodcutterGoal` | Chop logs near owner (bag has axe) |

## Role Detection

Role is determined from bag contents — there is no explicit `wolfClass` field.

| Tool in bag | Role activated |
|-------------|---------------|
| Pickaxe | Mining (expedition + `MinerGoal`) |
| Sword / Bow / Crossbow / Mace | Hunting (expedition + `HunterGoal`) |
| Axe | Woodcutting (expedition + `WoodcutterGoal`) |

Axes count as hunting weapons for `isMeleeWeapon()` (used to equip in combat), but for **role detection** only swords/bows/mace trigger hunting. An axe-only wolf is a woodcutter, not a hunter.

Helpers in `WolfBagHelper`:
- `hasHuntingWeapon(mixin)` — swords, bows, crossbows, mace
- `hasMiningTool(mixin)` — pickaxes
- `hasWoodcuttingTool(mixin)` — axes
- `hasAnyExpeditionTool(mixin)` — any of the above

## Network Sync

`WolfDataSyncPacket` (`common/.../network/WolfDataSyncPacket.java`) — a `CustomPacketPayload` record with type `workingwolves:wolf_data_sync`.

Fields: `wolfId`, `collarTier`, `bedPos`, `expeditionState`, `expeditionStartTime`, `expeditionDuration`, `mouthItem`.

`WorkingWolvesPackets.syncWolfData(wolf)` calls `sendToTracking.accept(wolf, WolfDataSyncPacket.fromWolf(wolf))`.

## Data Components

`ModDataComponents` registers 5 `DataComponentType<?>` holders:

- `collar_tier` — Integer
- `bed_position` — BlockPos
- `expedition_state` — String
- `expedition_start_time` — Long
- `expedition_duration` — Integer

All use `.persistent(Codec).networkSynchronized(StreamCodec)` builder pattern.
