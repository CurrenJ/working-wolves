package grill24.workingwolves.neoforge;

import grill24.workingwolves.Config;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class NeoForgeConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_WOLVES_PER_PLAYER;
    public static final ModConfigSpec.IntValue DETECTION_RANGE;
    public static final ModConfigSpec.IntValue EXPEDITION_DURATION_MINUTES;

    public static final ModConfigSpec SPEC;

    static {
        MAX_WOLVES_PER_PLAYER = BUILDER
                .comment("Maximum number of working wolves per player")
                .defineInRange("maxWolvesPerPlayer", 5, 1, 20);

        DETECTION_RANGE = BUILDER
                .comment("Range (blocks) within which wolves detect ores/mobs and pathfind to them. Also scales pathfinder resources.")
                .defineInRange("detectionRange", 64, 16, 256);

        EXPEDITION_DURATION_MINUTES = BUILDER
                .comment("How long (minutes) a wolf can be on expedition before auto-returning. 0 = unlimited.")
                .defineInRange("expeditionDurationMinutes", 15, 0, 120);

        SPEC = BUILDER.build();
    }

    public static void onLoad(ModConfigEvent.Loading event) {
        Config.maxWolvesPerPlayer = MAX_WOLVES_PER_PLAYER.get();
        Config.detectionRange = DETECTION_RANGE.get();
        Config.expeditionDurationMinutes = EXPEDITION_DURATION_MINUTES.get();
    }

    public static void onReload(ModConfigEvent.Reloading event) {
        Config.maxWolvesPerPlayer = MAX_WOLVES_PER_PLAYER.get();
        Config.detectionRange = DETECTION_RANGE.get();
        Config.expeditionDurationMinutes = EXPEDITION_DURATION_MINUTES.get();
    }
}
