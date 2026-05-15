package grill24.workingwolves.fabric;

import grill24.workingwolves.network.BedJournalUpdatePacket;
import grill24.workingwolves.network.WolfDataSyncPacket;
import grill24.workingwolves.network.WorkingWolvesPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class FabricPacketRegistrar {

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
    }

    public static void registerClient() {
        PayloadTypeRegistry.clientboundPlay().register(WolfDataSyncPacket.TYPE, WolfDataSyncPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BedJournalUpdatePacket.TYPE, BedJournalUpdatePacket.STREAM_CODEC);

        ClientPlayNetworking.registerGlobalReceiver(WolfDataSyncPacket.TYPE,
            (packet, ctx) -> ctx.client().execute(() -> applyClientData(packet)));

        ClientPlayNetworking.registerGlobalReceiver(BedJournalUpdatePacket.TYPE,
            (packet, ctx) -> {
                // Client-side handler stub — GUI not yet implemented
                // When bed journal GUI is added, handle the incoming line here
            });
    }

    private static void applyClientData(WolfDataSyncPacket packet) {
        WolfDataSyncPacket.applyToClient(Minecraft.getInstance().level, packet);
    }
}
