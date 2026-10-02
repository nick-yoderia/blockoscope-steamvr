package nzy.parallaxtheater;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Settings stored in config/parallax-theater.properties. */
public final class StereoConfig {
    /** Average human eye separation in metres; depth strength is a percentage of this. */
    public static final float AVERAGE_IPD = 0.064f;

    private static final Path FILE = Path.of("config", "parallax-theater.properties");
    /** Settings file of the development builds (named Stereo Theater), read once if there is no file yet. */
    private static final Path OLD_FILE = Path.of("config", "stereo-theater.properties");
    private static final String HEADER = String.join("\n",
        "Parallax Theater",
        "enabled: render half side-by-side 3D (false = normal 2D)",
        "renderScale: % of the half-window resolution each eye renders at (lower = faster, above 100 = sharper)",
        "depthPercent: 3D strength as a % of average eye spacing (100 = natural, 0 = flat)",
        "focusDistance: metres that sit exactly at the screen surface; 0 = infinity (everything in front of it)",
        "handDepthPercent: depth of your hand and held item as a % of the world's (0 = on the screen surface)",
        "crosshairAtTarget: show the crosshair at the depth of what it points at (false = at the HUD distance)",
        "hudDistance: metres at which the HUD and menus float; 0 = on the screen surface",
        "cameraBobbing: vanilla view bobbing of the camera while walking (the hand still bobs either way)",
        "damageTilt: tilt the camera when hurt or dying",
        "warpPercent: strength of the nausea and portal warp, as a % of vanilla's (on top of Distortion Effects)",
        "swapEyes: put the right eye on the left half (for viewers that expect cross-eyed order)",
        "hideCursor: hide the Windows cursor over the game window and draw one in both eyes instead",
        "confineCursor: keep the cursor inside the game window while it is focused");

    private static boolean enabled = true;
    private static int renderScale = 100;
    private static int depthPercent = 100;
    private static float focusDistance = 10f;
    private static int handDepthPercent = 100;
    private static float hudDistance = 1.35f;
    private static boolean crosshairAtTarget = true;
    private static boolean swapEyes = false;
    private static boolean cameraBobbing = false;
    private static boolean damageTilt = false;
    private static int warpPercent = 40;
    private static boolean hideCursor = true;
    private static boolean confineCursor = true;

    static {
        load();
    }

    private StereoConfig() {}

    public static boolean enabled() { return enabled; }
    public static int renderScale() { return renderScale; }
    public static int depthPercent() { return depthPercent; }
    public static float focusDistance() { return focusDistance; }
    public static int handDepthPercent() { return handDepthPercent; }
    public static float hudDistance() { return hudDistance; }
    public static boolean crosshairAtTarget() { return crosshairAtTarget; }
    public static boolean swapEyes() { return swapEyes; }
    public static boolean cameraBobbing() { return cameraBobbing; }
    public static boolean damageTilt() { return damageTilt; }
    public static int warpPercent() { return warpPercent; }
    public static boolean hideCursor() { return hideCursor; }
    public static boolean confineCursor() { return confineCursor; }

    public static void setEnabled(boolean value) { enabled = value; }
    public static void setRenderScale(int value) { renderScale = Math.max(25, Math.min(200, value)); }
    public static void setDepthPercent(int value) { depthPercent = Math.max(0, Math.min(300, value)); }
    public static void setFocusDistance(float value) { focusDistance = Math.max(0f, value); }
    public static void setHandDepthPercent(int value) { handDepthPercent = Math.max(0, Math.min(200, value)); }
    public static void setHudDistance(float value) { hudDistance = Math.max(0f, value); }
    public static void setCrosshairAtTarget(boolean value) { crosshairAtTarget = value; }
    public static void setSwapEyes(boolean value) { swapEyes = value; }
    public static void setCameraBobbing(boolean value) { cameraBobbing = value; }
    public static void setDamageTilt(boolean value) { damageTilt = value; }
    public static void setWarpPercent(int value) { warpPercent = Math.max(0, Math.min(100, value)); }
    public static void setHideCursor(boolean value) { hideCursor = value; }
    public static void setConfineCursor(boolean value) { confineCursor = value; }

    /** Distance between the two eye cameras, in metres. */
    public static float ipd() {
        return AVERAGE_IPD * depthPercent / 100f;
    }

    public static void load() {
        Properties props = new Properties();
        Path source = !Files.isRegularFile(FILE) && Files.isRegularFile(OLD_FILE) ? OLD_FILE : FILE;
        if (Files.isRegularFile(source)) {
            try (Reader reader = Files.newBufferedReader(source)) {
                props.load(reader);
            } catch (IOException e) {
                System.out.println("[Parallax Theater] Could not read " + source + ", using defaults: " + e);
            }
        }
        enabled = parseBoolean(props.getProperty("enabled"), true);
        setRenderScale((int) Math.round(parseDouble(props.getProperty("renderScale"), 100)));
        setDepthPercent((int) Math.round(parseDouble(props.getProperty("depthPercent"), 100)));
        setFocusDistance((float) parseDouble(props.getProperty("focusDistance"), 10));
        setHandDepthPercent((int) Math.round(parseDouble(props.getProperty("handDepthPercent"), 100)));
        setHudDistance((float) parseDouble(props.getProperty("hudDistance"), 1.35));
        crosshairAtTarget = parseBoolean(props.getProperty("crosshairAtTarget"), true);
        swapEyes = parseBoolean(props.getProperty("swapEyes"), false);
        cameraBobbing = parseBoolean(props.getProperty("cameraBobbing"), false);
        damageTilt = parseBoolean(props.getProperty("damageTilt"), false);
        setWarpPercent((int) Math.round(parseDouble(props.getProperty("warpPercent"), 40)));
        hideCursor = parseBoolean(props.getProperty("hideCursor"), true);
        confineCursor = parseBoolean(props.getProperty("confineCursor"), true);
        save(); // always list every setting
    }

    public static void save() {
        Properties out = new Properties();
        out.setProperty("enabled", String.valueOf(enabled));
        out.setProperty("renderScale", String.valueOf(renderScale));
        out.setProperty("depthPercent", String.valueOf(depthPercent));
        out.setProperty("focusDistance", String.valueOf(focusDistance));
        out.setProperty("handDepthPercent", String.valueOf(handDepthPercent));
        out.setProperty("hudDistance", String.valueOf(hudDistance));
        out.setProperty("crosshairAtTarget", String.valueOf(crosshairAtTarget));
        out.setProperty("swapEyes", String.valueOf(swapEyes));
        out.setProperty("cameraBobbing", String.valueOf(cameraBobbing));
        out.setProperty("damageTilt", String.valueOf(damageTilt));
        out.setProperty("warpPercent", String.valueOf(warpPercent));
        out.setProperty("hideCursor", String.valueOf(hideCursor));
        out.setProperty("confineCursor", String.valueOf(confineCursor));
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                out.store(writer, HEADER);
            }
        } catch (IOException e) {
            System.out.println("[Parallax Theater] Could not write " + FILE + ": " + e);
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
