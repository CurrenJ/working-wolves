package grill24.workingwolves.client;

import grill24.workingwolves.inventory.DogBedMenu;
import grill24.workingwolves.network.DispatchFromBedPacket;
import grill24.workingwolves.network.RecallFromBedPacket;
import grill24.workingwolves.network.WorkingWolvesPackets;
import io.github.currenj.gelatinui.GelatinUIScreen;
import io.github.currenj.gelatinui.gui.UI;
import io.github.currenj.gelatinui.gui.components.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

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
        root.addChildAt(actionButton, lp + 178 + 85, tp + 142);
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
        drawPanel(graphics, lp + 8, tp + 8, 154, 144);
        drawPanel(graphics, lp + 178, tp + 8, 170, 144);
        drawPanel(graphics, lp + 178, tp + 160, 170, 74);
    }

    @Override
    protected void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    private static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        // Fill
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, COLOR_PANEL_BG);
        // Border
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
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int i, int j) {
        // Suppress default label rendering — gelatin-ui handles labels
    }
}
