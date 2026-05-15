package grill24.workingwolves.neoforge;

import grill24.workingwolves.WorkingWolves;
import grill24.workingwolves.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class NeoForgePacketRegistrar {

    // Set by client init
    public static Consumer<WolfDataSyncPacket> wolfDataSyncHandler = packet -> {};
    public static Consumer<BedJournalUpdatePacket> bedJournalUpdateHandler = packet -> {};
    public static Consumer<BedStatePacket> bedStateHandler = packet -> {};

    // Set by server init
    public static BiConsumer<DispatchFromBedPacket, ServerPlayer> dispatchFromBedHandler = (p, player) -> {};
    public static BiConsumer<RecallFromBedPacket, ServerPlayer> recallFromBedHandler = (p, player) -> {};

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgePacketRegistrar::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var reg = event.registrar(WorkingWolves.MODID).optional();

        // Server → Client
        reg.playToClient(WolfDataSyncPacket.TYPE, WolfDataSyncPacket.STREAM_CODEC,
            (packet, ctx) -> ctx.enqueueWork(() -> wolfDataSyncHandler.accept(packet)));
        reg.playToClient(BedJournalUpdatePacket.TYPE, BedJournalUpdatePacket.STREAM_CODEC,
            (packet, ctx) -> ctx.enqueueWork(() -> bedJournalUpdateHandler.accept(packet)));
        reg.playToClient(BedStatePacket.TYPE, BedStatePacket.STREAM_CODEC,
            (packet, ctx) -> ctx.enqueueWork(() -> bedStateHandler.accept(packet)));

        // Client → Server
        reg.playToServer(DispatchFromBedPacket.TYPE, DispatchFromBedPacket.STREAM_CODEC,
            (packet, ctx) -> ctx.enqueueWork(() -> dispatchFromBedHandler.accept(packet, (ServerPlayer) ctx.player())));
        reg.playToServer(RecallFromBedPacket.TYPE, RecallFromBedPacket.STREAM_CODEC,
            (packet, ctx) -> ctx.enqueueWork(() -> recallFromBedHandler.accept(packet, (ServerPlayer) ctx.player())));
    }

    static {
        WorkingWolvesPackets.sendToPlayer = (player, payload) -> {
            PacketDistributor.sendToPlayer(player, payload);
        };
        WorkingWolvesPackets.sendToTracking = (entity, payload) -> {
            PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
        };
        WorkingWolvesPackets.sendToServer = payload -> {
            ClientPacketDistributor.sendToServer(payload);
        };
    }
}
