# Whistles

## Dispatch Whistle

`DispatchWhistleItem` (`common/.../item/DispatchWhistleItem.java`) — `stacksTo(1)`.

Recipe: iron ingot + note block (defined in datagen).

### Interaction (`interactLivingEntity`)

Right-click a wolf. Guards: tamed, owned, has collar (`collarTier > 0`).

Toggles expedition state:
- If currently `"on_expedition"` or `"departing"` → cancel expedition: sets to `"idle"`, restores visibility/AI/invulnerability. Sends `message.workingwolves.wolf_recalled`.
- Otherwise → dispatches the wolf.

**Dispatch flow:**
1. Guards: wolf class must be `"hunter"` or `"miner"`. Must have a bed assigned.
2. **Food check:** Requires ≥ 1 food item in wolf's bag or dog bed inventory (checks `DataComponents.FOOD`). Fails with `message.workingwolves.no_food_for_expedition`.
3. **Pickaxe check (miners only):** Requires ≥ 1 pickaxe (`ItemTags.PICKAXES`) in wolf's bag or bed inventory. Fails with `message.workingwolves.no_pickaxe`.
4. Picks a random departure point 10–14 blocks away on solid ground.
5. Sets state to `"departing"`, departure timer to 80 ticks, departure target, records `expeditionStartTime`.
6. Wolf navigates to departure point. When timer expires, `WolfSelfPreservationMixin.triggerVanish()` fires:
   - Wolf becomes invisible, NoAI, invulnerable.
   - Teleports inside the bed block (Y + 0.2 to avoid player collision).
   - Calls `DogBedBlockEntity.beginExpeditionSimulation()`.
   - Sets state to `"on_expedition"`.

Calls `syncData()` after each state change.

## Recall Whistle

`RecallWhistleItem` (`common/.../item/RecallWhistleItem.java`) — `stacksTo(1)`.

Recipe: dispatch whistle + echo shard (in datagen).

### Use on air (right-click)

Cooldown: 100 ticks (2 minutes) via vanilla `player.getCooldowns()`.

**Effect:** Scans all server dimensions for tamed wolves owned by the player. For each found wolf:
- Sets `expeditionState` to `"returning"`.
- Calls `syncData()`.
- Spawns 5 `NOTE` particles at wolf position.

Also spawns 10 `NOTE` particles at player position.

Sends `message.workingwolves.all_wolves_recalled`.

Works cross-dimension — iterates `server.getServer().getAllLevels()`.

### Use on dog bed (right-click block)

Cooldown: same 2-minute cooldown.

Targets the wolf assigned to the clicked bed. Guards:
- Block must be a `DogBedBlockEntity` with `simState == "running"`.
- Assigned wolf must exist and be owned by the player.
- Whistle must not be on cooldown.

Calls `DogBedBlockEntity.recallExpedition()`: stops the simulation, deposits pending loot, logs "Called back early.", restores the wolf and sets it to `"returning"` (teleports 10–14 blocks away, walks home). Sends `message.workingwolves.wolf_recalled`.
