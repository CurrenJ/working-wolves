package grill24.workingwolves.item;

import grill24.workingwolves.api.IWorkingWolf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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

            String currentState = accessor.workingwolves$getExpeditionState();

            // Cancel an active or departing expedition
            if ("on_expedition".equals(currentState) || "departing".equals(currentState)) {
                accessor.workingwolves$setExpeditionState("idle");
                wolf.setOrderedToSit(false);
                accessor.workingwolves$syncData();
                player.sendSystemMessage(
                    Component.translatable("message.workingwolves.wolf_recalled"));
                return InteractionResult.SUCCESS;
            }

            // Only hunters and miners can be dispatched
            String wolfClass = accessor.workingwolves$getWolfClass();
            if (!"hunter".equals(wolfClass) && !"miner".equals(wolfClass)) {
                player.sendSystemMessage(
                    Component.translatable("message.workingwolves.no_collar"));
                return InteractionResult.FAIL;
            }

            // Guard: wolf must have a bed assigned
            if (accessor.workingwolves$getBedPos() == null) {
                player.sendSystemMessage(
                    Component.translatable("message.workingwolves.no_bed_assigned"));
                return InteractionResult.FAIL;
            }

            // Start departure
            Level level = wolf.level();
            BlockPos wolfPos = wolf.blockPosition();

            // Pick a random departure point 10–14 blocks away (horizontal only)
            double angle = wolf.getRandom().nextDouble() * 2.0 * Math.PI;
            double dist = 10.0 + wolf.getRandom().nextDouble() * 4.0;
            int dx = (int) Math.round(Math.cos(angle) * dist);
            int dz = (int) Math.round(Math.sin(angle) * dist);

            // Find ground at departure point (scan down from wolf Y + 3)
            BlockPos departurePos = null;
            int startX = wolfPos.getX() + dx;
            int startZ = wolfPos.getZ() + dz;
            int startY = wolfPos.getY() + 3;
            for (int y = startY; y >= startY - 10; y--) {
                BlockPos candidate = new BlockPos(startX, y, startZ);
                BlockState below = level.getBlockState(candidate.below());
                BlockState at = level.getBlockState(candidate);
                BlockState above = level.getBlockState(candidate.above());
                if (below.isSolid() && at.isAir() && above.isAir()) {
                    departurePos = candidate;
                    break;
                }
            }
            // Fallback: use wolf's current position if no ground found
            if (departurePos == null) {
                departurePos = wolfPos;
            }

            wolf.getNavigation().moveTo(departurePos.getX() + 0.5, departurePos.getY(), departurePos.getZ() + 0.5, 1.2);
            accessor.workingwolves$setDepartureTimer(80);
            accessor.workingwolves$setDepartureTargetPos(departurePos);
            accessor.workingwolves$setExpeditionState("departing");
            accessor.workingwolves$setExpeditionStartTime(level.getGameTime());
            wolf.setOrderedToSit(false);
            accessor.workingwolves$syncData();
            player.sendSystemMessage(
                Component.translatable("message.workingwolves.wolf_dispatched"));
        }

        return InteractionResult.SUCCESS;
    }
}
