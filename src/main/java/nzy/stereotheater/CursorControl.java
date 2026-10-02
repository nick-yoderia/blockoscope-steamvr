package nzy.stereotheater;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;
import org.lwjgl.system.windows.RECT;
import org.lwjgl.system.windows.User32;

/**
 * In side-by-side 3D the Windows cursor would show in one eye only, so while a menu releases the mouse it is
 * hidden (the game still reports its position) and {@link SoftwareCursor} draws one into both eyes. It is also
 * kept inside the window while the game is focused, so a click can never land on another window.
 */
public final class CursorControl {
    private static boolean clipped;
    private static boolean hidden;

    private CursorControl() {}

    /** Called once per frame. */
    public static void update() {
        long window = Minecraft.getInstance().getWindow().handle();
        if (window == 0L) {
            return;
        }
        boolean active = StereoConfig.enabled();
        int mode = GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR);

        if (active && StereoConfig.hideCursor()) {
            if (mode == GLFW.GLFW_CURSOR_NORMAL) {
                GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
                hidden = true;
            }
        } else if (hidden) {
            if (mode == GLFW.GLFW_CURSOR_HIDDEN) {
                GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            }
            hidden = false;
        }

        if (Platform.get() != Platform.WINDOWS) {
            return;
        }
        boolean focused = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
        if (active && StereoConfig.confineCursor() && focused && mode != GLFW.GLFW_CURSOR_DISABLED) {
            clipToWindow(window); // in game (DISABLED) GLFW already confines the cursor itself
        } else if (clipped) {
            User32.nClipCursor(0L);
            clipped = false;
        }
    }

    /** True when the game draws the cursor itself (menus open, cursor hidden). */
    public static boolean drawsCursor() {
        return hidden && StereoConfig.enabled() && !Minecraft.getInstance().mouseHandler.isMouseGrabbed();
    }

    private static void clipToWindow(long window) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
            GLFW.glfwGetWindowPos(window, x, y); // top-left of the client area, in screen pixels
            GLFW.glfwGetWindowSize(window, w, h);
            if (w[0] <= 0 || h[0] <= 0) {
                return;
            }
            RECT rect = RECT.malloc(stack);
            rect.left(x[0]).top(y[0]).right(x[0] + w[0]).bottom(y[0] + h[0]);
            User32.ClipCursor(rect);
            clipped = true;
        }
    }
}
