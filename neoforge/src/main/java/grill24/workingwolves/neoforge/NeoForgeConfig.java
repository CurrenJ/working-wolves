package grill24.workingwolves.neoforge;

import grill24.workingwolves.Config;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class NeoForgeConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_WOLVES_PER_PLAYER;
    public static final ModConfigSpec.IntValue DETECTION_RANGE;
    public static final ModConfigSpec.IntValue EXPEDITION_DURATION_MINUTES_TIER1;
    public static final ModConfigSpec.IntValue EXPEDITION_DURATION_MINUTES_TIER2;
    public static final ModConfigSpec.IntValue EXPEDITION_DURATION_MINUTES_TIER3;
    public static final ModConfigSpec.IntValue HUNTER_SCAN_RANGE;
    public static final ModConfigSpec.IntValue EXPEDITION_EVENT_INTERVAL_MIN_TICKS;
    public static final ModConfigSpec.IntValue EXPEDITION_EVENT_INTERVAL_MAX_TICKS;
    public static final ModConfigSpec.DoubleValue RARE_EVENT_CHANCE;
    public static final ModConfigSpec.DoubleValue RARE_EVENT_CROSS_ROLE_CHANCE;
    public static final ModConfigSpec.DoubleValue EXPEDITION_DURABILITY_MULTIPLIER;

    public static final ModConfigSpec SPEC;

    static {
        MAX_WOLVES_PER_PLAYER = BUILDER
                .comment("Maximum number of working wolves per player")
                .defineInRange("maxWolvesPerPlayer", 5, 1, 20);

        DETECTION_RANGE = BUILDER
                .comment("Range (blocks) within which wolves detect ores/mobs and pathfind to them. Also scales pathfinder resources.")
                .defineInRange("detectionRange", 64, 16, 256);

        EXPEDITION_DURATION_MINUTES_TIER1 = BUILDER
                .comment("Expedition duration (minutes) for leather collar wolves. Cannot reach deep zones.")
                .defineInRange("expeditionDurationMinutesTier1", 10, 1, 120);

        EXPEDITION_DURATION_MINUTES_TIER2 = BUILDER
                .comment("Expedition duration (minutes) for iron-studded collar wolves.")
                .defineInRange("expeditionDurationMinutesTier2", 18, 1, 120);

        EXPEDITION_DURATION_MINUTES_TIER3 = BUILDER
                .comment("Expedition duration (minutes) for gold-trimmed collar wolves.")
                .defineInRange("expeditionDurationMinutesTier3", 25, 1, 120);

        HUNTER_SCAN_RANGE = BUILDER
                .comment("Range (blocks) within which hunter wolves scan for hostile mobs.")
                .defineInRange("hunterScanRange", 32, 8, 128);

        EXPEDITION_EVENT_INTERVAL_MIN_TICKS = BUILDER
                .comment("Minimum ticks between expedition event rolls (default 160 = 8 seconds).")
                .defineInRange("expeditionEventIntervalMinTicks", 160, 1, 72000);

        EXPEDITION_EVENT_INTERVAL_MAX_TICKS = BUILDER
                .comment("Maximum ticks between expedition event rolls, exclusive (default 600 = 30 seconds). Must be greater than min.")
                .defineInRange("expeditionEventIntervalMaxTicks", 600, 2, 72000);

        RARE_EVENT_CHANCE = BUILDER
                .comment("Probability (0.0–1.0) that a rare event fires on each expedition event roll.")
                .defineInRange("rareEventChance", 0.025, 0.0, 1.0);

        RARE_EVENT_CROSS_ROLE_CHANCE = BUILDER
                .comment("Probability (0.0–1.0) that a cross-role rare event fires when the wolf has 2+ roles.")
                .defineInRange("rareEventCrossRoleChance", 0.12, 0.0, 1.0);

        EXPEDITION_DURABILITY_MULTIPLIER = BUILDER
                .comment("Multiplier applied to all expedition tool durability damage (1.0 = default, 0.5 = half wear, 2.0 = double wear).")
                .defineInRange("expeditionDurabilityMultiplier", 1.0, 0.0, 10.0);

        SPEC = BUILDER.build();
    }

    public static void onLoad(ModConfigEvent.Loading event) {
        Config.maxWolvesPerPlayer = MAX_WOLVES_PER_PLAYER.get();
        Config.detectionRange = DETECTION_RANGE.get();
        Config.expeditionDurationMinutesTier1 = EXPEDITION_DURATION_MINUTES_TIER1.get();
        Config.expeditionDurationMinutesTier2 = EXPEDITION_DURATION_MINUTES_TIER2.get();
        Config.expeditionDurationMinutesTier3 = EXPEDITION_DURATION_MINUTES_TIER3.get();
        Config.hunterScanRange = HUNTER_SCAN_RANGE.get();
        Config.expeditionEventIntervalMinTicks = EXPEDITION_EVENT_INTERVAL_MIN_TICKS.get();
        Config.expeditionEventIntervalMaxTicks = EXPEDITION_EVENT_INTERVAL_MAX_TICKS.get();
        Config.rareEventChance = RARE_EVENT_CHANCE.get().floatValue();
        Config.rareEventCrossRoleChance = RARE_EVENT_CROSS_ROLE_CHANCE.get().floatValue();
        Config.expeditionDurabilityMultiplier = EXPEDITION_DURABILITY_MULTIPLIER.get().floatValue();
    }

    public static void onReload(ModConfigEvent.Reloading event) {
        Config.maxWolvesPerPlayer = MAX_WOLVES_PER_PLAYER.get();
        Config.detectionRange = DETECTION_RANGE.get();
        Config.expeditionDurationMinutesTier1 = EXPEDITION_DURATION_MINUTES_TIER1.get();
        Config.expeditionDurationMinutesTier2 = EXPEDITION_DURATION_MINUTES_TIER2.get();
        Config.expeditionDurationMinutesTier3 = EXPEDITION_DURATION_MINUTES_TIER3.get();
        Config.hunterScanRange = HUNTER_SCAN_RANGE.get();
        Config.expeditionEventIntervalMinTicks = EXPEDITION_EVENT_INTERVAL_MIN_TICKS.get();
        Config.expeditionEventIntervalMaxTicks = EXPEDITION_EVENT_INTERVAL_MAX_TICKS.get();
        Config.rareEventChance = RARE_EVENT_CHANCE.get().floatValue();
        Config.rareEventCrossRoleChance = RARE_EVENT_CROSS_ROLE_CHANCE.get().floatValue();
        Config.expeditionDurabilityMultiplier = EXPEDITION_DURABILITY_MULTIPLIER.get().floatValue();
    }
}
