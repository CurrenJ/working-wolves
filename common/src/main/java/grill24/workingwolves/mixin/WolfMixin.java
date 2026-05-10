package grill24.workingwolves.mixin;

import com.mojang.serialization.Codec;
import grill24.workingwolves.Config;
import grill24.workingwolves.ai.AntiStuckGoal;
import grill24.workingwolves.ai.HunterGoal;
import grill24.workingwolves.ai.MinerGoal;
import grill24.workingwolves.ai.ReturnToBaseGoal;
import grill24.workingwolves.ai.RetrieverGoal;
import grill24.workingwolves.ai.SelfPreservationGoal;
import grill24.workingwolves.api.IWorkingWolf;
import grill24.workingwolves.blockentity.DogBedBlockEntity;
import grill24.workingwolves.inventory.WolfBagContainer;
import grill24.workingwolves.item.CollarItem;
import grill24.workingwolves.pairing.WolfBedPairing;
import grill24.workingwolves.item.DispatchWhistleItem;
import grill24.workingwolves.item.RecallWhistleItem;
import grill24.workingwolves.network.WorkingWolvesPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Wolf.class)
public abstract class WolfMixin extends TamableAnimal implements IWorkingWolf {

    // Private constructor for mixin — never actually called
    protected WolfMixin(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    // ======== Added fields ========

    @Unique
    private int workingwolves$collarTier = 0;

    @Unique
    @Nullable
    private String workingwolves$wolfClass = null;

    @Unique
    @Nullable
    private BlockPos workingwolves$bedPos = null;

    @Unique
    private String workingwolves$expeditionState = "idle";

    @Unique
    private long workingwolves$expeditionStartTime = 0;

    @Unique
    private int workingwolves$expeditionDuration = 0;

    @Unique
    private NonNullList<ItemStack> workingwolves$bagInventory = NonNullList.withSize(0, ItemStack.EMPTY);

    @Unique
    private ItemStack workingwolves$filterItem = ItemStack.EMPTY;

    @Unique
    private int workingwolves$unlockedSlots = 0;

    // ======== Mining progress (transient, not persisted) ========

    @Unique
    @Nullable
    private BlockPos workingwolves$miningPos = null;

    @Unique
    private int workingwolves$miningProgress = 0;

    // ======== Persistence ========

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void workingwolves$addAdditionalSaveData(ValueOutput output, CallbackInfo ci) {
        output.putInt("ww_collar_tier", this.workingwolves$collarTier);
        output.storeNullable("ww_wolf_class", Codec.STRING, this.workingwolves$wolfClass);
        output.storeNullable("ww_bed_pos", BlockPos.CODEC, this.workingwolves$bedPos);
        output.putString("ww_expedition_state", this.workingwolves$expeditionState);
        output.putLong("ww_expedition_start_time", this.workingwolves$expeditionStartTime);
        output.putInt("ww_expedition_duration", this.workingwolves$expeditionDuration);
        output.store("ww_bag", ItemStack.OPTIONAL_CODEC.listOf(), this.workingwolves$bagInventory);
        output.store("ww_filter_item", ItemStack.OPTIONAL_CODEC, this.workingwolves$filterItem);
        output.putInt("ww_unlocked_slots", this.workingwolves$unlockedSlots);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void workingwolves$readAdditionalSaveData(ValueInput input, CallbackInfo ci) {
        this.workingwolves$collarTier = input.getIntOr("ww_collar_tier", 0);
        this.workingwolves$wolfClass = input.read("ww_wolf_class", Codec.STRING).orElse(null);
        this.workingwolves$bedPos = input.read("ww_bed_pos", BlockPos.CODEC).orElse(null);
        this.workingwolves$expeditionState = input.getStringOr("ww_expedition_state", "idle");
        this.workingwolves$expeditionStartTime = input.getLongOr("ww_expedition_start_time", 0);
        this.workingwolves$expeditionDuration = input.getIntOr("ww_expedition_duration", 0);

        this.workingwolves$bagInventory = NonNullList.withSize(getBagSize(), ItemStack.EMPTY);
        input.read("ww_bag", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(items -> {
            for (int i = 0; i < Math.min(items.size(), this.workingwolves$bagInventory.size()); i++) {
                this.workingwolves$bagInventory.set(i, items.get(i));
            }
        });

        this.workingwolves$filterItem = input.read("ww_filter_item", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        this.workingwolves$unlockedSlots = input.getIntOr("ww_unlocked_slots", 0);

        // Restore collar color based on tier
        Wolf self = (Wolf) (Object) this;
        if (this.workingwolves$collarTier > 0) {
            DyeColor expectedColor = switch (this.workingwolves$collarTier) {
                case 1 -> DyeColor.BROWN;
                case 2 -> DyeColor.GRAY;
                case 3 -> DyeColor.YELLOW;
                default -> DyeColor.RED;
            };
            ((Wolf) (Object) this).setCollarColor(expectedColor);
        }
    }

    // ======== Accessor methods ========

    @Unique
    public int workingwolves$getCollarTier() {
        return this.workingwolves$collarTier;
    }

    @Unique
    public void workingwolves$setCollarTier(int tier) {
        this.workingwolves$collarTier = tier;
    }

    @Unique
    @Nullable
    public String workingwolves$getWolfClass() {
        return this.workingwolves$wolfClass;
    }

    @Unique
    public void workingwolves$setWolfClass(@Nullable String wolfClass) {
        this.workingwolves$wolfClass = wolfClass;
    }

    @Unique
    @Nullable
    public BlockPos workingwolves$getBedPos() {
        return this.workingwolves$bedPos;
    }

    @Unique
    public void workingwolves$setBedPos(@Nullable BlockPos pos) {
        this.workingwolves$bedPos = pos;
    }

    @Unique
    public String workingwolves$getExpeditionState() {
        return this.workingwolves$expeditionState;
    }

    @Unique
    public void workingwolves$setExpeditionState(String state) {
        this.workingwolves$expeditionState = state;
    }

    @Unique
    public long workingwolves$getExpeditionStartTime() {
        return this.workingwolves$expeditionStartTime;
    }

    @Unique
    public void workingwolves$setExpeditionStartTime(long time) {
        this.workingwolves$expeditionStartTime = time;
    }

    @Unique
    public int workingwolves$getExpeditionDuration() {
        return this.workingwolves$expeditionDuration;
    }

    @Unique
    public void workingwolves$setExpeditionDuration(int duration) {
        this.workingwolves$expeditionDuration = duration;
    }

    @Unique
    public NonNullList<ItemStack> workingwolves$getBagInventory() {
        return this.workingwolves$bagInventory;
    }

    @Unique
    public ItemStack workingwolves$getFilterItem() {
        return this.workingwolves$filterItem;
    }

    @Unique
    public void workingwolves$setFilterItem(ItemStack stack) {
        this.workingwolves$filterItem = stack;
    }

    @Unique
    public int workingwolves$getUnlockedSlots() {
        return this.workingwolves$unlockedSlots;
    }

    @Unique
    public void workingwolves$setUnlockedSlots(int slots) {
        this.workingwolves$unlockedSlots = slots;
    }

    // ======== Network sync ========

    @Unique
    public void workingwolves$setCollarColorFromTier(DyeColor color) {
        ((Wolf) (Object) this).setCollarColor(color);
    }

    @Unique
    public void workingwolves$syncData() {
        if (!((Wolf) (Object) this).level().isClientSide()) {
            WorkingWolvesPackets.syncWolfData((Wolf) (Object) this);
        }
    }

    // ======== Mining progress accessors ========

    @Unique
    @Nullable
    public BlockPos workingwolves$getMiningPos() {
        return this.workingwolves$miningPos;
    }

    @Unique
    public void workingwolves$setMiningPos(@Nullable BlockPos pos) {
        this.workingwolves$miningPos = pos;
    }

    @Unique
    public int workingwolves$getMiningProgress() {
        return this.workingwolves$miningProgress;
    }

    @Unique
    public void workingwolves$setMiningProgress(int progress) {
        this.workingwolves$miningProgress = progress;
    }

    @Unique
    public void workingwolves$resizeBag() {
        int newSize = getBagSize();
        NonNullList<ItemStack> oldBag = this.workingwolves$bagInventory;
        this.workingwolves$bagInventory = NonNullList.withSize(newSize, ItemStack.EMPTY);
        if (oldBag != null) {
            for (int i = 0; i < Math.min(oldBag.size(), newSize); i++) {
                this.workingwolves$bagInventory.set(i, oldBag.get(i));
            }
        }
    }

    // ======== Helper ========

    @Unique
    private int getBaseBagSize() {
        return switch (this.workingwolves$collarTier) {
            case 1 -> 5;
            case 2 -> 9;
            case 3 -> 15;
            default -> 0;
        };
    }

    @Unique
    private int getBagSize() {
        return getBaseBagSize() + this.workingwolves$unlockedSlots;
    }

    @Unique
    public void workingwolves$unlockSlot() {
        this.workingwolves$unlockedSlots++;
        int newSize = getBagSize();
        NonNullList<ItemStack> oldBag = this.workingwolves$bagInventory;
        this.workingwolves$bagInventory = NonNullList.withSize(newSize, ItemStack.EMPTY);
        if (oldBag != null) {
            for (int i = 0; i < oldBag.size(); i++) {
                this.workingwolves$bagInventory.set(i, oldBag.get(i));
            }
        }
    }

    // ======== Intercept custom items in mobInteract ========

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void workingwolves$mobInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        Wolf self = (Wolf) (Object) this;

        // Collar — delegate to CollarItem.interactLivingEntity
        if (stack.getItem() instanceof CollarItem collar) {
            InteractionResult result = collar.interactLivingEntity(stack, player, self, hand);
            cir.setReturnValue(result);
            return;
        }

        // Dispatch or recall whistle — delegate to the item's interactLivingEntity
        if (stack.getItem() instanceof DispatchWhistleItem || stack.getItem() instanceof RecallWhistleItem) {
            InteractionResult result = stack.getItem().interactLivingEntity(stack, player, self, hand);
            cir.setReturnValue(result);
            return;
        }

        // Shift-right-click with empty hand on owned collared wolf
        if (player.isShiftKeyDown() && stack.isEmpty() && self.isTame() && self.isOwnedBy(player)) {
            IWorkingWolf mixin = (IWorkingWolf) (Object) this;
            if (mixin.workingwolves$getCollarTier() > 0) {
                if (!self.level().isClientSide() && player instanceof ServerPlayer sp) {
                    // If a bed pairing is active, pair this wolf to that bed
                    WolfBedPairing.PendingBed pendingBed = WolfBedPairing.consumeBedPairing(sp);
                    if (pendingBed != null) {
                        BlockPos bedPos = pendingBed.bedPos();
                        if (self.level().getBlockEntity(bedPos) instanceof DogBedBlockEntity be) {
                            String wolfName = self.hasCustomName() ? self.getCustomName().getString() : null;
                            be.assignWolf(self.getUUID(), wolfName);
                            mixin.workingwolves$setBedPos(bedPos);
                            mixin.workingwolves$syncData();
                            sp.sendSystemMessage(Component.translatable("message.workingwolves.bed_assigned"));
                        }
                    } else {
                        // No active bed pairing — open bag GUI
                        WolfBagContainer container = new WolfBagContainer(self);
                        String title = self.hasCustomName()
                            ? self.getCustomName().getString() + "'s Bag"
                            : "Wolf Bag";
                        player.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> ChestMenu.threeRows(id, inv, container),
                            Component.literal(title)
                        ));
                    }
                }
                cir.setReturnValue(InteractionResult.SUCCESS);
            }
        }
    }

    // ======== AI goal registration ========

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void workingwolves$registerGoals(CallbackInfo ci) {
        Wolf self = (Wolf) (Object) this;
        this.goalSelector.addGoal(0, new AntiStuckGoal(self));
        this.goalSelector.addGoal(0, new SelfPreservationGoal(self));
        this.goalSelector.addGoal(1, new ReturnToBaseGoal(self));
        this.goalSelector.addGoal(2, new RetrieverGoal(self));
        this.goalSelector.addGoal(2, new HunterGoal(self));
        this.goalSelector.addGoal(2, new MinerGoal(self));
    }
}
