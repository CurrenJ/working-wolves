package grill24.workingwolves.network;

import grill24.workingwolves.api.IWorkingWolf;
import grill24.workingwolves.blockentity.DogBedBlockEntity;
import grill24.workingwolves.inventory.WolfBagHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class BedPacketHandlers {

    public static void handleDispatch(DispatchFromBedPacket packet, ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos bedPos = packet.bedPos();

        if (!(level.getBlockEntity(bedPos) instanceof DogBedBlockEntity be)) return;

        UUID wolfUuid = be.getAssignedWolfUuid();
        if (wolfUuid == null) {
            player.sendSystemMessage(Component.translatable("message.workingwolves.no_wolf_assigned"));
            return;
        }

        if (!(level.getEntity(wolfUuid) instanceof Wolf wolf) || !wolf.isOwnedBy(player)) {
            player.sendSystemMessage(Component.translatable("message.workingwolves.not_owner"));
            return;
        }

        IWorkingWolf accessor = (IWorkingWolf) (Object) wolf;

        if (accessor.workingwolves$getCollarTier() == 0) {
            player.sendSystemMessage(Component.translatable("message.workingwolves.no_collar"));
            return;
        }

        String currentState = accessor.workingwolves$getExpeditionState();

        // Cancel an active expedition
        if ("on_expedition".equals(currentState) || "departing".equals(currentState)) {
            accessor.workingwolves$setExpeditionState("idle");
            accessor.workingwolves$setDepartureTimer(0);
            wolf.setInvisible(false);
            wolf.setNoAi(false);
            wolf.setInvulnerable(false);
            wolf.setOrderedToSit(false);
            accessor.workingwolves$syncData();
            player.sendSystemMessage(Component.translatable("message.workingwolves.wolf_recalled"));
            return;
        }

        // Guard: wolf needs an expedition tool
        boolean hasExpeditionTool = WolfBagHelper.hasAnyExpeditionTool(accessor);
        if (!hasExpeditionTool) {
            for (int i = 0; i < be.getContainerSize(); i++) {
                ItemStack bedStack = be.getItem(i);
                if (!bedStack.isEmpty() && (
                        bedStack.is(net.minecraft.tags.ItemTags.PICKAXES) ||
                        bedStack.is(net.minecraft.tags.ItemTags.AXES) ||
                        WolfBagHelper.isHuntingWeapon(bedStack))) {
                    hasExpeditionTool = true;
                    break;
                }
            }
        }
        if (!hasExpeditionTool) {
            player.sendSystemMessage(Component.translatable("message.workingwolves.no_expedition_tools"));
            return;
        }

        // Guard: must have food
        int totalFood = 0;
        for (ItemStack stack : accessor.workingwolves$getBagInventory()) {
            if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) totalFood += stack.getCount();
        }
        for (int i = 0; i < be.getContainerSize(); i++) {
            ItemStack stack = be.getItem(i);
            if (!stack.isEmpty() && stack.has(DataComponents.FOOD)) totalFood += stack.getCount();
        }
        if (totalFood < 1) {
            player.sendSystemMessage(Component.translatable("message.workingwolves.no_food_for_expedition"));
            return;
        }

        // Start departure: pick a random departure point 10-14 blocks away
        double angle = wolf.getRandom().nextDouble() * 2.0 * Math.PI;
        double dist = 10.0 + wolf.getRandom().nextDouble() * 4.0;
        int dx = (int) Math.round(Math.cos(angle) * dist);
        int dz = (int) Math.round(Math.sin(angle) * dist);
        BlockPos wolfPos = wolf.blockPosition();

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
        if (departurePos == null) departurePos = wolfPos;

        wolf.getNavigation().moveTo(departurePos.getX() + 0.5, departurePos.getY(), departurePos.getZ() + 0.5, 1.2);
        accessor.workingwolves$setDepartureTimer(80);
        accessor.workingwolves$setDepartureTargetPos(departurePos);
        accessor.workingwolves$setExpeditionState("departing");
        accessor.workingwolves$setExpeditionStartTime(level.getGameTime());
        wolf.setOrderedToSit(false);
        accessor.workingwolves$syncData();
        player.sendSystemMessage(Component.translatable("message.workingwolves.wolf_dispatched"));
    }

    public static void handleRecall(RecallFromBedPacket packet, ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos bedPos = packet.bedPos();

        if (!(level.getBlockEntity(bedPos) instanceof DogBedBlockEntity be)) return;
        if (!"running".equals(be.getSimState())) return;

        UUID wolfUuid = be.getAssignedWolfUuid();
        if (wolfUuid == null) return;

        if (!(level.getEntity(wolfUuid) instanceof Wolf wolf) || !wolf.isOwnedBy(player)) return;

        if (be.recallExpedition()) {
            player.sendSystemMessage(Component.translatable("message.workingwolves.wolf_recalled"));
        }
    }

    private BedPacketHandlers() {}
}
