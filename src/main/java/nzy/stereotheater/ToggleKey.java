package nzy.stereotheater;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** F9 switches between 3D and normal 2D (and saves the choice). Polled once per frame. */
public final class ToggleKey {
    private static boolean wasDown;

    private ToggleKey() {}

    public static void poll() {
        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().handle();
        boolean down = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F9) == GLFW.GLFW_PRESS;
        if (down && !wasDown && GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE) {
            StereoConfig.setEnabled(!StereoConfig.enabled());
            StereoConfig.save();
        }
        wasDown = down;
    }
}
