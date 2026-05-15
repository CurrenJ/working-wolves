package grill24.workingwolves.network;

import grill24.workingwolves.WorkingWolves;
import grill24.workingwolves.api.IWorkingWolf;
import grill24.workingwolves.item.CollarItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;

public record WolfDataSyncPacket(int wolfId, int collarTier,
                                 BlockPos bedPos, String expeditionState,
                                 long expeditionStartTime, int expeditionDuration,
                                 ItemStack mouthItem) implements CustomPacketPayload {

    public static final Type<WolfDataSyncPacket> TYPE = new Type<>(WorkingWolves.id("wolf_data_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WolfDataSyncPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, WolfDataSyncPacket::wolfId,
        ByteBufCodecs.VAR_INT, WolfDataSyncPacket::collarTier,
        BlockPos.STREAM_CODEC, WolfDataSyncPacket::bedPos,
        ByteBufCodecs.STRING_UTF8, WolfDataSyncPacket::expeditionState,
        ByteBufCodecs.LONG, WolfDataSyncPacket::expeditionStartTime,
        ByteBufCodecs.VAR_INT, WolfDataSyncPacket::expeditionDuration,
        ItemStack.OPTIONAL_STREAM_CODEC, WolfDataSyncPacket::mouthItem,
        WolfDataSyncPacket::new
    );

    @Override
    public Type<WolfDataSyncPacket> type() {
        return TYPE;
    }

    public static WolfDataSyncPacket fromWolf(Wolf wolf) {
        IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
        return new WolfDataSyncPacket(
            wolf.getId(),
            mixin.workingwolves$getCollarTier(),
            mixin.workingwolves$getBedPos() != null ? mixin.workingwolves$getBedPos() : BlockPos.ZERO,
            mixin.workingwolves$getExpeditionState(),
            mixin.workingwolves$getExpeditionStartTime(),
            mixin.workingwolves$getExpeditionDuration(),
            mixin.workingwolves$getMouthItem()
        );
    }

    public static void applyToClient(ClientLevel level, WolfDataSyncPacket packet) {
        if (level == null) return;
        Entity entity = level.getEntity(packet.wolfId());
        if (entity instanceof Wolf wolf) {
            IWorkingWolf mixin = (IWorkingWolf) (Object) wolf;
            mixin.workingwolves$setCollarTier(packet.collarTier());
            mixin.workingwolves$setBedPos(packet.bedPos().equals(BlockPos.ZERO) ? null : packet.bedPos());
            mixin.workingwolves$setExpeditionState(packet.expeditionState());
            mixin.workingwolves$setExpeditionStartTime(packet.expeditionStartTime());
            mixin.workingwolves$setExpeditionDuration(packet.expeditionDuration());
            mixin.workingwolves$setMouthItem(packet.mouthItem());
            if (!packet.mouthItem().isEmpty()) {
                WorkingWolves.LOGGER.info("WolfDataSync: received mouth item '{}' for wolf {}", packet.mouthItem().getDisplayName().getString(), packet.wolfId());
            }
            if (packet.collarTier() > 0) {
                mixin.workingwolves$setCollarColorFromTier(
                    CollarItem.getCollarColorForTier(packet.collarTier()));
            }
        }
    }
}
