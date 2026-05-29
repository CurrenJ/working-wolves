package grill24.workingwolves.client;

import grill24.workingwolves.Config;
import grill24.workingwolves.api.IWorkingWolf;
import grill24.workingwolves.inventory.DogBedMenu;
import grill24.workingwolves.item.CollarItem;
import grill24.workingwolves.network.DispatchFromBedPacket;
import grill24.workingwolves.network.RecallFromBedPacket;
import grill24.workingwolves.network.WorkingWolvesPackets;
import io.github.currenj.gelatinui.GelatinUIScreen;
import io.github.currenj.gelatinui.gui.UI;
import io.github.currenj.gelatinui.gui.components.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DogBedScreen extends GelatinUIScreen<DogBedMenu> {

    // Colors
    private static final int COLOR_PANEL_BG    = 0xC0181818;
    private static final int COLOR_PANEL_BORDER = 0xFF444444;
    private static final int COLOR_TITLE        = 0xFFFFD700;
    private static final int COLOR_WOLF_NAME    = 0xFFFFFFFF;
    private static final int COLOR_STATUS_IDLE  = 0xFF888888;
    private static final int COLOR_STATUS_ON    = 0xFF44FF44;
    private static final int COLOR_STATUS_RETURN= 0xFFFFAA00;
    private static final int COLOR_TIMER        = 0xFFCCCCCC;
    private static final int COLOR_BTN_DISPATCH = 0xFF2A6E2A;
    private static final int COLOR_BTN_RECALL   = 0xFF6E2A2A;
    private static final int COLOR_BTN_TEXT     = 0xFFFFFFFF;
    private static final int COLOR_LABEL_DIM    = 0xFF888888;

    // UI references for live updates
    private JournalPanel journalPanel;
    private Label statusLabel;
    private SpriteProgressBar progressBar;
    private Label timerLabel;
    private SpriteButton actionButton;

    // Cached state for live updates
    private int simElapsed;
    private int simTotal;
    private String simState;

    // Wolf preview rendering
    private Wolf fakeWolf = null;
    private float walkAnimTime = 0f;

    // ── Floor coordinate system ────────────────────────────────────────────────
    // The floor is a FLOOR_ROWS × FLOOR_COLS grid that scrolls under the wolf.
    // A 45° Y rotation is applied to the whole floor group before rendering, which
    // makes the grid appear angled — but the wolf itself always occupies the same
    // ROW. "One lane away" means one row closer to 0 or FLOOR_ROWS-1. The diagonal
    // formula |col - row - 1.5| seen in the docs is only the on-screen appearance
    // of the path; lava/side-object logic should use row-based lane distance.
    //
    // WOLF_ROW: determined empirically via DEBUG_SIDE_OBJECT_COORDS.
    private static final int WOLF_ROW = 2;
    private static final float SIDE_OBJECT_CHANCE = 0.01f;

    // ── Debug: set to true to identify which (col, row) cells the wolf overlaps.
    // Each row renders as a unique concrete color; scrolling is frozen.
    private static final boolean DEBUG_SIDE_OBJECT_COORDS = false;
    private static final BlockState[] DEBUG_COL_BLOCKS = {
        Blocks.WHITE_CONCRETE.defaultBlockState(),
        Blocks.ORANGE_CONCRETE.defaultBlockState(),
        Blocks.MAGENTA_CONCRETE.defaultBlockState(),
        Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(),
        Blocks.YELLOW_CONCRETE.defaultBlockState(),
        Blocks.LIME_CONCRETE.defaultBlockState(),
        Blocks.PINK_CONCRETE.defaultBlockState(),
        Blocks.GRAY_CONCRETE.defaultBlockState(),
        Blocks.CYAN_CONCRETE.defaultBlockState(),
    };

    private final Random floorRandom = new Random();
    private String lastKnownTheme = "";    // for detecting theme changes
    private String oldTheme = "";          // theme we're blending away from
    private float themeBlend = 1f;         // 0 = fully oldTheme, 1 = fully current theme
    private static final float THEME_BLEND_RATE = 1f / 30f; // blend completes over ~30 block generations
    private BlockState[] floorGrid = null;          // WolfPreviewFloorRenderer.FLOOR_COLS * WolfPreviewFloorRenderer.FLOOR_ROWS, row-major
    private BlockState[] sideObjectGrid = null;     // parallel grid; AIR in wolf's lane (WOLF_ROW) and lava areas
    private float floorRemainderZ = 0f;             // fractional scroll [0, 1)
    private float floorRemainderX = 0f;
    private int floorOriginX = 0;                   // virtual X of grid cell (0,0) in infinite plane
    private int floorOriginZ = 0;

    public DogBedScreen(DogBedMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        super.init(); // calls buildUI() which corrects leftPos/topPos and creates UI elements

        Minecraft.getInstance().options.hideGui = true;

        // Register callbacks for live updates while this screen is open
        DogBedScreenData.journalUpdateCallback = packet -> {
            if (journalPanel != null) journalPanel.appendLine(packet.line());
            simElapsed = packet.simElapsedTicks();
            simTotal = packet.simTotalTicks();
            refreshProgressUI();
        };
        DogBedScreenData.stateUpdateCallback = packet -> {
            simElapsed = packet.simElapsedTicks();
            simTotal = packet.simTotalTicks();
            simState = packet.simState();
            if (journalPanel != null) {
                String log = packet.expeditionLog();
                List<String> newLines = log.isEmpty() ? List.of() : List.of(log.split("\n", -1));
                journalPanel.setLines(newLines);
            }
            refreshProgressUI();
            refreshActionButton();
        };
    }

    @Override
    protected void buildUI() {
        // imageWidth/imageHeight are final and default to 176/166; fix leftPos/topPos for our dimensions
        this.leftPos = (this.width - DogBedMenu.IMAGE_WIDTH) / 2;
        this.topPos = (this.height - DogBedMenu.IMAGE_HEIGHT) / 2;

        // Snapshot current state from client cache
        simElapsed = DogBedScreenData.simElapsedTicks;
        simTotal = DogBedScreenData.simTotalTicks;
        simState = DogBedScreenData.simState;

        // Root ManualContainer covering full screen
        ManualContainer root = UI.manualContainer()
                .setSize(this.width, this.height)
                .backgroundColor(0x00000000); // transparent

        uiScreen.setRoot(root);
        uiScreen.setAutoCenterRoot(false);
        uiScreen.setScrollEnabled(false);

        int lp = this.leftPos;
        int tp = this.topPos;

        // ── Journal panel (left) ──────────────────────────────────────────
        journalPanel = new JournalPanel(154, 144);
        journalPanel.setLines(DogBedScreenData.journalLines);
        journalPanel.setSize(154, 144);
        // Place top-left at (lp+8, tp+8), so center at (lp+8+77, tp+8+72)
        root.addChildAt(journalPanel, lp + 8 + 77, tp + 8 + 72);

        // Journal title
        Label journalTitle = new Label("Expedition Journal", COLOR_TITLE);
        journalTitle.setSize(154, 9);
        // Center at top of journal area (above the panel)
        root.addChildAt(journalTitle, lp + 8 + 77, tp + 10);

        // ── Wolf header (right panel, above slots) ────────────────────────
        buildWolfHeader(root, lp, tp);

        // ── Action button (right panel, below slots) ──────────────────────
        buildActionButton(root, lp, tp);

        // ── Panel background rendering is done in extractContent ──────────
    }

    private void buildWolfHeader(ManualContainer root, int lp, int tp) {
        // Wolf name
        String wolfName = DogBedScreenData.wolfName.isEmpty() ? "Unassigned" : DogBedScreenData.wolfName;
        Label nameLabel = new Label(wolfName + "'s Bed", COLOR_WOLF_NAME);
        nameLabel.setSize(162, 9);
        root.addChildAt(nameLabel, lp + 178 + 85, tp + 10);

        // Role icons row
        HBox roleBox = UI.hbox().spacing(2).alignment(HBox.Alignment.CENTER);
        if (DogBedScreenData.hasMining) {
            roleBox.addChild(new Label("[Miner]", 0xFF99CCFF));
        }
        if (DogBedScreenData.hasHunting) {
            roleBox.addChild(new Label("[Hunter]", 0xFFFFCC88));
        }
        if (DogBedScreenData.hasWoodcutting) {
            roleBox.addChild(new Label("[Woodcutter]", 0xFF88FF88));
        }
        if (!DogBedScreenData.hasMining && !DogBedScreenData.hasHunting && !DogBedScreenData.hasWoodcutting) {
            roleBox.addChild(new Label("[No tools]", COLOR_LABEL_DIM));
        }
        roleBox.setSize(162, 9);
        root.addChildAt(roleBox, lp + 178 + 85, tp + 22);

        // Status label
        String statusText = statusText(simState);
        int statusColor = statusColor(simState);
        statusLabel = new Label(statusText, statusColor);
        statusLabel.setSize(162, 9);
        root.addChildAt(statusLabel, lp + 178 + 85, tp + 34);

        // Progress bar (hidden when not running) — no setSize; renders at native 63×19 sprite dimensions
        progressBar = UI.progressBar();
        progressBar.progressImmediate(progressFraction());
        progressBar.setVisible("running".equals(simState));
        root.addChildAt(progressBar, lp + 178 + 85, tp + 48);

        // Timer label
        timerLabel = new Label(timerText(), COLOR_TIMER);
        timerLabel.setSize(162, 9);
        timerLabel.setVisible("running".equals(simState));
        root.addChildAt(timerLabel, lp + 178 + 85, tp + 61);
    }

    private void buildActionButton(ManualContainer root, int lp, int tp) {
        boolean isRunning = "running".equals(simState);
        String btnText = isRunning ? "Recall Wolf" : "Dispatch Wolf";
        int btnColor = isRunning ? COLOR_BTN_RECALL : COLOR_BTN_DISPATCH;

        actionButton = UI.spriteButton(162, 20, btnColor).text(btnText, COLOR_BTN_TEXT);
        actionButton.onClick(e -> onActionButtonClick());
        // Center at (lp+178+81, tp+132+10)
        root.addChildAt(actionButton, lp + 178 + 85, tp + 141);
    }

    private void onActionButtonClick() {
        if ("running".equals(simState)) {
            WorkingWolvesPackets.sendToServer.accept(new RecallFromBedPacket(menu.getBedPos()));
            simState = "returning";
        } else {
            DogBedScreenData.journalLines.clear();
            if (journalPanel != null) journalPanel.setLines(List.of());
            WorkingWolvesPackets.sendToServer.accept(new DispatchFromBedPacket(menu.getBedPos()));
            simState = "departing";
        }
        refreshActionButton();
        refreshProgressUI();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if ("running".equals(simState) && simTotal > 0 && simElapsed < simTotal) {
            simElapsed++;
            refreshProgressUI();
        }
        // Animation is advanced per render frame in renderWolfPreview() using the frame delta,
        // so nothing to advance here.
    }

    private void refreshProgressUI() {
        if (progressBar != null) {
            progressBar.progress(progressFraction());
        }
        if (timerLabel != null) {
            timerLabel.text(timerText());
        }
        if (statusLabel != null) {
            statusLabel.text(statusText(simState)).color(statusColor(simState));
        }
    }

    private void refreshActionButton() {
        if (actionButton == null) return;
        boolean isRunning = "running".equals(simState);
        actionButton.text(isRunning ? "Recall Wolf" : "Dispatch Wolf", COLOR_BTN_TEXT)
                    .color(isRunning ? COLOR_BTN_RECALL : COLOR_BTN_DISPATCH);
        if (progressBar != null) progressBar.setVisible(isRunning);
        if (timerLabel != null) timerLabel.setVisible(isRunning);
    }

    private float progressFraction() {
        if (simTotal <= 0) return 0f;
        return Math.min(1f, (float) simElapsed / simTotal);
    }

    private String timerText() {
        if (simTotal <= 0) return "";
        int remaining = Math.max(0, simTotal - simElapsed);
        int seconds = remaining / 20;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d remaining", minutes, seconds);
    }

    private static String statusText(String state) {
        return switch (state) {
            case "running" -> "On Expedition";
            case "departing" -> "Departing...";
            case "returning" -> "Returning...";
            case "complete" -> "Returned";
            default -> DogBedScreenData.wolfName.isEmpty() ? "No wolf assigned" : "Idle";
        };
    }

    private static int statusColor(String state) {
        return switch (state) {
            case "running" -> COLOR_STATUS_ON;
            case "departing", "returning" -> COLOR_STATUS_RETURN;
            default -> COLOR_STATUS_IDLE;
        };
    }

    @Override
    public void extractTransparentBackground(GuiGraphicsExtractor graphics) {
        super.extractTransparentBackground(graphics);
        int lp = this.leftPos;
        int tp = this.topPos;
        drawPanelBg(graphics, lp + 8, tp + 8, 154, 144);
        drawPanelBg(graphics, lp + 8, tp + 160, 154, 74);
        drawPanelBg(graphics, lp + 178, tp + 8, 170, 144);
        drawPanelBg(graphics, lp + 178, tp + 160, 170, 74);
    }

    @Override
    protected void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        renderWolfPreview(graphics, partialTick);
        // Redraw borders on top of the wolf preview so they aren't buried under it
        int lp = this.leftPos;
        int tp = this.topPos;
        drawPanelBorder(graphics, lp + 8, tp + 8, 154, 144);
        drawPanelBorder(graphics, lp + 8, tp + 160, 154, 74);
        drawPanelBorder(graphics, lp + 178, tp + 8, 170, 144);
        drawPanelBorder(graphics, lp + 178, tp + 160, 170, 74);
    }

    @SuppressWarnings("unchecked")
    private void renderWolfPreview(GuiGraphicsExtractor graphics, float partialTick) {
        if (DogBedScreenData.wolfName.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        if (fakeWolf == null) {
            fakeWolf = EntityType.WOLF.create(level, EntitySpawnReason.LOAD);
            if (fakeWolf == null) return;
        }

        // Apply collar color from tier
        if (DogBedScreenData.collarTier > 0) {
            fakeWolf.setTame(true, false);
            fakeWolf.setCollarColor(CollarItem.getCollarColorForTier(DogBedScreenData.collarTier));
        }

        // Show the most recently found loot item in the wolf's mouth during expedition
        boolean isMoving = "running".equals(simState) || "departing".equals(simState) || "returning".equals(simState);
        ItemStack mouthItem = isMoving ? DogBedScreenData.lastLootItem : ItemStack.EMPTY;
        ((IWorkingWolf) (Object) fakeWolf).workingwolves$setMouthItem(mouthItem);

        // Extract render state via the registered WolfRenderer (which runs our WolfRendererMixin for mouth items)
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer<Wolf, ?> renderer = (EntityRenderer<Wolf, ?>) dispatcher.getRenderer(fakeWolf);
        EntityRenderState state = renderer.createRenderState(fakeWolf, partialTick);
        state.shadowPieces.clear();
        state.outlineColor = 0;

        // Advance animation per render frame (partialTick = getGameTimeDeltaTicks(), the per-frame
        // delta in tick units — not a 0→1 interpolation position — so we accumulate directly).
        if (isMoving) {
            walkAnimTime += 0.4f * partialTick;
            advanceFloorScroll(partialTick);
        }

        // Drive walk animation from our counter; override body/head rotation for a nice 3/4 view
        if (state instanceof LivingEntityRenderState living) {
            living.walkAnimationPos = walkAnimTime;
            living.walkAnimationSpeed = isMoving ? 0.4f : 0.0f;
            living.bodyRot = Config.previewBodyRot;
            living.yRot = Config.previewYRot;
            living.xRot = Config.previewXRot;
            if (state instanceof WolfRenderState wolfState) {
                wolfState.isSitting = !isMoving && !"complete".equals(simState);
                wolfState.tailAngle = isMoving ? (float) (Math.PI * 0.6) : (float) (Math.PI / 5);
            }
        }

        // Panel bounds
        int x0 = this.leftPos + 8;
        int y0 = this.topPos + 160;
        int x1 = this.leftPos + 162;
        int y1 = this.topPos + 234;

        Vector3f translation = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F, 0.0F);
        Quaternionf rotation = new Quaternionf()
            .rotateZ((float) Math.PI)
            .rotateX(Config.previewPitch * (float) (Math.PI / 180.0));

        graphics.guiRenderState.addPicturesInPictureState(new WolfPreviewFloorRenderState(
            state, translation, rotation, null,
            x0, y0, x1, y1, Config.previewSize,
            graphics.scissorStack.peek(),
            getFloorGridAsList(),
            floorRemainderZ,
            floorRemainderX,
            floorOriginX,
            floorOriginZ,
            getSideObjectGridAsList()
        ));
    }

    // ── Floor scroll queue ────────────────────────────────────────────────

    /** Current floor theme string, used to detect theme changes. */
    private String floorTheme() {
        if (DogBedScreenData.hasMining) return "mine";
        if (DogBedScreenData.hasWoodcutting) return "wood";
        return "hunt";
    }

    /** Returns the current mining zone (1-3) based on expedition progress, without blending side-effects. */
    private int currentMiningZone() {
        int total = DogBedScreenData.simTotalTicks;
        if (total <= 0) return 1;
        float progress = (float) DogBedScreenData.simElapsedTicks / total;
        if (progress >= 0.67f) return 3;
        if (progress >= 0.33f) return 2;
        return 1;
    }

    /** How many lanes away from the wolf a row is. Lane 0 = wolf's row. */
    private static int laneFromRow(int row) {
        return Math.abs(row - WOLF_ROW);
    }

    /** Random scenery object for a side cell, or AIR in the wolf's lane or over lava. */
    private BlockState newSideObjectForCol(int col, int row) {
        if (DEBUG_SIDE_OBJECT_COORDS) return DEBUG_COL_BLOCKS[row % DEBUG_COL_BLOCKS.length];
        if (row == WOLF_ROW) return Blocks.AIR.defaultBlockState();
        // In zone 3 mine, lava covers all non-wolf lanes — no objects floating above it
        if ("mine".equals(floorTheme()) && currentMiningZone() == 3 && laneFromRow(row) > 0)
            return Blocks.AIR.defaultBlockState();
        if (floorRandom.nextFloat() >= SIDE_OBJECT_CHANCE) return Blocks.AIR.defaultBlockState();
        return switch (floorTheme()) {
            case "mine" -> {
                int r = floorRandom.nextInt(4);
                if (r == 0) yield Blocks.CRAFTING_TABLE.defaultBlockState();
                if (r == 1) yield Blocks.FURNACE.defaultBlockState();
                if (r == 2) yield Blocks.COBBLESTONE_WALL.defaultBlockState();
                yield Blocks.CAULDRON.defaultBlockState();
            }
            case "wood" -> {
                int r = floorRandom.nextInt(3);
                if (r == 0) yield Blocks.OAK_LOG.defaultBlockState();
                if (r == 1) yield Blocks.SPRUCE_LOG.defaultBlockState();
                yield Blocks.BIRCH_LOG.defaultBlockState();
            }
            default -> { // hunt
                int r = floorRandom.nextInt(4);
                if (r == 0) yield Blocks.OAK_FENCE.defaultBlockState();
                if (r == 1) yield Blocks.DEAD_BUSH.defaultBlockState();
                if (r == 2) yield Blocks.DANDELION.defaultBlockState();
                yield Blocks.POPPY.defaultBlockState();
            }
        };
    }

    /** Weighted random block for the current theme, blending from the previous theme when it just changed. */
    private BlockState randomFloorBlock(int col, int row) {
        String theme = floorTheme();
        if (!theme.equals(lastKnownTheme)) {
            oldTheme = lastKnownTheme;
            lastKnownTheme = theme;
            themeBlend = 0f;
        }
        themeBlend = Math.min(1f, themeBlend + THEME_BLEND_RATE);
        if (themeBlend < 1f && !oldTheme.isEmpty() && floorRandom.nextFloat() >= themeBlend)
            return blockForTheme(oldTheme, col, row);
        return blockForTheme(theme, col, row);
    }

    private BlockState blockForTheme(String theme, int col, int row) {
        return switch (theme) {
            case "mine" -> randomMineFloorBlock(col, row);
            case "wood" -> {
                int r = floorRandom.nextInt(13);
                if (r < 5) yield Blocks.PODZOL.defaultBlockState();
                if (r < 8) yield Blocks.COARSE_DIRT.defaultBlockState();
                if (r < 10) yield Blocks.DIRT.defaultBlockState();
                if (r < 12) yield Blocks.ROOTED_DIRT.defaultBlockState();
                yield Blocks.MOSS_BLOCK.defaultBlockState();
            }
            default -> { // hunt / idle
                int r = floorRandom.nextInt(11);
                if (r < 6) yield Blocks.GRASS_BLOCK.defaultBlockState();
                if (r < 8) yield Blocks.DIRT.defaultBlockState();
                if (r < 10) yield Blocks.COARSE_DIRT.defaultBlockState();
                yield Blocks.PODZOL.defaultBlockState();
            }
        };
    }

    /** Width of the palette blend window (in expedition-progress units) centred on each zone threshold. */
    private static final float MINE_ZONE_TRANSITION = 0.08f;

    /** Blended floor block: near zone thresholds, samples probabilistically from both adjacent palettes. */
    private BlockState randomMineFloorBlock(int col, int row) {
        int total = DogBedScreenData.simTotalTicks;
        if (total <= 0) return randomMineFloorBlockForZone(1, col, row);
        float progress = (float) DogBedScreenData.simElapsedTicks / total;

        // blend12: 0.0 = pure zone 1, 1.0 = pure zone 2+  (window centred on 0.33)
        float blend12 = Math.max(0f, Math.min(1f, (progress - (0.33f - MINE_ZONE_TRANSITION * 0.5f)) / MINE_ZONE_TRANSITION));
        // blend23: 0.0 = pure zone 2, 1.0 = pure zone 3   (window centred on 0.67)
        float blend23 = Math.max(0f, Math.min(1f, (progress - (0.67f - MINE_ZONE_TRANSITION * 0.5f)) / MINE_ZONE_TRANSITION));

        if (blend23 > 0f)
            return floorRandom.nextFloat() < blend23 ? randomMineFloorBlockForZone(3, col, row) : randomMineFloorBlockForZone(2, col, row);
        return floorRandom.nextFloat() < blend12 ? randomMineFloorBlockForZone(2, col, row) : randomMineFloorBlockForZone(1, col, row);
    }

    // Lava probability per lane, per zone. Lane 0 = WOLF_ROW, always solid.
    // Each step away from WOLF_ROW increases lava chance by these amounts (capped at max).
    private static final float ZONE2_LAVA_PER_LANE = 0.01f;  // lane 1=1%, 2=2%, 3=3%
    private static final float ZONE3_LAVA_PER_LANE = 0.4f;  // lane 1=40%, 2=80%, 3=100%

    private BlockState randomMineFloorBlockForZone(int zone, int col, int row) {
        int lane = laneFromRow(row);
        return switch (zone) {
            case 2 -> { // mid-depth: lava seeps in from outer lanes
                float lavaChance = Math.min(0.65f, lane * ZONE2_LAVA_PER_LANE);
                if (lavaChance > 0f && floorRandom.nextFloat() < lavaChance)
                    yield Blocks.LAVA.defaultBlockState();
                int r = floorRandom.nextInt(20);
                if (r < 5)  yield Blocks.STONE.defaultBlockState();
                if (r < 10) yield Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                if (r < 17) yield Blocks.DEEPSLATE.defaultBlockState();
                if (r < 19) yield Blocks.GRAVEL.defaultBlockState();
                yield Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
            }
            case 3 -> { // deep: wolf's lane is solid ground, all others fill with lava
                if (lane == 0) {
                    // Wolf's lane: always solid deepslate/blackstone with rare ores
                    int r = floorRandom.nextInt(20);
                    if (r < 8)  yield Blocks.DEEPSLATE.defaultBlockState();
                    if (r < 13) yield Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                    if (r < 15) yield Blocks.BLACKSTONE.defaultBlockState();
                    if (r < 16) yield Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
                    if (r < 17) yield Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
                    if (r < 18) yield Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState();
                    if (r < 19) yield Blocks.BASALT.defaultBlockState();
                    yield Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState();
                } else {
                    float lavaChance = Math.min(0.95f, lane * ZONE3_LAVA_PER_LANE);
                    if (floorRandom.nextFloat() < lavaChance)
                        yield Blocks.LAVA.defaultBlockState();
                    // Rare solid patches that survived the lava
                    int r = floorRandom.nextInt(5);
                    if (r < 3) yield Blocks.DEEPSLATE.defaultBlockState();
                    if (r < 4) yield Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                    yield Blocks.BLACKSTONE.defaultBlockState();
                }
            }
            default -> { // zone 1: shallow — mostly stone, ~5% coal ore, no lava
                int r = floorRandom.nextInt(20);
                if (r < 10) yield Blocks.STONE.defaultBlockState();
                if (r < 14) yield Blocks.COBBLESTONE.defaultBlockState();
                if (r < 17) yield Blocks.GRAVEL.defaultBlockState();
                if (r < 19) yield Blocks.ANDESITE.defaultBlockState();
                yield Blocks.COAL_ORE.defaultBlockState();
            }
        };
    }

    /** Ensure the floor grid is initialized. Never wipes on theme change — new blocks scroll in with blending naturally. */
    private void ensureFloorGrid() {
        int expectedSize = WolfPreviewFloorRenderer.FLOOR_COLS * WolfPreviewFloorRenderer.FLOOR_ROWS;
        if (floorGrid != null && floorGrid.length == expectedSize)
            return;

        lastKnownTheme = floorTheme();
        themeBlend = 1f;
        floorGrid = new BlockState[expectedSize];
        sideObjectGrid = new BlockState[expectedSize];
        for (int row = 0; row < WolfPreviewFloorRenderer.FLOOR_ROWS; row++) {
            for (int col = 0; col < WolfPreviewFloorRenderer.FLOOR_COLS; col++) {
                int i = row * WolfPreviewFloorRenderer.FLOOR_COLS + col;
                floorGrid[i] = randomFloorBlock(col, row);
                sideObjectGrid[i] = newSideObjectForCol(col, row);
            }
        }
        floorRemainderZ = 0f;
        floorRemainderX = 0f;
        floorOriginX = 0;
        floorOriginZ = 0;
    }

    /** Advance scroll by a frame delta (in tick units), shifting the queue when a block boundary is crossed. */
    private void advanceFloorScroll(float delta) {
        if (DEBUG_SIDE_OBJECT_COORDS) { ensureFloorGrid(); return; }
        ensureFloorGrid();

        floorRemainderZ += Config.previewFloorScrollSpeed * delta;
        while (floorRemainderZ >= 1.0f) {
            floorRemainderZ -= 1.0f;
            shiftRowsTowardFar();
        }
        while (floorRemainderZ < 0f) {
            floorRemainderZ += 1.0f;
            shiftRowsTowardNear();
        }

        floorRemainderX += Config.previewFloorScrollSpeedX * delta;
        while (floorRemainderX >= 1.0f) {
            floorRemainderX -= 1.0f;
            shiftColsTowardRight();
        }
        while (floorRemainderX < 0f) {
            floorRemainderX += 1.0f;
            shiftColsTowardLeft();
        }
    }

    /** Row 0 is near (closest to camera), row ROWS-1 is far. Positive Z scroll moves blocks toward camera, so the far row scrolls off and a new near row is needed. */
    private void shiftRowsTowardFar() {
        for (int row = WolfPreviewFloorRenderer.FLOOR_ROWS - 1; row > 0; row--) {
            for (int col = 0; col < WolfPreviewFloorRenderer.FLOOR_COLS; col++) {
                int i = row * WolfPreviewFloorRenderer.FLOOR_COLS + col;
                int prev = (row - 1) * WolfPreviewFloorRenderer.FLOOR_COLS + col;
                floorGrid[i] = floorGrid[prev];
                sideObjectGrid[i] = sideObjectGrid[prev];
            }
        }
        for (int col = 0; col < WolfPreviewFloorRenderer.FLOOR_COLS; col++) {
            floorGrid[col] = randomFloorBlock(col, 0);
            sideObjectGrid[col] = newSideObjectForCol(col, 0);
        }
        floorOriginZ--;
    }

    private void shiftRowsTowardNear() {
        for (int row = 0; row < WolfPreviewFloorRenderer.FLOOR_ROWS - 1; row++) {
            for (int col = 0; col < WolfPreviewFloorRenderer.FLOOR_COLS; col++) {
                int i = row * WolfPreviewFloorRenderer.FLOOR_COLS + col;
                int next = (row + 1) * WolfPreviewFloorRenderer.FLOOR_COLS + col;
                floorGrid[i] = floorGrid[next];
                sideObjectGrid[i] = sideObjectGrid[next];
            }
        }
        int lastRow = (WolfPreviewFloorRenderer.FLOOR_ROWS - 1) * WolfPreviewFloorRenderer.FLOOR_COLS;
        for (int col = 0; col < WolfPreviewFloorRenderer.FLOOR_COLS; col++) {
            floorGrid[lastRow + col] = randomFloorBlock(col, WolfPreviewFloorRenderer.FLOOR_ROWS - 1);
            sideObjectGrid[lastRow + col] = newSideObjectForCol(col, WolfPreviewFloorRenderer.FLOOR_ROWS - 1);
        }
        floorOriginZ++;
    }

    private void shiftColsTowardRight() {
        for (int row = 0; row < WolfPreviewFloorRenderer.FLOOR_ROWS; row++) {
            int base = row * WolfPreviewFloorRenderer.FLOOR_COLS;
            for (int col = WolfPreviewFloorRenderer.FLOOR_COLS - 1; col > 0; col--) {
                floorGrid[base + col] = floorGrid[base + col - 1];
                sideObjectGrid[base + col] = sideObjectGrid[base + col - 1];
            }
            floorGrid[base] = randomFloorBlock(0, row);
            sideObjectGrid[base] = newSideObjectForCol(0, row);
        }
        floorOriginX--;
    }

    private void shiftColsTowardLeft() {
        int lastCol = WolfPreviewFloorRenderer.FLOOR_COLS - 1;
        for (int row = 0; row < WolfPreviewFloorRenderer.FLOOR_ROWS; row++) {
            int base = row * WolfPreviewFloorRenderer.FLOOR_COLS;
            for (int col = 0; col < lastCol; col++) {
                floorGrid[base + col] = floorGrid[base + col + 1];
                sideObjectGrid[base + col] = sideObjectGrid[base + col + 1];
            }
            floorGrid[base + lastCol] = randomFloorBlock(lastCol, row);
            sideObjectGrid[base + lastCol] = newSideObjectForCol(lastCol, row);
        }
        floorOriginX++;
    }

    /** Flatten the current grid into a list for the render state. */
    private List<BlockState> getFloorGridAsList() {
        ensureFloorGrid();
        var list = new ArrayList<BlockState>(floorGrid.length);
        for (BlockState bs : floorGrid) list.add(bs);
        return list;
    }

    private List<BlockState> getSideObjectGridAsList() {
        ensureFloorGrid();
        var list = new ArrayList<BlockState>(sideObjectGrid.length);
        for (BlockState bs : sideObjectGrid) list.add(bs);
        return list;
    }

    private static void drawPanelBg(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, COLOR_PANEL_BG);
    }

    private static void drawPanelBorder(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, COLOR_PANEL_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_PANEL_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_PANEL_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_PANEL_BORDER);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (journalPanel != null) {
            var opts = Minecraft.getInstance().options;
            if (opts.keyUp.matches(event) || event.key() == GLFW.GLFW_KEY_UP) {
                journalPanel.scroll(-1);
                return true;
            }
            if (opts.keyDown.matches(event) || event.key() == GLFW.GLFW_KEY_DOWN) {
                journalPanel.scroll(1);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        super.removed();
        Minecraft.getInstance().options.hideGui = false;
        DogBedScreenData.journalUpdateCallback = null;
        DogBedScreenData.stateUpdateCallback = null;
        fakeWolf = null;
        floorGrid = null;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int i, int j) {
        // Suppress default label rendering — gelatin-ui handles labels
    }
}
