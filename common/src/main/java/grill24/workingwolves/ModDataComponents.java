package grill24.workingwolves;

import com.mojang.serialization.Codec;
import grill24.workingwolves.architectury.RegistrationApiSided;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;

public class ModDataComponents {
    public static Holder<DataComponentType<Integer>> COLLAR_TIER;
    public static Holder<DataComponentType<BlockPos>> BED_POSITION;
    public static Holder<DataComponentType<String>> EXPEDITION_STATE;
    public static Holder<DataComponentType<Long>> EXPEDITION_START_TIME;
    public static Holder<DataComponentType<Integer>> EXPEDITION_DURATION;

    public static void registerDataComponents() {
        var api = RegistrationApiSided.getInstance();

        COLLAR_TIER = api.registerDataComponent("collar_tier", builder -> builder
                .persistent(Codec.INT)
                .networkSynchronized(ByteBufCodecs.INT));

        BED_POSITION = api.registerDataComponent("bed_position", builder -> builder
                .persistent(BlockPos.CODEC)
                .networkSynchronized(BlockPos.STREAM_CODEC));

        EXPEDITION_STATE = api.registerDataComponent("expedition_state", builder -> builder
                .persistent(Codec.STRING)
                .networkSynchronized(ByteBufCodecs.STRING_UTF8));

        EXPEDITION_START_TIME = api.registerDataComponent("expedition_start_time", builder -> builder
                .persistent(Codec.LONG)
                .networkSynchronized(ByteBufCodecs.LONG));

        EXPEDITION_DURATION = api.registerDataComponent("expedition_duration", builder -> builder
                .persistent(Codec.INT)
                .networkSynchronized(ByteBufCodecs.INT));
    }
}
