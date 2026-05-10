package grill24.workingwolves.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.wolf.Wolf;

import java.util.function.BiConsumer;

public class WorkingWolvesPackets {
    // Set by platform init
    public static BiConsumer<ServerPlayer, CustomPacketPayload> sendToPlayer = (p, payload) -> {};
    public static BiConsumer<Entity, CustomPacketPayload> sendToTracking = (e, payload) -> {};

    // Sync wolf working data to all tracking clients
    public static void syncWolfData(Wolf wolf) {
        sendToTracking.accept(wolf, WolfDataSyncPacket.fromWolf(wolf));
    }
}
