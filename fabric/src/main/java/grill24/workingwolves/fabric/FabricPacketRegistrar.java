package grill24.workingwolves.fabric;

import grill24.workingwolves.client.DogBedScreenData;
import grill24.workingwolves.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

public class FabricPacketRegistrar {

    // Set by server init (WorkingWolvesFabric)
    public static BiConsumer<DispatchFromBedPacket, ServerPlayer> dispatchFromBedHandler = (p, player) -> {};
    public static BiConsumer<RecallFromBedPacket, ServerPlayer> recallFromBedHandler = (p, player) -> {};

    public static void registerServer() {
        WorkingWolvesPackets.sendToPlayer = (player, payload) -> {
            ServerPlayNetworking.send(player, payload);
        };
        WorkingWolvesPackets.sendToTracking = (entity, payload) -> {
            if (entity.level() instanceof ServerLevel serverLevel) {
                double trackingRange = 160.0;
                for (ServerPlayer player : serverLevel.players()) {
                    if (player.distanceToSqr(entity) < trackingRange * trackingRange) {
                        ServerPlayNetworking.send(player, payload);
                    }
                }
            }
        };

        // C → S
        PayloadTypeRegistry.serverboundPlay().register(DispatchFromBedPacket.TYPE, DispatchFromBedPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RecallFromBedPacket.TYPE, RecallFromBedPacket.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(DispatchFromBedPacket.TYPE,
            (packet, ctx) -> ctx.server().execute(() -> dispatchFromBedHandler.accept(packet, ctx.player())));
        ServerPlayNetworking.registerGlobalReceiver(RecallFromBedPacket.TYPE,
            (packet, ctx) -> ctx.server().execute(() -> recallFromBedHandler.accept(packet, ctx.player())));
    }

    public static void registerClient() {
        // S → C
        PayloadTypeRegistry.clientboundPlay().register(WolfDataSyncPacket.TYPE, WolfDataSyncPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BedStatePacket.TYPE, BedStatePacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BedJournalUpdatePacket.TYPE, BedJournalUpdatePacket.STREAM_CODEC);

        ClientPlayNetworking.registerGlobalReceiver(WolfDataSyncPacket.TYPE,
            (packet, ctx) -> ctx.client().execute(() ->
                WolfDataSyncPacket.applyToClient(Minecraft.getInstance().level, packet)));

        ClientPlayNetworking.registerGlobalReceiver(BedStatePacket.TYPE,
            (packet, ctx) -> ctx.client().execute(() -> DogBedScreenData.applyStatePacket(packet)));

        ClientPlayNetworking.registerGlobalReceiver(BedJournalUpdatePacket.TYPE,
            (packet, ctx) -> ctx.client().execute(() -> DogBedScreenData.applyJournalUpdate(packet)));

        WorkingWolvesPackets.sendToServer = payload -> ClientPlayNetworking.send(payload);
    }
}
