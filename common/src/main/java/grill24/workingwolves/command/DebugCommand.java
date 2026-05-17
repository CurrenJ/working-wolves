package grill24.workingwolves.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import grill24.workingwolves.Config;
import grill24.workingwolves.WorkingWolves;
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
                .then(Commands.literal("tuneMouth")
                    .then(Commands.literal("x")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-5f, 5f))
                            .executes(ctx -> tune(ctx, "x", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("y")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-5f, 5f))
                            .executes(ctx -> tune(ctx, "y", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("z")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-5f, 5f))
                            .executes(ctx -> tune(ctx, "z", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("rotX")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-360f, 360f))
                            .executes(ctx -> tune(ctx, "rotX", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("rotY")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-360f, 360f))
                            .executes(ctx -> tune(ctx, "rotY", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("rotZ")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-360f, 360f))
                            .executes(ctx -> tune(ctx, "rotZ", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("show")
                        .executes(DebugCommand::showTuning))
                )
                .then(Commands.literal("tuneWolfPreview")
                    .then(Commands.literal("bodyRot")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-360f, 360f))
                            .executes(ctx -> tunePreview(ctx, "bodyRot", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("yRot")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-360f, 360f))
                            .executes(ctx -> tunePreview(ctx, "yRot", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("xRot")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-360f, 360f))
                            .executes(ctx -> tunePreview(ctx, "xRot", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("pitch")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-360f, 360f))
                            .executes(ctx -> tunePreview(ctx, "pitch", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("size")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(1f, 200f))
                            .executes(ctx -> tunePreview(ctx, "size", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("show")
                        .executes(DebugCommand::showPreviewTuning))
                )
                .then(Commands.literal("tuneFloor")
                    .then(Commands.literal("rotX")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-360f, 360f))
                            .executes(ctx -> tuneFloor(ctx, "rotX", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("rotY")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-360f, 360f))
                            .executes(ctx -> tuneFloor(ctx, "rotY", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("rotZ")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-360f, 360f))
                            .executes(ctx -> tuneFloor(ctx, "rotZ", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("scale")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0.1f, 5f))
                            .executes(ctx -> tuneFloor(ctx, "scale", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("offsetX")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-10f, 10f))
                            .executes(ctx -> tuneFloor(ctx, "offsetX", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("offsetZ")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-10f, 10f))
                            .executes(ctx -> tuneFloor(ctx, "offsetZ", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("y")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-10f, 10f))
                            .executes(ctx -> tuneFloor(ctx, "y", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("scrollSpeed")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-1f, 1f))
                            .executes(ctx -> tuneFloor(ctx, "scrollSpeed", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("scrollSpeedX")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(-1f, 1f))
                            .executes(ctx -> tuneFloor(ctx, "scrollSpeedX", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("spacing")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0.5f, 3f))
                            .executes(ctx -> tuneFloor(ctx, "spacing", FloatArgumentType.getFloat(ctx, "value")))))
                    .then(Commands.literal("show")
                        .executes(DebugCommand::showFloorTuning))
                )
        );
    }

    private static int tune(CommandContext<CommandSourceStack> ctx, String field, float value) {
        switch (field) {
            case "x" -> Config.mouthOffsetX = value;
            case "y" -> Config.mouthOffsetY = value;
            case "z" -> Config.mouthOffsetZ = value;
            case "rotX" -> Config.mouthRotX = value;
            case "rotY" -> Config.mouthRotY = value;
            case "rotZ" -> Config.mouthRotZ = value;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
            String.format("Mouth item %s = %.3f", field, value)), true);
        return 1;
    }

    private static int showTuning(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
            "Mouth item: pos(%.3f, %.3f, %.3f) rot(%.1f, %.1f, %.1f)",
            Config.mouthOffsetX, Config.mouthOffsetY, Config.mouthOffsetZ,
            Config.mouthRotX, Config.mouthRotY, Config.mouthRotZ)), false);
        return 1;
    }

    private static int tunePreview(CommandContext<CommandSourceStack> ctx, String field, float value) {
        switch (field) {
            case "bodyRot" -> Config.previewBodyRot = value;
            case "yRot"    -> Config.previewYRot = value;
            case "xRot"    -> Config.previewXRot = value;
            case "pitch"   -> Config.previewPitch = value;
            case "size"    -> Config.previewSize = (int) value;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
            String.format("Wolf preview %s = %.1f", field, value)), true);
        return 1;
    }

    private static int showPreviewTuning(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
            "Wolf preview: bodyRot=%.1f  yRot=%.1f  xRot=%.1f  pitch=%.1f  size=%d",
            Config.previewBodyRot, Config.previewYRot, Config.previewXRot, Config.previewPitch, Config.previewSize)), false);
        return 1;
    }

    private static int tuneFloor(CommandContext<CommandSourceStack> ctx, String field, float value) {
        switch (field) {
            case "rotX"        -> Config.previewFloorRotX = value;
            case "rotY"        -> Config.previewFloorRotY = value;
            case "rotZ"        -> Config.previewFloorRotZ = value;
            case "scale"       -> Config.previewFloorScale = value;
            case "offsetX"     -> Config.previewFloorOffsetX = value;
            case "offsetZ"     -> Config.previewFloorOffsetZ = value;
            case "y"           -> Config.previewFloorY = value;
            case "scrollSpeed" -> Config.previewFloorScrollSpeed = value;
            case "scrollSpeedX"-> Config.previewFloorScrollSpeedX = value;
            case "spacing"     -> Config.previewFloorSpacing = value;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
            String.format("Floor %s = %.3f", field, value)), true);
        return 1;
    }

    private static int showFloorTuning(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(
            "Floor: rot(%.1f, %.1f, %.1f)  scale=%.2f  offset(%.2f, %.2f)  y=%.2f  scrollZ=%.3f  scrollX=%.3f  spacing=%.3f",
            Config.previewFloorRotX, Config.previewFloorRotY, Config.previewFloorRotZ,
            Config.previewFloorScale, Config.previewFloorOffsetX, Config.previewFloorOffsetZ,
            Config.previewFloorY, Config.previewFloorScrollSpeed, Config.previewFloorScrollSpeedX,
            Config.previewFloorSpacing)), false);
        return 1;
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
            "Wolf at %d %d %d  dim=%s  state=%s  tier=%d  bed=%s  bedLoaded=%s",
            wolfPos.getX(), wolfPos.getY(), wolfPos.getZ(),
            wolf.level().dimension().identifier(),
            mixin.workingwolves$getExpeditionState(),
            mixin.workingwolves$getCollarTier(),
            bedPos != null ? bedPos.getX() + " " + bedPos.getY() + " " + bedPos.getZ() : "null",
            bedPos != null ? wolf.level().isLoaded(bedPos) : "N/A"
        )), false);
        return 1;
    }

    private static Wolf findWolf(ServerLevel level, UUID uuid) {
        AABB everywhere = WorkingWolves.ALL_ENTITIES;
        for (Wolf w : level.getEntitiesOfClass(Wolf.class, everywhere, w -> w.getUUID().equals(uuid))) {
            return w;
        }
        return null;
    }
}
