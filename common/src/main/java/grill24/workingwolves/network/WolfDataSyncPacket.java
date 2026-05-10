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

public record WolfDataSyncPacket(int wolfId, int collarTier, String wolfClass,
                                 BlockPos bedPos, String expeditionState,
                                 long expeditionStartTime, int expeditionDuration,
                                 ItemStack filterItem) implements CustomPacketPayload {

    public static final Type<WolfDataSyncPacket> TYPE = new Type<>(WorkingWolves.id("wolf_data_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WolfDataSyncPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, WolfDataSyncPacket::wolfId,
        ByteBufCodecs.VAR_INT, WolfDataSyncPacket::collarTier,
        ByteBufCodecs.STRING_UTF8, WolfDataSyncPacket::wolfClass,
        BlockPos.STREAM_CODEC, WolfDataSyncPacket::bedPos,
        ByteBufCodecs.STRING_UTF8, WolfDataSyncPacket::expeditionState,
        ByteBufCodecs.LONG, WolfDataSyncPacket::expeditionStartTime,
        ByteBufCodecs.VAR_INT, WolfDataSyncPacket::expeditionDuration,
        ItemStack.OPTIONAL_STREAM_CODEC, WolfDataSyncPacket::filterItem,
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
            mixin.workingwolves$getWolfClass() != null ? mixin.workingwolves$getWolfClass() : "",
            mixin.workingwolves$getBedPos() != null ? mixin.workingwolves$getBedPos() : BlockPos.ZERO,
            mixin.workingwolves$getExpeditionState(),
            mixin.workingwolves$getExpeditionStartTime(),
            mixin.workingwolves$getExpeditionDuration(),
            mixin.workingwolves$getFilterItem()
        );
    }

    public static void applyToClient(ClientLevel level, WolfDataSyncPacket packet) {
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
                mixin.workingwolves$setCollarColorFromTier(
                    CollarItem.getCollarColorForTier(packet.collarTier()));
            }
        }
    }
}
