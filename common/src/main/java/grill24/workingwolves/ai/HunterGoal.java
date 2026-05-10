package grill24.workingwolves.ai;

import grill24.workingwolves.Config;
import grill24.workingwolves.api.IWorkingWolf;
import grill24.workingwolves.inventory.WolfBagHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
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
 * Supports melee (swords/axes/mace), crossbow, and bow combat.
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

    // Ranged combat constants
    private static final float CROSSBOW_RANGE = 8.0f;
    private static final float BOW_RANGE = 15.0f;
    private static final int BOW_PULL_TICKS = 20;

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
    private int weaponSlot = -1;
    private boolean weaponEquipped = false;

    // Ranged combat state
    private boolean hasRangedWeapon = false;
    private boolean usingCrossbow = false;
    private int rangedState = 0; // 0=IDLE/UNCHARGED, 1=CHARGING/PULLING, 2=READY_DELAY/CHARGED, 3=FIRE
    private int rangedTimer = 0;

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
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        unequipWeapon(mixin);
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
            unequipWeapon(mixin);
            lastKillPos = target.blockPosition();
            collectTicks = 10;
            target = null;
            wolf.setTarget(null);
            return;
        }

        if (!weaponEquipped) {
            equipWeapon(mixin);
        }

        wolf.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // Delegate to ranged combat if equipped with a ranged weapon
        if (hasRangedWeapon) {
            tickRangedCombat(mixin, level);
            return;
        }

        // --- Melee path recalculation: vanilla MeleeAttackGoal pattern ---
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

        // --- Melee attack: separate cooldown, vanilla-style ---
        ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);
        if (ticksUntilNextAttack <= 0
            && wolf.isWithinMeleeAttackRange(target)
            && wolf.getSensing().hasLineOfSight(target)) {
            wolf.doHurtTarget((ServerLevel) wolf.level(), target);
            ticksUntilNextAttack = 20;
        }
    }

    private void tickRangedCombat(IWorkingWolf mixin, Level level) {
        // If target is too close, back away
        double distSq = wolf.distanceToSqr(target);
        if (distSq < 4.0 * 4.0) {
            Vec3 away = new Vec3(
                wolf.getX() - target.getX(),
                0,
                wolf.getZ() - target.getZ()
            ).normalize().scale(3);
            wolf.getNavigation().moveTo(
                wolf.getX() + away.x,
                wolf.getY(),
                wolf.getZ() + away.z,
                SPEED
            );
        } else {
            // Standard path recalculation toward target
            ticksUntilNextPathRecalculation = Math.max(ticksUntilNextPathRecalculation - 1, 0);
            if (ticksUntilNextPathRecalculation <= 0
                && (pathedTargetX == 0.0 && pathedTargetY == 0.0 && pathedTargetZ == 0.0
                    || target.distanceToSqr(pathedTargetX, pathedTargetY, pathedTargetZ) >= 1.0
                    || wolf.getRandom().nextFloat() < 0.05F)) {

                pathedTargetX = target.getX();
                pathedTargetY = target.getY();
                pathedTargetZ = target.getZ();
                ticksUntilNextPathRecalculation = PATH_RECALC_BASE_MIN + wolf.getRandom().nextInt(PATH_RECALC_BASE_MAX - PATH_RECALC_BASE_MIN + 1);

                if (!wolf.getNavigation().moveTo(target, SPEED)) {
                    ticksUntilNextPathRecalculation += 15;
                }
            }
        }

        // Ranged attack state machine
        if (usingCrossbow) {
            tickCrossbowAttack(mixin);
        } else {
            tickBowAttack(mixin);
        }
    }

    private void tickCrossbowAttack(IWorkingWolf mixin) {
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack handItem = wolf.getItemInHand(hand);

        switch (rangedState) {
            case 0: // UNCHARGED
                // Check ammo before loading
                if (!WolfBagHelper.consumeAmmo(mixin)) {
                    unequipWeapon(mixin);
                    return;
                }
                // Manually load crossbow with a single arrow
                handItem.set(DataComponents.CHARGED_PROJECTILES,
                    ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
                wolf.startUsingItem(hand);
                rangedState = 1; // CHARGING
                rangedTimer = 25;
                break;
            case 1: // CHARGING
                rangedTimer--;
                // Refresh mouth item from live crossbow so pull animation renders
                mixin.workingwolves$displayMouthItem(wolf.getItemInHand(hand).copy());
                if (rangedTimer <= 0) {
                    wolf.releaseUsingItem();
                    rangedState = 2; // CHARGED (ready delay)
                    rangedTimer = 20 + wolf.getRandom().nextInt(20);
                }
                break;
            case 2: // CHARGED — ready delay
                rangedTimer--;
                if (rangedTimer <= 0) {
                    rangedState = 3; // FIRE
                }
                break;
            case 3: // FIRE
                performRangedAttack(target, 1.6F);
                rangedState = 0; // UNCHARGED
                break;
        }
    }

    private void tickBowAttack(IWorkingWolf mixin) {
        switch (rangedState) {
            case 0: // IDLE
                wolf.startUsingItem(InteractionHand.MAIN_HAND);
                rangedState = 1; // PULLING
                rangedTimer = BOW_PULL_TICKS;
                break;
            case 1: // PULLING
                rangedTimer--;
                if (rangedTimer <= 0) {
                    // Check ammo before shooting
                    if (!WolfBagHelper.consumeAmmo(mixin)) {
                        unequipWeapon(mixin);
                        return;
                    }
                    wolf.stopUsingItem();
                    performRangedAttack(target, BowItem.getPowerForTime(BOW_PULL_TICKS));
                    rangedState = 0; // IDLE
                }
                break;
        }
    }

    private void performRangedAttack(LivingEntity target, float power) {
        Level level = wolf.level();
        InteractionHand hand = InteractionHand.MAIN_HAND;

        if (usingCrossbow) {
            ItemStack crossbowStack = wolf.getItemInHand(hand);
            if (crossbowStack.getItem() instanceof CrossbowItem crossbowItem && level instanceof ServerLevel serverLevel) {
                crossbowItem.performShooting(level, wolf, hand, crossbowStack, 1.6F,
                    14 - serverLevel.getDifficulty().getId() * 4, target);
            }
        } else {
            ItemStack bowItem = wolf.getItemInHand(hand);
            ItemStack arrowStack = new ItemStack(Items.ARROW);
            AbstractArrow arrow = ProjectileUtil.getMobArrow(wolf, arrowStack, power, bowItem);
            double dx = target.getX() - wolf.getX();
            double dy = target.getY(0.333) - arrow.getY();
            double dz = target.getZ() - wolf.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (level instanceof ServerLevel sl) {
                Projectile.spawnProjectileUsingShoot(arrow, sl, arrowStack, dx, dy + dist * 0.2, dz, 1.6F,
                    14 - sl.getDifficulty().getId() * 4);
            }
            wolf.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (wolf.getRandom().nextFloat() * 0.4F + 0.8F));
        }
    }

    private void startRetreating(int duration) {
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        unequipWeapon(mixin);
        isRetreating = true;
        retreatTicks = duration;
        target = null;
        wolf.setTarget(null);
    }

    private void scanForTarget(Level level, IWorkingWolf mixin) {
        ItemStack filterStack = mixin.workingwolves$getFilterItem();
        Predicate<Monster> predicate = getMobFilter(filterStack);

        // Determine scan range: use ranged weapon range if a ranged weapon is in the bag
        int scanRange = Config.hunterScanRange;
        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();
        for (ItemStack stack : bag) {
            if (stack.is(Items.CROSSBOW)) {
                scanRange = (int) Math.ceil(CROSSBOW_RANGE);
                break;
            }
            if (stack.is(Items.BOW)) {
                scanRange = (int) Math.ceil(BOW_RANGE);
                break;
            }
        }

        List<Monster> mobs = level.getEntitiesOfClass(Monster.class,
            new AABB(wolf.blockPosition()).inflate(scanRange),
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

    // ======== Weapon handling ========

    /**
     * Finds the first weapon in the bag (in slot order). Checks crossbow, bow, then melee.
     * Sets hasRangedWeapon and usingCrossbow flags accordingly.
     */
    private ItemStack findWeapon(IWorkingWolf mixin) {
        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();

        // Check cached slot first
        if (weaponSlot >= 0 && weaponSlot < bag.size()) {
            ItemStack cached = bag.get(weaponSlot);
            if (cached.is(Items.CROSSBOW)) {
                hasRangedWeapon = true;
                usingCrossbow = true;
                return cached;
            }
            if (cached.is(Items.BOW)) {
                hasRangedWeapon = true;
                usingCrossbow = false;
                return cached;
            }
            if (WolfBagHelper.isMeleeWeapon(cached)) {
                hasRangedWeapon = false;
                return cached;
            }
        }

        // Full scan in slot order: crossbow → bow → melee
        for (int i = 0; i < bag.size(); i++) {
            ItemStack stack = bag.get(i);
            if (stack.is(Items.CROSSBOW)) {
                weaponSlot = i;
                hasRangedWeapon = true;
                usingCrossbow = true;
                return stack;
            }
            if (stack.is(Items.BOW)) {
                weaponSlot = i;
                hasRangedWeapon = true;
                usingCrossbow = false;
                return stack;
            }
            if (WolfBagHelper.isMeleeWeapon(stack)) {
                weaponSlot = i;
                hasRangedWeapon = false;
                return stack;
            }
        }

        weaponSlot = -1;
        hasRangedWeapon = false;
        return ItemStack.EMPTY;
    }

    /**
     * Searches the bag for a melee weapon specifically. Used as fallback when a ranged
     * weapon is found but no ammo is available.
     */
    private ItemStack findMeleeWeaponFromBag(IWorkingWolf mixin) {
        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();
        for (int i = 0; i < bag.size(); i++) {
            if (WolfBagHelper.isMeleeWeapon(bag.get(i))) {
                weaponSlot = i;
                hasRangedWeapon = false;
                usingCrossbow = false;
                return bag.get(i);
            }
        }
        weaponSlot = -1;
        return ItemStack.EMPTY;
    }

    private void equipWeapon(IWorkingWolf mixin) {
        if (wolf.level().isClientSide()) return;
        ItemStack weapon = findWeapon(mixin);
        if (weapon.isEmpty()) return;

        // If ranged weapon is found but no ammo, fall back to melee
        if (hasRangedWeapon) {
            if (WolfBagHelper.findAmmo(mixin).isEmpty()) {
                ItemStack melee = findMeleeWeaponFromBag(mixin);
                if (melee.isEmpty()) return;
                weapon = melee;
            }
        }

        // Equip in main hand
        wolf.setItemSlot(EquipmentSlot.MAINHAND, weapon.copy());
        bagRemove(weaponSlot, mixin);
        mixin.workingwolves$displayMouthItem(weapon.copy());
        weaponEquipped = true;

        // For crossbow, start the charging cycle
        if (hasRangedWeapon && usingCrossbow) {
            wolf.startUsingItem(InteractionHand.MAIN_HAND);
        }
    }

    private void unequipWeapon(IWorkingWolf mixin) {
        if (!weaponEquipped) return;
        weaponEquipped = false;
        hasRangedWeapon = false;
        usingCrossbow = false;
        rangedState = 0;
        rangedTimer = 0;

        if (wolf.level().isClientSide()) return;

        // Stop any ongoing item use
        if (wolf.isUsingItem()) {
            wolf.stopUsingItem();
        }

        ItemStack handItem = wolf.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!handItem.isEmpty()) {
            WolfBagHelper.addToBag(mixin.workingwolves$getBagInventory(), handItem);
        }
        wolf.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        mixin.workingwolves$clearMouthItem();
    }

    private static void bagRemove(int slot, IWorkingWolf mixin) {
        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();
        if (slot >= 0 && slot < bag.size()) {
            bag.set(slot, ItemStack.EMPTY);
        }
    }
}
