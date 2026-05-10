package grill24.workingwolves.inventory;

import grill24.workingwolves.api.IWorkingWolf;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class WolfBagContainer implements Container {
    private static final int DISPLAY_SIZE = 27;
    private final Wolf wolf;

    public WolfBagContainer(Wolf wolf) {
        this.wolf = wolf;
    }

    private IWorkingWolf mixin() {
        return (IWorkingWolf) (Object) wolf;
    }

    private NonNullList<ItemStack> bag() {
        return mixin().workingwolves$getBagInventory();
    }

    public int usable() {
        return bag().size();
    }

    @Override
    public int getContainerSize() {
        return DISPLAY_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : bag()) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < usable()) return bag().get(slot);
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot >= usable()) return ItemStack.EMPTY;
        ItemStack stack = bag().get(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = stack.split(amount);
        if (stack.isEmpty()) bag().set(slot, ItemStack.EMPTY);
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot >= usable()) return ItemStack.EMPTY;
        ItemStack stack = bag().get(slot);
        bag().set(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= DISPLAY_SIZE) return;

        // Locked slot + netherite ingot = unlock
        if (slot >= usable() && stack.is(Items.NETHERITE_INGOT)) {
            stack.shrink(1);
            mixin().workingwolves$unlockSlot();
            mixin().workingwolves$syncData();
            return;
        }

        if (slot >= usable()) return;
        bag().set(slot, stack);
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return wolf.isAlive() && wolf.distanceToSqr(player) <= 64.0;
    }

    @Override
    public void clearContent() {
        bag().clear();
    }

    /** Slot subclass that shows a barrier icon for locked slots without them being real items. */
    public static class LockedSlot extends Slot {
        private final int usableSlots;

        public LockedSlot(Container container, int slot, int x, int y, int usableSlots) {
            super(container, slot, x, y);
            this.usableSlots = usableSlots;
        }

        @Override
        public boolean hasItem() {
            return super.hasItem() || this.getContainerSlot() >= usableSlots;
        }

        @Override
        public ItemStack getItem() {
            if (this.getContainerSlot() >= usableSlots) {
                ItemStack barrier = new ItemStack(Items.BARRIER);
                barrier.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.translatable("container.workingwolves.locked_slot"));
                return barrier;
            }
            return super.getItem();
        }

        @Override
        public boolean mayPickup(Player player) {
            return this.getContainerSlot() < usableSlots;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (this.getContainerSlot() >= usableSlots) {
                return stack.is(Items.NETHERITE_INGOT);
            }
            return super.mayPlace(stack);
        }
    }
}
