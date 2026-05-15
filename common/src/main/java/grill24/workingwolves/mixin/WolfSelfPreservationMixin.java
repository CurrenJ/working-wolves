package grill24.workingwolves.mixin;

import grill24.workingwolves.ai.WolfAIHelper;
import grill24.workingwolves.api.IWorkingWolf;
import grill24.workingwolves.inventory.WolfBagHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Injected at HEAD of Wolf.aiStep(). Handles survival behaviors directly,
 * bypassing the goal system. Runs every tick for every Wolf instance
 * (not just collared working wolves).
 */
@Mixin(Wolf.class)
public abstract class WolfSelfPreservationMixin {

    // ======== Instance fields (injected into Wolf) ========

    @Unique
    private int workingwolves$healCooldown = 0;

    @Unique
    private int workingwolves$mouthFoodTimer = 0;

    @Unique
    private int workingwolves$fleeTicks = 0;

    // ======== Constants ========

    private static final float HEAL_THRESHOLD = 0.75f;
    private static final int HEAL_COOLDOWN = 20;

    private static final int CORNER_HURT_THRESHOLD = 3;
    private static final int CORNER_HURT_WINDOW = 60;
    private static final double CORNER_MOVE_THRESHOLD = 16.0;
    private static final int CORNER_FLEE_DURATION = 80;
    private static final double CORNER_FLEE_SPEED = 1.6;
    private static final int CORNER_FLEE_SEARCH = 10;

    private static final int HAZARD_RANGE = 2;
    private static final int LAVA_FLEE_DURATION = 80;
    private static final double LAVA_FLEE_SPEED = 1.4;
    private static final int LAVA_FLEE_RADIUS = 12;

    private static final int CROWD_RANGE = 3;
    private static final int CROWD_THRESHOLD = 3;
    private static final double CROWD_SPEED = 1.3;
    private static final int CROWD_FLEE_DURATION = 40;
    private static final int CROWD_FLEE_SEARCH = 5;

    private static final int FALL_THRESHOLD = 4;
    private static final double FALL_AVOID_SPEED = 1.2;
    private static final int FALL_SEARCH_RADIUS = 3;

    // ======== aiStep injection ========

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void workingwolves$onAiStep(CallbackInfo ci) {
        IWorkingWolf mixin = (IWorkingWolf) (Object) this;
        if (mixin.workingwolves$getCollarTier() <= 0) return;

        Wolf self = (Wolf) (Object) this;
        Level level = self.level();
        BlockPos wolfPos = self.blockPosition();
        long gameTime = level.getGameTime();
        long hurtTs = self.getLastHurtByMobTimestamp();
        boolean justHurt = hurtTs > 0 && hurtTs != mixin.workingwolves$getPrevHurtTimestamp();
        mixin.workingwolves$setPrevHurtTimestamp(hurtTs);

        // -- Corner tracking (runs every tick regardless of flee state) --

        if (justHurt) {
            if (mixin.workingwolves$getCornerHurtCount() == 0) {
                mixin.workingwolves$setCornerHurtStartTime(gameTime);
                mixin.workingwolves$setCornerHurtStartPos(wolfPos.immutable());
            }
            mixin.workingwolves$setCornerHurtCount(mixin.workingwolves$getCornerHurtCount() + 1);
        }
        // Reset if the time window expired
        if (mixin.workingwolves$getCornerHurtCount() > 0
            && gameTime - mixin.workingwolves$getCornerHurtStartTime() > CORNER_HURT_WINDOW) {
            mixin.workingwolves$setCornerHurtCount(0);
        }
        // Reset if the wolf moved enough during the window
        BlockPos startPos = mixin.workingwolves$getCornerHurtStartPos();
        if (startPos != null && wolfPos.distSqr(startPos) > CORNER_MOVE_THRESHOLD) {
            mixin.workingwolves$setCornerHurtCount(0);
        }

        // -- Flee ticker: skip all aiStep processing while a survival flee is active --

        if (workingwolves$fleeTicks > 0) {
            workingwolves$fleeTicks--;
            return;
        }

        // ======== 1. Healing ========

        workingwolves$healCooldown--;
        if (self.getHealth() / self.getMaxHealth() < HEAL_THRESHOLD && workingwolves$healCooldown <= 0) {
            ItemStack food = WolfBagHelper.findFood(mixin);
            if (!food.isEmpty()) {
                mixin.workingwolves$displayMouthItem(food.copy());
                workingwolves$mouthFoodTimer = 20;
                // Eating particles at mouth
                if (level instanceof ServerLevel sl) {
                    double yawRad = self.yBodyRot * (Math.PI / 180.0);
                    double mouthX = self.getX() - Math.sin(yawRad) * 0.6;
                    double mouthY = self.getY() + self.getEyeHeight() - 0.15;
                    double mouthZ = self.getZ() + Math.cos(yawRad) * 0.6;
                    sl.sendParticles(
                        new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(food)),
                        mouthX, mouthY, mouthZ, 8, 0.15, 0.15, 0.15, 0.05);
                }
            }
            WolfBagHelper.eatFoodFromBag(mixin, self);
            workingwolves$healCooldown = HEAL_COOLDOWN;
        }

        // ======== 2. Corner escape ========

        if (mixin.workingwolves$getCornerHurtCount() >= CORNER_HURT_THRESHOLD) {
            mixin.workingwolves$setCornerHurtCount(0);
            Vec3 awayPos = findReachableFleePosition(level, wolfPos, CORNER_FLEE_SEARCH);
            if (awayPos != null) {
                self.getNavigation().moveTo(awayPos.x, awayPos.y, awayPos.z, CORNER_FLEE_SPEED);
                workingwolves$fleeTicks = CORNER_FLEE_DURATION;
            }
            return;
        }

        // ======== 3. Lava / fire avoidance ========

        if (isNearHazard(level, wolfPos, HAZARD_RANGE)) {
            BlockPos hazardPos = findNearestHazard(level, wolfPos, HAZARD_RANGE);
            Vec3 safePos = findSafePositionAwayFrom(level, wolfPos, hazardPos, LAVA_FLEE_RADIUS);
            if (safePos != null) {
                self.getNavigation().moveTo(safePos.x, safePos.y, safePos.z, LAVA_FLEE_SPEED);
                workingwolves$fleeTicks = LAVA_FLEE_DURATION;
            }
            return;
        }

        // Wolf itself is in lava or on fire
        if (self.isInLava() || self.isOnFire()) {
            Vec3 safePos = findSafePosition(level, wolfPos, LAVA_FLEE_RADIUS);
            if (safePos != null) {
                self.getNavigation().moveTo(safePos.x, safePos.y, safePos.z, LAVA_FLEE_SPEED);
                workingwolves$fleeTicks = LAVA_FLEE_DURATION;
            }
            return;
        }

        // ======== 4. Crowding ========

        int nearbyHostiles = countNearbyHostiles(level, wolfPos, CROWD_RANGE);
        if (nearbyHostiles >= CROWD_THRESHOLD) {
            Vec3 awayPos = findPositionAwayFromAllHostiles(level, wolfPos, CROWD_FLEE_SEARCH);
            if (awayPos != null) {
                self.getNavigation().moveTo(awayPos.x, awayPos.y, awayPos.z, CROWD_SPEED);
                workingwolves$fleeTicks = CROWD_FLEE_DURATION;
            }
            return;
        }

        // ======== 5. Falls ========

        boolean isReturning = "returning".equals(mixin.workingwolves$getExpeditionState());
        if (!isReturning && isNearDangerousFall(level, wolfPos, FALL_THRESHOLD)) {
            Vec3 safePos = findSafePosition(level, wolfPos, FALL_SEARCH_RADIUS);
            if (safePos != null) {
                self.getNavigation().moveTo(safePos.x, safePos.y, safePos.z, FALL_AVOID_SPEED);
            }
            return;
        }

        // ======== 6. Mouth food timer ========

        if (workingwolves$mouthFoodTimer > 0) {
            workingwolves$mouthFoodTimer--;
            if (workingwolves$mouthFoodTimer <= 0) {
                mixin.workingwolves$clearMouthItem();
            }
        }
    }

    // ===================================================================
    //  Helper methods (ported from SelfPreservationGoal)
    // ===================================================================

    @Unique
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

    @Unique
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

    @Unique
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

    @Unique
    private Vec3 findSafePositionAwayFrom(Level level, BlockPos fromPos, BlockPos awayFrom, int searchRadius) {
        if (awayFrom == null) {
            return findSafePosition(level, fromPos, searchRadius);
        }
        double dx = fromPos.getX() - awayFrom.getX();
        double dz = fromPos.getZ() - awayFrom.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        int dirX = dist > 0.01 ? (int) Math.round(dx / dist) : 0;
        int dirZ = dist > 0.01 ? (int) Math.round(dz / dist) : 0;

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int r = 2; r <= searchRadius; r++) {
            for (int tryDir = 0; tryDir < 5; tryDir++) {
                int sx = 0;
                int sz = 0;
                if (tryDir == 0) {
                    sx = dirX; sz = dirZ;       // straight away
                } else if (tryDir == 1) {
                    sx = dirX; sz = 0;           // lateral
                } else if (tryDir == 2) {
                    sx = 0; sz = dirZ;           // lateral
                } else if (tryDir == 3) {
                    sx = -dirX; sz = dirZ;       // diagonal
                } else {
                    sx = dirX; sz = -dirZ;       // diagonal
                }
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
        return findSafePosition(level, fromPos, searchRadius);
    }

    @Unique
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

    @Unique
    private int countNearbyHostiles(Level level, BlockPos center, int range) {
        return WolfAIHelper.countHostilesInRange(level, center, range);
    }

    @Unique
    private Vec3 findPositionAwayFromAllHostiles(Level level, BlockPos fromPos, int searchRadius) {
        Wolf self = (Wolf) (Object) this;
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

    @Unique
    private Vec3 findReachableFleePosition(Level level, BlockPos fromPos, int searchRadius) {
        Wolf self = (Wolf) (Object) this;
        List<Monster> hostiles = level.getEntitiesOfClass(Monster.class,
            new AABB(fromPos).inflate(8), Monster::isAlive);

        Vec3 awayDir;
        if (hostiles.isEmpty()) {
            double angle = self.getRandom().nextDouble() * 2.0 * Math.PI;
            awayDir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        } else {
            double totalX = 0.0;
            double totalZ = 0.0;
            for (Monster m : hostiles) {
                totalX += fromPos.getX() - m.getX();
                totalZ += fromPos.getZ() - m.getZ();
            }
            awayDir = new Vec3(totalX, 0, totalZ).normalize();
        }

        for (int dist = searchRadius; dist >= 4; dist -= 2) {
            for (int angleOffset = -1; angleOffset <= 1; angleOffset++) {
                double rad = Math.atan2(awayDir.z, awayDir.x) + angleOffset * 0.5;
                double dx = Math.cos(rad) * dist;
                double dz = Math.sin(rad) * dist;
                BlockPos target = BlockPos.containing(fromPos.getX() + dx, fromPos.getY(), fromPos.getZ() + dz);
                Vec3 safePos = findSafePosition(level, target, 3);
                if (safePos != null) {
                    if (self.getNavigation().createPath(safePos.x, safePos.y, safePos.z, 0) != null) {
                        return safePos;
                    }
                }
            }
        }
        return null;
    }

    @Unique
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
