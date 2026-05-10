package grill24.workingwolves;

public class Config {
    public static int maxWolvesPerPlayer = 5;

    /** Range (in blocks) within which wolves detect ores/mobs and pathfind to them.
     *  Also drives maxVisitedNodes and requiredPathLength in the pathfinder. */
    public static int detectionRange = 64;

    /** Expedition duration in minutes. 0 = unlimited. */
    public static int expeditionDurationMinutes = 15;
}
