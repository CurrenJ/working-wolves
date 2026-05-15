package grill24.workingwolves.inventory;

import grill24.workingwolves.ModMenuTypes;
import grill24.workingwolves.blockentity.DogBedBlockEntity;
import io.github.currenj.gelatinui.gui.GelatinMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class DogBedMenu extends GelatinMenu {
    // Layout constants (relative to leftPos/topPos in AbstractContainerScreen)
    public static final int IMAGE_WIDTH = 356;
    public static final int IMAGE_HEIGHT = 240;

    private static final int BED_SLOTS_X = 178;
    private static final int BED_SLOTS_Y = 74;
    private static final int PLAYER_INV_X = 178;
    private static final int PLAYER_INV_Y = 160;
    private static final int HOTBAR_Y = 218;

    private static final int BED_SLOT_COUNT = 27;

    private final BlockPos bedPos;
    private final DogBedBlockEntity be; // null on client

    // Server-side constructor
    public DogBedMenu(int containerId, Inventory playerInv, DogBedBlockEntity be) {
        super(ModMenuTypes.getDogBedMenuType(), containerId);
        this.bedPos = be.getBlockPos();
        this.be = be;
        addBedSlots(be);
        addPlayerSlots(playerInv);
    }

    // Client-side constructor (called by MenuType factory)
    public DogBedMenu(int containerId, Inventory playerInv) {
        super(ModMenuTypes.getDogBedMenuType(), containerId);
        this.bedPos = ModMenuTypes.pendingBedPos;
        this.be = null;
        addBedSlots(new SimpleContainer(BED_SLOT_COUNT));
        addPlayerSlots(playerInv);
    }

    private void addBedSlots(Container bedInv) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(bedInv, row * 9 + col,
                    BED_SLOTS_X + col * 18,
                    BED_SLOTS_Y + row * 18));
            }
        }
    }

    private void addPlayerSlots(Inventory playerInv) {
        // Player main inventory (rows 1-3, indices 9-35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, 9 + row * 9 + col,
                    PLAYER_INV_X + col * 18,
                    PLAYER_INV_Y + row * 18));
            }
        }
        // Hotbar (row 0, indices 0-8)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col,
                PLAYER_INV_X + col * 18,
                HOTBAR_Y));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (slotIndex < BED_SLOT_COUNT) {
            // Bed → player inventory
            if (!moveItemStackTo(stack, BED_SLOT_COUNT, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player inventory → bed
            if (!moveItemStackTo(stack, 0, BED_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (be != null) {
            return be.stillValid(player);
        }
        return true;
    }

    public BlockPos getBedPos() {
        return bedPos;
    }
}
