package grill24.workingwolves.blockentity;

import grill24.workingwolves.ModBlockEntityTypes;
import grill24.workingwolves.inventory.WolfBagHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class DogBedBlockEntity extends BlockEntity implements Container {
    private final NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);

    private UUID assignedWolfUuid = null;
    private String assignedWolfName = null;

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
}
