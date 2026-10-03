package nzy.blockoscope.steamvr;

import org.lwjgl.glfw.GLFW;

/** F9 switches between 3D and normal 2D (and saves the choice); F8 puts the SteamVR screen in front of you again. */
public final class ToggleKey {
    private ToggleKey() {}

    /** Called for every key event the game window receives. */
    public static void onKey(int key, int action) {
        if (key == GLFW.GLFW_KEY_F9 && action == GLFW.GLFW_PRESS) {
            StereoConfig.setEnabled(!StereoConfig.enabled());
            StereoConfig.save();
        } else if (key == GLFW.GLFW_KEY_F8 && action == GLFW.GLFW_PRESS) {
            VrScreen.requestRecenter();
        }
    }
}
