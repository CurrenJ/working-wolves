# Chunk Loading

`WolfChunkManager` (`common/.../chunk/WolfChunkManager.java`) is a singleton ticked by platform-specific server tick events.

## Core mechanism

Uses vanilla `ServerLevel.setChunkForced(int x, int z, boolean add)` which works identically on both NeoForge and Fabric — no platform APIs needed.

Uses a diff-based approach to minimize `setChunkForced` calls.

## Tick cycle (`tick(server)`)

**1. Build new forced set**

Iterates all `ServerLevel` dimensions, scanning for wolves via `level.getEntities(EntityType.WOLF, ...)`:
- Skips untamed, uncollared (`collarTier <= 0`), and ownerless wolves.
- Enforces `Config.maxWolvesPerPlayer` per-owner cap (counted via `playerWolfCount` map).
- Radius based on expedition state:
  - `"active"` → radius 2 (5×5 chunks, 25 chunks)
  - `"idle"` or `"returning"` → radius 1 (3×3 chunks, 9 chunks)

Overlapping zones from nearby wolves merge naturally via `Set<ChunkPos>` deduplication.

**2. Unforce stale chunks**

For each previously-forced dimension, iterate old set:
- If dimension is absent from new set → unforce all chunks.
- If dimension is present → unforce only chunks not in new set.

**3. Force new chunks**

For each new dimension entry, force only chunks not already in the old set for that dimension.

**4. Update tracking**

Replace `previouslyForced` with new set.

**5. Debug logging**

Every 200 ticks (10 seconds), logs total wolves, chunks, and dimensions at DEBUG level.

## Per-player wolf counting

`countWorkingWolves(player)` — static method used by `CollarItem` for wolf cap enforcement. Scans all server dimensions for tamed wolves owned by the player with `collarTier > 0`. Returns the count.

## Platform integration

Each platform mod class (e.g., `WorkingWolvesNeoForge`, `WorkingWolvesFabric`) calls `WolfChunkManager.getInstance().tick(server)` on the server tick event:
- NeoForge: `ServerTickEvent` (POST phase) 
- Fabric: `ServerTickEvents.END_SERVER_TICK`
