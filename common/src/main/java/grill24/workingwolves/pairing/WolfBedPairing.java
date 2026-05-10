package grill24.workingwolves.pairing;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WolfBedPairing {
    private static final int PAIRING_TIMEOUT_TICKS = 600; // 30 seconds
    private static final Map<UUID, PendingBed> pendingBeds = new ConcurrentHashMap<>();

    public static void startBedPairing(ServerPlayer player, BlockPos bedPos, ResourceKey<Level> dimension) {
        pendingBeds.put(player.getUUID(), new PendingBed(bedPos, dimension, player.level().getGameTime()));
    }

    public static PendingBed consumeBedPairing(ServerPlayer player) {
        PendingBed bed = pendingBeds.remove(player.getUUID());
        if (bed != null) {
            long elapsed = player.level().getGameTime() - bed.startTime;
            if (elapsed > PAIRING_TIMEOUT_TICKS) {
                return null;
            }
        }
        return bed;
    }

    public static boolean hasActiveBedPairing(ServerPlayer player) {
        PendingBed bed = pendingBeds.get(player.getUUID());
        if (bed == null) return false;
        long elapsed = player.level().getGameTime() - bed.startTime;
        if (elapsed > PAIRING_TIMEOUT_TICKS) {
            pendingBeds.remove(player.getUUID());
            return false;
        }
        return true;
    }

    public record PendingBed(BlockPos bedPos, ResourceKey<Level> dimension, long startTime) {}
}
