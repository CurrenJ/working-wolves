package grill24.workingwolves.network;

import grill24.workingwolves.WorkingWolves;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BedJournalUpdatePacket(BlockPos bedPos, String line, int simElapsedTicks, int simTotalTicks)
        implements CustomPacketPayload {

    public static final Type<BedJournalUpdatePacket> TYPE = new Type<>(WorkingWolves.id("bed_journal_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BedJournalUpdatePacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, BedJournalUpdatePacket::bedPos,
        ByteBufCodecs.STRING_UTF8, BedJournalUpdatePacket::line,
        ByteBufCodecs.VAR_INT, BedJournalUpdatePacket::simElapsedTicks,
        ByteBufCodecs.VAR_INT, BedJournalUpdatePacket::simTotalTicks,
        BedJournalUpdatePacket::new
    );

    @Override
    public Type<BedJournalUpdatePacket> type() {
        return TYPE;
    }
}
