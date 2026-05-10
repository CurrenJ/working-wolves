package grill24.workingwolves.inventory;

import grill24.workingwolves.api.IWorkingWolf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class WolfBagHelper {

    public static ItemStack addToBag(NonNullList<ItemStack> bag, ItemStack stack) {
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

    public static ItemStack tryInsert(Container container, ItemStack stack) {
        for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack slotStack = container.getItem(i);
            if (slotStack.isEmpty()) {
                container.setItem(i, stack.copy());
                container.setChanged();
                return ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(slotStack, stack)) {
                int transfer = Math.min(stack.getCount(), slotStack.getMaxStackSize() - slotStack.getCount());
                if (transfer > 0) {
                    slotStack.grow(transfer);
                    stack.shrink(transfer);
                    container.setChanged();
                }
            }
        }
        return stack;
    }

    public static void collectDropsAt(Level level, BlockPos pos, IWorkingWolf mixin, int range) {
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class,
            new AABB(pos).inflate(range), ItemEntity::isAlive);
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

    public static boolean isBagFull(IWorkingWolf mixin) {
        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();
        if (bag.isEmpty()) return true;
        for (ItemStack stack : bag) {
            if (stack.isEmpty()) return false;
        }
        return true;
    }

    public static boolean isBagFull(IWorkingWolf mixin, float threshold) {
        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();
        if (bag.isEmpty()) return true;
        int usedSlots = 0;
        for (ItemStack stack : bag) {
            if (!stack.isEmpty()) usedSlots++;
        }
        return (float) usedSlots / bag.size() >= threshold;
    }

    /** Attempts to eat one food item from the wolf's bag to heal. Returns true if food was found and consumed. */
    public static boolean eatFoodFromBag(IWorkingWolf mixin, LivingEntity wolf, float healAmount) {
        NonNullList<ItemStack> bag = mixin.workingwolves$getBagInventory();
        for (int i = 0; i < bag.size(); i++) {
            ItemStack stack = bag.get(i);
            if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) {
                stack.shrink(1);
                wolf.heal(healAmount);
                if (stack.isEmpty()) {
                    bag.set(i, ItemStack.EMPTY);
                }
                return true;
            }
        }
        return false;
    }
}
