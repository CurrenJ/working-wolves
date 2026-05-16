package grill24.workingwolves.client;

import io.github.currenj.gelatinui.gui.IRenderContext;
import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.UIEvent;
import io.github.currenj.gelatinui.gui.minecraft.MinecraftRenderContext;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * A fixed-size panel that renders expedition journal lines with word-wrap,
 * reduced text scale, and scrolling via scissor clipping.
 */
public class JournalPanel extends UIElement<JournalPanel> {
    private static final int LINE_HEIGHT = 9;
    private static final int PADDING = 4;
    private static final float TEXT_SCALE = 0.8f;
    private static final int BG_COLOR = 0xC0111111;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int DISCOVERY_COLOR = 0xFFAAFFAA;
    private static final int HAZARD_COLOR = 0xFFFFAAAA;

    private record DisplayLine(String text, int color) {}

    private final List<String> lines = new ArrayList<>();
    private final List<DisplayLine> displayLines = new ArrayList<>();
    private boolean displayLinesDirty = true;
    private int lastWrapWidth = -1;
    private int scrollOffset = 0;

    public JournalPanel(int width, int height) {
        this.size.set(width, height);
    }

    public void appendLine(String line) {
        lines.add(line);
        rebuildDisplayLines();
        scrollOffset = Math.max(0, displayLines.size() - maxVisibleLines());
        markDirty();
    }

    public void setLines(List<String> newLines) {
        lines.clear();
        lines.addAll(newLines);
        rebuildDisplayLines();
        scrollOffset = Math.max(0, displayLines.size() - maxVisibleLines());
        markDirty();
    }

    public void scroll(int delta) {
        if (displayLinesDirty) rebuildDisplayLines();
        int maxScroll = Math.max(0, displayLines.size() - maxVisibleLines());
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + delta));
        markDirty();
    }

    private int maxVisibleLines() {
        int innerH = (int) size.y - PADDING * 2;
        return Math.max(1, (int) (innerH / TEXT_SCALE / LINE_HEIGHT));
    }

    private int computeWrapWidth() {
        // Maximum font pixels per line so text fits in the panel at TEXT_SCALE,
        // with PADDING on each side and 4px reserved for the scroll indicator.
        return (int) ((size.x - PADDING * 2 - 4) / TEXT_SCALE);
    }

    private void rebuildDisplayLines() {
        displayLines.clear();
        int wrapWidth = computeWrapWidth();
        var font = Minecraft.getInstance().font;
        for (String line : lines) {
            int color = colorForLine(line);
            if (font.width(line) <= wrapWidth) {
                displayLines.add(new DisplayLine(line, color));
            } else {
                for (String segment : wrapLine(line, wrapWidth)) {
                    displayLines.add(new DisplayLine(segment, color));
                }
            }
        }
        lastWrapWidth = wrapWidth;
        displayLinesDirty = false;
    }

    private List<String> wrapLine(String line, int maxWidth) {
        var result = new ArrayList<String>();
        var font = Minecraft.getInstance().font;
        String[] words = line.split(" ", -1);
        var current = new StringBuilder();
        for (String word : words) {
            if (current.isEmpty()) {
                if (font.width(word) > maxWidth) {
                    result.add(word); // single word too long — add as-is rather than break mid-char
                } else {
                    current.append(word);
                }
            } else {
                String candidate = current + " " + word;
                if (font.width(candidate) > maxWidth) {
                    result.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current.append(' ').append(word);
                }
            }
        }
        if (!current.isEmpty()) result.add(current.toString());
        return result;
    }

    @Override
    protected void onUpdate(float deltaTime) {
        int wrapWidth = computeWrapWidth();
        if (wrapWidth != lastWrapWidth) displayLinesDirty = true;
    }

    @Override
    protected void renderSelf(IRenderContext context) {
        if (displayLinesDirty) rebuildDisplayLines();

        int x = 0;
        int y = 0;
        int w = (int) size.x;
        int h = (int) size.y;

        // Background + border in unscaled local coords
        context.enableBlend();
        context.fill(x, y, x + w, y + h, BG_COLOR);
        context.disableBlend();
        context.fill(x,         y,         x + w, y + 1,     BORDER_COLOR);
        context.fill(x,         y + h - 1, x + w, y + h,     BORDER_COLOR);
        context.fill(x,         y,         x + 1, y + h,     BORDER_COLOR);
        context.fill(x + w - 1, y,         x + w, y + h,     BORDER_COLOR);

        // Scissor in local coords — pose matrix (already translated by UIElement.render)
        // transforms these to screen space inside enableScissor.
        context.pushScissor(x + 1, y + 1, w - 2, h - 2);

        int maxVisible = maxVisibleLines();
        int from = Math.max(0, scrollOffset);
        int to = Math.min(displayLines.size(), from + maxVisible);

        // Push a scale matrix so text renders smaller while fills/scissor stay unscaled
        if (context instanceof MinecraftRenderContext mc) {
            mc.getGraphics().pose().pushMatrix();
            mc.getGraphics().pose().scale(TEXT_SCALE, TEXT_SCALE);
        }

        int textX = x + PADDING;
        int textY = y + PADDING;
        for (int i = from; i < to; i++) {
            DisplayLine dl = displayLines.get(i);
            context.drawString(dl.text(), textX, textY + (i - from) * LINE_HEIGHT, dl.color());
        }

        if (context instanceof MinecraftRenderContext mc) {
            mc.getGraphics().pose().popMatrix();
        }

        // Scroll indicator drawn back in unscaled local coords
        if (displayLines.size() > maxVisible) {
            float fraction = (float) scrollOffset / Math.max(1, displayLines.size() - maxVisible);
            int barH = Math.max(8, (int) ((h - 2) * ((float) maxVisible / displayLines.size())));
            int barY = y + 1 + (int) ((h - 2 - barH) * fraction);
            context.enableBlend();
            context.fill(x + w - 4, barY, x + w - 1, barY + barH, 0x88FFFFFF);
            context.disableBlend();
        }

        context.popScissor();
    }

    private int colorForLine(String line) {
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
            scroll(event.getScrollDelta() < 0 ? 1 : -1);
            return true;
        }
        return false;
    }

    @Override
    protected JournalPanel self() {
        return this;
    }
}
