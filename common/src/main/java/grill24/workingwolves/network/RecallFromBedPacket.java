package grill24.workingwolves.network;

import grill24.workingwolves.WorkingWolves;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RecallFromBedPacket(BlockPos bedPos) implements CustomPacketPayload {

    public static final Type<RecallFromBedPacket> TYPE = new Type<>(WorkingWolves.id("recall_from_bed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecallFromBedPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, RecallFromBedPacket::bedPos,
        RecallFromBedPacket::new
    );

    @Override
    public Type<RecallFromBedPacket> type() {
        return TYPE;
    }
}
