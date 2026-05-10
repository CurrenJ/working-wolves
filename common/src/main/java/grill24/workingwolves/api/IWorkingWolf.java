package grill24.workingwolves.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Interface implemented by wolf mixin. AI goals and items reference this
 * instead of WolfMixin directly to avoid cross-namespace class loading issues
 * in the Architectury NeoForge transformer pipeline.
 */
public interface IWorkingWolf {
    int workingwolves$getCollarTier();
    void workingwolves$setCollarTier(int tier);

    @Nullable String workingwolves$getWolfClass();
    void workingwolves$setWolfClass(@Nullable String wolfClass);

    @Nullable BlockPos workingwolves$getBedPos();
    void workingwolves$setBedPos(@Nullable BlockPos pos);

    String workingwolves$getExpeditionState();
    void workingwolves$setExpeditionState(String state);

    long workingwolves$getExpeditionStartTime();
    void workingwolves$setExpeditionStartTime(long time);

    int workingwolves$getExpeditionDuration();
    void workingwolves$setExpeditionDuration(int duration);

    NonNullList<ItemStack> workingwolves$getBagInventory();

    ItemStack workingwolves$getFilterItem();
    void workingwolves$setFilterItem(ItemStack stack);

    ItemStack workingwolves$getMouthItem();
    void workingwolves$setMouthItem(ItemStack stack);

    default void workingwolves$displayMouthItem(ItemStack stack) {
        workingwolves$setMouthItem(stack);
        workingwolves$syncData();
    }

    default void workingwolves$clearMouthItem() {
        workingwolves$setMouthItem(ItemStack.EMPTY);
        workingwolves$syncData();
    }

    void workingwolves$setCollarColorFromTier(net.minecraft.world.item.DyeColor color);
    void workingwolves$syncData();
    void workingwolves$resizeBag();

    default void workingwolves$applyNavBudget(int range) {
        net.minecraft.world.entity.animal.wolf.Wolf self = (net.minecraft.world.entity.animal.wolf.Wolf) this;
        self.getNavigation().pathFinder.setMaxVisitedNodes(range * range / 4);
        self.getNavigation().requiredPathLength = (float) range;
    }

    default boolean workingwolves$isExpeditionExpired(long gameTime) {
        long startTime = workingwolves$getExpeditionStartTime();
        if (startTime <= 0) return false;
        return gameTime - startTime >= workingwolves$getExpeditionDuration();
    }

    default void workingwolves$triggerReturn() {
        workingwolves$setExpeditionState("returning");
        workingwolves$syncData();
    }

    @Nullable BlockPos workingwolves$getMiningPos();
    void workingwolves$setMiningPos(@Nullable BlockPos pos);

    int workingwolves$getMiningProgress();
    void workingwolves$setMiningProgress(int progress);

    int workingwolves$getUnlockedSlots();
    void workingwolves$setUnlockedSlots(int slots);
    void workingwolves$unlockSlot();
}
