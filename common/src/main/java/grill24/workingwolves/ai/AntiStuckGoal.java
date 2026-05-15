package grill24.workingwolves.ai;

import grill24.workingwolves.api.IWorkingWolf;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

/**
 * Priority 0 goal. If a working wolf hasn't moved more than ~2 blocks in 30 seconds,
 * teleport it to a nearby safe position and resume.
 */
public class AntiStuckGoal extends Goal {
    private static final int STUCK_TICKS = 600; // 30 seconds
    private static final double STUCK_DIST_SQ = 4.0; // ~2 blocks
    private static final int BASE_TELEPORT_RADIUS = 32;
    private static final int MAX_TELEPORT_RADIUS = 128;

    private final Wolf wolf;
    private BlockPos lastPos;
    private int stuckTimer = 0;
    private int consecutiveFailures = 0;

    public AntiStuckGoal(Wolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        if (mixin.workingwolves$getCollarTier() <= 0) return false;
        String state = mixin.workingwolves$getExpeditionState();
        return "returning".equals(state);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        BlockPos current = wolf.blockPosition();

        if (lastPos == null || current.distSqr(lastPos) > STUCK_DIST_SQ) {
            lastPos = current;
            stuckTimer = 0;
            consecutiveFailures = 0;
            return;
        }

        stuckTimer++;
        if (stuckTimer >= STUCK_TICKS) {
            stuckTimer = 0;
            teleportNearby();
        }
    }

    private void teleportNearby() {
        Level level = wolf.level();
        BlockPos center = wolf.blockPosition();
        int radius = Math.min(BASE_TELEPORT_RADIUS * (1 << consecutiveFailures), MAX_TELEPORT_RADIUS);

        BlockPos safePos = WolfAIHelper.findSafeTeleportPosition(level, center, radius, 256);
        if (safePos != null) {
            wolf.teleportTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
            lastPos = safePos;
            consecutiveFailures = 0;
            return;
        }

        // Fallback: teleport straight up 1 block if possible
        BlockPos up = center.above();
        if (level.getBlockState(up).isAir() && level.getBlockState(up.above()).isAir()) {
            wolf.teleportTo(up.getX() + 0.5, up.getY(), up.getZ() + 0.5);
            lastPos = up;
            consecutiveFailures = 0;
        } else {
            consecutiveFailures++;
        }
    }
}
