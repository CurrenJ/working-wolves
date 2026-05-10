package grill24.workingwolves.fabric;

import grill24.workingwolves.api.IWorkingWolf;
import grill24.workingwolves.network.WolfDataSyncPacket;
import grill24.workingwolves.network.WorkingWolvesPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.DyeColor;

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

        ClientPlayNetworking.registerGlobalReceiver(WolfDataSyncPacket.TYPE,
            (packet, ctx) -> ctx.client().execute(() -> applyClientData(packet)));
    }

    private static void applyClientData(WolfDataSyncPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity entity = level.getEntity(packet.wolfId());
        if (entity instanceof Wolf wolf) {
            IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
            mixin.workingwolves$setCollarTier(packet.collarTier());
            mixin.workingwolves$setWolfClass(packet.wolfClass().isEmpty() ? null : packet.wolfClass());
            mixin.workingwolves$setBedPos(packet.bedPos().equals(BlockPos.ZERO) ? null : packet.bedPos());
            mixin.workingwolves$setExpeditionState(packet.expeditionState());
            mixin.workingwolves$setExpeditionStartTime(packet.expeditionStartTime());
            mixin.workingwolves$setExpeditionDuration(packet.expeditionDuration());
            mixin.workingwolves$setFilterItem(packet.filterItem());

            if (packet.collarTier() > 0) {
                DyeColor color = switch (packet.collarTier()) {
                    case 1 -> DyeColor.BROWN;
                    case 2 -> DyeColor.GRAY;
                    case 3 -> DyeColor.YELLOW;
                    default -> DyeColor.RED;
                };
                mixin.workingwolves$setCollarColorFromTier(color);
            }
        }
    }
}
