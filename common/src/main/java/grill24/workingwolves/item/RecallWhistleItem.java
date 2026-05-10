package grill24.workingwolves.item;

import grill24.workingwolves.WorkingWolves;
import grill24.workingwolves.api.IWorkingWolf;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
}
