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
import net.minecraft.world.item.Item;
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

    private enum HazardLevel { LIGHT, MODERATE, SEVERE }

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

        String[] departureLines = {
            "Left the warmth of the bed.",
            "Out before dawn.",
            "Set out. The work won't find itself.",
            "Gone to work."
        };
        addLogLine(departureLines[this.level.getRandom().nextInt(departureLines.length)]);
        WorkingWolvesPackets.pushBedState(this.level, this.worldPosition, this);
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
        Random rng = new Random(level.getGameTime() + simElapsedTicks);

        if (rng.nextFloat() < 0.025f && rollRareEvent(level, zone, rng)) return;

        int roleCount = (simHasMining ? 1 : 0) + (simHasHunting ? 1 : 0) + (simHasWoodcutting ? 1 : 0);
        if (roleCount >= 2 && rng.nextFloat() < 0.12f && rollCrossRoleEvent(level, rng)) return;

        if (roleCount == 0) return;
        int roll = rng.nextInt(roleCount);
        int idx = 0;
        if (simHasHunting && idx++ == roll) { rollHunterEvent(level, zone); return; }
        if (simHasMining && idx++ == roll) { rollMinerEvent(level, zone); return; }
        if (simHasWoodcutting) { rollWoodcutterEvent(level, zone); }
    }

    private HazardLevel pickHazardLevel(int zone, Random rng) {
        int roll = rng.nextInt(100);
        return switch (zone) {
            case 0  -> roll < 75 ? HazardLevel.LIGHT : roll < 97 ? HazardLevel.MODERATE : HazardLevel.SEVERE;
            case 1  -> roll < 48 ? HazardLevel.LIGHT : roll < 88 ? HazardLevel.MODERATE : HazardLevel.SEVERE;
            default -> roll < 30 ? HazardLevel.LIGHT : roll < 75 ? HazardLevel.MODERATE : HazardLevel.SEVERE;
        };
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
                "Something watched from the ridge. Did not follow.",
                "Tracks in the dirt. Recent.",
                "The forest grew quiet.",
                "Distant howl. Not ours.",
                "Old campfire. Cold for weeks. Someone was here.",
                "Wind shifted. Catalogued seven new smells.",
                "Fog sat low. Walked through it anyway.",
                "A sound like digging, far off. Not me.",
                "The trees here grow wrong. Filed for later.",
                "Crossed the creek twice. Lost count after that.",
                "Scent of iron in the air. Getting closer."
            };
            addLogLine(travelLines[rng.nextInt(travelLines.length)]);
        } else if (roll < weights[0] + weights[1]) {
            rollHunterDiscovery(level, zone, rng);
        } else {
            HazardLevel hl = pickHazardLevel(zone, rng);
            rollHunterHazard(level, rng, hl);
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
                String[] lines = {
                    "Skeleton at range. Closed before it could draw again.",
                    "Arrow clipped my ear. Didn't slow me down.",
                    "Two skeletons. Took turns on each.",
                    "Pinned behind a tree for a moment. Then I wasn't.",
                    "Skeleton in the open. No cover for either of us.",
                    "Nocked and ready. I was faster."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "zombie" -> {
                int flesh = 1 + rng.nextInt(2) + simLooting;
                simPendingLoot.add(new ItemStack(Items.ROTTEN_FLESH, flesh));
                String[] lines = {
                    "Zombie, slow and loud. Over quickly.",
                    "Three zombies. Messy. Done.",
                    "It grabbed my scruff. Regretted it.",
                    "Zombie mob. Picked them off one by one.",
                    "Got swarmed for a moment. Pushed through.",
                    "Undead smell. Three bites, none landed."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "spider" -> {
                int string = rng.nextInt(3) + simLooting;
                int eye = rng.nextInt(2) + simLooting;
                if (string > 0) simPendingLoot.add(new ItemStack(Items.STRING, string));
                if (eye > 0) simPendingLoot.add(new ItemStack(Items.SPIDER_EYE, eye));
                String[] lines = {
                    "Spider dropped from above. Nearly had me.",
                    "Webbing on my paws. Slowed the approach.",
                    "Jockey. Skeleton on its back. Harder than expected.",
                    "Spider hit first. Left marks. Still won.",
                    "Cave spider. Smaller. The poison was not.",
                    "Leapt wide. I was already moving."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "creeper" -> {
                if (zone >= 1) {
                    int powder = rng.nextInt(2) + simLooting;
                    if (powder > 0) simPendingLoot.add(new ItemStack(Items.GUNPOWDER, powder));
                }
                String[] lines = {
                    "Heard it hissing. Backed off. Waited for the click. Then closed in.",
                    "Creeper cornered. Circled until it gave up.",
                    "Close. Too close. Singed. Won't do that again.",
                    "Two creepers tangled together. Gave them wide space.",
                    "It started to glow. I ran. It didn't follow far.",
                    "Killed it quick enough. The trick is not hesitating."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "drowned" -> {
                if (zone >= 1 && rng.nextFloat() < 0.25f + simLooting * 0.1f) {
                    simPendingLoot.add(new ItemStack(Items.NAUTILUS_SHELL, 1));
                }
                String[] lines = {
                    "Trident hit the bank beside me. Pulled it out of the water anyway.",
                    "Something waded out of the shallows. Finished it fast.",
                    "Drowned with a trident. Kept moving to stay out of the arc.",
                    "Three drowned. Lured them onto dry ground.",
                    "It was slow on land. That's the trick.",
                    "Caught one emerging. The current made it awkward."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "slime" -> {
                int slimeballs = 1 + rng.nextInt(3) + simLooting;
                if (slimeballs > 0) simPendingLoot.add(new ItemStack(Items.SLIME_BALL, slimeballs));
                String[] lines = {
                    "Slime. Hit it and it became two problems.",
                    "Four of them by the end. Sticky paws.",
                    "Big one. Split twice before it stopped moving.",
                    "Small slimes. More annoying than dangerous. Cleaned up."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "witch" -> {
                Item[] witchDrops = {Items.GLASS_BOTTLE, Items.SUGAR, Items.REDSTONE, Items.GUNPOWDER, Items.SPIDER_EYE, Items.GLOWSTONE_DUST};
                int dropCount = 1 + rng.nextInt(3) + simLooting;
                for (int d = 0; d < dropCount; d++) {
                    simPendingLoot.add(new ItemStack(witchDrops[rng.nextInt(witchDrops.length)], 1));
                }
                String[] lines = {
                    "Witch in a hollow. Took a splash before I closed in.",
                    "Slowed by a hex, then a poison one. Still got her.",
                    "She kept drinking. Wouldn't stop healing. Had to be fast.",
                    "Robe and hat. Cackling. Not anymore."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "pillager" -> {
                int arrows = 2 + rng.nextInt(3) + simLooting;
                simPendingLoot.add(new ItemStack(Items.ARROW, arrows));
                if (rng.nextFloat() < 0.10f + simLooting * 0.03f) {
                    simPendingLoot.add(new ItemStack(Items.EMERALD, 1));
                }
                if (rng.nextFloat() < 0.03f) {
                    simPendingLoot.add(new ItemStack(Items.CROSSBOW, 1));
                }
                String[] lines = {
                    "Pillager patrol. Crossbows up. Broke their formation.",
                    "Bolt hit the dirt by my paw. Closed the distance before reload.",
                    "Two pillagers at an outpost edge. Didn't go inside.",
                    "Pillager captain. Tougher than the rest."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "enderman" -> {
                if (zone >= 2) {
                    int pearls = rng.nextInt(2) + simLooting;
                    if (pearls > 0) simPendingLoot.add(new ItemStack(Items.ENDER_PEARL, pearls));
                }
                String[] lines = {
                    "Fought it in the rain. Couldn't teleport.",
                    "Caught it off guard near water. It panicked.",
                    "Three failed lunges. The fourth connected.",
                    "It grabbed a block and watched me. I didn't look at its face.",
                    "Kept blinking. Every lunge, somewhere else. Eventually connected.",
                    "Tall. Fast. Got lucky."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "wither_skeleton" -> {
                if (zone >= 2) {
                    int coal = rng.nextInt(2);
                    if (coal > 0) simPendingLoot.add(new ItemStack(Items.COAL, coal));
                    if (rng.nextFloat() < 0.05f + simLooting * 0.01f) {
                        simPendingLoot.add(new ItemStack(Items.WITHER_SKELETON_SKULL, 1));
                    }
                }
                String[] lines = {
                    "Wither skeleton. Its blade gave me the shakes. Pushed through.",
                    "Taller than expected. Slower than expected. Worked in my favor.",
                    "Wither effect. Vision went black for a moment. Kept fighting.",
                    "Something in the dark. Much taller. Much worse.",
                    "It had a reach advantage. Had to get inside its guard.",
                    "Old bones. Strong swing. Took three clean hits."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "phantom" -> {
                int membranes = 1 + (simCollarTier >= 3 ? rng.nextInt(3) : rng.nextInt(2)) + simLooting;
                simPendingLoot.add(new ItemStack(Items.PHANTOM_MEMBRANE, Math.max(1, membranes)));
                String[] lines = {
                    "Something diving from above. No warning. Phantom.",
                    "Wings in the dark. Attacked in passes. Waited for the gap.",
                    "Phantom grabbed my scruff mid-leap. Landed hard. Found it again.",
                    "Three of them, circling. Picked them out of the air one by one."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "blaze" -> {
                if (zone >= 2) {
                    int rods = rng.nextInt(3) + simLooting;
                    if (rods > 0) simPendingLoot.add(new ItemStack(Items.BLAZE_ROD, rods));
                }
                String[] lines = {
                    "Three fireballs in a row, then a window. Used the window.",
                    "Blaze circling overhead. Waited for it to descend.",
                    "Fireball clipped my flank. The heat was worse than the hit.",
                    "The smoke was thick. Found it by the sound.",
                    "Dodged wide on the first burst. Closed on the second pause.",
                    "Fur still smells like forge smoke."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            default -> addLogLine("Something moved. Gone now.");
        }
    }

    private String getMobForZone(int zone, Random rng) {
        return switch (zone) {
            case 0 -> {
                String[] pool = {"skeleton", "zombie", "spider", "slime"};
                yield pool[rng.nextInt(pool.length)];
            }
            case 1 -> {
                String[] pool = {"skeleton", "zombie", "spider", "creeper", "drowned", "slime", "witch", "pillager"};
                yield pool[rng.nextInt(pool.length)];
            }
            default -> {
                String[] pool = {"enderman", "wither_skeleton", "phantom", "pillager",
                    ("mountain".equals(simBiomeCategory) || "cave".equals(simBiomeCategory) || "other".equals(simBiomeCategory)) ? "blaze" : "enderman"};
                yield pool[rng.nextInt(pool.length)];
            }
        };
    }

    private void rollHunterHazard(Level level, Random rng, HazardLevel hl) {
        String[] lines = switch (hl) {
            case LIGHT -> new String[]{
                "Got bit. Not badly. Kept going.",
                "Wrong side of a ravine. Had to double back.",
                "Ambushed. Recovered faster than expected.",
                "Something stirred in the brush. Moved on.",
                "Lava nearby. Backed off."
            };
            case MODERATE -> new String[]{
                "Three of them at once. Held on.",
                "Cornered briefly. Found a way out.",
                "Took a hit. Kept moving.",
                "Outnumbered. Fought anyway.",
                "Pack of them. Retreated and regrouped."
            };
            case SEVERE -> new String[]{
                "Too many. Barely got clear.",
                "Took the worst of it. Still here.",
                "Nearly didn't make it out. Did.",
                "The pack was bigger than it looked. Ran.",
                "Something found me before I found it. Cost me."
            };
        };
        addLogLine(lines[rng.nextInt(lines.length)]);
        applyHazardCost(level, rng, hl);
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
            String[] travelLines = zone == 0 ? new String[]{
                // Zone 0 — shallow: familiar, daylight still a memory
                "Entrance shaft. Air still tastes of surface.",
                "Thin seam of chalk in the wall. No ore, but promising.",
                "Old torch stub in the stone. Someone dug here before.",
                "Rootlets pushing through the ceiling. Still near the surface.",
                "Pebbles underfoot. Smooth, water-worn. Ancient stream bed.",
                "Low ceiling. Had to press close to the wall.",
                "Patch of moss on the north face. Moisture in the rock.",
                "Faint dripping somewhere ahead. Getting deeper.",
                "Gravel chute. Held still until it settled.",
                "Soil giving way to true stone. The real work starts here."
            } : zone == 1 ? new String[]{
                // Zone 1 — mid-depth: darker, stranger, wetter
                "Aquifer seeping through a crack. Floor slick with it.",
                "Gallery opens up. Ceiling lost in the dark above.",
                "Calcite formations on the wall. Smoother than the stone beside them.",
                "Smell of sulfur. Distant, but real.",
                "Bones of something in a side passage. Old. Very old.",
                "Fossil half-emerged from the limestone. Strange fish-shape.",
                "Dripstone columns ahead, floor to ceiling. Navigated through carefully.",
                "Sound of water under the floor. A buried river.",
                "The rock changed color here. Banded layers. Thousands of years of quiet.",
                "Wind from somewhere. No way to know which direction."
            } : new String[]{
                // Zone 2 — deep: oppressive, ancient, barely safe
                "Absolute darkness below the ledge. Dropped a stone. Did not hear it land.",
                "The walls are warm here. Not from the stone.",
                "Ancient city brickwork in the distance. Did not go closer.",
                "Magma block underfoot. Backed off and found another way.",
                "Ceiling dripping slowly, one drop every few seconds. Counted them.",
                "No wind. No sound. The rock absorbs everything.",
                "Sculk patches on the floor. Stepped around each one.",
                "Vein of deepslate running floor-to-ceiling. Millions of years compressed into a seam.",
                "Glow of magma through a crack in the wall. Held paw to it. Warm.",
                "Passage narrowed to nothing. Backtracked. Found another way down."
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
            HazardLevel hl = pickHazardLevel(zone, rng);
            rollMinerHazard(level, rng, hl);
            int dmg = switch (hl) { case LIGHT -> 1; case MODERATE -> 2; case SEVERE -> 3; };
            if (!applyMinerDurability(level, dmg)) {
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
                String[] lines = {
                    "Coal seam in the wall. Wide as my paw.",
                    "Black vein, three layers deep. Long work but worth it.",
                    "Coal dust already in the air here. Good sign.",
                    "Pocket of coal behind a thin slate wall. Lucky find.",
                    "Large seam, runs deeper than I can see. Took what I could reach."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "iron" -> {
                int base = zone == 0 ? 1 + rng.nextInt(3) : 2 + rng.nextInt(4);
                int count = (int)(base * biomeMultiplier);
                simPendingLoot.add(new ItemStack(simSilkTouch ? Items.IRON_ORE : Items.RAW_IRON, Math.max(1, count)));
                String[] lines = {
                    "Iron ore behind the stone. Raw and rough.",
                    "Caught scent of iron before I saw it. Dug in.",
                    "Red-brown streak through grey granite. Classic iron.",
                    "Cluster of iron nodules in the ceiling. Had to work at an angle.",
                    "Ochre staining the rock face. Scraped it back and found the vein."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "copper" -> {
                int base = 1 + rng.nextInt(4);
                int count = (int)(base * fortuneMultiplier * biomeMultiplier);
                simPendingLoot.add(new ItemStack(simSilkTouch ? Items.COPPER_ORE : Items.RAW_COPPER, Math.max(1, count)));
                String[] lines = {
                    "Copper, oxidized green. Unmistakable.",
                    "Teal streak bleeding through the limestone. A big pocket.",
                    "Copper blooms across the rock face like frozen flames.",
                    "Green dust on the floor. Looked up and found the source.",
                    "Verdigris on everything here. Rich copper deposit, weathered through."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "gold" -> {
                int base = 1 + rng.nextInt(3);
                int count = (int)(base * biomeMultiplier);
                simPendingLoot.add(new ItemStack(simSilkTouch ? Items.GOLD_ORE : Items.RAW_GOLD, Math.max(1, count)));
                String[] lines = {
                    "Gold glint in the torchlight. Checked twice to be sure.",
                    "A vein of gold. Small but rich. Careful extraction.",
                    "Gold in the deepslate. Deep enough to mean it.",
                    "Yellow fleck, then more flecks, then a full seam. Good day.",
                    "Heavy ore. Had to shift the weight in the bag."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "lapis" -> {
                int base = 2 + rng.nextInt(7);
                int count = (int)(base * fortuneMultiplier * biomeMultiplier);
                simPendingLoot.add(new ItemStack(simSilkTouch ? Items.LAPIS_ORE : Items.LAPIS_LAZULI, Math.max(1, count)));
                String[] lines = {
                    "Lapis. Bright blue in the grey rock. Striking.",
                    "Lapis dust already on the floor where a chunk fell out.",
                    "Deep blue vein, wider than expected. Took a long time.",
                    "The stone split and the lapis inside was almost luminous.",
                    "Ultramarine scatter through the limestone. Beautiful, even down here."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "redstone" -> {
                int base = 2 + rng.nextInt(7);
                int count = (int)(base * fortuneMultiplier * biomeMultiplier);
                simPendingLoot.add(new ItemStack(simSilkTouch ? Items.REDSTONE_ORE : Items.REDSTONE, Math.max(1, count)));
                String[] lines = {
                    "Redstone. The rock hums faintly where it runs.",
                    "Deep red dust on the paw. Vein in the ceiling.",
                    "Redstone seam lit up when I scraped it. Strange light in the dark.",
                    "The walls pulsed dim red as I worked. Kept going.",
                    "Redstone ore, dense and deep. The deepslate soaked it up over millennia."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "diamond" -> {
                int base = 1 + rng.nextInt(3);
                int count = (int)(base * fortuneMultiplier * biomeMultiplier);
                simPendingLoot.add(new ItemStack(simSilkTouch ? Items.DIAMOND_ORE : Items.DIAMOND, Math.max(1, count)));
                String[] lines = {
                    "Diamond. Stopped breathing for a moment. Then dug.",
                    "Blue-white glint in the deepslate. Heart hammering.",
                    "Diamond vein. Small. Did not care. Worked until my paws ached.",
                    "Found it by accident, feeling along the wall in the dark. Diamond.",
                    "The pickaxe rang differently against this stone. Looked closer. Diamond."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "amethyst" -> {
                int base = 1 + rng.nextInt(4);
                int count = (int)(base * fortuneMultiplier * biomeMultiplier);
                simPendingLoot.add(new ItemStack(Items.AMETHYST_SHARD, Math.max(1, count)));
                String[] lines = {
                    "Geode pocket. Amethyst clusters inside, perfect and untouched.",
                    "The rock opened into a hollow lined with purple crystal. Unexpected.",
                    "Amethyst. Chimed softly when the pickaxe hit it.",
                    "A small geode, cracked. Violet shards caught whatever light there was.",
                    "Walls of amethyst around me for a moment. A private cathedral."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "flint" -> {
                int base = 2 + rng.nextInt(3);
                simPendingLoot.add(new ItemStack(Items.FLINT, Math.max(1, base)));
                String[] lines = {
                    "Gravel seam. Sifted through it carefully. Good flint.",
                    "Flint nodules in the limestone. Ancient sea floor, once.",
                    "Black flint, sharp-edged. Sorted the best pieces.",
                    "Gravel pocket collapsed. Picked through the pile. Worth it.",
                    "Flint-rich band running through the chalk. Took what would carry."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            default -> addLogLine("Odd mineral. Took what was worth taking.");
        }
    }

    private void rollMinerHazard(Level level, Random rng, HazardLevel hl) {
        String[] lines = switch (hl) {
            case LIGHT -> new String[]{
                "Passage looped. Spent time finding north again.",
                "Wrong tunnel for a while. Doubled back. Lost time.",
                "Sound of many feet. Stayed still until it passed.",
                "Groaning in the walls. Zombie trapped in the stone somewhere.",
                "Something moved in the chamber below. Did not go down."
            };
            case MODERATE -> new String[]{
                "Ceiling cracked. Held still. It held.",
                "Gravel pour from above. Moved before it filled the corridor.",
                "Water flooded the lower corridor fast. Climbed out.",
                "Deep water ahead, no bottom. Found another way around.",
                "Clicking in the dark behind me. Picked up the pace.",
                "Breath in the dark that wasn't mine. Not going back that way.",
                "Heat rising through the floor. Magma below. Found a way around it."
            };
            case SEVERE -> new String[]{
                "Lava pocket opened up mid-swing. Retreated fast. Singed.",
                "Cave-in above. Buried the passage. Had to dig back out.",
                "Support pillar gone. The whole section leaned. Left quickly.",
                "Aquifer burst through the wall. Cold and sudden. Lost the passage.",
                "Something in the dark that wasn't afraid. Moved fast."
            };
        };
        addLogLine(lines[rng.nextInt(lines.length)]);
        applyHazardCost(level, rng, hl);
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
                "A clearing, then more forest.",
                "Old growth. Roots everywhere.",
                "Light through the branches.",
                "Sound of wind through the canopy.",
                "The path I marked is already closing.",
                "Something moved through the understory. Held still.",
                "Following a deer path. Goes exactly where I needed.",
                "Rain started. The canopy is holding most of it.",
                "The fern layer is thick. Good soil under it.",
                "Something ahead went quiet. Waited. Kept moving."
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
            HazardLevel hl = pickHazardLevel(zone, rng);
            rollWoodcutterHazard(level, rng, hl);
            int dmg = switch (hl) { case LIGHT -> 1; case MODERATE -> 2; case SEVERE -> 3; };
            if (!applyWoodcutterDurability(level, dmg)) {
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
                String[] lines = {
                    "Dense jungle. Every trunk laced with vines.",
                    "Something large moved through the understory. Held still.",
                    "Orchids growing from a wound in the bark.",
                    "The trees here do not fall cleanly."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "dark_forest" -> {
                int logs = Math.max(1, (int)((1 + rng.nextInt(4)) * axeMultiplier));
                simPendingLoot.add(new ItemStack(Items.DARK_OAK_LOG, logs));
                if (rng.nextFloat() < 0.2f) simPendingLoot.add(new ItemStack(Items.DARK_OAK_SAPLING, 1));
                if (rng.nextFloat() < 0.15f) simPendingLoot.add(new ItemStack(Items.BROWN_MUSHROOM, 1 + rng.nextInt(2)));
                if (rng.nextFloat() < 0.15f) simPendingLoot.add(new ItemStack(Items.RED_MUSHROOM, 1));
                String[] lines = {
                    "Found a clearing. The trees lean inward around it.",
                    "Mushrooms growing up the trunks, not just the ground.",
                    "Long claw marks on the trunk. Higher than I wanted to think.",
                    "The inside of the dark oak was pale. Did not expect that."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "taiga" -> {
                int logs = Math.max(1, (int)((1 + rng.nextInt(4)) * axeMultiplier));
                simPendingLoot.add(new ItemStack(Items.SPRUCE_LOG, logs));
                if (rng.nextFloat() < 0.3f) simPendingLoot.add(new ItemStack(Items.SWEET_BERRIES, 1 + rng.nextInt(3)));
                if (rng.nextFloat() < 0.2f) simPendingLoot.add(new ItemStack(Items.SPRUCE_SAPLING, 1));
                if (rng.nextFloat() < 0.1f) simPendingLoot.add(new ItemStack(Items.STICK, 2 + rng.nextInt(4)));
                String[] lines = {
                    "Spruce stands. Cold air. Smells like winter stored inside wood.",
                    "Good straight grain. These trees grow slow and honest.",
                    "A cavity in the trunk. Old axe marks. Someone didn't finish.",
                    "Snow came down all at once when the trunk finally went."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "savanna" -> {
                int logs = Math.max(1, (int)((1 + rng.nextInt(3)) * axeMultiplier));
                simPendingLoot.add(new ItemStack(Items.ACACIA_LOG, logs));
                if (rng.nextFloat() < 0.2f) simPendingLoot.add(new ItemStack(Items.ACACIA_SAPLING, 1));
                String[] lines = {
                    "Scattered acacia. Flat light.",
                    "Twisted trunks in the savanna heat.",
                    "Dead acacia still standing. Bark stripped clean by antlers.",
                    "The roots tore the ground when it went over."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "cherry" -> {
                int logs = Math.max(1, (int)((1 + rng.nextInt(3)) * axeMultiplier));
                simPendingLoot.add(new ItemStack(Items.CHERRY_LOG, logs));
                if (rng.nextFloat() < 0.3f) simPendingLoot.add(new ItemStack(Items.CHERRY_SAPLING, 1));
                if (rng.nextFloat() < 0.4f) simPendingLoot.add(new ItemStack(Items.PINK_PETALS, 1 + rng.nextInt(3)));
                String[] lines = {
                    "Petals drifting down without any wind.",
                    "Wood pale and close-grained. Smells faintly sweet.",
                    "This place makes time feel very long.",
                    "A family of rabbits under a fallen log. Worked around them."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            case "mangrove" -> {
                int logs = Math.max(1, (int)((1 + rng.nextInt(3)) * axeMultiplier));
                simPendingLoot.add(new ItemStack(Items.MANGROVE_LOG, logs));
                if (rng.nextFloat() < 0.3f) simPendingLoot.add(new ItemStack(Items.MANGROVE_PROPAGULE, 1));
                if (rng.nextFloat() < 0.2f) simPendingLoot.add(new ItemStack(Items.MANGROVE_ROOTS, 1 + rng.nextInt(2)));
                String[] lines = {
                    "Roots in the water first, then down to the mud.",
                    "A frog on the root arch I was about to step over. Stepped around it.",
                    "The aerial roots hang like curtains. Patience required.",
                    "Dense, uneven wood. A life lived half in water."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
            default -> { // "forest" and everything else
                int logs = Math.max(1, (int)((1 + rng.nextInt(4)) * axeMultiplier));
                boolean birch = rng.nextBoolean();
                simPendingLoot.add(new ItemStack(birch ? Items.BIRCH_LOG : Items.OAK_LOG, logs));
                if (rng.nextFloat() < 0.25f) simPendingLoot.add(new ItemStack(Items.APPLE, 1 + rng.nextInt(2)));
                if (rng.nextFloat() < 0.15f) simPendingLoot.add(new ItemStack(birch ? Items.BIRCH_SAPLING : Items.OAK_SAPLING, 1));
                String[] lines = {
                    "Good stand of oak. Clean work.",
                    "Birch grove, white bark. Quiet.",
                    "The oak at the center is old enough to matter.",
                    "Birch light is different. White bark scatters everything.",
                    "Clean, quick work. These trees have nothing to say about it."
                };
                addLogLine(lines[rng.nextInt(lines.length)]);
            }
        }
    }

    private void rollWoodcutterHazard(Level level, Random rng, HazardLevel hl) {
        String[] lines = switch (hl) {
            case LIGHT -> new String[]{
                "Roots tangled the path. Lost time.",
                "Fog rolling in. Slowed down.",
                "Thorns and thick brush.",
                "Rain made the bark slick. Careful work.",
                "The path I marked was already grown over."
            };
            case MODERATE -> new String[]{
                "Tree fell the wrong way. Close call.",
                "Beehive in the branches. Stings.",
                "Hostile mob in the undergrowth.",
                "A root caught my back foot. Went down hard.",
                "Something in the dark forest did not want me there.",
                "Disturbed a nest I did not see. Moved before counting."
            };
            case SEVERE -> new String[]{
                "Bear investigating my woodpile. Then investigating me.",
                "The dry grass caught from a spark. Not so controlled.",
                "Tree came down on top of me. Working out from under it.",
                "The mud swallowed me to the hip. Took time to extract.",
                "Something large, very close, very fast. Dropped everything and ran."
            };
        };
        addLogLine(lines[rng.nextInt(lines.length)]);
        applyHazardCost(level, rng, hl);
    }

    // ======== Rare and cross-role events ========

    private boolean rollRareEvent(Level level, int zone, Random rng) {
        float p = rng.nextFloat();
        float cursor = 0f;

        // WARDEN'S_BREATH — cave zone 2 only (0.5%)
        if ("cave".equals(simBiomeCategory) && zone == 2) {
            cursor += 0.005f;
            if (p < cursor) {
                addLogLine("Did not make a sound. Not one.");
                addLogLine("Three hours of perfect silence.");
                addLogLine("Emerged with mud on the paws and a look you don't ask about.");
                simPendingLoot.add(new ItemStack(Items.ECHO_SHARD, 1 + rng.nextInt(2)));
                if (rng.nextFloat() < 0.25f) applyHazardCost(level, rng, HazardLevel.MODERATE);
                return true;
            }
        }

        // WOLF_PACK_STANDOFF — forest/plains (2%)
        if ("forest".equals(simBiomeCategory) || "plains".equals(simBiomeCategory)) {
            cursor += 0.02f;
            if (p < cursor) {
                addLogLine("A pack emerged from the tree line.");
                addLogLine("Did not run. Neither did they.");
                addLogLine("When the moon moved, both groups went their separate ways.");
                return true;
            }
        }

        // ECHO_OF_THE_DEEP — underground zone 2 (1%)
        if (zone == 2 && (simHasMining || "cave".equals(simBiomeCategory))) {
            cursor += 0.01f;
            if (p < cursor) {
                addLogLine("The silence changed quality.");
                if (!simPendingLoot.isEmpty()) {
                    simPendingLoot.remove(simPendingLoot.size() - 1);
                    addLogLine("Came back without something. Won't say why.");
                } else {
                    addLogLine("Something down there. Left quickly.");
                }
                return true;
            }
        }

        // GEODE_CRACK — underground/mountain, miner preferred (3% miner, 1% other)
        if ("cave".equals(simBiomeCategory) || "mountain".equals(simBiomeCategory) || simHasMining) {
            cursor += simHasMining ? 0.03f : 0.01f;
            if (p < cursor) {
                addLogLine("The stone rang like a bell when struck.");
                addLogLine("Inside: violet. Still growing.");
                simPendingLoot.add(new ItemStack(Items.AMETHYST_SHARD, 4 + rng.nextInt(5)));
                return true;
            }
        }

        // STRONGHOLD_LIBRARY — underground zone 1+, collar 2+ (0.75%)
        if (zone >= 1 && simCollarTier >= 2 && (simHasMining || "cave".equals(simBiomeCategory))) {
            cursor += 0.0075f;
            if (p < cursor) {
                addLogLine("The passage opened into something vast and paper-smelling.");
                addLogLine("Cannot read. Brought back a page anyway.");
                simPendingLoot.add(new ItemStack(Items.BOOK, 1));
                if (simCollarTier >= 3) {
                    simPendingLoot.add(new ItemStack(Items.LAPIS_LAZULI, 4 + rng.nextInt(8)));
                }
                return true;
            }
        }

        // ABANDONED_MINESHAFT_FIND — cave, miner preferred (4% miner, 2% other)
        if ("cave".equals(simBiomeCategory) || simHasMining) {
            cursor += simHasMining ? 0.04f : 0.02f;
            if (p < cursor) {
                addLogLine("The shaft went deeper than it should have.");
                addLogLine("Came back with mud on the paws and something extra.");
                if (simCollarTier >= 3 && rng.nextFloat() < 0.2f) {
                    simPendingLoot.add(new ItemStack(Items.EMERALD, 1));
                } else {
                    int ore = rng.nextInt(3);
                    if (ore == 0) simPendingLoot.add(new ItemStack(Items.RAW_GOLD, 2 + rng.nextInt(3)));
                    else if (ore == 1) simPendingLoot.add(new ItemStack(Items.RAW_IRON, 3 + rng.nextInt(4)));
                    else simPendingLoot.add(new ItemStack(Items.COAL, 4 + rng.nextInt(6)));
                }
                return true;
            }
        }

        // ELDER_GUARDIAN_SHADOW — ocean biome (1.5%)
        if ("ocean".equals(simBiomeCategory)) {
            cursor += 0.015f;
            if (p < cursor) {
                addLogLine("The water darkened beneath.");
                addLogLine("Something vast passed. It did not stop.");
                if (rng.nextFloat() < 0.4f) simPendingLoot.add(new ItemStack(Items.PRISMARINE_CRYSTALS, 1 + rng.nextInt(3)));
                if (simCollarTier >= 3 && rng.nextFloat() < 0.3f) simPendingLoot.add(new ItemStack(Items.NAUTILUS_SHELL, 1));
                return true;
            }
        }

        // WANDERING_TRADER_DEAL — surface (2%)
        if (!"cave".equals(simBiomeCategory)) {
            cursor += 0.02f;
            if (p < cursor) {
                addLogLine("A man with two llamas. Offered something wrapped in cloth.");
                addLogLine("Gave him a ration. He seemed satisfied.");
                simSatiation -= 2;
                Item[] traderGoods = {Items.BLUE_DYE, Items.KELP, Items.PUMPKIN_SEEDS, Items.CACTUS, Items.DEAD_BUSH, Items.FERN};
                simPendingLoot.add(new ItemStack(traderGoods[rng.nextInt(traderGoods.length)], 1 + rng.nextInt(3)));
                return true;
            }
        }

        // BURIED_CHEST — plains/ocean (1.5%)
        if ("plains".equals(simBiomeCategory) || "ocean".equals(simBiomeCategory)) {
            cursor += 0.015f;
            if (p < cursor) {
                addLogLine("Dug because of the smell of iron. Was right.");
                if (simCollarTier >= 4 && rng.nextInt(10) == 0) {
                    simPendingLoot.add(new ItemStack(Items.HEART_OF_THE_SEA, 1));
                } else if (rng.nextInt(3) == 0) {
                    simPendingLoot.add(new ItemStack(Items.RAW_GOLD, 2 + rng.nextInt(4)));
                } else {
                    simPendingLoot.add(new ItemStack(Items.RAW_IRON, 3 + rng.nextInt(5)));
                    simPendingLoot.add(new ItemStack(Items.COAL, 2 + rng.nextInt(3)));
                }
                return true;
            }
        }

        // ANCIENT_INSCRIPTION — collar 3+ (1%)
        if (simCollarTier >= 3) {
            cursor += 0.01f;
            if (p < cursor) {
                addLogLine("Paused at a moss-covered stone. Strange marks.");
                addLogLine("Sat with it a long while before moving on.");
                simSatiation += 4;
                return true;
            }
        }

        // BONE_CROWN — collar 4 only (0.5%)
        if (simCollarTier >= 4) {
            cursor += 0.005f;
            if (p < cursor) {
                addLogLine("Wedged in a root: a circlet of bone and river-iron.");
                addLogLine("Old and deliberate. Brought it home.");
                simPendingLoot.add(new ItemStack(Items.BONE, 1));
                return true;
            }
        }

        // SPIDER_NEST — forest/cave (2%)
        if ("forest".equals(simBiomeCategory) || "cave".equals(simBiomeCategory)) {
            cursor += 0.02f;
            if (p < cursor) {
                addLogLine("The web was thick as rope. The eggs were not all hatched.");
                boolean success = simCollarTier >= 2 || rng.nextFloat() < 0.5f;
                if (success) {
                    addLogLine("Destroyed what was there. Took time.");
                    simPendingLoot.add(new ItemStack(Items.STRING, 2 + rng.nextInt(4) + simLooting));
                    if (rng.nextFloat() < 0.5f) simPendingLoot.add(new ItemStack(Items.SPIDER_EYE, rng.nextInt(3) + simLooting));
                    applyHazardCost(level, rng, HazardLevel.LIGHT);
                } else {
                    addLogLine("Too many. Retreated.");
                    applyHazardCost(level, rng, HazardLevel.MODERATE);
                }
                return true;
            }
        }

        // THE_VEIN_THAT_SHOULDNT_BE — miner only, underground (1%)
        if (simHasMining && ("cave".equals(simBiomeCategory) || "mountain".equals(simBiomeCategory))) {
            cursor += 0.01f;
            if (p < cursor) {
                addLogLine("Copper. Then iron. Then something that wasn't either.");
                addLogLine("Brought back what could be carried.");
                simPendingLoot.add(new ItemStack(Items.RAW_COPPER, 2 + rng.nextInt(3)));
                simPendingLoot.add(new ItemStack(Items.RAW_IRON, 1 + rng.nextInt(3)));
                if (simCollarTier >= 4 && rng.nextFloat() < 0.15f) {
                    simPendingLoot.add(new ItemStack(Items.ANCIENT_DEBRIS, 1));
                } else {
                    simPendingLoot.add(new ItemStack(Items.GOLD_INGOT, 1 + rng.nextInt(2)));
                }
                return true;
            }
        }

        return false;
    }

    private boolean rollCrossRoleEvent(Level level, Random rng) {
        if (simHasHunting && simHasWoodcutting && !simHasMining) {
            String[] lines = {
                "Was lining up a cut when a boar appeared at the next tree. Held still. Eventually continued.",
                "The tree came down wrong and flushed something large from the understory. It was a deer. Both equally startled.",
                "Took a boar while the brush fire was still burning. The smoke slowed it down.",
                "A wolf den under the root mass of a fallen oak. No adults home. Noted the location. Moving on."
            };
            addLogLine(lines[rng.nextInt(lines.length)]);
            return true;
        }
        if (simHasHunting && simHasMining && !simHasWoodcutting) {
            String[] lines = {
                "The tunnel opened into a cavity. Something had been denning there. Backed out. Some places choose their tenants.",
                "Killed a spider in the tunnels and found iron wrapped in silk behind it.",
                "A seam of coal in the wall. On the other side: something scratching. Rhythmic. Deliberate.",
                "The miner in me said forty feet underground with nothing ahead. The miner won. Today."
            };
            addLogLine(lines[rng.nextInt(lines.length)]);
            return true;
        }
        if (simHasMining && simHasWoodcutting && !simHasHunting) {
            String[] lines = {
                "Been underground long enough that wood smells strange now. Too bright. Too alive.",
                "Came up early. Spent the afternoon cutting oak just to remember daylight.",
                "The tunnel roof was lined with roots. Careful work not to collapse the whole thing.",
                "Dug through to a hollow full of roots. The forest above had no idea."
            };
            addLogLine(lines[rng.nextInt(lines.length)]);
            return true;
        }
        if (simHasHunting && simHasMining && simHasWoodcutting) {
            String[] lines = {
                "Too much to do in one place. Prioritised. Got most of it done.",
                "Good day. Everything went roughly according to plan.",
                "Three jobs. Managed two properly. Filed the third for later."
            };
            addLogLine(lines[rng.nextInt(lines.length)]);
            return true;
        }
        return false;
    }

    // ======== Shared hazard cost ========

    private void applyHazardCost(Level level, Random rng, HazardLevel hl) {
        simSatiation -= switch (hl) {
            case LIGHT -> 2;
            case MODERATE -> 4;
            case SEVERE -> 3;
        };

        // Severe hazards always cause a direct injury; armor can absorb it
        if (hl == HazardLevel.SEVERE) {
            boolean armorAbsorbs = simArmorPoints >= 8 || (simArmorPoints >= 4 && rng.nextFloat() < 0.5f);
            if (!armorAbsorbs) {
                simInjuryCount++;
                if (simInjuryCount >= 3) {
                    boolean death = simArmorPoints < 4 && rng.nextFloat() < 0.12f;
                    completeSimulation(level, true, death);
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
                    completeSimulation(level, true, death);
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
            String[] deathLines = {"Didn't come back.", "Gone.", "The expedition ended."};
            addLogLine(deathLines[level.getRandom().nextInt(deathLines.length)]);
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

        WorkingWolvesPackets.pushBedState(level, this.worldPosition, this);
        setChanged();
    }

    public boolean recallExpedition() {
        if (!"running".equals(simState) || this.level == null) return false;

        simState = "complete";
        String[] recallLines = {"Called back early.", "Recalled. Not finished. Going home.", "Whistle from home. Turning back."};
        addLogLine(recallLines[new Random().nextInt(recallLines.length)]);

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
        WorkingWolvesPackets.pushBedState(this.level, this.worldPosition, this);
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

        String[] arrivalLines = {"Back at the bed.", "Home.", "Found the way back."};
        addLogLine(arrivalLines[new Random(level.getGameTime()).nextInt(arrivalLines.length)]);
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
