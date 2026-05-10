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

    void workingwolves$setCollarColorFromTier(net.minecraft.world.item.DyeColor color);
    void workingwolves$syncData();
    void workingwolves$resizeBag();

    @Nullable BlockPos workingwolves$getMiningPos();
    void workingwolves$setMiningPos(@Nullable BlockPos pos);

    int workingwolves$getMiningProgress();
    void workingwolves$setMiningProgress(int progress);

    int workingwolves$getUnlockedSlots();
    void workingwolves$setUnlockedSlots(int slots);
    void workingwolves$unlockSlot();
}
