package grill24.workingwolves.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class WolfAIHelper {

    public static int countHostilesInRange(Level level, BlockPos center, int range) {
        AABB aabb = new AABB(center).inflate(range);
        double rangeSq = (double) range * range;
        return level.getEntitiesOfClass(Monster.class, aabb,
            monster -> monster.isAlive() && monster.blockPosition().distSqr(center) <= rangeSq
        ).size();
    }

    public static BlockPos findGround(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos mutable = pos.mutable();
        int minY = -64;
        int maxY = 320;
        for (int y = pos.getY(); y > minY; y--) {
            mutable.setY(y);
            if (level.getBlockState(mutable).isSolid() && level.getBlockState(mutable.above()).isAir()) {
                return mutable.above().immutable();
            }
        }
        for (int y = pos.getY(); y < maxY; y++) {
            mutable.setY(y);
            if (level.getBlockState(mutable.below()).isSolid() && level.getBlockState(mutable).isAir()) {
                return mutable.immutable();
            }
        }
        return null;
    }

    public static BlockPos findAdjacentStandingPos(Level level, BlockPos orePos) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dz == 0 && dy == 0) continue;
                    m.set(orePos.getX() + dx, orePos.getY() + dy, orePos.getZ() + dz);
                    if (level.getBlockState(m).isAir() && level.getBlockState(m.below()).isSolid()) {
                        return m.immutable();
                    }
                }
            }
        }
        return null;
    }

    public static BlockPos findSafeTeleportPosition(Level level, BlockPos center, int radius, int maxAttempts) {
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int dx = level.getRandom().nextInt(radius * 2) - radius;
            int dz = level.getRandom().nextInt(radius * 2) - radius;
            int dy = level.getRandom().nextInt(4) - 2;
            target.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
            BlockState ground = level.getBlockState(target.below());
            BlockState at = level.getBlockState(target);
            BlockState above = level.getBlockState(target.above());
            if (ground.isSolid() && at.isAir() && above.isAir()
                && !at.liquid() && !ground.liquid()) {
                return target.immutable();
            }
        }
        return null;
    }
}
