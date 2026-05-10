package grill24.workingwolves.ai;

import grill24.workingwolves.Config;
import grill24.workingwolves.WorkingWolves;
import grill24.workingwolves.api.IWorkingWolf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Priority 2 goal. Active when: collarTier > 0, class is "miner", expeditionState is "active".
 * Scans for ores within 12 blocks, mines them at half player speed using a pickaxe from the bag.
 * Progressively explores outward from the dispatch point when no ores are found nearby,
 * tracking visited regions to avoid circling.
 */
public class MinerGoal extends Goal {
    private final Wolf wolf;
    private static final double SPEED = 1.0;
    private static final double MINING_CLOSE_DIST_SQ = 3.5 * 3.5;
    private static final double MINING_DRIFT_DIST_SQ = 5.0 * 5.0;
    private static final int REPATH_INTERVAL = 30;
    private static final int ORE_SCAN_COOLDOWN = 10;
    private static final int EXPLORE_SCAN_DELAY = 60;

    private int oreScanRange() { return Config.detectionRange; }
    private int exploreStepRange() { return Config.detectionRange * 2; }
    private static final float PICKAXE_LOW_DURABILITY_RATIO = 0.10f;
    private static final int DROP_COLLECT_RANGE = 3;
    private static final int CACHE_TIMEOUT_TICKS = 100; // 5s — short enough that wolf doesn't outrun its own cache

    private static final double CACHE_RECHECK_RATIO = 0.25; // Recheck if wolf is < half the cached distance

    private final Map<BlockPos, CacheEntry> unreachableCache = new HashMap<>();

    private record CacheEntry(long gameTime, double distSq) {}
    private final Set<ChunkPos> visitedChunks = new HashSet<>();

    private BlockPos targetOre = null;
    private BlockPos originPos = null;
    private int repathTicks = 0;
    private int scanCooldown = 0;
    private int exploreTicks = 0;
    private int mineTimeForCurrentOre = 0;
    private int pickaxeSlot = -1;
    private boolean isExploring = false;
    private BlockPos exploreTarget = null;

    public MinerGoal(Wolf wolf) {
        this.wolf = wolf;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        return mixin.workingwolves$getCollarTier() > 0
            && "miner".equals(mixin.workingwolves$getWolfClass())
            && "active".equals(mixin.workingwolves$getExpeditionState());
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        ((IWorkingWolf) (Object) wolf).workingwolves$applyNavBudget(Config.detectionRange);
        originPos = wolf.blockPosition().immutable();
        visitedChunks.clear();
        visitedChunks.add(ChunkPos.containing(originPos));
        repathTicks = 0;
        scanCooldown = 0;
        exploreTicks = 0;
    }

    @Override
    public void stop() {
        targetOre = null;
        isExploring = false;
        exploreTarget = null;
        repathTicks = 0;
        exploreTicks = 0;
        pickaxeSlot = -1;
        resetMiningProgress();
    }

    @Override
    public void tick() {
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        Level level = wolf.level();
        long gameTime = level.getGameTime();

        // Clean expired unreachable cache entries
        unreachableCache.values().removeIf(e -> gameTime - e.gameTime > CACHE_TIMEOUT_TICKS);

        // 1. Check expedition timer (startTime==0 means never dispatched — don't expire)
        long startTime = mixin.workingwolves$getExpeditionStartTime();
        if (startTime > 0 && gameTime - startTime >= mixin.workingwolves$getExpeditionDuration()) {
            mixin.workingwolves$setExpeditionState("returning");
            mixin.workingwolves$syncData();
            return;
        }

        // 2. Check pickaxe durability
        if (!hasValidPickaxe(mixin)) {
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

        // 4. If currently mining a block, tick progress
        BlockPos miningPos = mixin.workingwolves$getMiningPos();
        if (miningPos != null) {
            tickMiningProgress(level, mixin, miningPos);
            return;
        }

        // 5. Scan frequently (every 10 ticks = 0.5s) — fast enough to catch ores while moving
        scanCooldown--;
        if (scanCooldown <= 0) {
            scanCooldown = ORE_SCAN_COOLDOWN;
            scanForOres(level, mixin);
        }

        // 6. Handle pathfinding to target ore
        if (targetOre != null) {
            tickOreApproach(level, mixin);
            return;
        }

        // 7. Handle exploration movement
        if (isExploring) {
            tickExploring(level, mixin);
            return;
        }

        // 8. Idle — tick explore delay before venturing further
        exploreTicks++;
        if (exploreTicks >= EXPLORE_SCAN_DELAY) {
            exploreTicks = 0;
            startExploring(level);
        }
    }

    // ======== Mining ========

    private void tickMiningProgress(Level level, IWorkingWolf mixin, BlockPos miningPos) {
        BlockState state = level.getBlockState(miningPos);
        if (!isAnyOre(state)) {
            collectDropsAt(level, miningPos, mixin);
            resetMiningProgress();
            targetOre = null;
            return;
        }

        if (wolf.blockPosition().distSqr(miningPos) > MINING_DRIFT_DIST_SQ) {
            resetMiningProgress();
            targetOre = miningPos;
            return;
        }

        int progress = mixin.workingwolves$getMiningProgress() + 1;
        mixin.workingwolves$setMiningProgress(progress);

        if (level instanceof ServerLevel serverLevel && progress % 5 == 0) {
            BlockState oreState = level.getBlockState(miningPos);
            serverLevel.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, oreState),
                wolf.getX(), wolf.getY() + 0.5, wolf.getZ(),
                3, 0.2, 0.2, 0.2, 0.0);
        }

        level.destroyBlockProgress(wolf.getId(), miningPos, (int) ((float) progress / mineTimeForCurrentOre * 10.0f));
        wolf.swing(net.minecraft.world.InteractionHand.MAIN_HAND);

        if (progress >= mineTimeForCurrentOre) {
            finishMining(level, mixin, miningPos, state);
            resetMiningProgress();
            targetOre = null;
            exploreTicks = 0; // Reset explore delay — we just found something here
        }
    }

    private void tickOreApproach(Level level, IWorkingWolf mixin) {
        if (!level.isLoaded(targetOre) || !isAnyOre(level.getBlockState(targetOre))) {
            targetOre = null;
            return;
        }

        // Close enough to mine — start mining immediately, don't repath
        if (wolf.blockPosition().distSqr(targetOre) <= MINING_CLOSE_DIST_SQ) {
            startMining(mixin, targetOre, level);
            return;
        }

        // Only repath on timer — not on isDone(), which causes stutter loops
        // when the wolf arrives at an adjacent standing position
        repathTicks--;
        if (repathTicks <= 0) {
            Path path = wolf.getNavigation().createPath(targetOre, 3);
            if (path == null || !path.canReach()) {
                BlockPos standPos = findAdjacentStandingPos(level, targetOre);
                if (standPos != null) {
                    path = wolf.getNavigation().createPath(standPos, 1);
                }
            }
            if (path != null && path.canReach() && path.getTarget().distSqr(targetOre) <= MINING_CLOSE_DIST_SQ) {
                wolf.getNavigation().moveTo(path, SPEED);
                repathTicks = REPATH_INTERVAL;
            } else {
                unreachableCache.put(targetOre, new CacheEntry(level.getGameTime(), wolf.blockPosition().distSqr(targetOre)));
                targetOre = null;
            }
        }
    }

    private void finishMining(Level level, IWorkingWolf mixin, BlockPos orePos, BlockState state) {
        // Generate drops with full enchantment support (fortune, silk touch)
        if (level instanceof ServerLevel serverLevel) {
            ItemStack tool = getPickaxe(mixin);
            List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(
                state, serverLevel, orePos, null, wolf, tool);
            for (ItemStack drop : drops) {
                level.addFreshEntity(new ItemEntity(level,
                    orePos.getX() + 0.5, orePos.getY() + 0.5, orePos.getZ() + 0.5, drop));
            }
        }

        level.destroyBlock(orePos, false, wolf);
        level.levelEvent(2001, orePos, 0);

        // Unbreaking-aware tool damage
        ItemStack tool = getPickaxe(mixin);
        tool.hurtAndBreak(1, wolf, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        if (tool.isEmpty()) pickaxeSlot = -1;

        collectDropsAt(level, orePos, mixin);
    }

    private void startMining(IWorkingWolf mixin, BlockPos orePos, Level level) {
        mixin.workingwolves$setMiningPos(orePos);
        mixin.workingwolves$setMiningProgress(0);
        ItemStack pickaxe = getPickaxe(mixin);
        BlockState state = level.getBlockState(orePos);
        float hardness = state.getDestroySpeed(level, orePos);
        float toolSpeed = Math.max(pickaxe.getDestroySpeed(state), 1.0f);
        // Player mines at ticks ≈ hardness * 1.5 / speed. Wolf mines at half speed (×2).
        mineTimeForCurrentOre = Math.max(10, (int)(hardness * 3.0f / toolSpeed));
    }

    private ItemStack getPickaxe(IWorkingWolf mixin) {
        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();
        if (pickaxeSlot >= 0 && pickaxeSlot < bag.size() && isPickaxe(bag.get(pickaxeSlot))) {
            return bag.get(pickaxeSlot);
        }
        // Find any pickaxe
        for (int i = 0; i < bag.size(); i++) {
            if (isPickaxe(bag.get(i))) {
                pickaxeSlot = i;
                return bag.get(i);
            }
        }
        return ItemStack.EMPTY;
    }

    private void resetMiningProgress() {
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        mixin.workingwolves$setMiningPos(null);
        mixin.workingwolves$setMiningProgress(0);
        mineTimeForCurrentOre = 0;
    }

    // ======== Exploration ========

    private void startExploring(Level level) {
        BlockPos current = wolf.blockPosition();
        visitedChunks.add(ChunkPos.containing(current));

        // Pick a direction away from the origin, weighted toward unexplored chunks
        BlockPos target = pickExploreTarget(level, current);
        if (target != null) {
            isExploring = true;
            exploreTarget = target;
            exploreTicks = exploreStepRange() * 2;
            wolf.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), SPEED);
        }
    }

    private BlockPos pickExploreTarget(Level level, BlockPos from) {
        // If we have an origin, bias away from it. Otherwise just explore outward.
        BlockPos reference = originPos != null ? originPos : from;
        Vec3 away = new Vec3(from.getX() - reference.getX(), 0, from.getZ() - reference.getZ());
        if (away.lengthSqr() < 1.0) {
            // At origin — pick a random direction
            double angle = wolf.getRandom().nextDouble() * 2.0 * Math.PI;
            away = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        }
        away = away.normalize();

        // Try several distances and angles, picking the first that leads to an unexplored area
        for (int attempt = 0; attempt < 8; attempt++) {
            double angleOffset = (wolf.getRandom().nextDouble() - 0.5) * Math.PI * 0.5; // ±45°
            double dx = Math.cos(Math.atan2(away.z, away.x) + angleOffset);
            double dz = Math.sin(Math.atan2(away.z, away.x) + angleOffset);
            int dist = exploreStepRange() - 8 + wolf.getRandom().nextInt(16);

            BlockPos candidate = from.offset((int) (dx * dist), 0, (int) (dz * dist));

            // Find a solid ground position near the candidate
            BlockPos ground = findGround(level, candidate);
            if (ground != null && !visitedChunks.contains(ChunkPos.containing(ground))) {
                return ground;
            }
        }

        // Fallback: just pick a spot further out in the away direction
        BlockPos fallback = from.offset((int) (away.x * exploreStepRange()), 0, (int) (away.z * exploreStepRange()));
        return findGround(level, fallback);
    }

    private BlockPos findGround(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos mutable = pos.mutable();
        // Find solid ground from candidate y down to -64
        for (int y = pos.getY(); y > -64; y--) {
            mutable.setY(y);
            if (level.getBlockState(mutable).isSolid() && level.getBlockState(mutable.above()).isAir()) {
                return mutable.above().immutable();
            }
        }
        // Search upward from candidate
        for (int y = pos.getY(); y < 320; y++) {
            mutable.setY(y);
            if (level.getBlockState(mutable.below()).isSolid() && level.getBlockState(mutable).isAir()) {
                return mutable.immutable();
            }
        }
        return null;
    }

    private void tickExploring(Level level, IWorkingWolf mixin) {
        exploreTicks--;

        if (exploreTarget == null || wolf.blockPosition().distSqr(exploreTarget) <= 4.0 || exploreTicks <= 0) {
            isExploring = false;
            exploreTarget = null;
            return;
        }

        if (wolf.getNavigation().isDone()) {
            wolf.getNavigation().moveTo(exploreTarget.getX(), exploreTarget.getY(), exploreTarget.getZ(), SPEED);
        }
    }

    // ======== Ore scanning ========

    private void scanForOres(Level level, IWorkingWolf mixin) {
        BlockPos center = wolf.blockPosition();
        ItemStack filterStack = mixin.workingwolves$getFilterItem();
        long gameTime = level.getGameTime();

        int r = oreScanRange();
        BlockPos minPos = center.offset(-r, -r / 2, -r);
        BlockPos maxPos = center.offset(r, r / 2, r);

        // Collect candidate ores, sorted by distance (no pathfinding yet)
        List<BlockPos> candidates = new ArrayList<>();
        int cacheSkipped = 0;
        for (BlockPos pos : BlockPos.betweenClosed(minPos, maxPos)) {
            if (!level.isLoaded(pos)) continue;
            CacheEntry cached = unreachableCache.get(pos);
            if (cached != null) {
                double currentDist = wolf.blockPosition().distSqr(pos);
                // Recheck if wolf is significantly closer now than when cached
                if (currentDist <= MINING_CLOSE_DIST_SQ || currentDist < cached.distSq * CACHE_RECHECK_RATIO) {
                    unreachableCache.remove(pos);
                    // Fall through to add as candidate
                } else {
                    cacheSkipped++;
                    continue;
                }
            }
            BlockState state = level.getBlockState(pos);
            if (isTargetOre(state, filterStack) && hasExposedFace(level, pos)) {
                candidates.add(pos.immutable());
            }
        }
        if (!candidates.isEmpty() || cacheSkipped > 0) {
            WorkingWolves.LOGGER.info("Miner scan: {} candidates, {} cached, center={}",
                candidates.size(), cacheSkipped, center);
        }
        candidates.sort(Comparator.comparingDouble(center::distSqr));

        BlockPos bestPos = null;
        BlockPos bestStandPos = null;
        int checked = 0;
        for (BlockPos pos : candidates) {
            if (checked++ >= 16) break;
            Path path = wolf.getNavigation().createPath(pos, 3);
            if (path != null && path.canReach()) {
                bestPos = pos;
                bestStandPos = null; // Direct approach — no stand pos needed
                break;
            }
            // Try via an adjacent standing position (works for flush/embedded ores)
            BlockPos standPos = findAdjacentStandingPos(level, pos);
            if (standPos != null) {
                path = wolf.getNavigation().createPath(standPos, 3);
                if (path != null && path.canReach()) {
                    bestPos = pos;
                    bestStandPos = standPos;
                    break;
                }
            }
            unreachableCache.put(pos, new CacheEntry(gameTime, center.distSqr(pos)));
        }

        if (bestPos != null) {
            // Don't interrupt an in-progress approach to the same ore
            if (bestPos.equals(targetOre)) return;

            isExploring = false;
            exploreTarget = null;

            if (wolf.blockPosition().distSqr(bestPos) <= MINING_CLOSE_DIST_SQ) {
                startMining(mixin, bestPos, level);
                return;
            }

            Path path = null;
            if (bestStandPos != null) {
                path = wolf.getNavigation().createPath(bestStandPos, 3);
            } else {
                path = wolf.getNavigation().createPath(bestPos, 3);
            }
            if (path != null && path.canReach() && path.getTarget().distSqr(bestPos) <= MINING_CLOSE_DIST_SQ) {
                targetOre = bestPos;
                repathTicks = REPATH_INTERVAL;
                wolf.getNavigation().stop();
                wolf.getNavigation().moveTo(path, SPEED);
            } else {
                WorkingWolves.LOGGER.info("Miner unreachable: ore={} path={} canReach={} pathTarget={}",
                    bestPos, path != null, path != null ? path.canReach() : false,
                    path != null ? path.getTarget() : "null");
                unreachableCache.put(bestPos, new CacheEntry(gameTime, wolf.blockPosition().distSqr(bestPos)));
            }
        }
    }

    // ======== Pickaxe management ========

    private boolean hasValidPickaxe(IWorkingWolf mixin) {
        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();

        if (pickaxeSlot >= 0 && pickaxeSlot < bag.size()) {
            ItemStack stack = bag.get(pickaxeSlot);
            if (isPickaxe(stack)) {
                int remaining = stack.getMaxDamage() - stack.getDamageValue();
                if ((float) remaining / stack.getMaxDamage() > PICKAXE_LOW_DURABILITY_RATIO) return true;
            }
        }

        for (int i = 0; i < bag.size(); i++) {
            ItemStack stack = bag.get(i);
            if (isPickaxe(stack)) {
                int remaining = stack.getMaxDamage() - stack.getDamageValue();
                if ((float) remaining / stack.getMaxDamage() > PICKAXE_LOW_DURABILITY_RATIO) {
                    pickaxeSlot = i;
                    return true;
                }
            }
        }

        pickaxeSlot = -1;
        return false;
    }

    private boolean isPickaxe(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES);
    }

    // ======== Position helpers ========

    private BlockPos findAdjacentStandingPos(Level level, BlockPos orePos) {
        // Search all 26 neighboring positions for a standable spot (air with solid ground below)
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dz == 0 && dy == 0) continue; // Skip the ore itself
                    m.set(orePos.getX() + dx, orePos.getY() + dy, orePos.getZ() + dz);
                    if (level.getBlockState(m).isAir() && level.getBlockState(m.below()).isSolid()) {
                        return m.immutable();
                    }
                }
            }
        }
        return null;
    }

    // ======== Ore detection ========

    private boolean isTargetOre(BlockState state, ItemStack filterStack) {
        if (filterStack.isEmpty()) return isAnyOre(state);

        if (filterStack.is(Items.COAL)) return state.is(BlockTags.COAL_ORES);
        if (filterStack.is(Items.COPPER_INGOT) || filterStack.is(Items.RAW_COPPER)) return state.is(BlockTags.COPPER_ORES);
        if (filterStack.is(Items.IRON_INGOT) || filterStack.is(Items.RAW_IRON)) return state.is(BlockTags.IRON_ORES);
        if (filterStack.is(Items.LAPIS_LAZULI)) return state.is(BlockTags.LAPIS_ORES);
        if (filterStack.is(Items.GOLD_INGOT) || filterStack.is(Items.RAW_GOLD)) return state.is(BlockTags.GOLD_ORES);
        if (filterStack.is(Items.REDSTONE)) return state.is(BlockTags.REDSTONE_ORES);
        if (filterStack.is(Items.EMERALD)) return state.is(BlockTags.EMERALD_ORES);
        if (filterStack.is(Items.DIAMOND)) return state.is(BlockTags.DIAMOND_ORES);

        return isAnyOre(state);
    }

    private boolean hasExposedFace(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) continue; // Only 1-axis offsets
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (level.getBlockState(m).isAir()) return true;
                }
            }
        }
        return false;
    }

    private boolean isAnyOre(BlockState state) {
        return state.is(BlockTags.COAL_ORES) || state.is(BlockTags.COPPER_ORES)
            || state.is(BlockTags.IRON_ORES) || state.is(BlockTags.LAPIS_ORES)
            || state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.REDSTONE_ORES)
            || state.is(BlockTags.EMERALD_ORES) || state.is(BlockTags.DIAMOND_ORES);
    }


    // ======== Drop collection ========

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
