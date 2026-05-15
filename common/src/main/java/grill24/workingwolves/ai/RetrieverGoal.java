package grill24.workingwolves.ai;

import grill24.workingwolves.Config;
import grill24.workingwolves.api.IWorkingWolf;
import grill24.workingwolves.inventory.WolfBagHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * Priority 2 goal. Active when: collarTier > 0, class is "retriever", expeditionState is "idle".
 * Scans for dropped items within the configured range of the bed (or the owner in companion mode),
 * picks them up into the bag.
 * When bag is nearly full, sets expeditionState to "returning".
 * Caches unreachable items for 30 seconds.
 */
public class RetrieverGoal extends Goal {
    private final Wolf wolf;
    private static final double SPEED = 1.0;
    // Scan range is controlled by Config.retrieverScanRange
    private static final int CACHE_TIMEOUT_TICKS = 600; // 30 seconds
    private static final float BAG_FULL_THRESHOLD = 0.8f;
    private static final double PICKUP_DISTANCE_SQ = 1.5 * 1.5;
    private static final int REPATH_INTERVAL = 20;
    private static final int SCAN_COOLDOWN = 20;
    private static final int IDLE_DEPOSIT_TICKS = 200; // Deposit if idle for 10 seconds with items in bag

    private final Map<BlockPos, Long> unreachableCache = new HashMap<>();
    private ItemEntity targetItem = null;
    private int repathTicks = 0;
    private int scanCooldown = 0;
    private int idleTicks = 0;

    public RetrieverGoal(Wolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        return mixin.workingwolves$getCollarTier() > 0
            && !WolfBagHelper.hasAnyExpeditionTool(mixin)
            && "idle".equals(mixin.workingwolves$getExpeditionState())
            && !wolf.isOrderedToSit();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        ((IWorkingWolf) (Object) wolf).workingwolves$applyNavBudget(Config.retrieverScanRange);
    }

    @Override
    public void stop() {
        targetItem = null;
        repathTicks = 0;
    }

    @Override
    public void tick() {
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        Level level = wolf.level();
        long gameTime = level.getGameTime();

        // Clean expired cache entries
        unreachableCache.values().removeIf(cachedTime -> gameTime - cachedTime > CACHE_TIMEOUT_TICKS);

        // Check bag capacity -> trigger return
        if (WolfBagHelper.isBagFull(mixin, BAG_FULL_THRESHOLD)) {
            mixin.workingwolves$triggerReturn();
            return;
        }

        // Determine scan center: bed if assigned, otherwise the owner (companion mode)
        BlockPos bedPos = mixin.workingwolves$getBedPos();
        BlockPos scanCenter;

        if (bedPos != null) {
            scanCenter = bedPos;
        } else {
            // Companion mode: scan around the owner instead of a bed
            LivingEntity owner = wolf.getOwner();
            if (owner == null) {
                return; // No bed and no owner, nothing to do
            }
            // Don't scan if too far from owner — the wolf should stay close and follow
            if (wolf.distanceToSqr(owner) > 32.0 * 32.0) {
                return;
            }
            scanCenter = owner.blockPosition();
        }

        // Handle target item pursuit
        if (targetItem != null) {
            tickTargetPursuit(mixin, level, gameTime);
            return;
        }

        // Cooldown between scans
        scanCooldown--;
        if (scanCooldown > 0) {
            return;
        }
        scanCooldown = SCAN_COOLDOWN;

        // Scan for items near the scan center (bed or owner)
        List<ItemEntity> validItems = level.getEntitiesOfClass(ItemEntity.class,
            new AABB(scanCenter).inflate(Config.retrieverScanRange),
            item -> item.isAlive() && !unreachableCache.containsKey(item.blockPosition()));

        if (validItems.isEmpty()) {
            // Nothing to pick up — if we have items in bag, tick the idle deposit timer
            if (hasItemsInBag(mixin)) {
                idleTicks++;
                if (idleTicks >= IDLE_DEPOSIT_TICKS) {
                    mixin.workingwolves$triggerReturn();
                }
            }
            return;
        }

        // Find nearest reachable item
        targetItem = findNearestReachableItem(level, validItems, mixin, gameTime);
        if (targetItem == null) {
            // All visible items are unreachable — start idle deposit countdown
            if (hasItemsInBag(mixin)) {
                idleTicks++;
                if (idleTicks >= IDLE_DEPOSIT_TICKS) {
                    mixin.workingwolves$triggerReturn();
                }
            }
        }
    }

    private void tickTargetPursuit(IWorkingWolf mixin, Level level, long gameTime) {
        if (!targetItem.isAlive()) {
            targetItem = null;
            return;
        }

        double distSq = wolf.distanceToSqr(targetItem);

        // Close enough to pick up
        if (distSq <= PICKUP_DISTANCE_SQ) {
            pickupItem(targetItem, mixin);
            targetItem = null;
            idleTicks = 0; // Reset idle timer on successful pickup
            return;
        }

        // Re-path periodically
        repathTicks--;
        if (repathTicks <= 0) {
            Path path = wolf.getNavigation().createPath(targetItem, 1);
            if (path != null && path.canReach()) {
                wolf.getNavigation().moveTo(path, SPEED);
                repathTicks = REPATH_INTERVAL;
            } else {
                // Unreachable: cache failure and abort
                unreachableCache.put(targetItem.blockPosition(), gameTime);
                targetItem = null;
            }
        }
    }

    private ItemEntity findNearestReachableItem(Level level, List<ItemEntity> items, IWorkingWolf mixin, long gameTime) {
        ItemEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (ItemEntity item : items) {
            BlockPos itemPos = item.blockPosition();
            double dist = wolf.distanceToSqr(item);

            if (dist >= nearestDist) {
                continue;
            }

            // Check reachability via pathfinding
            Path path = wolf.getNavigation().createPath(item, 1);
            if (path != null && path.canReach()) {
                nearestDist = dist;
                nearest = item;
            } else {
                unreachableCache.put(itemPos, gameTime);
            }
        }
        return nearest;
    }

    private boolean hasItemsInBag(IWorkingWolf mixin) {
        for (ItemStack stack : mixin.workingwolves$getBagInventory()) {
            if (!stack.isEmpty()) return true;
        }
        return false;
    }

    private void pickupItem(ItemEntity item, IWorkingWolf mixin) {
        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();
        ItemStack remainder = WolfBagHelper.addToBag(bag, item.getItem().copy());
        if (remainder.isEmpty()) {
            item.discard();
        } else {
            item.setItem(remainder);
        }
    }
}
