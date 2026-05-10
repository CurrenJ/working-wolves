package grill24.workingwolves.item;

import grill24.workingwolves.Config;
import grill24.workingwolves.ModItems;
import grill24.workingwolves.chunk.WolfChunkManager;
import grill24.workingwolves.api.IWorkingWolf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CollarItem extends Item {
    private final int tier;
    private final int bagSlots;
    private final int expeditionDurationTicks;

    public CollarItem(Properties properties, int tier, int bagSlots, int expeditionDurationTicks) {
        super(properties);
        this.tier = tier;
        this.bagSlots = bagSlots;
        this.expeditionDurationTicks = expeditionDurationTicks;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Wolf wolf)) {
            return InteractionResult.PASS;
        }

        if (!wolf.isTame()) {
            return InteractionResult.PASS;
        }

        if (!wolf.isOwnedBy(player)) {
            if (!player.level().isClientSide()) {
                player.sendSystemMessage(Component.translatable("message.workingwolves.not_owner"));
            }
            return InteractionResult.FAIL;
        }

        if (!player.level().isClientSide()) {
            IWorkingWolf accessor = (IWorkingWolf) (Object) wolf;

            int oldTier = accessor.workingwolves$getCollarTier();

            // Enforce per-player wolf cap (only when applying to a wolf without a collar)
            if (oldTier == 0 && WolfChunkManager.countWorkingWolves(player) >= Config.maxWolvesPerPlayer) {
                player.sendSystemMessage(
                    Component.translatable("message.workingwolves.wolf_cap_reached",
                        Config.maxWolvesPerPlayer));
                return InteractionResult.FAIL;
            }

            // If wolf already has a collar, return the old one to the player
            if (oldTier > 0) {
                ItemStack oldCollar = CollarItem.createCollarForTier(oldTier);
                if (!player.addItem(oldCollar)) {
                    player.drop(oldCollar, false);
                }
            }

            // Apply new collar data
            accessor.workingwolves$setCollarTier(tier);
            accessor.workingwolves$setExpeditionDuration(expeditionDurationTicks);

            // Keep existing class or set default
            String existingClass = accessor.workingwolves$getWolfClass();
            if (existingClass == null) {
                accessor.workingwolves$setWolfClass(CollarItem.getDefaultClassForTier(tier));
            }

            // Resize bag if tier changed
            if (oldTier != tier) {
                accessor.workingwolves$resizeBag();
            }

            // Sync collar color for visual
            DyeColor color = CollarItem.getCollarColorForTier(tier);
            ((IWorkingWolf) (Object) wolf).workingwolves$setCollarColorFromTier(color);

            // Consume the collar item
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }

            player.sendSystemMessage(
                Component.translatable("message.workingwolves.collar_applied",
                    Component.translatable("item.workingwolves." + getCollarName(tier))));

            // Sync updated data to tracking clients
            accessor.workingwolves$syncData();
        }

        return InteractionResult.SUCCESS;
    }

    private static String getCollarName(int tier) {
        return switch (tier) {
            case 1 -> "leather_collar";
            case 2 -> "iron_studded_collar";
            case 3 -> "gold_trimmed_collar";
            default -> "leather_collar";
        };
    }

    public int getTier() {
        return tier;
    }

    public int getBagSlots() {
        return bagSlots;
    }

    public int getExpeditionDurationTicks() {
        return expeditionDurationTicks;
    }

    public static ItemStack createCollarForTier(int tier) {
        return switch (tier) {
            case 1 -> new ItemStack(ModItems.LEATHER_COLLAR.value());
            case 2 -> new ItemStack(ModItems.IRON_STUDDED_COLLAR.value());
            case 3 -> new ItemStack(ModItems.GOLD_TRIMMED_COLLAR.value());
            default -> ItemStack.EMPTY;
        };
    }

    public static String getDefaultClassForTier(int tier) {
        return switch (tier) {
            case 1 -> "retriever";
            case 2 -> "hunter";
            case 3 -> "miner";
            default -> "retriever";
        };
    }

    public static DyeColor getCollarColorForTier(int tier) {
        return switch (tier) {
            case 1 -> DyeColor.BROWN;
            case 2 -> DyeColor.GRAY;
            case 3 -> DyeColor.YELLOW;
            default -> DyeColor.RED;
        };
    }
}
