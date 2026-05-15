package grill24.workingwolves.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class WorkingWolvesPackets {
    // Set by platform server init
    public static BiConsumer<ServerPlayer, CustomPacketPayload> sendToPlayer = (p, payload) -> {};
    public static BiConsumer<Entity, CustomPacketPayload> sendToTracking = (e, payload) -> {};

    // Set by platform client init
    public static Consumer<CustomPacketPayload> sendToServer = payload -> {};

    public static void syncWolfData(Wolf wolf) {
        sendToTracking.accept(wolf, WolfDataSyncPacket.fromWolf(wolf));
    }

    public static void pushJournalLine(Level level, BlockPos pos, String line, int simElapsed, int simTotal) {
        if (!(level instanceof ServerLevel sl)) return;
        BedJournalUpdatePacket packet = new BedJournalUpdatePacket(pos, line, simElapsed, simTotal);
        Vec3 center = Vec3.atCenterOf(pos);
        for (ServerPlayer player : sl.players()) {
            if (player.distanceToSqr(center) < 128.0 * 128.0) {
                sendToPlayer.accept(player, packet);
            }
        }
    }
}
