package grill24.workingwolves.blockentity.expedition;

import grill24.workingwolves.api.IWorkingWolf;
import grill24.workingwolves.blockentity.DogBedBlockEntity;
import grill24.workingwolves.inventory.WolfBagHelper;
import grill24.workingwolves.network.WorkingWolvesPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

public class ExpeditionSimulator {

    enum HazardLevel { LIGHT, MODERATE, SEVERE }

    private final DogBedBlockEntity bed;

    private String simState = "inactive";
    private boolean simHasMining = false;
    private boolean simHasHunting = false;
    private boolean simHasWoodcutting = false;
    private int simCollarTier = 0;
    private int simSatiation = 0;
    private int simArmorPoints = 0;
    private float simPickaxeSpeed = 1.0f;
    private int simFortune = 0;
    private boolean simSilkTouch = false;
    private int simLooting = 0;
    private float simAxeSpeed = 1.0f;
    private int simTotalTicks = 0;
    private int simElapsedTicks = 0;
    private int simEventTimer = 0;
    private int simInjuryCount = 0;
    private UUID simWolfUuid = null;
    private String simBiomeCategory = "other";
    private String simWoodBiome = "forest";
    private final List<ItemStack> simPendingLoot = new ArrayList<>();
    private final List<String> expeditionLog = new ArrayList<>();

    public ExpeditionSimulator(DogBedBlockEntity bed) {
        this.bed = bed;
    }

    // ======== Public API ========

    public void begin(IWorkingWolf mixin) {
        simCollarTier = mixin.workingwolves$getCollarTier();
        simTotalTicks = mixin.workingwolves$getExpeditionDuration();
        if (simTotalTicks <= 0) simTotalTicks = 12000;

        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();

        moveToBag(bag, s -> s.has(DataComponents.FOOD));
        moveToBag(bag, s -> s.is(ItemTags.PICKAXES));
        moveToBag(bag, s -> s.is(ItemTags.AXES));
        moveToBag(bag, WolfBagHelper::isHuntingWeapon);

        simSatiation = 0;
        simHasMining = false;
        simHasHunting = false;
        simHasWoodcutting = false;
        simPickaxeSpeed = 1.0f;
        simFortune = 0;
        simSilkTouch = false;
        simLooting = 0;
        simAxeSpeed = 1.0f;

        for (ItemStack stack : bag) {
            if (stack.isEmpty()) continue;
            if (stack.is(ItemTags.PICKAXES)) {
                simHasMining = true;
                float speed = stack.getDestroySpeed(Blocks.STONE.defaultBlockState());
                if (speed > simPickaxeSpeed) simPickaxeSpeed = speed;
                for (var entry : stack.getEnchantments().entrySet()) {
                    if (entry.getKey().is(Enchantments.FORTUNE)) simFortune = Math.max(simFortune, entry.getIntValue());
                    if (entry.getKey().is(Enchantments.SILK_TOUCH) && entry.getIntValue() > 0) simSilkTouch = true;
                }
            }
            if (WolfBagHelper.isHuntingWeapon(stack)) {
                simHasHunting = true;
                for (var entry : stack.getEnchantments().entrySet()) {
                    if (entry.getKey().is(Enchantments.LOOTING)) simLooting = Math.max(simLooting, entry.getIntValue());
                }
            }
            if (stack.is(ItemTags.AXES)) {
                simHasWoodcutting = true;
                float speed = stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState());
                if (speed > simAxeSpeed) simAxeSpeed = speed;
            }
        }

        Wolf wolf = (Wolf) (Object) mixin;
        simArmorPoints = (int) wolf.getAttributeValue(Attributes.ARMOR);
        simWolfUuid = wolf.getUUID();

        Level level = bed.getLevel();
        BlockPos pos = bed.getBlockPos();
        if (level != null) {
            simBiomeCategory = ExpeditionBiomeHelper.getBiomeCategory(level, pos);
            simWoodBiome = ExpeditionBiomeHelper.getWoodBiome(level, pos);
        }

        simState = "running";
        simElapsedTicks = 0;
        simEventTimer = 60 + (level != null ? level.getRandom().nextInt(40) : 20);
        simInjuryCount = 0;
        simPendingLoot.clear();
        expeditionLog.clear();

        String[] departureLines = {
            "Left the warmth of the bed.",
            "Out before dawn.",
            "Set out. The work won't find itself.",
            "Gone to work."
        };
        addLogLine(departureLines[level.getRandom().nextInt(departureLines.length)]);
        WorkingWolvesPackets.pushBedState(level, pos, bed);
        bed.setChanged();
    }

    public void tick(Level level) {
        if (!"running".equals(simState)) return;

        simElapsedTicks++;

        if (simElapsedTicks >= simTotalTicks) {
            complete(level, false, false);
            return;
        }

        simEventTimer--;
        if (simEventTimer <= 0) {
            rollEvent(level);
            simEventTimer = 60 + level.getRandom().nextInt(40);
            bed.setChanged();
        }
    }

    public boolean recall() {
        Level level = bed.getLevel();
        if (!isRunning() || level == null) return false;

        simState = "complete";
        String[] recallLines = {"Called back early.", "Recalled. Not finished. Going home.", "Whistle from home. Turning back."};
        addLogLine(recallLines[new Random().nextInt(recallLines.length)]);

        BlockPos pos = bed.getBlockPos();
        for (ItemStack stack : simPendingLoot) {
            ItemStack remaining = bed.tryInsert(stack);
            if (!remaining.isEmpty() && level instanceof ServerLevel sl) {
                ItemEntity drop = new ItemEntity(
                    sl, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, remaining);
                sl.addFreshEntity(drop);
            }
        }
        simPendingLoot.clear();
        triggerWolfArrival(level);
        WorkingWolvesPackets.pushBedState(level, pos, bed);
        bed.setChanged();
        return true;
    }

    public void abandonIfRunning() {
        Level level = bed.getLevel();
        if ("running".equals(simState) && level instanceof ServerLevel sl && simWolfUuid != null) {
            Entity entity = sl.getEntity(simWolfUuid);
            if (entity instanceof Wolf wolf) {
                wolf.setInvisible(false);
                wolf.setNoAi(false);
                wolf.setInvulnerable(false);
                IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
                mixin.workingwolves$setExpeditionState("idle");
                mixin.workingwolves$setBedPos(null);
                mixin.workingwolves$syncData();
            }
            BlockPos pos = bed.getBlockPos();
            for (ItemStack stack : simPendingLoot) {
                ItemEntity drop = new ItemEntity(
                    sl, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack.copy());
                sl.addFreshEntity(drop);
            }
            simPendingLoot.clear();
            simState = "inactive";
            addLogLine("Bed destroyed. Expedition abandoned.");
        }
    }

    public void save(ValueOutput output) {
        output.putString("sim_state", simState);
        output.putInt("sim_has_mining", simHasMining ? 1 : 0);
        output.putInt("sim_has_hunting", simHasHunting ? 1 : 0);
        output.putInt("sim_has_woodcutting", simHasWoodcutting ? 1 : 0);
        output.putInt("sim_collar_tier", simCollarTier);
        output.putInt("sim_satiation", simSatiation);
        output.putInt("sim_armor_points", simArmorPoints);
        output.putInt("sim_pickaxe_speed_x100", (int)(simPickaxeSpeed * 100));
        output.putInt("sim_fortune", simFortune);
        output.putInt("sim_silk_touch", simSilkTouch ? 1 : 0);
        output.putInt("sim_looting", simLooting);
        output.putInt("sim_axe_speed_x100", (int)(simAxeSpeed * 100));
        output.putInt("sim_total_ticks", simTotalTicks);
        output.putInt("sim_elapsed_ticks", simElapsedTicks);
        output.putInt("sim_event_timer", simEventTimer);
        output.putInt("sim_injury_count", simInjuryCount);
        if (simWolfUuid != null) output.putString("sim_wolf_uuid", simWolfUuid.toString());
        output.putString("sim_biome_category", simBiomeCategory);
        output.putString("sim_wood_biome", simWoodBiome);
        output.putString("expedition_log", String.join("\n", expeditionLog));
        if (!simPendingLoot.isEmpty()) {
            output.store("sim_pending_loot", ItemStack.OPTIONAL_CODEC.listOf(), simPendingLoot);
        }
    }

    public void load(ValueInput input) {
        simState = input.getStringOr("sim_state", "inactive");
        simHasMining = input.getIntOr("sim_has_mining", 0) != 0;
        simHasHunting = input.getIntOr("sim_has_hunting", 0) != 0;
        simHasWoodcutting = input.getIntOr("sim_has_woodcutting", 0) != 0;
        simCollarTier = input.getIntOr("sim_collar_tier", 0);
        simSatiation = input.getIntOr("sim_satiation", 0);
        simArmorPoints = input.getIntOr("sim_armor_points", 0);
        simPickaxeSpeed = input.getIntOr("sim_pickaxe_speed_x100", 100) / 100.0f;
        simFortune = input.getIntOr("sim_fortune", 0);
        simSilkTouch = input.getIntOr("sim_silk_touch", 0) != 0;
        simLooting = input.getIntOr("sim_looting", 0);
        simAxeSpeed = input.getIntOr("sim_axe_speed_x100", 100) / 100.0f;
        simTotalTicks = input.getIntOr("sim_total_ticks", 0);
        simElapsedTicks = input.getIntOr("sim_elapsed_ticks", 0);
        simEventTimer = input.getIntOr("sim_event_timer", 0);
        simInjuryCount = input.getIntOr("sim_injury_count", 0);
        String wolfUuidStr = input.getStringOr("sim_wolf_uuid", "");
        simWolfUuid = wolfUuidStr.isEmpty() ? null : UUID.fromString(wolfUuidStr);
        simBiomeCategory = input.getStringOr("sim_biome_category", "other");
        simWoodBiome = input.getStringOr("sim_wood_biome", "forest");

        String logStr = input.getStringOr("expedition_log", "");
        expeditionLog.clear();
        if (!logStr.isEmpty()) {
            for (String line : logStr.split("\n", -1)) {
                expeditionLog.add(line);
            }
        }

        simPendingLoot.clear();
        input.read("sim_pending_loot", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(simPendingLoot::addAll);
    }

    // ======== Public accessors ========

    public String getState() { return simState; }
    public boolean isRunning() { return "running".equals(simState); }
    public int getElapsedTicks() { return simElapsedTicks; }
    public int getTotalTicks() { return simTotalTicks; }
    public int getCollarTier() { return simCollarTier; }
    public boolean hasMining() { return simHasMining; }
    public boolean hasHunting() { return simHasHunting; }
    public boolean hasWoodcutting() { return simHasWoodcutting; }
    public List<String> getLog() { return expeditionLog; }

    // ======== Package-private accessors (used by handlers) ========

    void addLogLine(String line) {
        expeditionLog.add(line);
        Level level = bed.getLevel();
        if (level != null) {
            WorkingWolvesPackets.pushJournalLine(level, bed.getBlockPos(), line, simElapsedTicks, simTotalTicks);
        }
        bed.setChanged();
    }

    List<ItemStack> getPendingLoot() { return simPendingLoot; }
    void addPendingLoot(ItemStack stack) { simPendingLoot.add(stack); }

    void adjustSatiation(int delta) { simSatiation += delta; }
    int getSatiation() { return simSatiation; }

    void incrementInjury() { simInjuryCount++; }
    int getInjuryCount() { return simInjuryCount; }

    int getArmorPoints() { return simArmorPoints; }
    UUID getWolfUuid() { return simWolfUuid; }
    String getBiomeCategory() { return simBiomeCategory; }
    String getWoodBiome() { return simWoodBiome; }
    int getLooting() { return simLooting; }
    int getFortune() { return simFortune; }
    boolean isSilkTouch() { return simSilkTouch; }
    float getPickaxeSpeed() { return simPickaxeSpeed; }
    float getAxeSpeed() { return simAxeSpeed; }
    BlockPos getBlockPos() { return bed.getBlockPos(); }

    void setPickaxeSpeed(float v) { simPickaxeSpeed = v; }
    void setFortune(int v) { simFortune = v; }
    void setSilkTouch(boolean v) { simSilkTouch = v; }
    void setAxeSpeed(float v) { simAxeSpeed = v; }

    void complete(Level level, boolean failed, boolean death) {
        if (!"running".equals(simState)) return;
        simState = "complete";

        BlockPos pos = bed.getBlockPos();
        if (death) {
            String[] deathLines = {"Didn't come back.", "Gone.", "The expedition ended."};
            addLogLine(deathLines[level.getRandom().nextInt(deathLines.length)]);
            if (level instanceof ServerLevel sl) {
                Entity entity = sl.getEntity(simWolfUuid);
                if (entity instanceof Wolf wolf) {
                    for (ItemStack stack : simPendingLoot) {
                        ItemEntity drop = new ItemEntity(
                            sl, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack.copy());
                        sl.addFreshEntity(drop);
                    }
                    wolf.setInvulnerable(false);
                    wolf.hurt(sl.damageSources().genericKill(), Float.MAX_VALUE);
                }
            }
            simPendingLoot.clear();
        } else if (failed) {
            String[] failLines = {
                "Came back with nothing. Sat by the bed for a long time.",
                "Came back empty. Did not explain.",
                "Nothing to show. Nothing to say."
            };
            addLogLine(failLines[level.getRandom().nextInt(failLines.length)]);
            simPendingLoot.clear();
            triggerWolfArrival(level);
        } else {
            String[] successLines = {
                "Home. Bag heavy.",
                "Came back slower than expected. Came back.",
                "Long way. Worth it.",
                "The familiar smell of home."
            };
            addLogLine(successLines[level.getRandom().nextInt(successLines.length)]);
            for (ItemStack stack : simPendingLoot) {
                ItemStack remaining = bed.tryInsert(stack);
                if (!remaining.isEmpty()) {
                    if (level instanceof ServerLevel sl) {
                        ItemEntity drop = new ItemEntity(
                            sl, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, remaining);
                        sl.addFreshEntity(drop);
                    }
                }
            }
            simPendingLoot.clear();
            triggerWolfArrival(level);
        }

        WorkingWolvesPackets.pushBedState(level, pos, bed);
        bed.setChanged();
    }

    void applyHazardCost(Level level, Random rng, HazardLevel hl) {
        simSatiation -= switch (hl) {
            case LIGHT -> 2;
            case MODERATE -> 4;
            case SEVERE -> 3;
        };

        if (hl == HazardLevel.SEVERE) {
            boolean armorAbsorbs = simArmorPoints >= 8 || (simArmorPoints >= 4 && rng.nextFloat() < 0.5f);
            if (!armorAbsorbs) {
                simInjuryCount++;
                if (simInjuryCount >= 3) {
                    boolean death = simArmorPoints < 4 && rng.nextFloat() < 0.12f;
                    complete(level, true, death);
                    return;
                }
            }
            if (!simPendingLoot.isEmpty() && rng.nextFloat() < 0.25f) {
                simPendingLoot.remove(rng.nextInt(simPendingLoot.size()));
                addLogLine("Something fell. No time to go back for it.");
            }
        }

        if (simSatiation <= 0) {
            int nutrition = eatFoodFromWolfBag(level);
            if (nutrition > 0) {
                simSatiation += nutrition;
            } else {
                simInjuryCount++;
                if (simInjuryCount >= 3) {
                    boolean death = simArmorPoints < 4 && rng.nextFloat() < 0.12f;
                    complete(level, true, death);
                    return;
                }
                String[] lowLines = {
                    "Running low. Pushing on.",
                    "Leg hurts. Has hurt before.",
                    "Supplies thin. The work is not.",
                    "Worse shape than yesterday. Yesterday is not today.",
                    "Something went wrong back there. Not dwelling on it."
                };
                addLogLine(lowLines[level.getRandom().nextInt(lowLines.length)]);
            }
        }
    }

    // ======== Package-private static helpers ========

    static HazardLevel pickHazardLevel(int zone, Random rng) {
        int roll = rng.nextInt(100);
        return switch (zone) {
            case 0  -> roll < 75 ? HazardLevel.LIGHT : roll < 97 ? HazardLevel.MODERATE : HazardLevel.SEVERE;
            case 1  -> roll < 48 ? HazardLevel.LIGHT : roll < 88 ? HazardLevel.MODERATE : HazardLevel.SEVERE;
            default -> roll < 30 ? HazardLevel.LIGHT : roll < 75 ? HazardLevel.MODERATE : HazardLevel.SEVERE;
        };
    }

    // ======== Private helpers ========

    private void rollEvent(Level level) {
        float progress = (float) simElapsedTicks / Math.max(simTotalTicks, 1);
        int zone = progress < 0.25f ? 0 : progress < 0.75f ? 1 : 2;
        Random rng = new Random(level.getGameTime() + simElapsedTicks);

        if (rng.nextFloat() < 0.025f && RareEventHandler.roll(level, zone, rng, this)) return;

        int roleCount = (simHasMining ? 1 : 0) + (simHasHunting ? 1 : 0) + (simHasWoodcutting ? 1 : 0);
        if (roleCount >= 2 && rng.nextFloat() < 0.12f && RareEventHandler.rollCrossRole(level, rng, this)) return;

        if (roleCount == 0) return;
        int roll = rng.nextInt(roleCount);
        int idx = 0;
        if (simHasHunting && idx++ == roll) { HunterEventHandler.rollEvent(level, zone, this); return; }
        if (simHasMining && idx++ == roll) { MinerEventHandler.rollEvent(level, zone, this); return; }
        if (simHasWoodcutting) { WoodcutterEventHandler.rollEvent(level, zone, this); }
    }

    private void triggerWolfArrival(Level level) {
        if (!(level instanceof ServerLevel sl)) return;
        if (simWolfUuid == null) return;

        Entity entity = sl.getEntity(simWolfUuid);
        if (!(entity instanceof Wolf wolf)) return;

        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        BlockPos pos = bed.getBlockPos();

        Random rng = new Random(level.getGameTime());
        float angle = rng.nextFloat() * 2 * (float) Math.PI;
        int dist = 10 + rng.nextInt(5);
        int arrX = pos.getX() + (int) (Math.cos(angle) * dist);
        int arrZ = pos.getZ() + (int) (Math.sin(angle) * dist);
        int arrY = pos.getY();
        for (int dy = 3; dy >= -3; dy--) {
            BlockPos check = new BlockPos(arrX, arrY + dy, arrZ);
            if (level.getBlockState(check.below()).isSolid() && level.getBlockState(check).isAir()) {
                arrY = check.getY();
                break;
            }
        }

        wolf.teleportTo(arrX + 0.5, arrY, arrZ + 0.5);
        wolf.setInvisible(false);
        wolf.setNoAi(false);
        wolf.setInvulnerable(false);

        mixin.workingwolves$setExpeditionState("returning");
        wolf.setOrderedToSit(false);
        mixin.workingwolves$syncData();

        String[] arrivalLines = {"Back at the bed.", "Home.", "Found the way back."};
        addLogLine(arrivalLines[new Random(level.getGameTime()).nextInt(arrivalLines.length)]);
    }

    private int eatFoodFromWolfBag(Level level) {
        if (!(level instanceof ServerLevel sl)) return 0;
        Entity entity = sl.getEntity(simWolfUuid);
        if (!(entity instanceof Wolf wolf)) return 0;
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();

        for (int i = 0; i < bag.size(); i++) {
            ItemStack stack = bag.get(i);
            if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) {
                var food = stack.get(DataComponents.FOOD);
                int nutrition = food != null ? food.nutrition() : 4;
                stack.shrink(1);
                if (stack.isEmpty()) bag.set(i, ItemStack.EMPTY);
                return nutrition;
            }
        }
        return 0;
    }

    private void moveToBag(NonNullList<ItemStack> bag, Predicate<ItemStack> filter) {
        int bedSize = bed.getContainerSize();
        for (int i = 0; i < bedSize; i++) {
            ItemStack bedStack = bed.getItem(i);
            if (bedStack.isEmpty() || !filter.test(bedStack)) continue;

            ItemStack remaining = bedStack.copy();
            for (int j = 0; j < bag.size() && !remaining.isEmpty(); j++) {
                ItemStack bagStack = bag.get(j);
                if (!bagStack.isEmpty() && ItemStack.isSameItemSameComponents(bagStack, remaining)) {
                    int space = bagStack.getMaxStackSize() - bagStack.getCount();
                    int move = Math.min(space, remaining.getCount());
                    if (move > 0) {
                        bagStack.grow(move);
                        remaining.shrink(move);
                    }
                }
            }
            for (int j = 0; j < bag.size() && !remaining.isEmpty(); j++) {
                if (bag.get(j).isEmpty()) {
                    bag.set(j, remaining.copy());
                    remaining = ItemStack.EMPTY;
                }
            }
            bed.setItem(i, remaining.isEmpty() ? ItemStack.EMPTY : remaining);
        }
    }
}
