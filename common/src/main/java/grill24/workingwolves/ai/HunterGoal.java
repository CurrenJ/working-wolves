package grill24.workingwolves.ai;

import grill24.workingwolves.api.IWorkingWolf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Priority 2 goal. Active when: collarTier > 0, class is "hunter", expeditionState is "active".
 * Dispatched on expedition to kill hostile mobs and collect drops.
 * Manages health-based retreat/engage, bag capacity, and expedition timer.
 */
public class HunterGoal extends Goal {
    private final Wolf wolf;
    private static final double SPEED = 1.2;
    private static final double RETREAT_SPEED = 1.4;
    private static final int SCAN_RANGE = 32;
    private static final int MELEE_RANGE = 2;
    private static final float RETREAT_HEALTH_RATIO = 0.5f;
    private static final float REENGAGE_HEALTH_RATIO = 0.75f;
    private static final int RETREAT_DURATION = 100; // 5 seconds
    private static final int PREEMPTIVE_RETREAT_DURATION = 60; // 3 seconds
    private static final int PREEMPTIVE_HOSTILE_RANGE = 3;
    private static final int PREEMPTIVE_HOSTILE_COUNT = 3;
    private static final int REPATH_INTERVAL = 20;
    private static final int SCAN_COOLDOWN = 20;
    private static final int DROP_COLLECT_RANGE = 4;
    private static final float FOOD_HEAL_AMOUNT = 6.0f;
    private static final int FLEE_DISTANCE = 10;

    private LivingEntity target = null;
    private int repathTicks = 0;
    private int scanCooldown = 0;
    private boolean isRetreating = false;
    private int retreatTicks = 0;
    private int collectTicks = 0;
    private BlockPos lastKillPos = null;

    public HunterGoal(Wolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        return mixin.workingwolves$getCollarTier() > 0
            && "hunter".equals(mixin.workingwolves$getWolfClass())
            && "active".equals(mixin.workingwolves$getExpeditionState());
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        target = null;
        isRetreating = false;
        retreatTicks = 0;
        collectTicks = 0;
        lastKillPos = null;
        wolf.setTarget(null);
    }

    @Override
    public void tick() {
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        Level level = wolf.level();

        // 1. Collect drops from last kill
        if (collectTicks > 0) {
            collectTicks--;
            if (lastKillPos != null) {
                collectDropsAt(level, lastKillPos, mixin);
            }
            if (collectTicks <= 0) {
                lastKillPos = null;
            }
            return;
        }

        // 2. Check expedition timer (startTime==0 means never dispatched — don't expire)
        long startTime = mixin.workingwolves$getExpeditionStartTime();
        if (startTime > 0 && level.getGameTime() - startTime >= mixin.workingwolves$getExpeditionDuration()) {
            mixin.workingwolves$setExpeditionState("returning");
            mixin.workingwolves$syncData();
            return;
        }

        // 3. Check bag capacity
        if (isBagFull(mixin)) {
            mixin.workingwolves$setExpeditionState("returning");
            mixin.workingwolves$syncData();
            return;
        }

        // 4. Preemptive retreat if 3+ hostiles within melee range
        if (!isRetreating && countHostilesInRange(level, wolf.blockPosition(), PREEMPTIVE_HOSTILE_RANGE) >= PREEMPTIVE_HOSTILE_COUNT) {
            startRetreating(RETREAT_DURATION);
            fleeFromNearestHostile(level, mixin);
            return;
        }

        // 5. Health management
        float healthRatio = wolf.getHealth() / wolf.getMaxHealth();

        if (isRetreating) {
            tickRetreat(mixin, level, healthRatio);
            return;
        }

        if (healthRatio < RETREAT_HEALTH_RATIO) {
            startRetreating(RETREAT_DURATION);
            fleeFromNearestHostile(level, mixin);
            return;
        }

        // 6. Combat logic
        if (target != null) {
            tickCombat(mixin, level);
            return;
        }

        // 7. Scan for targets
        scanCooldown--;
        if (scanCooldown <= 0) {
            scanCooldown = SCAN_COOLDOWN;
            scanForTarget(level, mixin);
        }
    }

    private void tickRetreat(IWorkingWolf mixin, Level level, float healthRatio) {
        retreatTicks--;
        eatFoodFromBag(mixin);

        if (healthRatio >= REENGAGE_HEALTH_RATIO && retreatTicks <= 0) {
            isRetreating = false;
            target = null;
            wolf.setTarget(null);
            return;
        }

        // Keep fleeing
        fleeFromNearestHostile(level, mixin);
    }

    private void tickCombat(IWorkingWolf mixin, Level level) {
        if (!target.isAlive()) {
            // Target died: collect drops, clear target
            lastKillPos = target.blockPosition();
            collectTicks = 10; // Collect for 0.5 seconds
            target = null;
            wolf.setTarget(null);
            return;
        }

        double distSq = wolf.distanceToSqr(target);

        if (distSq <= MELEE_RANGE * MELEE_RANGE) {
            // In melee range: attack
            wolf.doHurtTarget((ServerLevel) wolf.level(), target);
            repathTicks = 10;
        } else {
            // Pursue
            repathTicks--;
            if (repathTicks <= 0) {
                wolf.getNavigation().moveTo(target, SPEED);
                wolf.setTarget(target);
                repathTicks = REPATH_INTERVAL;
            }
        }
    }

    private void startRetreating(int duration) {
        isRetreating = true;
        retreatTicks = duration;
        target = null;
        wolf.setTarget(null);
    }

    private void scanForTarget(Level level, IWorkingWolf mixin) {
        ItemStack filterStack = mixin.workingwolves$getFilterItem();
        Predicate<Monster> predicate = getMobFilter(filterStack);

        List<Monster> mobs = level.getEntitiesOfClass(Monster.class,
            new AABB(wolf.blockPosition()).inflate(SCAN_RANGE),
            monster -> monster.isAlive() && predicate.test(monster));

        if (mobs.isEmpty()) return;

        // Pick nearest
        Monster nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Monster mob : mobs) {
            double dist = wolf.distanceToSqr(mob);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = mob;
            }
        }

        if (nearest != null) {
            target = nearest;
            wolf.setTarget(nearest);
            wolf.getNavigation().moveTo(nearest, SPEED);
            repathTicks = REPATH_INTERVAL;
        }
    }

    private Predicate<Monster> getMobFilter(ItemStack filterStack) {
        if (filterStack.isEmpty()) {
            return m -> true;
        }

        if (filterStack.is(Items.BONE)) {
            return m -> {
                String name = net.minecraft.world.entity.EntityType.getKey(m.getType()).getPath();
                return name.contains("skeleton") || name.contains("stray") || name.contains("wither");
            };
        }
        if (filterStack.is(Items.ROTTEN_FLESH)) {
            return m -> {
                String name = net.minecraft.world.entity.EntityType.getKey(m.getType()).getPath();
                return name.contains("zombie") || name.contains("drowned") || name.contains("husk");
            };
        }
        if (filterStack.is(Items.STRING)) {
            return m -> {
                String name = net.minecraft.world.entity.EntityType.getKey(m.getType()).getPath();
                return name.contains("spider");
            };
        }
        if (filterStack.is(Items.GUNPOWDER)) {
            return m -> {
                String name = net.minecraft.world.entity.EntityType.getKey(m.getType()).getPath();
                return name.contains("creeper");
            };
        }
        if (filterStack.is(Items.ENDER_PEARL)) {
            return m -> {
                String name = net.minecraft.world.entity.EntityType.getKey(m.getType()).getPath();
                return name.contains("enderman");
            };
        }
        if (filterStack.is(Items.BLAZE_POWDER) || filterStack.is(Items.BLAZE_ROD)) {
            return m -> {
                String name = net.minecraft.world.entity.EntityType.getKey(m.getType()).getPath();
                return name.contains("blaze");
            };
        }
        if (filterStack.is(Items.GHAST_TEAR)) {
            return m -> {
                String name = net.minecraft.world.entity.EntityType.getKey(m.getType()).getPath();
                return name.contains("ghast");
            };
        }
        if (filterStack.is(Items.ARROW)) {
            return m -> {
                String name = net.minecraft.world.entity.EntityType.getKey(m.getType()).getPath();
                return name.contains("skeleton");
            };
        }
        if (filterStack.is(Items.EMERALD)) {
            return m -> {
                String name = net.minecraft.world.entity.EntityType.getKey(m.getType()).getPath();
                return name.contains("vindicator") || name.contains("evoker")
                    || name.contains("pillager") || name.contains("ravager");
            };
        }

        return m -> true;
    }

    private int countHostilesInRange(Level level, BlockPos pos, int range) {
        AABB aabb = new AABB(pos).inflate(range);
        return level.getEntitiesOfClass(Monster.class, aabb, Monster::isAlive).size();
    }

    private void fleeFromNearestHostile(Level level, IWorkingWolf mixin) {
        // First, try to flee toward bed
        BlockPos bedPos = mixin.workingwolves$getBedPos();
        if (bedPos != null && wolf.blockPosition().distSqr(bedPos) > REENGAGE_HEALTH_RATIO * REENGAGE_HEALTH_RATIO) {
            // Flee toward bed direction
            Vec3 towardBed = new Vec3(
                bedPos.getX() - wolf.getX(),
                0,
                bedPos.getZ() - wolf.getZ()
            ).normalize();
            wolf.getNavigation().moveTo(
                wolf.getX() + towardBed.x * FLEE_DISTANCE,
                wolf.getY(),
                wolf.getZ() + towardBed.z * FLEE_DISTANCE,
                RETREAT_SPEED
            );
            return;
        }

        // Flee away from nearest hostile
        List<Monster> hostiles = level.getEntitiesOfClass(Monster.class,
            new AABB(wolf.blockPosition()).inflate(16), Monster::isAlive);

        if (hostiles.isEmpty()) {
            if (bedPos != null) {
                wolf.getNavigation().moveTo(bedPos.getX(), bedPos.getY(), bedPos.getZ(), RETREAT_SPEED);
            }
            return;
        }

        Monster nearest = hostiles.stream()
            .min((a, b) -> Double.compare(wolf.distanceToSqr(a), wolf.distanceToSqr(b)))
            .orElse(null);

        if (nearest != null) {
            Vec3 away = new Vec3(
                wolf.getX() - nearest.getX(),
                0,
                wolf.getZ() - nearest.getZ()
            ).normalize().scale(FLEE_DISTANCE);

            wolf.getNavigation().moveTo(
                wolf.getX() + away.x,
                wolf.getY(),
                wolf.getZ() + away.z,
                RETREAT_SPEED
            );
        }
    }

    private void eatFoodFromBag(IWorkingWolf mixin) {
        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();
        Level level = wolf.level();

        for (int i = 0; i < bag.size(); i++) {
            ItemStack stack = bag.get(i);
            if (!stack.isEmpty() && stack.get(DataComponents.FOOD) != null) {
                // Consume one item for healing
                stack.shrink(1);
                wolf.heal(FOOD_HEAL_AMOUNT);
                stack.shrink(1);
                if (stack.isEmpty()) {
                    bag.set(i, ItemStack.EMPTY);
                }
                return;
            }
        }
    }

    private void collectDropsAt(Level level, BlockPos pos, IWorkingWolf mixin) {
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class,
            new AABB(pos).inflate(DROP_COLLECT_RANGE), ItemEntity::isAlive);

        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem().copy();
            ItemStack remainder = addToBag(bag, stack);
            if (remainder.isEmpty()) {
                drop.discard();
            } else {
                drop.setItem(remainder);
            }
        }
    }

    private ItemStack addToBag(NonNullList<ItemStack> bag, ItemStack stack) {
        ItemStack remainder = stack;
        for (int i = 0; i < bag.size() && !remainder.isEmpty(); i++) {
            ItemStack slot = bag.get(i);
            if (slot.isEmpty()) {
                bag.set(i, remainder);
                remainder = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(slot, remainder)) {
                int transfer = Math.min(remainder.getCount(), slot.getMaxStackSize() - slot.getCount());
                if (transfer > 0) {
                    slot.grow(transfer);
                    remainder.shrink(transfer);
                }
            }
        }
        return remainder;
    }

    private boolean isBagFull(IWorkingWolf mixin) {
        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();
        if (bag.isEmpty()) return true;
        for (ItemStack stack : bag) {
            if (stack.isEmpty()) return false;
        }
        return true;
    }
}
