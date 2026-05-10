# Dog Bed

## Block

`DogBedBlock` (`common/.../block/DogBedBlock.java`) extends `BaseEntityBlock` with `FACING` property (horizontal directional). CODEC = `simpleCodec(DogBedBlock::new)`.

Properties: strength 2.0/3.0, no occlusion, wood sound.

### Right-click behavior (`useWithoutItem`)

- **Shift-right-click:** Starts bed pairing via `WolfBedPairing.startBedPairing(sp, pos, dimension)`. Sends `message.workingwolves.bed_pairing_started`.
- **Normal right-click:** Opens a 3-row `ChestMenu` backed by the BE. GUI title shows `<wolf name>'s Dog Bed` if a wolf is assigned, otherwise `"Dog Bed"`.

### Block destruction

`destroy()` calls `be.dropContents(level, pos)` to scatter all stored items as world entities, then calls super.

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

## Return-to-base deposit

`ReturnToBaseGoal.handleArrival()`:
- If `bedPos` is set and the BE exists, calls `depositItems(dogBed, mixin)` which iterates the bag and calls `dogBed.tryInsert(stack)` for each slot.
- If the BE is missing (bed broken), drops all items on the ground and clears `bedPos`.
- Sets expedition state to `"idle"`, orders wolf to sit, syncs data.
