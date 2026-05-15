package grill24.workingwolves.ai;

import grill24.workingwolves.blockentity.DogBedBlockEntity;
import grill24.workingwolves.api.IWorkingWolf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;

public class ReturnToBaseGoal extends Goal {
    private final Wolf wolf;
    private static final double SPEED = 1.2;
    private static final int REACH_DISTANCE = 2;
    private static final int WAYPOINT_DIST = 64;
    private static final int REPATH_TICKS = 40;

    private int repathTimer = 0;

    public ReturnToBaseGoal(Wolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        return "returning".equals(mixin.workingwolves$getExpeditionState());
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        mixin.workingwolves$applyNavBudget(64);
        repathTimer = 0;
        pathfindTowardDestination();
    }

    @Override
    public void tick() {
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        Level level = wolf.level();
        BlockPos dest = getDestination(mixin);

        if (dest == null) {
            mixin.workingwolves$setExpeditionState("idle");
            mixin.workingwolves$syncData();
            return;
        }

        if (wolf.blockPosition().distSqr(dest) <= REACH_DISTANCE * REACH_DISTANCE) {
            handleArrival(level, mixin, dest);
            return;
        }

        repathTimer--;
        if (repathTimer <= 0 || wolf.getNavigation().isDone()) {
            repathTimer = REPATH_TICKS;
            pathfindTowardDestination();
        }
    }

    private BlockPos getDestination(IWorkingWolf mixin) {
        BlockPos bedPos = mixin.workingwolves$getBedPos();
        if (bedPos != null) {
            // Validate that the bed still exists
            Level level = wolf.level();
            if (level.isLoaded(bedPos) && !(level.getBlockEntity(bedPos) instanceof DogBedBlockEntity)) {
                mixin.workingwolves$setBedPos(null);
                mixin.workingwolves$syncData();
            } else {
                return bedPos;
            }
        }
        LivingEntity owner = wolf.getOwner();
        return owner != null ? owner.blockPosition() : null;
    }

    private void handleArrival(Level level, IWorkingWolf mixin, BlockPos dest) {
        BlockPos bedPos = mixin.workingwolves$getBedPos();
        if (bedPos != null) {
            BlockEntity be = level.getBlockEntity(bedPos);
            if (be instanceof DogBedBlockEntity dogBed) {
                depositItems(dogBed, mixin);
                finishReturn(mixin);
                return;
            }
            // Bed was destroyed — clear it and continue to player
            mixin.workingwolves$setBedPos(null);
            mixin.workingwolves$syncData();
            return;
        }
        // No bed — arrived at player, drop items
        dropAllItems(level, mixin);
        finishReturn(mixin);
    }

    private void finishReturn(IWorkingWolf mixin) {
        mixin.workingwolves$setExpeditionState("idle");
        wolf.setOrderedToSit(true);
        mixin.workingwolves$syncData();
    }

    private void pathfindTowardDestination() {
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        BlockPos dest = getDestination(mixin);
        if (dest == null) return;

        if (wolf.blockPosition().distSqr(dest) <= WAYPOINT_DIST * WAYPOINT_DIST) {
            Path path = wolf.getNavigation().createPath(dest, REACH_DISTANCE);
            if (path != null && path.canReach()) {
                wolf.getNavigation().moveTo(path, SPEED);
                return;
            }
        }

        double dx = dest.getX() - wolf.getX();
        double dz = dest.getZ() - wolf.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 1.0) {
            int wayX = (int) (wolf.getX() + dx / dist * WAYPOINT_DIST);
            int wayZ = (int) (wolf.getZ() + dz / dist * WAYPOINT_DIST);
            BlockPos waypoint = new BlockPos(wayX, dest.getY(), wayZ);
            BlockPos ground = findGround(wolf.level(), waypoint);
            if (ground != null) {
                Path path = wolf.getNavigation().createPath(ground, 4);
                if (path != null && path.canReach()) {
                    wolf.getNavigation().moveTo(path, SPEED);
                    return;
                }
            }
        }

        wolf.getNavigation().moveTo(dest.getX(), dest.getY(), dest.getZ(), SPEED);
    }

    private BlockPos findGround(Level level, BlockPos pos) {
        return WolfAIHelper.findGround(level, pos);
    }

    private void depositItems(DogBedBlockEntity dogBed, IWorkingWolf mixin) {
        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();
        for (int i = 0; i < bag.size(); i++) {
            ItemStack stack = bag.get(i);
            if (!stack.isEmpty()) {
                bag.set(i, dogBed.tryInsert(stack));
            }
        }
    }

    private void dropAllItems(Level level, IWorkingWolf mixin) {
        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();
        BlockPos pos = wolf.blockPosition();
        for (ItemStack stack : bag) {
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        for (int i = 0; i < bag.size(); i++) {
            bag.set(i, ItemStack.EMPTY);
        }
    }
}
