package nzy.parallaxtheater;

import org.lwjgl.glfw.GLFW;

/** F9 switches between 3D and normal 2D (and saves the choice). */
public final class ToggleKey {
    private ToggleKey() {}

    /** Called for every key event the game window receives. */
    public static void onKey(int key, int action) {
        if (key == GLFW.GLFW_KEY_F9 && action == GLFW.GLFW_PRESS) {
            StereoConfig.setEnabled(!StereoConfig.enabled());
            StereoConfig.save();
        }
    }
}
