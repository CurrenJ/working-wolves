package grill24.workingwolves.ai;

import grill24.workingwolves.api.IWorkingWolf;
import grill24.workingwolves.inventory.WolfBagHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Priority 0 goal. Active for any collared wolf.
 * Handles self-healing from food, lava/fire avoidance, fall avoidance, and crowding disengagement.
 */
public class SelfPreservationGoal extends Goal {
    private final Wolf wolf;
    private int fleeTicks = 0;
    private int healCooldown = 0;
    private boolean noFoodAvailable = false;
    private static final int FLEE_DURATION = 40; // 2 seconds of fleeing
    private static final double FLEE_SPEED = 1.4;
    private static final double CROWD_SPEED = 1.3;
    private static final double FALL_AVOID_SPEED = 1.2;
    private static final int HAZARD_RANGE = 2;
    private static final int CROWD_RANGE = 3;
    private static final int CROWD_THRESHOLD = 3;
    private static final int FALL_THRESHOLD = 4;
    private static final int FLEE_SEARCH_RADIUS = 5;
    private static final int LAVA_FLEE_RADIUS = 12;
    private static final int LAVA_FLEE_DURATION = 80; // 4 seconds — enough to clear the hazard zone
    private static final float HEAL_THRESHOLD = 0.5f;
    private static final int HEAL_COOLDOWN = 20; // eat at most once per second
    private static final float FOOD_HEAL_AMOUNT = 6.0f;

    public SelfPreservationGoal(Wolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        return mixin.workingwolves$getCollarTier() > 0 && (hasThreat() || needsHealing());
    }

    @Override
    public boolean canContinueToUse() {
        if (fleeTicks > 0) return true;
        if (noFoodAvailable) return false;
        return needsHealing();
    }

    @Override
    public void stop() {
        fleeTicks = 0;
        healCooldown = 0;
        noFoodAvailable = false;
    }

    private boolean needsHealing() {
        return wolf.getHealth() / wolf.getMaxHealth() < HEAL_THRESHOLD;
    }

    private boolean hasThreat() {
        Level level = wolf.level();
        BlockPos wolfPos = wolf.blockPosition();
        return wolf.isInLava() || wolf.isOnFire()
            || isNearHazard(level, wolfPos, HAZARD_RANGE)
            || countNearbyHostiles(level, wolfPos, CROWD_RANGE) >= CROWD_THRESHOLD
            || isNearDangerousFall(level, wolfPos, FALL_THRESHOLD);
    }

    @Override
    public void tick() {
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        Level level = wolf.level();
        BlockPos wolfPos = wolf.blockPosition();

        // 0. Eat food if low on health (before any movement, so healing takes priority)
        healCooldown--;
        if (needsHealing() && healCooldown <= 0) {
            if (!WolfBagHelper.eatFoodFromBag(mixin, wolf, FOOD_HEAL_AMOUNT)) {
                noFoodAvailable = true;
            }
            healCooldown = HEAL_COOLDOWN;
        }

        if (fleeTicks > 0) {
            fleeTicks--;
            return;
        }

        // 1. Lava/fire within range — directional flee away from the hazard
        if (isNearHazard(level, wolfPos, HAZARD_RANGE)) {
            BlockPos hazardPos = findNearestHazard(level, wolfPos, HAZARD_RANGE);
            Vec3 safePos = findSafePositionAwayFrom(level, wolfPos, hazardPos, LAVA_FLEE_RADIUS);
            if (safePos != null) {
                wolf.getNavigation().moveTo(safePos.x, safePos.y, safePos.z, FLEE_SPEED);
                fleeTicks = LAVA_FLEE_DURATION;
            }
            return;
        }

        // 2. Wolf itself is in lava or on fire
        if (wolf.isInLava() || wolf.isOnFire()) {
            Vec3 safePos = findSafePosition(level, wolfPos, LAVA_FLEE_RADIUS);
            if (safePos != null) {
                wolf.getNavigation().moveTo(safePos.x, safePos.y, safePos.z, FLEE_SPEED);
                fleeTicks = LAVA_FLEE_DURATION;
            }
            return;
        }

        // 3. Crowding: 3+ hostile mobs within melee range -> disengage
        int nearbyHostiles = countNearbyHostiles(level, wolfPos, CROWD_RANGE);
        if (nearbyHostiles >= CROWD_THRESHOLD) {
            Vec3 awayPos = findPositionAwayFromHostiles(level, wolfPos, FLEE_SEARCH_RADIUS);
            if (awayPos != null) {
                wolf.getNavigation().moveTo(awayPos.x, awayPos.y, awayPos.z, CROWD_SPEED);
                fleeTicks = FLEE_DURATION;
            }
            return;
        }

        // 4. Dangerous falls: avoid unless returning to base
        boolean isReturning = "returning".equals(mixin.workingwolves$getExpeditionState());
        if (!isReturning && isNearDangerousFall(level, wolfPos, FALL_THRESHOLD)) {
            Vec3 safePos = findSafePosition(level, wolfPos, 3);
            if (safePos != null) {
                wolf.getNavigation().moveTo(safePos.x, safePos.y, safePos.z, FALL_AVOID_SPEED);
            }
            return;
        }

    }

    private boolean isNearHazard(Level level, BlockPos center, int range) {
        BlockPos minPos = center.offset(-range, -range, -range);
        BlockPos maxPos = center.offset(range, range, range);
        for (BlockPos pos : BlockPos.betweenClosed(minPos, maxPos)) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.FIRE) || state.getBlock() instanceof BaseFireBlock) {
                return true;
            }
            if (state.is(Blocks.LAVA)) {
                return true;
            }
        }
        return false;
    }

    private Vec3 findSafePosition(Level level, BlockPos fromPos, int searchRadius) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int z = -searchRadius; z <= searchRadius; z++) {
                for (int y = -1; y <= 1; y++) {
                    mutable.set(fromPos.getX() + x, fromPos.getY() + y, fromPos.getZ() + z);
                    if (!mutable.equals(fromPos) && isSafeStandingPosition(level, mutable)) {
                        return Vec3.atCenterOf(mutable);
                    }
                }
            }
        }
        return null;
    }

    /** Finds the nearest lava or fire block within range of center. */
    private BlockPos findNearestHazard(Level level, BlockPos center, int range) {
        BlockPos nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        BlockPos minPos = center.offset(-range, -range, -range);
        BlockPos maxPos = center.offset(range, range, range);
        for (BlockPos pos : BlockPos.betweenClosed(minPos, maxPos)) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.FIRE) || state.getBlock() instanceof BaseFireBlock || state.is(Blocks.LAVA)) {
                double distSq = center.distSqr(pos);
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = pos.immutable();
                }
            }
        }
        return nearest;
    }

    /** Like findSafePosition but biases search away from a specific threat position. */
    private Vec3 findSafePositionAwayFrom(Level level, BlockPos fromPos, BlockPos awayFrom, int searchRadius) {
        if (awayFrom == null) {
            return findSafePosition(level, fromPos, searchRadius);
        }
        // Direction away from the threat
        double dx = fromPos.getX() - awayFrom.getX();
        double dz = fromPos.getZ() - awayFrom.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        int dirX = dist > 0.01 ? (int) Math.round(dx / dist) : 0;
        int dirZ = dist > 0.01 ? (int) Math.round(dz / dist) : 0;

        // Scan in bands outward from fromPos, biased toward the away direction
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int r = 2; r <= searchRadius; r++) {
            // Prefer positions in the away direction first (within this radius band)
            for (int tryDir = 0; tryDir < 5; tryDir++) {
                int sx = 0;
                int sz = 0;
                if (tryDir == 0) { sx = dirX; sz = dirZ; }           // primary: straight away
                else if (tryDir == 1) { sx = dirX; sz = 0; }          // sideways
                else if (tryDir == 2) { sx = 0; sz = dirZ; }           // sideways
                else if (tryDir == 3) { sx = -dirX; sz = dirZ; }       // diagonal-ish
                else { sx = dirX; sz = -dirZ; }                         // diagonal-ish

                int x = fromPos.getX() + sx * r;
                int z = fromPos.getZ() + sz * r;
                for (int y = -1; y <= 1; y++) {
                    mutable.set(x, fromPos.getY() + y, z);
                    if (isSafeStandingPosition(level, mutable)) {
                        return Vec3.atCenterOf(mutable);
                    }
                }
            }
        }
        // Fallback: standard scan
        return findSafePosition(level, fromPos, searchRadius);
    }

    private boolean isSafeStandingPosition(Level level, BlockPos pos) {
        BlockState ground = level.getBlockState(pos.below());
        BlockState at = level.getBlockState(pos);
        BlockState above = level.getBlockState(pos.above());
        return ground.isSolid()
            && at.isAir()
            && above.isAir()
            && !level.getBlockState(pos).is(BlockTags.FIRE)
            && !level.getBlockState(pos).is(Blocks.LAVA);
    }

    private int countNearbyHostiles(Level level, BlockPos center, int range) {
        return WolfAIHelper.countHostilesInRange(level, center, range);
    }

    private Vec3 findPositionAwayFromHostiles(Level level, BlockPos fromPos, int searchRadius) {
        List<Monster> hostiles = level.getEntitiesOfClass(Monster.class,
            new AABB(fromPos).inflate(8), Monster::isAlive);

        if (hostiles.isEmpty()) {
            return findSafePosition(level, fromPos, searchRadius);
        }

        double avgX = hostiles.stream().mapToDouble(LivingEntity::getX).average().orElse(fromPos.getX());
        double avgZ = hostiles.stream().mapToDouble(LivingEntity::getZ).average().orElse(fromPos.getZ());

        Vec3 awayDir = new Vec3(fromPos.getX() - avgX, 0, fromPos.getZ() - avgZ).normalize();

        BlockPos targetPos = BlockPos.containing(
            fromPos.getX() + awayDir.x * searchRadius,
            fromPos.getY(),
            fromPos.getZ() + awayDir.z * searchRadius
        );

        Vec3 safePos = findSafePosition(level, targetPos, 2);
        if (safePos != null) return safePos;

        return findSafePosition(level, fromPos, searchRadius);
    }

    private boolean isNearDangerousFall(Level level, BlockPos pos, int minDrop) {
        for (int i = 1; i <= minDrop; i++) {
            BlockState state = level.getBlockState(pos.below(i));
            if (!state.isAir()) {
                return false;
            }
        }
        return true;
    }

}
