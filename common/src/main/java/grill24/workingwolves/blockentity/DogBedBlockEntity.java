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
    private String simWolfClass = "";
    private int simCollarTier = 0;
    private ItemStack simFilterItem = ItemStack.EMPTY;
    private int simFoodEndurance = 0;
    private int simArmorPoints = 0;
    private float simPickaxeSpeed = 1.0f;
    private int simFortune = 0;
    private boolean simSilkTouch = false;
    private int simLooting = 0;
    private int simTotalTicks = 0;
    private int simElapsedTicks = 0;
    private int simEventTimer = 0;
    private int simInjuryCount = 0;
    private UUID simWolfUuid = null;
    private String simBiomeCategory = "other";
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
        simWolfClass = input.getStringOr("sim_wolf_class", "");
        simCollarTier = input.getIntOr("sim_collar_tier", 0);
        simFoodEndurance = input.getIntOr("sim_food_endurance", 0);
        simArmorPoints = input.getIntOr("sim_armor_points", 0);
        simPickaxeSpeed = input.getIntOr("sim_pickaxe_speed_x100", 100) / 100.0f;
        simFortune = input.getIntOr("sim_fortune", 0);
        simSilkTouch = input.getIntOr("sim_silk_touch", 0) != 0;
        simLooting = input.getIntOr("sim_looting", 0);
        simTotalTicks = input.getIntOr("sim_total_ticks", 0);
        simElapsedTicks = input.getIntOr("sim_elapsed_ticks", 0);
        simEventTimer = input.getIntOr("sim_event_timer", 0);
        simInjuryCount = input.getIntOr("sim_injury_count", 0);
        String wolfUuidStr = input.getStringOr("sim_wolf_uuid", "");
        simWolfUuid = wolfUuidStr.isEmpty() ? null : UUID.fromString(wolfUuidStr);
        simBiomeCategory = input.getStringOr("sim_biome_category", "other");
        simFilterItem = input.read("sim_filter_item", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);

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
        output.putString("sim_wolf_class", simWolfClass);
        output.putInt("sim_collar_tier", simCollarTier);
        output.putInt("sim_food_endurance", simFoodEndurance);
        output.putInt("sim_armor_points", simArmorPoints);
        output.putInt("sim_pickaxe_speed_x100", (int)(simPickaxeSpeed * 100));
        output.putInt("sim_fortune", simFortune);
        output.putInt("sim_silk_touch", simSilkTouch ? 1 : 0);
        output.putInt("sim_looting", simLooting);
        output.putInt("sim_total_ticks", simTotalTicks);
        output.putInt("sim_elapsed_ticks", simElapsedTicks);
        output.putInt("sim_event_timer", simEventTimer);
        output.putInt("sim_injury_count", simInjuryCount);
        if (simWolfUuid != null) output.putString("sim_wolf_uuid", simWolfUuid.toString());
        output.putString("sim_biome_category", simBiomeCategory);
        output.store("sim_filter_item", ItemStack.OPTIONAL_CODEC, simFilterItem);
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

    // ======== Simulation ========

    public void beginExpeditionSimulation(IWorkingWolf mixin) {
        simWolfClass = mixin.workingwolves$getWolfClass() != null ? mixin.workingwolves$getWolfClass() : "";
        simCollarTier = mixin.workingwolves$getCollarTier();
        simFilterItem = mixin.workingwolves$getFilterItem().copy();
        simTotalTicks = mixin.workingwolves$getExpeditionDuration();
        if (simTotalTicks <= 0) simTotalTicks = 12000; // fallback 10 min

        // Snapshot food endurance (count food stacks in bag)
        simFoodEndurance = 0;
        for (ItemStack stack : mixin.workingwolves$getBagInventory()) {
            if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) {
                simFoodEndurance += stack.getCount();
            }
        }

        // Snapshot armor (wolf's armor attribute value)
        Wolf wolf = (Wolf) (Object) mixin;
        simArmorPoints = (int) wolf.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);

        // Snapshot pickaxe/weapon stats from bag
        simPickaxeSpeed = 1.0f;
        simFortune = 0;
        simSilkTouch = false;
        simLooting = 0;
        for (ItemStack stack : mixin.workingwolves$getBagInventory()) {
            if (stack.isEmpty()) continue;
            if ("miner".equals(simWolfClass) && stack.is(ItemTags.PICKAXES)) {
                BlockState stone = Blocks.STONE.defaultBlockState();
                simPickaxeSpeed = Math.max(simPickaxeSpeed, stack.getDestroySpeed(stone));
                for (var entry : stack.getEnchantments().entrySet()) {
                    if (entry.getKey().is(Enchantments.FORTUNE)) simFortune = Math.max(simFortune, entry.getIntValue());
                    if (entry.getKey().is(Enchantments.SILK_TOUCH) && entry.getIntValue() > 0) simSilkTouch = true;
                }
            }
            if ("hunter".equals(simWolfClass)) {
                if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)
                    || stack.getItem() instanceof net.minecraft.world.item.BowItem
                    || stack.getItem() instanceof net.minecraft.world.item.CrossbowItem) {
                    for (var entry : stack.getEnchantments().entrySet()) {
                        if (entry.getKey().is(Enchantments.LOOTING)) simLooting = Math.max(simLooting, entry.getIntValue());
                    }
                }
            }
        }

        // Snapshot wolf UUID
        simWolfUuid = wolf.getUUID();

        // Biome at bed
        if (this.level != null) {
            simBiomeCategory = getBiomeCategory(this.level, this.worldPosition);
        }

        // Reset sim state
        simState = "running";
        simElapsedTicks = 0;
        simEventTimer = 60 + (this.level != null ? this.level.getRandom().nextInt(40) : 20);
        simInjuryCount = 0;
        simPendingLoot.clear();
        expeditionLog.clear();

        addLogLine("Left the warmth of the bed.");
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DogBedBlockEntity be) {
        if (!"running".equals(be.simState)) return;

        be.simElapsedTicks++;

        // Check completion by time
        if (be.simElapsedTicks >= be.simTotalTicks) {
            be.completeSimulation(level, false, false);
            return;
        }

        // Advance event timer
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

        if ("hunter".equals(simWolfClass)) {
            rollHunterEvent(level, zone);
        } else if ("miner".equals(simWolfClass)) {
            rollMinerEvent(level, zone);
        }
    }

    private void rollHunterEvent(Level level, int zone) {
        Random rng = new Random(level.getGameTime() + simElapsedTicks);
        int[] weights = switch (zone) {
            case 0 -> new int[]{50, 40, 10};
            case 1 -> new int[]{30, 45, 25};
            default -> new int[]{20, 35, 45};
        };
        int roll = rng.nextInt(100);

        if (roll < weights[0]) {
            // Travel event (flavor only)
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
            // Discovery event (loot)
            rollHunterDiscovery(level, zone, rng);
        } else {
            // Hazard event
            rollHunterHazard(level, rng);
        }
    }

    private void rollHunterDiscovery(Level level, int zone, Random rng) {
        // Determine target mob
        String mob = getMobFromFilter(zone, rng);

        // Per-mob loot
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
                    // 5% chance of wither skull
                    if (rng.nextFloat() < 0.05f + simLooting * 0.01f) {
                        simPendingLoot.add(new ItemStack(Items.WITHER_SKELETON_SKULL, 1));
                    }
                }
                addLogLine("Something in the dark. Much taller.");
            }
            default -> {
                addLogLine("Something moved. Gone now.");
            }
        }
    }

    private String getMobFromFilter(int zone, Random rng) {
        // If filter is set, determine mob from filter item
        if (!simFilterItem.isEmpty()) {
            if (simFilterItem.is(Items.BONE) || simFilterItem.is(Items.ARROW)) return "skeleton";
            if (simFilterItem.is(Items.ROTTEN_FLESH)) return "zombie";
            if (simFilterItem.is(Items.STRING)) return "spider";
            if (simFilterItem.is(Items.GUNPOWDER)) return "creeper";
            if (simFilterItem.is(Items.ENDER_PEARL)) return "enderman";
            if (simFilterItem.is(Items.BLAZE_POWDER) || simFilterItem.is(Items.BLAZE_ROD)) return "blaze";
        }
        // Random from zone pool
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

        // Consume food endurance
        simFoodEndurance--;
        if (simFoodEndurance < 0) {
            simInjuryCount++;
            if (simInjuryCount >= 3) {
                boolean death = simArmorPoints < 4 && rng.nextFloat() < 0.12f;
                completeSimulation(level, true, death);
                return;
            }
            addLogLine("Running low. Pushing on.");
        }
    }

    private void rollMinerEvent(Level level, int zone) {
        Random rng = new Random(level.getGameTime() + simElapsedTicks);
        int[] weights = switch (zone) {
            case 0 -> new int[]{50, 40, 10};
            case 1 -> new int[]{30, 45, 25};
            default -> new int[]{20, 35, 45};
        };
        int roll = rng.nextInt(100);

        if (roll < weights[0]) {
            // Travel event
            String[] travelLines = {
                "Narrow tunnel. Kept digging.",
                "Cave system ahead. Dripping water.",
                "Loose gravel above. Careful.",
                "Sound of water in the dark.",
                "Old mineshaft. Something glinted.",
                "Long corridor. Nothing yet."
            };
            addLogLine(travelLines[rng.nextInt(travelLines.length)]);
        } else if (roll < weights[0] + weights[1]) {
            // Discovery event
            rollMinerDiscovery(level, zone, rng);
        } else {
            // Hazard event
            rollMinerHazard(level, rng);
        }
    }

    private void rollMinerDiscovery(Level level, int zone, Random rng) {
        // Biome yield multiplier
        float biomeMultiplier = ("mountain".equals(simBiomeCategory) || "cave".equals(simBiomeCategory)) ? 1.25f : 1.0f;

        // If filter is set, heavily weight toward the requested ore (70% chance)
        String forcedOre = null;
        if (!simFilterItem.isEmpty()) {
            if (simFilterItem.is(Items.COAL)) forcedOre = "coal";
            else if (simFilterItem.is(Items.RAW_IRON) || simFilterItem.is(Items.IRON_INGOT)) forcedOre = "iron";
            else if (simFilterItem.is(Items.RAW_COPPER) || simFilterItem.is(Items.COPPER_INGOT)) forcedOre = "copper";
            else if (simFilterItem.is(Items.RAW_GOLD) || simFilterItem.is(Items.GOLD_INGOT)) forcedOre = "gold";
            else if (simFilterItem.is(Items.LAPIS_LAZULI)) forcedOre = "lapis";
            else if (simFilterItem.is(Items.REDSTONE)) forcedOre = "redstone";
            else if (simFilterItem.is(Items.DIAMOND)) forcedOre = "diamond";
            else if (simFilterItem.is(Items.AMETHYST_SHARD)) forcedOre = "amethyst";
        }

        String ore;
        if (forcedOre != null && rng.nextFloat() < 0.70f) {
            ore = forcedOre;
        } else {
            ore = switch (zone) {
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
        }

        // Fortune multiplier: (1 + fortune * 0.5)
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

        // Consume food endurance
        simFoodEndurance--;
        if (simFoodEndurance < 0) {
            simInjuryCount++;
            if (simInjuryCount >= 3) {
                boolean death = simArmorPoints < 4 && rng.nextFloat() < 0.12f;
                completeSimulation(level, true, death);
                return;
            }
            addLogLine("Running low. Pushing on.");
        }
    }

    private void completeSimulation(Level level, boolean failed, boolean death) {
        simState = "complete";

        if (death) {
            addLogLine("Didn't come back.");
            if (level instanceof ServerLevel sl) {
                Entity entity = sl.getEntity(simWolfUuid);
                if (entity instanceof Wolf wolf) {
                    // Drop pending loot at bed pos
                    for (ItemStack stack : simPendingLoot) {
                        ItemEntity drop = new ItemEntity(
                            sl, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, stack.copy());
                        sl.addFreshEntity(drop);
                    }
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
            // Deposit pending loot into bed storage
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

    private void triggerWolfArrival(Level level) {
        if (!(level instanceof ServerLevel sl)) return;
        if (simWolfUuid == null) return;

        Entity entity = sl.getEntity(simWolfUuid);
        if (!(entity instanceof Wolf wolf)) return;

        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;

        // Pick arrival point ~10-14 blocks from bed in a random direction
        Random rng = new Random(level.getGameTime());
        float angle = rng.nextFloat() * 2 * (float) Math.PI;
        int dist = 10 + rng.nextInt(5);
        int arrX = worldPosition.getX() + (int) (Math.cos(angle) * dist);
        int arrZ = worldPosition.getZ() + (int) (Math.sin(angle) * dist);
        int arrY = worldPosition.getY();
        // Find ground at arrival point
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

        mixin.workingwolves$setExpeditionState("returning");
        wolf.setOrderedToSit(false);
        mixin.workingwolves$syncData();

        addLogLine("Back at the bed.");
    }

    private void addLogLine(String line) {
        expeditionLog.add(line);
        if (this.level != null) {
            WorkingWolvesPackets.pushJournalLine(this.level, this.worldPosition, line);
        }
        setChanged();
    }

    // ======== Biome helper ========

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

    // ======== Simulation state accessors ========

    public String getSimState() {
        return simState;
    }

    public List<String> getExpeditionLog() {
        return expeditionLog;
    }
}
