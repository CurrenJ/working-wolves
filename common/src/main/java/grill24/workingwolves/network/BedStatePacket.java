package grill24.workingwolves.network;

import grill24.workingwolves.WorkingWolves;
import grill24.workingwolves.blockentity.DogBedBlockEntity;
import grill24.workingwolves.inventory.WolfBagHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.wolf.Wolf;

import java.util.UUID;

public record BedStatePacket(
        BlockPos bedPos,
        String wolfName,
        int collarTier,
        String simState,
        boolean hasMining,
        boolean hasHunting,
        boolean hasWoodcutting,
        int simElapsedTicks,
        int simTotalTicks,
        String expeditionLog
) implements CustomPacketPayload {

    public static final Type<BedStatePacket> TYPE = new Type<>(WorkingWolves.id("bed_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BedStatePacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, BedStatePacket::bedPos,
        ByteBufCodecs.STRING_UTF8, BedStatePacket::wolfName,
        ByteBufCodecs.VAR_INT, BedStatePacket::collarTier,
        ByteBufCodecs.STRING_UTF8, BedStatePacket::simState,
        ByteBufCodecs.BOOL, BedStatePacket::hasMining,
        ByteBufCodecs.BOOL, BedStatePacket::hasHunting,
        ByteBufCodecs.BOOL, BedStatePacket::hasWoodcutting,
        ByteBufCodecs.VAR_INT, BedStatePacket::simElapsedTicks,
        ByteBufCodecs.VAR_INT, BedStatePacket::simTotalTicks,
        ByteBufCodecs.STRING_UTF8, BedStatePacket::expeditionLog,
        BedStatePacket::new
    );

    @Override
    public Type<BedStatePacket> type() {
        return TYPE;
    }

    public static BedStatePacket fromBE(DogBedBlockEntity be, ServerLevel level) {
        String wolfName = be.getAssignedWolfName() != null ? be.getAssignedWolfName() : "";
        String simState = be.getSimState();
        boolean hasMining = be.isSimHasMining();
        boolean hasHunting = be.isSimHasHunting();
        boolean hasWoodcutting = be.isSimHasWoodcutting();
        int collarTier = be.getSimCollarTier();

        // If idle, derive roles from the actual wolf entity if it's loaded
        if ("inactive".equals(simState) || "complete".equals(simState)) {
            UUID wolfUuid = be.getAssignedWolfUuid();
            if (wolfUuid != null && level.getEntity(wolfUuid) instanceof Wolf wolf) {
                var accessor = (grill24.workingwolves.api.IWorkingWolf) (Object) wolf;
                collarTier = accessor.workingwolves$getCollarTier();
                hasMining = WolfBagHelper.hasMiningTool(accessor);
                hasHunting = WolfBagHelper.hasHuntingWeapon(accessor);
                hasWoodcutting = WolfBagHelper.hasWoodcuttingTool(accessor);
            }
        }

        String log = String.join("\n", be.getExpeditionLog());
        return new BedStatePacket(
            be.getBlockPos(),
            wolfName,
            collarTier,
            simState,
            hasMining,
            hasHunting,
            hasWoodcutting,
            be.getSimElapsedTicks(),
            be.getSimTotalTicks(),
            log
        );
    }
}
