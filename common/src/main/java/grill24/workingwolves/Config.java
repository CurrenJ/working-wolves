package grill24.workingwolves;

public class Config {
    public static int maxWolvesPerPlayer = 5;

    /** Range (in blocks) within which miner wolves scan for ores.
     *  Also drives maxVisitedNodes and requiredPathLength for mining pathfinding. */
    public static int detectionRange = 64;

    /** Range (in blocks) within which hunter wolves scan for hostile mobs. */
    public static int hunterScanRange = 32;

    /** Per-tier expedition durations in minutes. */
    public static int expeditionDurationMinutesTier1 = 10;
    public static int expeditionDurationMinutesTier2 = 18;
    public static int expeditionDurationMinutesTier3 = 25;

    /** Minimum ticks between expedition event rolls. */
    public static int expeditionEventIntervalMinTicks = 160;

    /** Maximum ticks between expedition event rolls (exclusive upper bound). Must be > expeditionEventIntervalMinTicks. */
    public static int expeditionEventIntervalMaxTicks = 600;

    /** Probability (0–1) that a rare event fires on each expedition event roll. */
    public static float rareEventChance = 0.025f;

    /** Probability (0–1) that a cross-role rare event fires when the wolf has 2+ roles. */
    public static float rareEventCrossRoleChance = 0.12f;

    /** Multiplier applied to all expedition tool durability damage (1.0 = default, 0.5 = half wear, 2.0 = double wear). */
    public static float expeditionDurabilityMultiplier = 1.0f;

    /** When true, creepers will not prime or explode when targeted by working wolves. */
    public static boolean hunterCreeperSafe = true;

    // Mouth item render tuning (runtime, not persisted to config files)
    public static float mouthOffsetX = 0.125F;
    public static float mouthOffsetY = 0.094F;
    public static float mouthOffsetZ = -0.375F;
    public static float mouthRotX = -90.0F;
    public static float mouthRotY = 0.0F;
    public static float mouthRotZ = 45.0F;

    // Wolf preview GUI render tuning (runtime, not persisted to config files)
    public static float previewBodyRot = 220f;    // living.bodyRot  — spins body around vertical axis
    public static float previewYRot = 15f;        // living.yRot     — head horizontal yaw
    public static float previewXRot = 0f;         // living.xRot     — head vertical pitch
    public static float previewPitch = -20f;      // quaternion X    — tilts whole entity toward/away from viewer
    public static int previewSize = 45;
    public static float previewFloorScrollSpeed = 0f;    // Z-axis scroll rate (0 = no Z scroll)
    public static float previewFloorY = -1.0f;           // Y of floor block bottom (top face at Y+1 = entity feet level)

    // Floor block render tuning — rotation, scale, grid offset, scroll direction
    public static float previewFloorRotX = 0f;
    public static float previewFloorRotY = 45f;
    public static float previewFloorRotZ = 0f;
    public static float previewFloorScale = 1.0f;
    public static float previewFloorOffsetX = 0f;
    public static float previewFloorOffsetZ = 1f;
    public static float previewFloorScrollSpeedX = -0.05f;  // X-axis scroll rate (negative = scroll left)
    public static float previewFloorSpacing = 1.0f;        // grid cell size in blocks (1.0 = adjacent, >1 = gaps, <1 = overlap)
}
