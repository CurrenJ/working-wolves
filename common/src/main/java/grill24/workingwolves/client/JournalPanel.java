package grill24.workingwolves.client;

import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.UIEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * A fixed-size panel that renders expedition journal lines with scrolling via scissor clipping.
 */
public class JournalPanel extends UIElement<JournalPanel> {
    private static final int LINE_HEIGHT = 9;
    private static final int PADDING = 4;
    private static final int BG_COLOR = 0xC0111111;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int TRAVEL_COLOR = 0xFFAAAAAA;
    private static final int DISCOVERY_COLOR = 0xFFAAFFAA;
    private static final int HAZARD_COLOR = 0xFFFFAAAA;
    private static final int TITLE_COLOR = 0xFFFFD700;

    private final List<String> lines = new ArrayList<>();
    private int scrollOffset = 0; // index of first visible line

    public JournalPanel(int width, int height) {
        this.size.set(width, height);
    }

    public void appendLine(String line) {
        lines.add(line);
        // Auto-scroll to show the latest line
        int maxVisible = maxVisibleLines();
        int maxScroll = Math.max(0, lines.size() - maxVisible);
        scrollOffset = maxScroll;
        markDirty();
    }

    public void setLines(List<String> newLines) {
        lines.clear();
        lines.addAll(newLines);
        int maxVisible = maxVisibleLines();
        int maxScroll = Math.max(0, lines.size() - maxVisible);
        scrollOffset = maxScroll;
        markDirty();
    }

    private int maxVisibleLines() {
        int innerH = (int) size.y - PADDING * 2;
        return Math.max(1, innerH / LINE_HEIGHT);
    }

    @Override
    protected void onUpdate(float deltaTime) {}

    @Override
    protected void renderSelf(IRenderContext context) {
        int x = 0;
        int y = 0;
        int w = (int) size.x;
        int h = (int) size.y;

        // Background
        context.enableBlend();
        context.fill(x, y, x + w, y + h, BG_COLOR);
        context.disableBlend();

        // Border
        context.fill(x, y, x + w, y + 1, BORDER_COLOR);
        context.fill(x, y + h - 1, x + w, y + h, BORDER_COLOR);
        context.fill(x, y, x + 1, y + h, BORDER_COLOR);
        context.fill(x + w - 1, y, x + w, y + h, BORDER_COLOR);

        // Scissor to clip text inside the panel
        // Global position for scissor (must be in screen coords)
        var gp = getGlobalPosition();
        int scissorX = (int) gp.x + 1;
        int scissorY = (int) gp.y + 1;
        int scissorW = w - 2;
        int scissorH = h - 2;

        context.pushScissor(scissorX, scissorY, scissorW, scissorH);

        int textX = x + PADDING;
        int textY = y + PADDING;
        int maxVisible = maxVisibleLines();

        int from = Math.max(0, scrollOffset);
        int to = Math.min(lines.size(), from + maxVisible);

        for (int i = from; i < to; i++) {
            String line = lines.get(i);
            int color = colorForLine(line);
            context.drawString(line, textX, textY + (i - from) * LINE_HEIGHT, color);
        }

        // Scroll indicator: thin bar on right edge if there are off-screen lines
        if (lines.size() > maxVisible) {
            float fraction = (float) scrollOffset / Math.max(1, lines.size() - maxVisible);
            int barH = Math.max(8, (int) ((h - 2) * ((float) maxVisible / lines.size())));
            int barY = y + 1 + (int) ((h - 2 - barH) * fraction);
            context.enableBlend();
            context.fill(x + w - 4, barY, x + w - 1, barY + barH, 0x88FFFFFF);
            context.disableBlend();
        }

        context.popScissor();
    }

    private int colorForLine(String line) {
        // Discovery: ore names, loot keywords
        if (line.startsWith("+")
                || line.contains("iron") || line.contains("gold") || line.contains("diamond")
                || line.contains("coal") || line.contains("Coal") || line.contains("copper") || line.contains("Copper")
                || line.contains("lapis") || line.contains("Lapis") || line.contains("redstone") || line.contains("Redstone")
                || line.contains("amethyst") || line.contains("Amethyst") || line.contains("flint") || line.contains("Flint")
                || line.contains("bone") || line.contains("arrow") || line.contains("geode") || line.contains("Geode")
                || line.contains("crystal") || line.contains("vein") || line.contains("seam") || line.contains("ore")
                || line.contains("wood") || line.contains("log") || line.contains("oak")
                || line.contains("drops") || line.contains("Gold") || line.contains("Diamond") || line.contains("Iron")) {
            return DISCOVERY_COLOR;
        }
        // Hazard: danger, injury, threat keywords
        if (line.contains("Lava") || line.contains("lava") || line.contains("Magma") || line.contains("magma")
                || line.contains("hit") || line.contains("injur") || line.contains("bit") || line.contains("Singed")
                || line.contains("Took") || line.contains("Three") || line.contains("starv") || line.contains("food")
                || line.contains("Cave-in") || line.contains("cave-in") || line.contains("Ceiling cracked")
                || line.contains("Gravel pour") || line.contains("clicking") || line.contains("Clicking")
                || line.contains("flooded") || line.contains("Flooded") || line.contains("burst")
                || line.contains("Something moved") || line.contains("Groaning") || line.contains("Breath in the dark")
                || line.contains("Backed off") || line.contains("Retreated") || line.contains("pillar gone")) {
            return HAZARD_COLOR;
        }
        return TEXT_COLOR;
    }

    @Override
    protected boolean onEvent(UIEvent event) {
        if (event.getType() == UIEvent.Type.SCROLL) {
            int delta = event.getScrollDelta() < 0 ? 1 : -1;
            int maxVisible = maxVisibleLines();
            int maxScroll = Math.max(0, lines.size() - maxVisible);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + delta));
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    protected JournalPanel self() {
        return this;
    }
}
