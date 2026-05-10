package grill24.workingwolves.item;

import grill24.workingwolves.api.IWorkingWolf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class DispatchWhistleItem extends Item {
    public DispatchWhistleItem(Properties properties) {
        super(properties);
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

            // Check wolf has a collar
            if (accessor.workingwolves$getCollarTier() == 0) {
                player.sendSystemMessage(
                    Component.translatable("message.workingwolves.no_collar"));
                return InteractionResult.FAIL;
            }

            // Toggle expedition state
            String currentState = accessor.workingwolves$getExpeditionState();
            if ("active".equals(currentState)) {
                accessor.workingwolves$setExpeditionState("idle");
                accessor.workingwolves$syncData();
                player.sendSystemMessage(
                    Component.translatable("message.workingwolves.wolf_recalled"));
            } else {
                accessor.workingwolves$setExpeditionState("active");
                accessor.workingwolves$setExpeditionStartTime(wolf.level().getGameTime());
                wolf.setOrderedToSit(false); // SitWhenOrderedToGoal.stop() unsets pose naturally
                accessor.workingwolves$syncData();
                player.sendSystemMessage(
                    Component.translatable("message.workingwolves.wolf_dispatched"));
            }
        }

        return InteractionResult.SUCCESS;
    }
}
