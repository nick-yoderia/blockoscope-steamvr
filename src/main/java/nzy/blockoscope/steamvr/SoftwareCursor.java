package nzy.blockoscope.steamvr;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A pointer drawn as part of the GUI, so it appears in both eyes at the mouse position. Drawn on top of
 * everything, at a fixed size in window pixels whatever the GUI scale.
 */
public final class SoftwareCursor {
    /** Window pixels per cursor pixel. */
    private static final float PIXEL = 2f;
    private static final int OUTLINE = 0xFF000000;
    private static final int FILL = 0xFFFFFFFF;

    // X = outline, O = fill, . = transparent. The hot spot is the top-left pixel.
    private static final String[] SHAPE = {
        "X",
        "XX",
        "XOX",
        "XOOX",
        "XOOOX",
        "XOOOOX",
        "XOOOOOX",
        "XOOOOOOX",
        "XOOOOOOOX",
        "XOOOOOOOOX",
        "XOOOOOXXXXX",
        "XOOXOOX",
        "XOX.XOOX",
        "XX..XOOX",
        "X....XOOX",
        ".....XOOX",
        "......XX",
    };

    private SoftwareCursor() {}

    public static void draw(GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        int guiScale = Math.max(Minecraft.getInstance().getWindow().getGuiScale(), 1);
        graphics.nextStratum(); // above screens, tooltips and toasts
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) mouseX, (float) mouseY);
        graphics.pose().scale(PIXEL / guiScale);
        for (int y = 0; y < SHAPE.length; y++) {
            String row = SHAPE[y];
            int x = 0;
            while (x < row.length()) {
                char c = row.charAt(x);
                int end = x + 1;
                while (end < row.length() && row.charAt(end) == c) {
                    end++;
                }
                if (c != '.') {
                    graphics.fill(x, y, end, y + 1, c == 'X' ? OUTLINE : FILL);
                }
                x = end;
            }
        }
        graphics.pose().popMatrix();
    }
}
