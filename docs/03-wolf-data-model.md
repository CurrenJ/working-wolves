# Wolf Data Model

## IWorkingWolf Interface

`common/.../api/IWorkingWolf.java` — the contract between AI goals, items, and the mixin. Every field uses the `workingwolves$` prefix convention (Mixin AP requirement for `@Unique`).

Fields exposed:
- `collarTier` (int, 0 = no collar)
- `wolfClass` (nullable String: "retriever", "hunter", "miner")
- `bedPos` (nullable BlockPos)
- `expeditionState` (String: "idle", "active", "returning")
- `expeditionStartTime` (long, game ticks)
- `expeditionDuration` (int, ticks)
- `bagInventory` (NonNullList\<ItemStack\>, sized dynamically)
- `filterItem` (ItemStack, held in mouth for filtering)
- `unlockedSlots` (int, bonus bag slots from netherite ingots)
- `miningPos` / `miningProgress` (transient mining state, not persisted)

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
| `ww_wolf_class` | String (nullable) |
| `ww_bed_pos` | BlockPos (nullable) |
| `ww_expedition_state` | String |
| `ww_expedition_start_time` | long |
| `ww_expedition_duration` | int |
| `ww_bag` | `ItemStack.OPTIONAL_CODEC.listOf()` |
| `ww_filter_item` | `ItemStack.OPTIONAL_CODEC` |
| `ww_unlocked_slots` | int |

On load, the wolf's collar color is restored based on `collarTier` (BROWN/GRAY/YELLOW).

### mobInteract interception

`@Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)`:

1. If held item is `CollarItem` → delegates to `collar.interactLivingEntity(...)`.
2. If held item is `DispatchWhistleItem` or `RecallWhistleItem` → delegates to the item's `interactLivingEntity`.
3. Shift-right-click with empty hand on owned collared wolf:
   - If player has active bed pairing → assign wolf to bed.
   - Otherwise → open bag GUI via `WolfBagContainer`.

### Pathfinding budget

`IWorkingWolf.workingwolves$applyNavBudget(int range)` is a default method that sets `pathFinder.maxVisitedNodes = range² / 4` and `navigation.requiredPathLength = range`. Each goal calls it in `start()` with its own config range:

- `HunterGoal`: `Config.hunterScanRange` (32)
- `RetrieverGoal`: `Config.retrieverScanRange` (64)
- `MinerGoal`: `Config.detectionRange` (64)
- `ReturnToBaseGoal`: `64` (matches waypoint segment distance)

Each goal pays only for the range it actually needs — no shared max overhead.

### AI goal registration

`@Inject(method = "registerGoals", at = @At("TAIL"))` adds 6 goals:

| Priority | Goal | Purpose |
|---|---|---|
| 0 | `AntiStuckGoal` | Teleport if stuck 30s |
| 0 | `SelfPreservationGoal` | Lava/fire/fall/crowding avoidance |
| 1 | `ReturnToBaseGoal` | Navigate back to bed |
| 2 | `RetrieverGoal` | Collect dropped items near bed |
| 2 | `HunterGoal` | Kill hostiles on expedition |
| 2 | `MinerGoal` | Mine ores on expedition |

## Network Sync

`WolfDataSyncPacket` (`common/.../network/WolfDataSyncPacket.java`) — a `CustomPacketPayload` record with type `workingwolves:wolf_data_sync`.

Fields: `wolfId`, `collarTier`, `wolfClass`, `bedPos`, `expeditionState`, `expeditionStartTime`, `expeditionDuration`, `filterItem`.

`StreamCodec` uses composite: `VAR_INT`, `VAR_INT`, `STRING_UTF8`, `BlockPos.STREAM_CODEC`, `STRING_UTF8`, `LONG`, `VAR_INT`, `ItemStack.OPTIONAL_STREAM_CODEC`.

`WorkingWolvesPackets.syncWolfData(wolf)` calls `sendToTracking.accept(wolf, WolfDataSyncPacket.fromWolf(wolf))`. The `sendToTracking` consumer is set by platform init (NeoForge `PacketDistributor.sendToPlayersTrackingEntity`, Fabric `PayloadTypeRegistry.playS2C` + `PlayerLookup.tracking`).

## Data Components

`ModDataComponents` registers 7 `DataComponentType<?>` holders (used on items, not directly used in current code — the mixin stores data directly on the wolf entity):

- `wolf_class` — String
- `collar_tier` — Integer
- `bed_position` — BlockPos
- `expedition_state` — String
- `expedition_start_time` — Long
- `expedition_duration` — Integer
- `filter_item` — ItemStack

All use `.persistent(Codec).networkSynchronized(StreamCodec)` builder pattern.
