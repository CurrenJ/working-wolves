package grill24.workingwolves.network;

import grill24.workingwolves.WorkingWolves;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DispatchFromBedPacket(BlockPos bedPos) implements CustomPacketPayload {

    public static final Type<DispatchFromBedPacket> TYPE = new Type<>(WorkingWolves.id("dispatch_from_bed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DispatchFromBedPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, DispatchFromBedPacket::bedPos,
        DispatchFromBedPacket::new
    );

    @Override
    public Type<DispatchFromBedPacket> type() {
        return TYPE;
    }
}
