# Collars and Wolf Bag

## CollarItem

`common/.../item/CollarItem.java` — three instances registered in `ModItems`:

| Tier | Registry name | Slots | Expedition (ticks) |
|---|---|---|---|
| 1 | `leather_collar` | 5 | `expeditionDurationMinutes * 60 * 20` |
| 2 | `iron_studded_collar` | 9 | same |
| 3 | `gold_trimmed_collar` | 15 | same |

Each collar is `stacksTo(1)`. Expedition duration comes from `Config.expeditionDurationMinutes` (default 15 min); if zero, `Integer.MAX_VALUE` = unlimited.

### Applying a collar

`CollarItem.interactLivingEntity(player, wolf, hand)`:

1. Guards: must be tamed, must be owned by player (sends `message.workingwolves.not_owner` if not).
2. Enforces `Config.maxWolvesPerPlayer` wolf cap via `WolfChunkManager.countWorkingWolves(player)` — only when applying to a wolf with no existing collar (`oldTier == 0`).
3. If wolf already has a collar (`oldTier > 0`), returns the old collar item to the player (drops on ground if inventory full).
4. Sets `collarTier`, `expeditionDuration`. Preserves existing `wolfClass`; if null, picks default for tier (`getDefaultClassForTier`).
5. Calls `resizeBag()` if tier changed — creates a new `NonNullList` of the new size, copies over as many old items as fit.
6. Sets `collarColor` on the vanilla wolf: tier 1 → BROWN, tier 2 → GRAY, tier 3 → YELLOW.
7. Calls `syncData()` to push to tracking clients.

### Getting the old collar back

`CollarItem.createCollarForTier(tier)` returns the `ItemStack` for a given tier by looking up the `Holder<Item>` from `ModItems`. Used both for returning the old collar on upgrade and for drop-on-death logic.

### Interaction routing

`WolfMixin` intercepts `mobInteract` at `@At("HEAD")` with `cancellable = true`. If the held item is a `CollarItem`, it delegates to `collar.interactLivingEntity(...)` and cancels further processing.

## Wolf Bag

### Container implementation

`WolfBagContainer` (`common/.../inventory/WolfBagContainer.java`) implements `Container` with a display size of 27 slots (3 rows). Only the first N slots are usable, where N is `getBaseBagSize() + unlockedSlots`.

Locked slots (index >= usable, < 27) show a barrier item with name `container.workingwolves.locked_slot`.

**Slot unlocking:** Placing a netherite ingot in a locked slot consumes the ingot and calls `workingwolves$unlockSlot()`, which increments `unlockedSlots` and resizes the bag.

### Opening the bag

Shift-right-click with an empty hand on a collared wolf that is tame and owned:
- If the player has an active bed pairing (set via shift-right-click on a dog bed), the wolf is assigned to that bed instead.
- Otherwise, the bag GUI opens via `player.openMenu(new SimpleMenuProvider(...))`.

### Bag resize logic

`WolfMixin.getBagSize()` = `getBaseBagSize() + unlockedSlots`. `getBaseBagSize()` switches on `collarTier`: 0 → 0, 1 → 5, 2 → 9, 3 → 15.

`resizeBag()` creates a new `NonNullList` of the new size and copies over items from the old bag. `unlockSlot()` does the same but also increments `unlockedSlots` first.
