package grill24.workingwolves.chunk;

import grill24.workingwolves.Config;
import grill24.workingwolves.WorkingWolves;
import grill24.workingwolves.api.IWorkingWolf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * Manages chunk forcing for working wolves.
 *
 * On each server tick (every 20 ticks), this manager scans all dimensions for
 * tamed wolves with a collar (collarTier > 0) and forces the chunks in a radius
 * around each wolf to stay loaded:
 *   - Expedition state "active": 5x5 chunk radius (radius 2)
 *   - Idle / returning: 3x3 chunk radius (radius 1)
 *
 * Chunk zones from nearby wolves merge into a single ticket set (enforced by
 * the Set&lt;ChunkPos&gt; data structure). The per-player wolf cap from
 * {@link Config#maxWolvesPerPlayer} is respected.
 *
 * Uses {@link ServerLevel#setChunkForced(int, int, boolean)} which works on
 * both Fabric and NeoForge in vanilla MC 26.1.2 — no platform-specific APIs
 * are required for the core chunk loading logic.
 */
public class WolfChunkManager {
    private static WolfChunkManager INSTANCE;

    /**
     * Tracks which chunks were forced on the previous tick, grouped by dimension.
     * Used to compute the diff so we only force/unforce chunks that changed.
     */
    private Map<ResourceKey<Level>, Set<ChunkPos>> previouslyForced = new HashMap<>();

    public static WolfChunkManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new WolfChunkManager();
        }
        return INSTANCE;
    }

    /**
     * Tick method — called every N server ticks (e.g., every 20 ticks from the
     * platform-specific event handler). Recalculates which chunks should be
     * forced for all working wolves, then applies the diff to the server levels.
     */
    public void tick(MinecraftServer server) {
        if (server == null) return;

        // ------------------------------------------------------------------
        // 1. Build the new set of chunks to force, grouped by dimension
        // ------------------------------------------------------------------
        Map<ResourceKey<Level>, Set<ChunkPos>> newForced = new HashMap<>();
        Map<UUID, Integer> playerWolfCount = new HashMap<>();
        int totalWolvesProcessed = 0;

        AABB allEntitiesBounds = new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000);

        for (ServerLevel level : server.getAllLevels()) {
            Set<ChunkPos> levelChunks = new HashSet<>();

            for (Wolf wolf : level.getEntities(EntityType.WOLF, allEntitiesBounds, w -> true)) {
                if (!wolf.isTame()) continue;
                IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
                if (mixin.workingwolves$getCollarTier() <= 0) continue;
                UUID ownerId = wolf.getOwner() != null ? wolf.getOwner().getUUID() : null;
                if (ownerId == null) continue;

                // Enforce wolf cap per player
                int currentCount = playerWolfCount.getOrDefault(ownerId, 0);
                if (currentCount >= Config.maxWolvesPerPlayer) continue;
                playerWolfCount.put(ownerId, currentCount + 1);
                totalWolvesProcessed++;

                // Determine chunk radius based on expedition state:
                //   "active" (mining/hunting expedition) → 5x5 chunks
                //   "idle" or "returning"               → 3x3 chunks
                int radius = "active".equals(mixin.workingwolves$getExpeditionState()) ? 2 : 1;

                ChunkPos centerChunk = ChunkPos.containing(wolf.blockPosition());
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        levelChunks.add(new ChunkPos(centerChunk.x() + dx, centerChunk.z() + dz));
                    }
                }
            }

            if (!levelChunks.isEmpty()) {
                newForced.put(level.dimension(), levelChunks);
            }
        }

        // ------------------------------------------------------------------
        // 2. Unforce chunks that are no longer needed by any wolf
        // ------------------------------------------------------------------
        for (Map.Entry<ResourceKey<Level>, Set<ChunkPos>> entry : previouslyForced.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) continue;

            Set<ChunkPos> currentChunks = newForced.get(entry.getKey());
            if (currentChunks == null) {
                // All chunks in this dimension should be released
                for (ChunkPos pos : entry.getValue()) {
                    level.setChunkForced(pos.x(), pos.z(), false);
                }
            } else {
                // Only release chunks that are no longer in the new set
                for (ChunkPos pos : entry.getValue()) {
                    if (!currentChunks.contains(pos)) {
                        level.setChunkForced(pos.x(), pos.z(), false);
                    }
                }
            }
        }

        // ------------------------------------------------------------------
        // 3. Force new chunks that wolves now need
        // ------------------------------------------------------------------
        for (Map.Entry<ResourceKey<Level>, Set<ChunkPos>> entry : newForced.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) continue;

            Set<ChunkPos> oldChunks = previouslyForced.get(entry.getKey());

            for (ChunkPos pos : entry.getValue()) {
                if (oldChunks == null || !oldChunks.contains(pos)) {
                    level.setChunkForced(pos.x(), pos.z(), true);
                }
            }
        }

        // ------------------------------------------------------------------
        // 4. Update tracking for next tick
        // ------------------------------------------------------------------
        previouslyForced = newForced;

        // ------------------------------------------------------------------
        // 5. Periodic logging every 10 seconds (200 ticks)
        // ------------------------------------------------------------------
        if (server.getTickCount() % 200 == 0) {
            int totalForced = 0;
            for (Set<ChunkPos> chunks : newForced.values()) {
                totalForced += chunks.size();
            }
            WorkingWolves.LOGGER.debug("WolfChunkManager: {} wolves loading {} chunks across {} dimensions",
                totalWolvesProcessed, totalForced, newForced.size());
        }
    }

    /**
     * Counts how many working wolves (collarTier > 0, tame, owned by the
     * given player) currently exist across all server dimensions.
     * <p>
     * Used by {@link grill24.workingwolves.item.CollarItem} to enforce the
     * per-player wolf cap when applying a new collar.
     *
     * @param player the player whose wolves to count
     * @return the number of working wolves owned by this player
     */
    public static int countWorkingWolves(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return 0;
        MinecraftServer server = serverLevel.getServer();
        if (server == null) return 0;

        int count = 0;
        UUID playerUuid = player.getUUID();

        AABB allEntitiesBounds = new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000);

        for (ServerLevel level : server.getAllLevels()) {
            for (Wolf w : level.getEntities(EntityType.WOLF, allEntitiesBounds, wolf -> true)) {
                if (w.isTame()
                    && w.getOwner() != null
                    && playerUuid.equals(w.getOwner().getUUID())
                    && ((IWorkingWolf) (Object) w).workingwolves$getCollarTier() > 0) {
                    count++;
                }
            }
        }
        return count;
    }
}
