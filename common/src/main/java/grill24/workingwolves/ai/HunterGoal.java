package grill24.workingwolves.ai;

import grill24.workingwolves.Config;
import grill24.workingwolves.api.IWorkingWolf;
import grill24.workingwolves.inventory.WolfBagHelper;
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
    private static final double SPEED = 1.5;
    private static final double RETREAT_SPEED = 1.4;
    // Scan range is controlled by Config.hunterScanRange
    private static final int MELEE_RANGE = 2;
    private static final float RETREAT_HEALTH_RATIO = 0.5f;
    private static final float REENGAGE_HEALTH_RATIO = 0.75f;
    private static final int RETREAT_DURATION = 100; // 5 seconds
    private static final int PREEMPTIVE_RETREAT_DURATION = 60; // 3 seconds
    private static final int PREEMPTIVE_HOSTILE_RANGE = 3;
    private static final int PREEMPTIVE_HOSTILE_COUNT = 3;
    private static final int SCAN_COOLDOWN = 20;
    private static final int DROP_COLLECT_RANGE = 4;
    private static final float FOOD_HEAL_AMOUNT = 6.0f;
    private static final int FLEE_DISTANCE = 10;
    // Path recalculation — follows vanilla MeleeAttackGoal pattern:
    // short adaptive interval, retriggers on target movement, separate from attack timing
    private static final int PATH_RECALC_BASE_MIN = 4;
    private static final int PATH_RECALC_BASE_MAX = 10;

    private LivingEntity target = null;
    private int ticksUntilNextPathRecalculation = 0;
    private int ticksUntilNextAttack = 0;
    private double pathedTargetX;
    private double pathedTargetY;
    private double pathedTargetZ;
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
    public void start() {
        ((IWorkingWolf) (Object) wolf).workingwolves$applyNavBudget(Config.hunterScanRange);
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

        // 2. Check expedition timer (startTime==0 means never dispatched - don't expire)
        if (mixin.workingwolves$isExpeditionExpired(level.getGameTime())) {
            mixin.workingwolves$triggerReturn();
            return;
        }

        // 3. Check bag capacity
        if (WolfBagHelper.isBagFull(mixin)) {
            mixin.workingwolves$triggerReturn();
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
            lastKillPos = target.blockPosition();
            collectTicks = 10;
            target = null;
            wolf.setTarget(null);
            return;
        }

        wolf.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // --- Path recalculation: vanilla MeleeAttackGoal pattern ---
        // Decoupled from attack timing — attacking does NOT freeze movement.
        // Repaths when the timer expires AND the target has moved meaningfully,
        // or on a random hedge. On pathing failure, shrinks the timer to retry sooner.
        ticksUntilNextPathRecalculation = Math.max(ticksUntilNextPathRecalculation - 1, 0);
        if (ticksUntilNextPathRecalculation <= 0
            && (pathedTargetX == 0.0 && pathedTargetY == 0.0 && pathedTargetZ == 0.0
                || target.distanceToSqr(pathedTargetX, pathedTargetY, pathedTargetZ) >= 1.0
                || wolf.getRandom().nextFloat() < 0.05F)) {

            pathedTargetX = target.getX();
            pathedTargetY = target.getY();
            pathedTargetZ = target.getZ();
            ticksUntilNextPathRecalculation = PATH_RECALC_BASE_MIN + wolf.getRandom().nextInt(PATH_RECALC_BASE_MAX - PATH_RECALC_BASE_MIN + 1);

            double targetDistSq = wolf.distanceToSqr(target);
            if (targetDistSq > 1024.0) {
                ticksUntilNextPathRecalculation += 10;
            } else if (targetDistSq > 256.0) {
                ticksUntilNextPathRecalculation += 5;
            }

            if (!wolf.getNavigation().moveTo(target, SPEED)) {
                ticksUntilNextPathRecalculation += 15;
            }
        }

        // --- Attack: separate cooldown, vanilla-style ---
        ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);
        if (ticksUntilNextAttack <= 0
            && wolf.isWithinMeleeAttackRange(target)
            && wolf.getSensing().hasLineOfSight(target)) {
            wolf.doHurtTarget((ServerLevel) wolf.level(), target);
            ticksUntilNextAttack = 20;
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
            new AABB(wolf.blockPosition()).inflate(Config.hunterScanRange),
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
            pathedTargetX = 0.0;
            pathedTargetY = 0.0;
            pathedTargetZ = 0.0;
            ticksUntilNextPathRecalculation = 0;
            wolf.getNavigation().moveTo(nearest, SPEED);
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
        return WolfAIHelper.countHostilesInRange(level, pos, range);
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
        WolfBagHelper.eatFoodFromBag(mixin, wolf, FOOD_HEAL_AMOUNT);
    }

    private void collectDropsAt(Level level, BlockPos pos, IWorkingWolf mixin) {
        WolfBagHelper.collectDropsAt(level, pos, mixin, DROP_COLLECT_RANGE);
    }
}
