package grill24.workingwolves.blockentity;

import grill24.workingwolves.ModBlockEntityTypes;
import grill24.workingwolves.api.IWorkingWolf;
import grill24.workingwolves.inventory.WolfBagHelper;
import grill24.workingwolves.network.WorkingWolvesPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class DogBedBlockEntity extends BlockEntity implements Container {
    private final NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);

    private UUID assignedWolfUuid = null;
    private String assignedWolfName = null;

    // ======== Expedition simulation ========

    private String simState = "inactive"; // "inactive", "running", "complete"
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

    public DogBedBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.DOG_BED.value(), pos, state);
    }

    // ======== Container interface ========

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = items.get(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (stack.getCount() <= amount) {
            items.set(slot, ItemStack.EMPTY);
            setChanged();
            return stack;
        } else {
            ItemStack split = stack.split(amount);
            setChanged();
            return split;
        }
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    // ======== Persistence ========

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        input.read("items", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(list -> {
            for (int i = 0; i < list.size() && i < items.size(); i++) {
                items.set(i, list.get(i));
            }
        });
        String uuidStr = input.getStringOr("assigned_wolf", "");
        assignedWolfUuid = uuidStr.isEmpty() ? null : UUID.fromString(uuidStr);
        assignedWolfName = input.getStringOr("assigned_wolf_name", "");
        if (assignedWolfName.isEmpty()) assignedWolfName = null;

        // Simulation state
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
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("items", ItemStack.OPTIONAL_CODEC.listOf(), items);
        if (assignedWolfUuid != null) {
            output.putString("assigned_wolf", assignedWolfUuid.toString());
        }
        if (assignedWolfName != null) {
            output.putString("assigned_wolf_name", assignedWolfName);
        }

        // Simulation state
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
    }

    // ======== Assignment methods ========

    public void assignWolf(UUID wolfUuid, String wolfName) {
        this.assignedWolfUuid = wolfUuid;
        this.assignedWolfName = wolfName;
        setChanged();
    }

    public void unassignWolf() {
        this.assignedWolfUuid = null;
        this.assignedWolfName = null;
        setChanged();
    }

    public UUID getAssignedWolfUuid() {
        return assignedWolfUuid;
    }

    public String getAssignedWolfName() {
        return assignedWolfName;
    }

    public boolean hasWolf() {
        return assignedWolfUuid != null;
    }

    // ======== Inventory management ========

    public void dropContents(Level level, BlockPos pos) {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        items.clear();
        setChanged();
    }

    public ItemStack tryInsert(ItemStack stack) {
        return WolfBagHelper.tryInsert(this, stack);
    }

    @Override
    public void setRemoved() {
        if ("running".equals(simState) && this.level instanceof ServerLevel sl && simWolfUuid != null) {
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
            for (ItemStack stack : simPendingLoot) {
                ItemEntity drop = new ItemEntity(
                    sl, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack.copy());
                sl.addFreshEntity(drop);
            }
            simPendingLoot.clear();
            simState = "inactive";
            addLogLine("Bed destroyed. Expedition abandoned.");
        }
        super.setRemoved();
    }

    // ======== Simulation ========

    public void beginExpeditionSimulation(IWorkingWolf mixin) {
        simCollarTier = mixin.workingwolves$getCollarTier();
        simTotalTicks = mixin.workingwolves$getExpeditionDuration();
        if (simTotalTicks <= 0) simTotalTicks = 12000;

        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();

        // Move food from bed into wolf's bag
        moveToBag(bag, s -> s.has(DataComponents.FOOD));

        // Move pickaxes from bed into wolf's bag
        moveToBag(bag, s -> s.is(ItemTags.PICKAXES));

        // Move axes from bed into wolf's bag
        moveToBag(bag, s -> s.is(ItemTags.AXES));

        // Move hunting weapons from bed into wolf's bag
        moveToBag(bag, s -> WolfBagHelper.isHuntingWeapon(s));

        simSatiation = 0;

        // Detect roles from bag contents
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
        simArmorPoints = (int) wolf.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        simWolfUuid = wolf.getUUID();

        if (this.level != null) {
            simBiomeCategory = getBiomeCategory(this.level, this.worldPosition);
            simWoodBiome = getWoodBiome(this.level, this.worldPosition);
        }

        simState = "running";
        simElapsedTicks = 0;
        simEventTimer = 60 + (this.level != null ? this.level.getRandom().nextInt(40) : 20);
        simInjuryCount = 0;
        simPendingLoot.clear();
        expeditionLog.clear();

        addLogLine("Left the warmth of the bed.");
        setChanged();
    }

    private void moveToBag(NonNullList<ItemStack> bag, java.util.function.Predicate<ItemStack> filter) {
        for (int i = 0; i < items.size(); i++) {
            ItemStack bedStack = items.get(i);
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
            items.set(i, remaining.isEmpty() ? ItemStack.EMPTY : remaining);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DogBedBlockEntity be) {
        if (!"running".equals(be.simState)) return;

        be.simElapsedTicks++;

        if (be.simElapsedTicks >= be.simTotalTicks) {
            be.completeSimulation(level, false, false);
            return;
        }

        be.simEventTimer--;
        if (be.simEventTimer <= 0) {
            be.rollEvent(level);
            be.simEventTimer = 60 + level.getRandom().nextInt(40);
            be.setChanged();
        }
    }

    private void rollEvent(Level level) {
        float progress = (float) simElapsedTicks / Math.max(simTotalTicks, 1);
        int zone = progress < 0.25f ? 0 : progress < 0.75f ? 1 : 2;

        // Count active roles and pick one uniformly at random
        int roleCount = (simHasMining ? 1 : 0) + (simHasHunting ? 1 : 0) + (simHasWoodcutting ? 1 : 0);
        if (roleCount == 0) return;

        Random rng = new Random(level.getGameTime() + simElapsedTicks);
        int roll = rng.nextInt(roleCount);
        int idx = 0;
        if (simHasHunting && idx++ == roll) { rollHunterEvent(level, zone); return; }
        if (simHasMining && idx++ == roll) { rollMinerEvent(level, zone); return; }
        if (simHasWoodcutting) { rollWoodcutterEvent(level, zone); }
    }

    // ======== Hunter events ========

    private void rollHunterEvent(Level level, int zone) {
        Random rng = new Random(level.getGameTime() + simElapsedTicks + 1);
        int[] weights = switch (zone) {
            case 0 -> new int[]{50, 40, 10};
            case 1 -> new int[]{30, 45, 25};
            default -> new int[]{20, 35, 45};
        };
        int roll = rng.nextInt(100);

        if (roll < weights[0]) {
            String[] travelLines = {
                "Followed a scent through the trees.",
                "Something moved ahead. Paused. Continued.",
                "Tracks in the dirt.",
                "Dark hollow. Dripping stone.",
                "The forest grew quiet.",
                "Distant howl. Not ours."
            };
            addLogLine(travelLines[rng.nextInt(travelLines.length)]);
        } else if (roll < weights[0] + weights[1]) {
            rollHunterDiscovery(level, zone, rng);
        } else {
            rollHunterHazard(level, rng);
        }
    }

    private void rollHunterDiscovery(Level level, int zone, Random rng) {
        String mob = getMobForZone(zone, rng);

        switch (mob) {
            case "skeleton" -> {
                int bones = 1 + rng.nextInt(3) + simLooting;
                int arrows = rng.nextInt(2) + simLooting;
                if (bones > 0) simPendingLoot.add(new ItemStack(Items.BONE, bones));
                if (arrows > 0) simPendingLoot.add(new ItemStack(Items.ARROW, arrows));
                String[] lines = {"Skeleton, alone. Dispatched.", "Two skeletons. Hard chase."};
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "zombie" -> {
                int flesh = 1 + rng.nextInt(2) + simLooting;
                simPendingLoot.add(new ItemStack(Items.ROTTEN_FLESH, flesh));
                addLogLine("Zombie in the hollow. Easy work.");
            }
            case "spider" -> {
                int string = rng.nextInt(3) + simLooting;
                int eye = rng.nextInt(2) + simLooting;
                if (string > 0) simPendingLoot.add(new ItemStack(Items.STRING, string));
                if (eye > 0) simPendingLoot.add(new ItemStack(Items.SPIDER_EYE, eye));
                addLogLine("Spider overhead. Got the drop on it.");
            }
            case "creeper" -> {
                if (zone >= 1) {
                    int powder = rng.nextInt(2) + simLooting;
                    if (powder > 0) simPendingLoot.add(new ItemStack(Items.GUNPOWDER, powder));
                }
                addLogLine("Creeper, at distance. Waited it out.");
            }
            case "drowned" -> {
                if (zone >= 1 && rng.nextFloat() < 0.25f + simLooting * 0.1f) {
                    simPendingLoot.add(new ItemStack(Items.NAUTILUS_SHELL, 1));
                }
                addLogLine("Something cold in the water. Drowned.");
            }
            case "enderman" -> {
                if (zone >= 2) {
                    int pearls = rng.nextInt(2) + simLooting;
                    if (pearls > 0) simPendingLoot.add(new ItemStack(Items.ENDER_PEARL, pearls));
                }
                addLogLine("Enderman. Fast. Got lucky.");
            }
            case "blaze" -> {
                if (zone >= 2) {
                    int rods = rng.nextInt(3) + simLooting;
                    if (rods > 0) simPendingLoot.add(new ItemStack(Items.BLAZE_ROD, rods));
                }
                addLogLine("Heat ahead. Blaze, circling. Pushed through.");
            }
            case "wither_skeleton" -> {
                if (zone >= 2) {
                    int coal = rng.nextInt(2);
                    if (coal > 0) simPendingLoot.add(new ItemStack(Items.COAL, coal));
                    if (rng.nextFloat() < 0.05f + simLooting * 0.01f) {
                        simPendingLoot.add(new ItemStack(Items.WITHER_SKELETON_SKULL, 1));
                    }
                }
                addLogLine("Something in the dark. Much taller.");
            }
            default -> addLogLine("Something moved. Gone now.");
        }
    }

    private String getMobForZone(int zone, Random rng) {
        return switch (zone) {
            case 0 -> {
                String[] pool = {"skeleton", "zombie", "spider"};
                yield pool[rng.nextInt(pool.length)];
            }
            case 1 -> {
                String[] pool = {"skeleton", "zombie", "spider", "creeper", "drowned"};
                yield pool[rng.nextInt(pool.length)];
            }
            default -> {
                String[] pool = {"enderman", "wither_skeleton",
                    ("mountain".equals(simBiomeCategory) || "cave".equals(simBiomeCategory) || "other".equals(simBiomeCategory)) ? "blaze" : "enderman"};
                yield pool[rng.nextInt(pool.length)];
            }
        };
    }

    private void rollHunterHazard(Level level, Random rng) {
        String[] hazardLines = {
            "Three of them at once. Bit and held on.",
            "Took a hit. Kept moving.",
            "Cornered briefly. Found a way out.",
            "Lava nearby. Backed off.",
            "Pack of them. Retreated.",
            "Something big. Chose not to engage."
        };
        addLogLine(hazardLines[rng.nextInt(hazardLines.length)]);
        applyHazardCost(level, rng);
    }

    // ======== Miner events ========

    private void rollMinerEvent(Level level, int zone) {
        Random rng = new Random(level.getGameTime() + simElapsedTicks + 2);
        int[] weights = switch (zone) {
            case 0 -> new int[]{50, 40, 10};
            case 1 -> new int[]{30, 45, 25};
            default -> new int[]{20, 35, 45};
        };
        int roll = rng.nextInt(100);

        if (roll < weights[0]) {
            String[] travelLines = {
                "Narrow tunnel. Kept digging.",
                "Cave system ahead. Dripping water.",
                "Loose gravel above. Careful.",
                "Sound of water in the dark.",
                "Old mineshaft. Something glinted.",
                "Long corridor. Nothing yet."
            };
            addLogLine(travelLines[rng.nextInt(travelLines.length)]);
            if (!applyMinerDurability(level, 1)) {
                completeSimulation(level, false, false);
            }
        } else if (roll < weights[0] + weights[1]) {
            rollMinerDiscovery(level, zone, rng);
            if (!applyMinerDurability(level, 3)) {
                completeSimulation(level, false, false);
            }
        } else {
            rollMinerHazard(level, rng);
            if (!applyMinerDurability(level, 1)) {
                completeSimulation(level, false, false);
            }
        }
    }

    private void rollMinerDiscovery(Level level, int zone, Random rng) {
        float biomeMultiplier = ("mountain".equals(simBiomeCategory) || "cave".equals(simBiomeCategory)) ? 1.25f : 1.0f;

        String ore = switch (zone) {
            case 0 -> {
                String[] pool = {"coal", "iron", "flint"};
                yield pool[rng.nextInt(pool.length)];
            }
            case 1 -> {
                String[] pool = {"iron", "copper", "gold", "lapis"};
                yield pool[rng.nextInt(pool.length)];
            }
            default -> {
                String[] pool = {"gold", "redstone",
                    (simCollarTier >= 3 ? "diamond" : "redstone"),
                    "amethyst"};
                yield pool[rng.nextInt(pool.length)];
            }
        };

        float fortuneMultiplier = 1.0f + simFortune * 0.5f;

        switch (ore) {
            case "coal" -> {
                int base = 2 + rng.nextInt(5);
                int count = (int)(base * fortuneMultiplier * biomeMultiplier);
                simPendingLoot.add(new ItemStack(simSilkTouch ? Items.COAL_ORE : Items.COAL, Math.max(1, count)));
                String[] lines = {"Coal seam in the wall.", "Dark vein. Long work."};
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "iron" -> {
                int base = zone == 0 ? 1 + rng.nextInt(3) : 2 + rng.nextInt(4);
                int count = (int)(base * biomeMultiplier);
                simPendingLoot.add(new ItemStack(simSilkTouch ? Items.IRON_ORE : Items.RAW_IRON, Math.max(1, count)));
                String[] lines = {"Iron ore behind the stone.", "Caught scent of iron. Dug in."};
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "copper" -> {
                int base = 1 + rng.nextInt(4);
                int count = (int)(base * fortuneMultiplier * biomeMultiplier);
                simPendingLoot.add(new ItemStack(simSilkTouch ? Items.COPPER_ORE : Items.RAW_COPPER, Math.max(1, count)));
                String[] lines = {"Copper, oxidized green.", "Teal streak in the rock."};
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "gold" -> {
                int base = 1 + rng.nextInt(3);
                int count = (int)(base * biomeMultiplier);
                simPendingLoot.add(new ItemStack(simSilkTouch ? Items.GOLD_ORE : Items.RAW_GOLD, Math.max(1, count)));
                String[] lines = {"Gold glint. Careful extraction.", "A vein of gold. Rich pocket."};
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "lapis" -> {
                int base = 2 + rng.nextInt(7);
                int count = (int)(base * fortuneMultiplier * biomeMultiplier);
                simPendingLoot.add(new ItemStack(simSilkTouch ? Items.LAPIS_ORE : Items.LAPIS_LAZULI, Math.max(1, count)));
                String[] lines = {"Bright blue. Lapis.", "Lapis dust on the floor."};
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "redstone" -> {
                int base = 2 + rng.nextInt(7);
                int count = (int)(base * fortuneMultiplier * biomeMultiplier);
                simPendingLoot.add(new ItemStack(simSilkTouch ? Items.REDSTONE_ORE : Items.REDSTONE, Math.max(1, count)));
                String[] lines = {"Redstone dust, trickling.", "Deep red. Redstone vein."};
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "diamond" -> {
                int base = 1 + rng.nextInt(3);
                int count = (int)(base * fortuneMultiplier * biomeMultiplier);
                simPendingLoot.add(new ItemStack(simSilkTouch ? Items.DIAMOND_ORE : Items.DIAMOND, Math.max(1, count)));
                addLogLine("Diamond. Small vein. Heart pounding.");
            }
            case "amethyst" -> {
                int base = 1 + rng.nextInt(4);
                int count = (int)(base * fortuneMultiplier * biomeMultiplier);
                simPendingLoot.add(new ItemStack(Items.AMETHYST_SHARD, Math.max(1, count)));
                addLogLine("Purple crystals. Amethyst cluster.");
            }
            case "flint" -> {
                int base = 2 + rng.nextInt(3);
                simPendingLoot.add(new ItemStack(Items.FLINT, Math.max(1, base)));
                addLogLine("Gravel pocket. Sorted through it.");
            }
            default -> addLogLine("Odd mineral. Took what was worth taking.");
        }
    }

    private void rollMinerHazard(Level level, Random rng) {
        String[] hazardLines = {
            "Lava pocket. Retreated fast.",
            "Cave-in above. Dust settled. Fine.",
            "Mob in the tunnel. Skirmish.",
            "Deep water ahead. Found another way.",
            "Lost the vein. Took time to reorient."
        };
        addLogLine(hazardLines[rng.nextInt(hazardLines.length)]);
        applyHazardCost(level, rng);
    }

    // ======== Woodcutter events ========

    private void rollWoodcutterEvent(Level level, int zone) {
        Random rng = new Random(level.getGameTime() + simElapsedTicks + 3);
        int[] weights = switch (zone) {
            case 0 -> new int[]{50, 40, 10};
            case 1 -> new int[]{30, 45, 25};
            default -> new int[]{20, 35, 45};
        };
        int roll = rng.nextInt(100);

        if (roll < weights[0]) {
            String[] travelLines = {
                "Deep into the trees.",
                "Bark and pine needles underfoot.",
                "Sound of wind through the canopy.",
                "A clearing, then more forest.",
                "Old growth. Roots everywhere.",
                "Light through the branches."
            };
            addLogLine(travelLines[rng.nextInt(travelLines.length)]);
            if (!applyWoodcutterDurability(level, 1)) {
                completeSimulation(level, false, false);
            }
        } else if (roll < weights[0] + weights[1]) {
            rollWoodcutterDiscovery(level, zone, rng);
            if (!applyWoodcutterDurability(level, 3)) {
                completeSimulation(level, false, false);
            }
        } else {
            rollWoodcutterHazard(level, rng);
            if (!applyWoodcutterDurability(level, 1)) {
                completeSimulation(level, false, false);
            }
        }
    }

    private void rollWoodcutterDiscovery(Level level, int zone, Random rng) {
        // Axe speed scales yield: iron axe (speed 6) = baseline 1.0x
        float axeMultiplier = Math.max(0.5f, simAxeSpeed / 6.0f);

        switch (simWoodBiome) {
            case "jungle" -> {
                int logs = Math.max(1, (int)((1 + rng.nextInt(4)) * axeMultiplier));
                simPendingLoot.add(new ItemStack(Items.JUNGLE_LOG, logs));
                if (rng.nextFloat() < 0.4f) simPendingLoot.add(new ItemStack(Items.BAMBOO, 1 + rng.nextInt(3)));
                if (rng.nextFloat() < 0.25f) simPendingLoot.add(new ItemStack(Items.COCOA_BEANS, 1 + rng.nextInt(2)));
                if (rng.nextFloat() < 0.2f) simPendingLoot.add(new ItemStack(Items.JUNGLE_SAPLING, 1));
                String[] lines = {"Dense jungle. Machete work.", "Vines and thick trunks.", "Found a good stand of jungle trees."};
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "dark_forest" -> {
                int logs = Math.max(1, (int)((1 + rng.nextInt(4)) * axeMultiplier));
                simPendingLoot.add(new ItemStack(Items.DARK_OAK_LOG, logs));
                if (rng.nextFloat() < 0.2f) simPendingLoot.add(new ItemStack(Items.DARK_OAK_SAPLING, 1));
                if (rng.nextFloat() < 0.15f) simPendingLoot.add(new ItemStack(Items.BROWN_MUSHROOM, 1 + rng.nextInt(2)));
                if (rng.nextFloat() < 0.15f) simPendingLoot.add(new ItemStack(Items.RED_MUSHROOM, 1));
                String[] lines = {"Dark canopy. Barely any light.", "Ancient dark oaks. Hard wood.", "Mushrooms at the base of every trunk."};
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "taiga" -> {
                int logs = Math.max(1, (int)((1 + rng.nextInt(4)) * axeMultiplier));
                simPendingLoot.add(new ItemStack(Items.SPRUCE_LOG, logs));
                if (rng.nextFloat() < 0.3f) simPendingLoot.add(new ItemStack(Items.SWEET_BERRIES, 1 + rng.nextInt(3)));
                if (rng.nextFloat() < 0.2f) simPendingLoot.add(new ItemStack(Items.SPRUCE_SAPLING, 1));
                if (rng.nextFloat() < 0.1f) simPendingLoot.add(new ItemStack(Items.STICK, 2 + rng.nextInt(4)));
                String[] lines = {"Spruce stands. Cold air.", "Tall conifers, quiet.", "Pine needles everywhere."};
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "savanna" -> {
                int logs = Math.max(1, (int)((1 + rng.nextInt(3)) * axeMultiplier));
                simPendingLoot.add(new ItemStack(Items.ACACIA_LOG, logs));
                if (rng.nextFloat() < 0.2f) simPendingLoot.add(new ItemStack(Items.ACACIA_SAPLING, 1));
                String[] lines = {"Scattered acacia. Flat light.", "Twisted trunks in the savanna heat.", "A lone acacia. Took what was there."};
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "cherry" -> {
                int logs = Math.max(1, (int)((1 + rng.nextInt(3)) * axeMultiplier));
                simPendingLoot.add(new ItemStack(Items.CHERRY_LOG, logs));
                if (rng.nextFloat() < 0.3f) simPendingLoot.add(new ItemStack(Items.CHERRY_SAPLING, 1));
                if (rng.nextFloat() < 0.4f) simPendingLoot.add(new ItemStack(Items.PINK_PETALS, 1 + rng.nextInt(3)));
                String[] lines = {"Petals drifting down.", "Cherry blossoms overhead.", "Beautiful grove. Worked carefully."};
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "mangrove" -> {
                int logs = Math.max(1, (int)((1 + rng.nextInt(3)) * axeMultiplier));
                simPendingLoot.add(new ItemStack(Items.MANGROVE_LOG, logs));
                if (rng.nextFloat() < 0.3f) simPendingLoot.add(new ItemStack(Items.MANGROVE_PROPAGULE, 1));
                if (rng.nextFloat() < 0.2f) simPendingLoot.add(new ItemStack(Items.MANGROVE_ROOTS, 1 + rng.nextInt(2)));
                String[] lines = {"Roots in the water.", "Tangled mangrove. Hard going.", "Salty air. Good timber."};
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            default -> { // "forest" and everything else
                int logs = Math.max(1, (int)((1 + rng.nextInt(4)) * axeMultiplier));
                boolean birch = rng.nextBoolean();
                simPendingLoot.add(new ItemStack(birch ? Items.BIRCH_LOG : Items.OAK_LOG, logs));
                if (rng.nextFloat() < 0.25f) simPendingLoot.add(new ItemStack(Items.APPLE, 1 + rng.nextInt(2)));
                if (rng.nextFloat() < 0.15f) simPendingLoot.add(new ItemStack(birch ? Items.BIRCH_SAPLING : Items.OAK_SAPLING, 1));
                String[] lines = {"Good stand of oak.", "Birch grove, white bark.", "Mixed forest. Decent timber.", "Quiet woods. Productive."};
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
        }
    }

    private void rollWoodcutterHazard(Level level, Random rng) {
        String[] hazardLines = {
            "Tree fell the wrong way. Close call.",
            "Beehive in the branches. Stings.",
            "Hostile mob in the undergrowth.",
            "Fog rolling in. Slowed down.",
            "Thorns and thick brush.",
            "Roots tangled the path. Lost time."
        };
        addLogLine(hazardLines[rng.nextInt(hazardLines.length)]);
        applyHazardCost(level, rng);
    }

    // ======== Shared hazard cost ========

    private void applyHazardCost(Level level, Random rng) {
        simSatiation -= 2;
        if (simSatiation <= 0) {
            int nutrition = eatFoodFromWolfBag(level);
            if (nutrition > 0) {
                simSatiation += nutrition;
            } else {
                simInjuryCount++;
                if (simInjuryCount >= 3) {
                    boolean death = simArmorPoints < 4 && rng.nextFloat() < 0.12f;
                    completeSimulation(level, true, death);
                    return;
                }
                addLogLine("Running low. Pushing on.");
            }
        }
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

    // ======== Pickaxe durability ========

    private static final int TOOL_LOW_THRESHOLD = 5;

    private boolean applyMinerDurability(Level level, int baseDamage) {
        return applyToolDurability(level, baseDamage, ItemTags.PICKAXES, "pickaxe",
            "No pickaxe left. Heading back.",
            "Pickaxe too precious to break. Heading back.",
            "Pickaxe shattered.",
            "Last pickaxe gone. Heading back.",
            "Switched to spare pickaxe.",
            "Pickaxe nearly done. Heading back.");
    }

    private boolean applyWoodcutterDurability(Level level, int baseDamage) {
        return applyToolDurability(level, baseDamage, ItemTags.AXES, "axe",
            "No axe left. Heading back.",
            "Axe too precious to break. Heading back.",
            "Axe handle shattered.",
            "Last axe gone. Heading back.",
            "Switched to spare axe.",
            "Axe nearly done. Heading back.");
    }

    private boolean applyToolDurability(Level level, int baseDamage,
            net.minecraft.tags.TagKey<net.minecraft.world.item.Item> toolTag,
            String toolName,
            String noToolMsg, String enchantedProtectMsg, String brokeMsg,
            String lastGoneMsg, String switchedMsg, String lowMsg) {
        if (!(level instanceof ServerLevel sl)) return true;
        Entity entity = sl.getEntity(simWolfUuid);
        if (!(entity instanceof Wolf wolf)) return true;
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();

        int bestSlot = -1;
        float bestSpeed = 0;
        BlockState testBlock = toolTag == ItemTags.PICKAXES
            ? Blocks.STONE.defaultBlockState()
            : Blocks.OAK_LOG.defaultBlockState();

        for (int i = 0; i < bag.size(); i++) {
            ItemStack stack = bag.get(i);
            if (!stack.isEmpty() && stack.is(toolTag)) {
                float speed = stack.getDestroySpeed(testBlock);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestSlot = i;
                }
            }
        }

        if (bestSlot < 0) {
            addLogLine(noToolMsg);
            return false;
        }

        ItemStack tool = bag.get(bestSlot);
        int unbreaking = 0;
        boolean isEnchanted = tool.isEnchanted();
        for (var entry : tool.getEnchantments().entrySet()) {
            if (entry.getKey().is(Enchantments.UNBREAKING)) {
                unbreaking = entry.getIntValue();
            }
        }

        int currentDamage = tool.getDamageValue();
        int maxDurability = tool.getMaxDamage();
        int remainingBefore = maxDurability - currentDamage;

        if (isEnchanted && remainingBefore <= 1) {
            if (hasSpare(bag, bestSlot, toolTag)) {
                switchToNextTool(bag, bestSlot, toolTag, testBlock, toolTag == ItemTags.PICKAXES);
                addLogLine(switchedMsg);
                return true;
            }
            addLogLine(enchantedProtectMsg);
            return false;
        }

        int actualDamage = 0;
        for (int d = 0; d < baseDamage; d++) {
            if (unbreaking == 0 || level.getRandom().nextInt(unbreaking + 1) == 0) {
                actualDamage++;
            }
        }
        if (actualDamage == 0) return true;

        int newDamage = currentDamage + actualDamage;

        if (isEnchanted && newDamage >= maxDurability) {
            tool.setDamageValue(maxDurability - 1);
            if (hasSpare(bag, bestSlot, toolTag)) {
                switchToNextTool(bag, bestSlot, toolTag, testBlock, toolTag == ItemTags.PICKAXES);
                addLogLine(switchedMsg);
                return true;
            }
            addLogLine(enchantedProtectMsg);
            return false;
        }

        if (newDamage >= maxDurability) {
            bag.set(bestSlot, ItemStack.EMPTY);
            addLogLine(brokeMsg);
            if (!switchToNextTool(bag, -1, toolTag, testBlock, toolTag == ItemTags.PICKAXES)) {
                addLogLine(lastGoneMsg);
                return false;
            }
            return true;
        }

        tool.setDamageValue(newDamage);

        int remainingAfter = maxDurability - newDamage;
        if (remainingAfter < TOOL_LOW_THRESHOLD && !hasSpare(bag, bestSlot, toolTag)) {
            addLogLine(lowMsg);
            return false;
        }

        return true;
    }

    private boolean hasSpare(NonNullList<ItemStack> bag, int excludeSlot,
            net.minecraft.tags.TagKey<net.minecraft.world.item.Item> toolTag) {
        for (int i = 0; i < bag.size(); i++) {
            if (i == excludeSlot) continue;
            if (!bag.get(i).isEmpty() && bag.get(i).is(toolTag)) return true;
        }
        return false;
    }

    private boolean switchToNextTool(NonNullList<ItemStack> bag, int excludeSlot,
            net.minecraft.tags.TagKey<net.minecraft.world.item.Item> toolTag,
            BlockState testBlock, boolean updatePickaxeStats) {
        int bestSlot = -1;
        float bestSpeed = 0;
        for (int i = 0; i < bag.size(); i++) {
            if (i == excludeSlot) continue;
            ItemStack stack = bag.get(i);
            if (!stack.isEmpty() && stack.is(toolTag)) {
                float speed = stack.getDestroySpeed(testBlock);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestSlot = i;
                }
            }
        }
        if (bestSlot < 0) return false;

        if (updatePickaxeStats) {
            ItemStack tool = bag.get(bestSlot);
            simPickaxeSpeed = tool.getDestroySpeed(testBlock);
            simFortune = 0;
            simSilkTouch = false;
            for (var entry : tool.getEnchantments().entrySet()) {
                if (entry.getKey().is(Enchantments.FORTUNE)) simFortune = Math.max(simFortune, entry.getIntValue());
                if (entry.getKey().is(Enchantments.SILK_TOUCH) && entry.getIntValue() > 0) simSilkTouch = true;
            }
        } else {
            simAxeSpeed = bag.get(bestSlot).getDestroySpeed(testBlock);
        }
        return true;
    }

    // ======== Completion ========

    private void completeSimulation(Level level, boolean failed, boolean death) {
        if (!"running".equals(simState)) return;
        simState = "complete";

        if (death) {
            addLogLine("Didn't come back.");
            if (level instanceof ServerLevel sl) {
                Entity entity = sl.getEntity(simWolfUuid);
                if (entity instanceof Wolf wolf) {
                    for (ItemStack stack : simPendingLoot) {
                        ItemEntity drop = new ItemEntity(
                            sl, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, stack.copy());
                        sl.addFreshEntity(drop);
                    }
                    wolf.setInvulnerable(false);
                    wolf.hurt(sl.damageSources().genericKill(), Float.MAX_VALUE);
                }
            }
            simPendingLoot.clear();
        } else if (failed) {
            addLogLine("Came back with nothing. Sat by the bed for a long time.");
            simPendingLoot.clear();
            triggerWolfArrival(level);
        } else {
            addLogLine("Home. Bag heavy.");
            for (ItemStack stack : simPendingLoot) {
                ItemStack remaining = tryInsert(stack);
                if (!remaining.isEmpty()) {
                    if (level instanceof ServerLevel sl) {
                        ItemEntity drop = new ItemEntity(
                            sl, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, remaining);
                        sl.addFreshEntity(drop);
                    }
                }
            }
            simPendingLoot.clear();
            triggerWolfArrival(level);
        }

        setChanged();
    }

    public boolean recallExpedition() {
        if (!"running".equals(simState) || this.level == null) return false;

        simState = "complete";
        addLogLine("Called back early.");

        for (ItemStack stack : simPendingLoot) {
            ItemStack remaining = tryInsert(stack);
            if (!remaining.isEmpty() && this.level instanceof ServerLevel sl) {
                ItemEntity drop = new ItemEntity(
                    sl, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, remaining);
                sl.addFreshEntity(drop);
            }
        }
        simPendingLoot.clear();
        triggerWolfArrival(this.level);
        setChanged();
        return true;
    }

    private void triggerWolfArrival(Level level) {
        if (!(level instanceof ServerLevel sl)) return;
        if (simWolfUuid == null) return;

        Entity entity = sl.getEntity(simWolfUuid);
        if (!(entity instanceof Wolf wolf)) return;

        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;

        Random rng = new Random(level.getGameTime());
        float angle = rng.nextFloat() * 2 * (float) Math.PI;
        int dist = 10 + rng.nextInt(5);
        int arrX = worldPosition.getX() + (int) (Math.cos(angle) * dist);
        int arrZ = worldPosition.getZ() + (int) (Math.sin(angle) * dist);
        int arrY = worldPosition.getY();
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

        addLogLine("Back at the bed.");
    }

    private void addLogLine(String line) {
        expeditionLog.add(line);
        if (this.level != null) {
            WorkingWolvesPackets.pushJournalLine(this.level, this.worldPosition, line, simElapsedTicks, simTotalTicks);
        }
        setChanged();
    }

    // ======== Biome helpers ========

    private static String getBiomeCategory(Level level, BlockPos pos) {
        Holder<Biome> holder = level.getBiome(pos);
        Identifier loc = holder.unwrapKey()
            .map(k -> k.identifier()).orElse(null);
        if (loc == null) return "other";
        String path = loc.getPath();
        if (path.contains("cave") || path.contains("dripstone") || path.contains("lush")) return "cave";
        if (path.contains("mountain") || path.contains("peak") || path.contains("badlands") || path.contains("stony")) return "mountain";
        if (path.contains("forest") || path.contains("taiga") || path.contains("jungle") || path.contains("dark")) return "forest";
        if (path.contains("ocean") || path.contains("beach") || path.contains("swamp") || path.contains("mangrove")) return "ocean";
        if (path.contains("plains") || path.contains("meadow") || path.contains("savanna")) return "plains";
        return "other";
    }

    private static String getWoodBiome(Level level, BlockPos pos) {
        Holder<Biome> holder = level.getBiome(pos);
        Identifier loc = holder.unwrapKey()
            .map(k -> k.identifier()).orElse(null);
        if (loc == null) return "forest";
        String path = loc.getPath();
        if (path.contains("bamboo") || path.contains("jungle")) return "jungle";
        if (path.contains("cherry")) return "cherry";
        if (path.contains("dark_forest")) return "dark_forest";
        if (path.contains("mangrove")) return "mangrove";
        if (path.contains("savanna")) return "savanna";
        if (path.contains("taiga") || path.contains("snowy_forest") || path.contains("spruce")
                || path.contains("grove") || path.contains("mountain") || path.contains("peak")) return "taiga";
        return "forest";
    }

    // ======== Simulation state accessors ========

    public String getSimState() {
        return simState;
    }

    public boolean isSimHasMining() { return simHasMining; }
    public boolean isSimHasHunting() { return simHasHunting; }
    public boolean isSimHasWoodcutting() { return simHasWoodcutting; }
    public int getSimCollarTier() { return simCollarTier; }
    public int getSimElapsedTicks() { return simElapsedTicks; }
    public int getSimTotalTicks() { return simTotalTicks; }

    public List<String> getExpeditionLog() {
        return expeditionLog;
    }
}
