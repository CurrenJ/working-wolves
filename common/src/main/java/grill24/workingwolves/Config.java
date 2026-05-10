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
}
