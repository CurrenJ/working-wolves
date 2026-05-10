package grill24.workingwolves.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import grill24.workingwolves.api.IWorkingWolf;
import grill24.workingwolves.blockentity.DogBedBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.UUID;

public class DebugCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("workingwolves")
                .then(Commands.literal("debug")
                    .executes(DebugCommand::execute))
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();

        HitResult hit = player.pick(10.0, 0.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            ctx.getSource().sendFailure(Component.literal("Look at a dog bed to debug."));
            return 0;
        }

        BlockPos pos = blockHit.getBlockPos();
        BlockEntity be = player.level().getBlockEntity(pos);
        if (!(be instanceof DogBedBlockEntity dogBed)) {
            ctx.getSource().sendFailure(Component.literal("That is not a dog bed."));
            return 0;
        }

        UUID wolfUuid = dogBed.getAssignedWolfUuid();
        if (wolfUuid == null) {
            ctx.getSource().sendFailure(Component.literal("No wolf assigned to this bed."));
            return 0;
        }

        ServerLevel serverLevel = (ServerLevel) player.level();
        Wolf wolf = findWolf(serverLevel, wolfUuid);
        if (wolf == null) {
            ctx.getSource().sendFailure(Component.literal("Assigned wolf not found in this dimension."));
            return 0;
        }

        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        BlockPos wolfPos = wolf.blockPosition();
        BlockPos bedPos = mixin.workingwolves$getBedPos();

        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
            "Wolf at %d %d %d  dim=%s  state=%s  class=%s  tier=%d  bed=%s  bedLoaded=%s",
            wolfPos.getX(), wolfPos.getY(), wolfPos.getZ(),
            wolf.level().dimension().identifier(),
            mixin.workingwolves$getExpeditionState(),
            mixin.workingwolves$getWolfClass(),
            mixin.workingwolves$getCollarTier(),
            bedPos != null ? bedPos.getX() + " " + bedPos.getY() + " " + bedPos.getZ() : "null",
            bedPos != null ? wolf.level().isLoaded(bedPos) : "N/A"
        )), false);
        return 1;
    }

    private static Wolf findWolf(ServerLevel level, UUID uuid) {
        AABB everywhere = new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000);
        for (Wolf w : level.getEntitiesOfClass(Wolf.class, everywhere, w -> w.getUUID().equals(uuid))) {
            return w;
        }
        return null;
    }
}
