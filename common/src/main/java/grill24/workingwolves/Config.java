package grill24.workingwolves;

public class Config {
    public static int maxWolvesPerPlayer = 5;

    /** Range (in blocks) within which miner wolves scan for ores.
     *  Also drives maxVisitedNodes and requiredPathLength for mining pathfinding. */
    public static int detectionRange = 64;

    /** Range (in blocks) within which hunter wolves scan for hostile mobs. */
    public static int hunterScanRange = 32;

    /** Range (in blocks) within which retriever wolves collect dropped items (centered on bed). */
    public static int retrieverScanRange = 64;

    /** Expedition duration in minutes. 0 = unlimited. */
    public static int expeditionDurationMinutes = 15;

    /** When true, creepers will not prime or explode when targeted by working wolves. */
    public static boolean hunterCreeperSafe = true;

    // Mouth item render tuning (runtime, not persisted to config files)
    public static float mouthOffsetX = 0.125F;
    public static float mouthOffsetY = 0.094F;
    public static float mouthOffsetZ = -0.375F;
    public static float mouthRotX = -90.0F;
    public static float mouthRotY = 0.0F;
    public static float mouthRotZ = 45.0F;
}
