package grill24.workingwolves.client;

import grill24.workingwolves.ModMenuTypes;
import grill24.workingwolves.network.BedJournalUpdatePacket;
import grill24.workingwolves.network.BedStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Client-side cache for the currently open dog bed's state.
 * Populated by BedStatePacket before the screen opens, then updated by BedJournalUpdatePacket.
 */
public final class DogBedScreenData {
    public static BlockPos currentBedPos = BlockPos.ZERO;
    public static String wolfName = "";
    public static int collarTier = 0;
    public static String simState = "inactive";
    public static boolean hasMining = false;
    public static boolean hasHunting = false;
    public static boolean hasWoodcutting = false;
    public static int simElapsedTicks = 0;
    public static int simTotalTicks = 0;
    public static List<String> journalLines = new ArrayList<>();
    public static ItemStack lastLootItem = ItemStack.EMPTY;

    // Callbacks set by DogBedScreen while it's open; cleared on screen close
    public static Consumer<BedJournalUpdatePacket> journalUpdateCallback = null;
    public static Consumer<BedStatePacket> stateUpdateCallback = null;

    public static void applyStatePacket(BedStatePacket packet) {
        currentBedPos = packet.bedPos();
        wolfName = packet.wolfName();
        collarTier = packet.collarTier();
        simState = packet.simState();
        hasMining = packet.hasMining();
        hasHunting = packet.hasHunting();
        hasWoodcutting = packet.hasWoodcutting();
        simElapsedTicks = packet.simElapsedTicks();
        simTotalTicks = packet.simTotalTicks();
        String log = packet.expeditionLog();
        journalLines = log.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(log.split("\n", -1)));

        // Also set the pending bed pos so the client-side menu constructor can read it
        ModMenuTypes.pendingBedPos = currentBedPos;

        // Notify open screen if any
        if (stateUpdateCallback != null) {
            stateUpdateCallback.accept(packet);
        }
    }

    public static void applyJournalUpdate(BedJournalUpdatePacket packet) {
        if (!packet.bedPos().equals(currentBedPos)) return;
        simElapsedTicks = packet.simElapsedTicks();
        simTotalTicks = packet.simTotalTicks();
        journalLines.add(packet.line());
        if (!packet.lastLootItem().isEmpty()) {
            lastLootItem = packet.lastLootItem();
        }
        if (journalUpdateCallback != null) {
            journalUpdateCallback.accept(packet);
        }
    }

    public static void clear() {
        currentBedPos = BlockPos.ZERO;
        wolfName = "";
        collarTier = 0;
        simState = "inactive";
        hasMining = false;
        hasHunting = false;
        hasWoodcutting = false;
        simElapsedTicks = 0;
        simTotalTicks = 0;
        journalLines = new ArrayList<>();
        lastLootItem = ItemStack.EMPTY;
        journalUpdateCallback = null;
        stateUpdateCallback = null;
    }

    private DogBedScreenData() {}
}
