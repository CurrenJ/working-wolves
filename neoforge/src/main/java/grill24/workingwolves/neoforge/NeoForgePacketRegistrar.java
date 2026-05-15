package grill24.workingwolves.neoforge;

import grill24.workingwolves.WorkingWolves;
import grill24.workingwolves.network.BedJournalUpdatePacket;
import grill24.workingwolves.network.WolfDataSyncPacket;
import grill24.workingwolves.network.WorkingWolvesPackets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.function.Consumer;

public class NeoForgePacketRegistrar {

    // Set by client init; server-safe default is a no-op
    public static Consumer<WolfDataSyncPacket> wolfDataSyncHandler = packet -> {};
    public static java.util.function.Consumer<BedJournalUpdatePacket> bedJournalUpdateHandler = packet -> {};

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgePacketRegistrar::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var reg = event.registrar(WorkingWolves.MODID).optional();
        reg.playToClient(WolfDataSyncPacket.TYPE, WolfDataSyncPacket.STREAM_CODEC,
            (packet, ctx) -> ctx.enqueueWork(() -> wolfDataSyncHandler.accept(packet)));
        reg.playToClient(BedJournalUpdatePacket.TYPE, BedJournalUpdatePacket.STREAM_CODEC,
            (packet, ctx) -> ctx.enqueueWork(() -> bedJournalUpdateHandler.accept(packet)));
    }

    static {
        WorkingWolvesPackets.sendToPlayer = (player, payload) -> {
            PacketDistributor.sendToPlayer(player, payload);
        };
        WorkingWolvesPackets.sendToTracking = (entity, payload) -> {
            PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
        };
    }
}
