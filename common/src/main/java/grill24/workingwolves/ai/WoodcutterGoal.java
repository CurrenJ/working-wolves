package grill24.workingwolves.ai;

import grill24.workingwolves.api.IWorkingWolf;
import grill24.workingwolves.inventory.WolfBagHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/**
 * Priority 2 goal. Active when: collarTier > 0, has axe in bag, expeditionState is "idle" (companion mode).
 * Scans for logs near the owner and chops them using an axe from the bag.
 */
public class WoodcutterGoal extends Goal {
    private final Wolf wolf;
    private static final double SPEED = 1.0;
    private static final double CHOP_CLOSE_DIST_SQ = 3.5 * 3.5;
    private static final double CHOP_DRIFT_DIST_SQ = 5.0 * 5.0;
    private static final int REPATH_INTERVAL = 30;
    private static final int LOG_SCAN_COOLDOWN = 10;
    private static final int COMPANION_RANGE = 16;
    private static final int DROP_COLLECT_RANGE = 3;
    private static final float AXE_LOW_DURABILITY_RATIO = 0.10f;
    private static final int CACHE_TIMEOUT_TICKS = 100;

    private final Map<BlockPos, Long> unreachableCache = new HashMap<>();

    private BlockPos targetLog = null;
    private int repathTicks = 0;
    private int scanCooldown = 0;
    private int chopTimeForCurrentLog = 0;
    private int axeSlot = -1;
    private int idleTicks = 0;

    public WoodcutterGoal(Wolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        if (mixin.workingwolves$getCollarTier() <= 0) return false;
        if (!WolfBagHelper.hasWoodcuttingTool(mixin)) return false;
        return "idle".equals(mixin.workingwolves$getExpeditionState())
            && !wolf.isOrderedToSit()
            && wolf.getOwner() != null
            && wolf.distanceToSqr(wolf.getOwner()) <= 32.0 * 32.0;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        ((IWorkingWolf) (Object) wolf).workingwolves$applyNavBudget(COMPANION_RANGE * 2);
        repathTicks = 0;
        scanCooldown = 0;
        idleTicks = 0;
    }

    @Override
    public void stop() {
        targetLog = null;
        repathTicks = 0;
        axeSlot = -1;
        idleTicks = 0;
        resetChopProgress();
    }

    @Override
    public void tick() {
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        Level level = wolf.level();
        long gameTime = level.getGameTime();

        unreachableCache.values().removeIf(t -> gameTime - t > CACHE_TIMEOUT_TICKS);

        if (!hasValidAxe(mixin)) return;

        BlockPos miningPos = mixin.workingwolves$getMiningPos();
        if (miningPos != null) {
            tickChopProgress(level, mixin, miningPos);
            return;
        }

        if (wolf.getOwner() != null && wolf.distanceToSqr(wolf.getOwner()) > 32.0 * 32.0) return;

        scanCooldown--;
        if (scanCooldown <= 0) {
            scanCooldown = LOG_SCAN_COOLDOWN;
            scanForLogs(level, mixin, gameTime);
        }

        if (targetLog != null) {
            tickLogApproach(level);
            return;
        }

        // Stay near owner when idle
        idleTicks++;
        if (wolf.getOwner() != null) {
            if (idleTicks >= 600) {
                wolf.getNavigation().moveTo(wolf.getOwner(), SPEED);
            } else if (wolf.distanceToSqr(wolf.getOwner()) > 8.0 * 8.0) {
                wolf.getNavigation().moveTo(wolf.getOwner(), SPEED);
            }
        }
    }

    private void tickChopProgress(Level level, IWorkingWolf mixin, BlockPos chopPos) {
        BlockState state = level.getBlockState(chopPos);
        if (!isLog(state)) {
            collectDropsAt(level, chopPos, mixin);
            resetChopProgress();
            targetLog = null;
            return;
        }

        if (wolf.blockPosition().distSqr(chopPos) > CHOP_DRIFT_DIST_SQ) {
            resetChopProgress();
            targetLog = chopPos;
            return;
        }

        int progress = mixin.workingwolves$getMiningProgress() + 1;
        mixin.workingwolves$setMiningProgress(progress);

        double wobble = Math.sin(progress * 0.4) * 0.4;
        wolf.getLookControl().setLookAt(
            chopPos.getX() + 0.5 + wobble,
            chopPos.getY() + 0.5,
            chopPos.getZ() + 0.5);

        if (level instanceof ServerLevel serverLevel && progress % 5 == 0) {
            BlockState logState = level.getBlockState(chopPos);
            double yawRad = wolf.yBodyRot * (Math.PI / 180.0);
            double forwardX = -Math.sin(yawRad) * 0.6;
            double forwardZ = Math.cos(yawRad) * 0.6;
            serverLevel.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, logState),
                wolf.getX() + forwardX, wolf.getY() + wolf.getEyeHeight() - 0.15, wolf.getZ() + forwardZ,
                3, 0.15, 0.15, 0.15, 0.0);
        }

        level.destroyBlockProgress(wolf.getId(), chopPos, (int)((float) progress / chopTimeForCurrentLog * 10.0f));
        wolf.swing(net.minecraft.world.InteractionHand.MAIN_HAND);

        if (progress >= chopTimeForCurrentLog) {
            finishChopping(level, mixin, chopPos, state);
            resetChopProgress();
            targetLog = null;
        }
    }

    private void finishChopping(Level level, IWorkingWolf mixin, BlockPos logPos, BlockState state) {
        if (level instanceof ServerLevel serverLevel) {
            ItemStack tool = getAxe(mixin);
            List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(
                state, serverLevel, logPos, null, wolf, tool);
            for (ItemStack drop : drops) {
                level.addFreshEntity(new ItemEntity(level,
                    logPos.getX() + 0.5, logPos.getY() + 0.5, logPos.getZ() + 0.5, drop));
            }
        }

        level.destroyBlock(logPos, false, wolf);
        level.levelEvent(2001, logPos, 0);

        ItemStack tool = getAxe(mixin);
        tool.hurtAndBreak(1, wolf, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        if (tool.isEmpty()) axeSlot = -1;

        collectDropsAt(level, logPos, mixin);
    }

    private void startChopping(IWorkingWolf mixin, BlockPos logPos, Level level) {
        mixin.workingwolves$setMiningPos(logPos);
        mixin.workingwolves$setMiningProgress(0);
        ItemStack axe = getAxe(mixin);
        mixin.workingwolves$displayMouthItem(axe.copy());
        BlockState state = level.getBlockState(logPos);
        float hardness = state.getDestroySpeed(level, logPos);
        float toolSpeed = Math.max(axe.getDestroySpeed(state), 1.0f);
        chopTimeForCurrentLog = Math.max(10, (int)(hardness * 3.0f / toolSpeed));
        idleTicks = 0;
    }

    private void tickLogApproach(Level level) {
        if (!level.isLoaded(targetLog) || !isLog(level.getBlockState(targetLog))) {
            targetLog = null;
            return;
        }

        if (wolf.blockPosition().distSqr(targetLog) <= CHOP_CLOSE_DIST_SQ) {
            startChopping((IWorkingWolf)(Object) wolf, targetLog, level);
            return;
        }

        repathTicks--;
        if (repathTicks <= 0) {
            Path path = wolf.getNavigation().createPath(targetLog, 3);
            if (path == null || !path.canReach()) {
                BlockPos standPos = WolfAIHelper.findAdjacentStandingPos(level, targetLog);
                if (standPos != null) path = wolf.getNavigation().createPath(standPos, 1);
            }
            if (path != null && path.canReach()) {
                wolf.getNavigation().moveTo(path, SPEED);
                repathTicks = REPATH_INTERVAL;
            } else {
                unreachableCache.put(targetLog, level.getGameTime());
                targetLog = null;
            }
        }
    }

    private void scanForLogs(Level level, IWorkingWolf mixin, long gameTime) {
        BlockPos center = wolf.getOwner() != null
            ? wolf.getOwner().blockPosition()
            : wolf.blockPosition();

        int r = COMPANION_RANGE;
        BlockPos minPos = center.offset(-r, -r / 2, -r);
        BlockPos maxPos = center.offset(r, r / 2, r);

        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(minPos, maxPos)) {
            if (!level.isLoaded(pos)) continue;
            if (unreachableCache.containsKey(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (isLog(state) && hasExposedFace(level, pos)) {
                candidates.add(pos.immutable());
            }
        }
        candidates.sort(Comparator.comparingDouble(center::distSqr));

        for (BlockPos pos : candidates.subList(0, Math.min(candidates.size(), 16))) {
            Path path = wolf.getNavigation().createPath(pos, 3);
            if (path != null && path.canReach()) {
                targetLog = pos;
                idleTicks = 0;
                repathTicks = REPATH_INTERVAL;
                wolf.getNavigation().moveTo(path, SPEED);
                return;
            }
            BlockPos standPos = WolfAIHelper.findAdjacentStandingPos(level, pos);
            if (standPos != null) {
                path = wolf.getNavigation().createPath(standPos, 3);
                if (path != null && path.canReach()) {
                    targetLog = pos;
                    idleTicks = 0;
                    repathTicks = REPATH_INTERVAL;
                    wolf.getNavigation().moveTo(path, SPEED);
                    return;
                }
            }
            unreachableCache.put(pos, gameTime);
        }
    }

    private boolean isLog(BlockState state) {
        return state.is(BlockTags.LOGS);
    }

    private boolean hasExposedFace(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) continue;
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (level.getBlockState(m).isAir()) return true;
                }
            }
        }
        return false;
    }

    private boolean hasValidAxe(IWorkingWolf mixin) {
        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();

        if (axeSlot >= 0 && axeSlot < bag.size()) {
            ItemStack stack = bag.get(axeSlot);
            if (stack.is(ItemTags.AXES)) {
                int remaining = stack.getMaxDamage() - stack.getDamageValue();
                if ((float) remaining / stack.getMaxDamage() > AXE_LOW_DURABILITY_RATIO) return true;
            }
        }

        for (int i = 0; i < bag.size(); i++) {
            ItemStack stack = bag.get(i);
            if (stack.is(ItemTags.AXES)) {
                int remaining = stack.getMaxDamage() - stack.getDamageValue();
                if ((float) remaining / stack.getMaxDamage() > AXE_LOW_DURABILITY_RATIO) {
                    axeSlot = i;
                    return true;
                }
            }
        }

        axeSlot = -1;
        return false;
    }

    private ItemStack getAxe(IWorkingWolf mixin) {
        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();
        if (axeSlot >= 0 && axeSlot < bag.size() && bag.get(axeSlot).is(ItemTags.AXES)) {
            return bag.get(axeSlot);
        }
        for (int i = 0; i < bag.size(); i++) {
            if (bag.get(i).is(ItemTags.AXES)) {
                axeSlot = i;
                return bag.get(i);
            }
        }
        return ItemStack.EMPTY;
    }

    private void resetChopProgress() {
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        mixin.workingwolves$setMiningPos(null);
        mixin.workingwolves$setMiningProgress(0);
        mixin.workingwolves$clearMouthItem();
        chopTimeForCurrentLog = 0;
    }

    private void collectDropsAt(Level level, BlockPos pos, IWorkingWolf mixin) {
        WolfBagHelper.collectDropsAt(level, pos, mixin, DROP_COLLECT_RANGE);
    }
}
