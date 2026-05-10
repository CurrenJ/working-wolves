# Whistles

## Dispatch Whistle

`DispatchWhistleItem` (`common/.../item/DispatchWhistleItem.java`) — `stacksTo(1)`.

Recipe: iron ingot + note block (defined in datagen).

### Interaction (`interactLivingEntity`)

Right-click a wolf. Guards: tamed, owned, has collar (`collarTier > 0`).

Toggles expedition state:
- If currently `"active"` → set to `"idle"` (recall individual wolf). Sends `message.workingwolves.wolf_recalled`.
- Otherwise → set to `"active"`, record `expeditionStartTime` to current game time, un-sit the wolf. Sends `message.workingwolves.wolf_dispatched`.

Calls `syncData()` after each state change.

## Recall Whistle

`RecallWhistleItem` (`common/.../item/RecallWhistleItem.java`) — `stacksTo(1)`.

Recipe: dispatch whistle + echo shard (in datagen).

### Use (right-click air)

Cooldown: 100 ticks (2 minutes) via vanilla `player.getCooldowns()`.

**Effect:** Scans all server dimensions for tamed wolves owned by the player. For each found wolf:
- Sets `expeditionState` to `"returning"`.
- Calls `syncData()`.
- Spawns 5 `NOTE` particles at wolf position.

Also spawns 10 `NOTE` particles at player position.

Sends `message.workingwolves.all_wolves_recalled`.

Works cross-dimension — iterates `server.getServer().getAllLevels()`.
