# Config and Commands

## Config

`Config` class (`common/.../Config.java`) — simple static fields, no config file yet:

| Field | Default | Used by |
|---|---|---|
| `maxWolvesPerPlayer` | 5 | `WolfChunkManager` tick + `countWorkingWolves`, `CollarItem` cap check |
| `detectionRange` | 64 | `MinerGoal` ore scan + explore step range |
| `hunterScanRange` | 32 | `HunterGoal` mob detection scan AABB |
| `expeditionDurationMinutes` | 15 | `ModItems` collar constructor (multiplied to ticks) |

Pathfinding boost (`WolfMixin`) uses `max(detectionRange, hunterScanRange)` to set `maxVisitedNodes` and `requiredPathLength`, ensuring the navigation budget covers whichever goal is active.

Platform config classes (`FabricConfig`, `NeoForgeConfig`) exist but currently just reference the static fields.

## Debug Command

`DebugCommand` (`common/.../command/DebugCommand.java`) — registered under `/workingwolves debug`.

Requires player to look at a dog bed block (10-block pick range). Outputs:
- Wolf position (x y z)
- Dimension
- Expedition state
- Wolf class
- Collar tier
- Bed position (or "null")
- Whether bed chunk is loaded

Uses `findWolf(level, uuid)` which does an AABB scan across the full world bounds (could be expensive on servers with many entities — debug-only).

## Mod Registration Entry Points

`ModItems.registerItems()` — 3 collars + 2 whistles via `RegistrationApiSided`.

`ModBlocks.registerBlocks()` — `DogBedBlock` (1 block).

`ModBlockEntityTypes.registerBlockEntityTypes()` — `DogBedBlockEntity` (1 BE type).

`ModDataComponents.registerDataComponents()` — 7 data component types.

`ModCreativeTabs.registerCreativeTabs()` — 1 tab with all 6 items.

`ModSoundEvents` — registered but currently empty (recall howl sound not yet implemented as custom sound — uses NOTE particles instead).

## Platform Bootstrap

### NeoForge

`WorkingWolvesNeoForge` constructor:
- Registers all mod content via the `*ModInitializer` pattern.
- Registers packet handler on `RegisterPayloadHandlersEvent` via `NeoForgePacketRegistrar`.
- Registers server tick → `WolfChunkManager.getInstance().tick(server)`.
- Registers `/workingwolves debug` command via `RegisterCommandsEvent`.

### Fabric

`WorkingWolvesFabric.onInitialize()`:
- Registers all mod content.
- Registers packets via `FabricPacketRegistrar`.
- Registers server tick and command on `ServerLifecycleEvents.SERVER_STARTED`.
