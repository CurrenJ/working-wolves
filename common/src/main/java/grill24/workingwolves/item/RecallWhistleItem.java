package grill24.workingwolves.item;

import grill24.workingwolves.WorkingWolves;
import grill24.workingwolves.api.IWorkingWolf;
import grill24.workingwolves.blockentity.DogBedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class RecallWhistleItem extends Item {
    private static final int COOLDOWN_TICKS = 100; // 2 minutes (20 ticks per second)

    public RecallWhistleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // Check vanilla cooldown
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.PASS;
        }

        // Apply cooldown
        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);

        // Find all wolves owned by this player across all dimensions
        ServerLevel serverLevel = (ServerLevel) level;
        AABB allEntitiesBounds = WorkingWolves.ALL_ENTITIES;
        for (ServerLevel dimensionLevel : serverLevel.getServer().getAllLevels()) {
            for (Wolf wolf : dimensionLevel.getEntities(EntityType.WOLF, allEntitiesBounds,
                w -> w.isTame() && w.getOwner() != null && player.getUUID().equals(w.getOwner().getUUID()))) {
                // Set expedition state to "returning"
                IWorkingWolf accessor = (IWorkingWolf) (Object) wolf;
                accessor.workingwolves$setExpeditionState("returning");
                accessor.workingwolves$syncData();

                // Spawn howl particles for visual feedback at wolf position
                Vec3 wolfPos = wolf.position();
                dimensionLevel.sendParticles(
                    ParticleTypes.NOTE,
                    wolfPos.x, wolfPos.y + 1.0, wolfPos.z,
                    5, 0.3, 0.3, 0.3, 0.0
                );
            }
        }

        player.sendSystemMessage(Component.translatable("message.workingwolves.all_wolves_recalled"));

        // Spawn recall particles at player position
        if (player.level() instanceof ServerLevel playerLevel) {
            playerLevel.sendParticles(
                ParticleTypes.NOTE,
                player.getX(), player.getY() + 1.5, player.getZ(),
                10, 0.5, 0.5, 0.5, 0.0
            );
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();

        if (level.isClientSide() || player == null) return InteractionResult.SUCCESS;

        if (!(level.getBlockEntity(pos) instanceof DogBedBlockEntity bedBE)) return InteractionResult.PASS;

        // Bed must have a running expedition
        if (!"running".equals(bedBE.getSimState())) return InteractionResult.PASS;

        // Verify the assigned wolf belongs to this player
        UUID wolfUuid = bedBE.getAssignedWolfUuid();
        if (wolfUuid == null) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel sl)) return InteractionResult.PASS;
        if (!(sl.getEntity(wolfUuid) instanceof Wolf wolf) || !wolf.isOwnedBy(player)) return InteractionResult.PASS;

        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.PASS;

        if (bedBE.recallExpedition()) {
            player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
            player.sendSystemMessage(Component.translatable("message.workingwolves.wolf_recalled"));
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }
}
