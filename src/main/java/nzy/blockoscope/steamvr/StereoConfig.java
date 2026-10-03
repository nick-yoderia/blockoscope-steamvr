package nzy.blockoscope.steamvr;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/** Settings stored in config/blockoscope-steamvr.properties. */
public final class StereoConfig {
    /** Where the in-game HUD sits in depth. */
    public enum HudDepth {
        /** On the nearest thing behind the hotbar and status bars (own raycasts). */
        SCENE,
        /** At the crosshair's depth. */
        AIM,
        /** At {@link #hudDistance()}. */
        FIXED
    }

    /** When the game renders in 3D. */
    public enum Mode {
        /** While the SteamVR screen is up (SteamVR running with a headset in use), flat otherwise: right for a shared mod pack. */
        AUTO,
        /** Always; without SteamVR as half side-by-side in the window. */
        ON,
        /** Never: plain Minecraft. */
        OFF
    }

    /** How the SteamVR screen's size and the game's field of view relate. */
    public enum ScreenSize {
        /** Screen width as set; the game's own field of view (the picture is stretched or squeezed to fit). */
        CUSTOM,
        /** The screen is sized to cover the game's field of view: life-size world, the screen may get big. */
        TRUE_SCALE,
        /** The game's field of view is set to what the screen covers: life-size world on a screen of the set width. */
        MATCH_FOV
    }

    /**
     * Defaults, as tuned in the headset (Steam Frame, 21:9 window, FOV 90): a 4.3 m screen at 2.8 m with a slight
     * curve, the world at 4 m on the screen surface, the held item at half depth and at vanilla arm length.
     */
    public static final float DEFAULT_SCREEN_WIDTH = 4.3f;
    public static final float DEFAULT_SCREEN_DISTANCE = 2.8f;
    public static final int DEFAULT_SCREEN_CURVATURE = 10;
    public static final float DEFAULT_FOCUS_DISTANCE = 4f;
    public static final float DEFAULT_NEAR_LIMIT = 1f;
    public static final int DEFAULT_HAND_DEPTH = 50;
    public static final int DEFAULT_HAND_REACH = 0;
    public static final float DEFAULT_HUD_DISTANCE = 1.2f;

    /** Average human eye separation in metres; depth strength is a percentage of this. */
    public static final float AVERAGE_IPD = 0.064f;

    private static final Path FILE = Path.of("config", "blockoscope-steamvr.properties");
    /**
     * Read once if there is no file yet, first match wins: this mod's settings under its earlier name (Parallax
     * Screen), then those of Blockoscope SBS (the Bigscreen version) under its current and earlier name.
     */
    private static final List<Path> OLD_FILES = List.of(
        Path.of("config", "parallax-screen.properties"),
        Path.of("config", "blockoscope-sbs.properties"),
        Path.of("config", "parallax-theater.properties"));
    private static final String HEADER = String.join("\n",
        "Blockoscope SteamVR",
        "mode: auto (3D on the SteamVR screen while SteamVR runs with a headset connected and awake, normal 2D otherwise), on (always 3D; half side-by-side in the window without SteamVR) or off (normal 2D)",
        "renderScale: in the window (no SteamVR screen), % of the half-window resolution each eye renders at (lower = faster)",
        "depthPercent: 3D strength as a % of average eye spacing (100 = natural, 0 = flat)",
        "focusDistance: metres that sit exactly at the screen surface; 0 = infinity (everything in front of it)",
        "nearLimit: on the SteamVR screen, the nearest anything comes out towards you, in metres; blocks right in front of you get gentler 3D instead; 0 = off",
        "handReach: centimetres the hand and held item are pushed forward in 3D, as if the arm were longer",
        "handRaise: centimetres the hand and held item are raised in 3D (more of them in view)",
        "handInward: centimetres the hand and held item are moved towards the middle in 3D",
        "handDepthPercent: depth of your hand and held item as a % of the world's (0 = on the screen surface)",
        "crosshairAtTarget: show the crosshair at the depth of what it aims at within reach (false = with the HUD)",
        "crosshairRestOffset: metres nearer (negative) or farther than your block reach where the crosshair rests when nothing is in reach",
        "hudDepth: depth of the in-game HUD: scene (on whatever is behind the hotbar), aim (at the crosshair's depth) or fixed (hudDistance)",
        "hudDistance: metres at which the in-game HUD floats when hudDepth is fixed; 0 = on the screen surface",
        "menuDistance: metres at which menus and other screens float; 0 = on the screen surface",
        "cameraBobbing: vanilla view bobbing of the camera while walking (the hand still bobs either way)",
        "damageTilt: tilt the camera when hurt or dying",
        "warpPercent: strength of the nausea and portal warp, as a % of vanilla's (on top of Distortion Effects)",
        "swapEyes: put the right eye on the left half (for viewers that expect cross-eyed order)",
        "steamVrScreen: show the game on a screen in SteamVR (false = half side-by-side in the window)",
        "eyeResolution: width in pixels each eye renders at for the SteamVR screen (height follows the window's shape); 0 = automatic, 1.5x as many as the headset shows across the screen, rounded so the HUD lands on whole pixels",
        "syncToHeadset: one game frame per headset refresh while the SteamVR screen is on (smooth motion; runs free when the game can't keep up)",
        "headsetOffTo2D: back to normal 2D on the monitor while the headset is off your head (its proximity sensor), 3D again when you put it on; false = only when it is disconnected or asleep",
        "screenSize: custom (screenWidth, the game's FOV), true_scale (the screen grows to the game's FOV) or match_fov (the game's FOV shrinks to the screen); both life-size modes focus at the screen and ignore focusDistance",
        "screenWidth: width of the SteamVR screen in metres",
        "screenDistance: metres from your head (where it was at the last recenter, F8) to the SteamVR screen",
        "screenHeight: metres the SteamVR screen sits above (or below) your eyes",
        "screenCurvature: curve of the SteamVR screen in % (0 = flat)",
        "flipScreen: debug; flips the picture on the SteamVR screen upside down",
        "previewBothEyes: while the SteamVR screen is on, show both eyes side by side in the window (false = left eye only)",
        "hideCursor: hide the Windows cursor over the game window and draw one in both eyes instead",
        "confineCursor: keep the cursor inside the game window while it is focused");

    private static Mode mode = Mode.AUTO;
    private static int renderScale = 100;
    private static int depthPercent = 100;
    private static float focusDistance = DEFAULT_FOCUS_DISTANCE;
    private static float nearLimit = DEFAULT_NEAR_LIMIT;
    private static int handDepthPercent = DEFAULT_HAND_DEPTH;
    private static int handReach = DEFAULT_HAND_REACH;
    private static int handRaise = 0;
    private static int handInward = 0;
    private static HudDepth hudDepth = HudDepth.SCENE;
    private static float hudDistance = DEFAULT_HUD_DISTANCE;
    private static float menuDistance = 0f;
    private static boolean crosshairAtTarget = true;
    private static float crosshairRestOffset = 0f;
    private static boolean swapEyes = false;
    private static boolean steamVrScreen = true;
    private static int eyeResolution = 0;
    private static boolean syncToHeadset = true;
    private static ScreenSize screenSize = ScreenSize.CUSTOM;
    private static boolean headsetOffTo2D = true;
    private static float screenWidth = DEFAULT_SCREEN_WIDTH;
    private static float screenDistance = DEFAULT_SCREEN_DISTANCE;
    private static float screenHeight = 0f;
    private static int screenCurvature = DEFAULT_SCREEN_CURVATURE;
    private static boolean flipScreen = false;
    private static boolean previewBothEyes = false;
    private static boolean cameraBobbing = false;
    private static boolean damageTilt = false;
    private static int warpPercent = 40;
    private static boolean hideCursor = true;
    private static boolean confineCursor = true;

    /**
     * Screen settings shown live while the settings screen is open (null = the saved value): dragging a slider moves
     * or reshapes the SteamVR screen at once, so you can see the result in the headset without saving and reopening
     * the menu. Save keeps the values, Cancel drops them (see {@link StereoConfigScreen#updatePreview}).
     */
    private static ScreenSize previewScreenSize;
    private static Float previewScreenWidth;
    private static Float previewScreenDistance;
    private static Float previewScreenHeight;
    private static Integer previewScreenCurvature;

    static {
        load();
    }

    private StereoConfig() {}

    public static Mode mode() { return mode; }

    /**
     * True when the game renders in 3D now. Auto follows the SteamVR screen, so friends without VR playing the same
     * mod pack see plain Minecraft without touching a setting, and putting on the headset (starting SteamVR) is all
     * it takes to get 3D.
     */
    public static boolean enabled() {
        return mode == Mode.ON || mode == Mode.AUTO && VrScreen.active();
    }
    public static int renderScale() { return renderScale; }
    public static int depthPercent() { return depthPercent; }
    public static float focusDistance() { return focusDistance; }
    public static float nearLimit() { return nearLimit; }
    public static int handDepthPercent() { return handDepthPercent; }
    public static int handReach() { return handReach; }
    public static int handRaise() { return handRaise; }
    public static int handInward() { return handInward; }
    public static HudDepth hudDepth() { return hudDepth; }
    public static float hudDistance() { return hudDistance; }
    public static float menuDistance() { return menuDistance; }
    public static boolean crosshairAtTarget() { return crosshairAtTarget; }
    public static float crosshairRestOffset() { return crosshairRestOffset; }
    public static boolean swapEyes() { return swapEyes; }
    public static boolean steamVrScreen() { return steamVrScreen; }
    public static int eyeResolution() { return eyeResolution; }
    public static ScreenSize screenSize() { return previewScreenSize != null ? previewScreenSize : screenSize; }
    /** True in either life-size mode: the screen covers exactly the game's field of view and is the focus distance. */
    public static boolean lifeSize() { return screenSize() != ScreenSize.CUSTOM; }
    public static boolean headsetOffTo2D() { return headsetOffTo2D; }
    public static float screenWidth() { return previewScreenWidth != null ? previewScreenWidth : screenWidth; }
    public static float screenDistance() { return previewScreenDistance != null ? previewScreenDistance : screenDistance; }
    public static float screenHeight() { return previewScreenHeight != null ? previewScreenHeight : screenHeight; }
    public static int screenCurvature() { return previewScreenCurvature != null ? previewScreenCurvature : screenCurvature; }
    public static boolean flipScreen() { return flipScreen; }
    public static boolean previewBothEyes() { return previewBothEyes; }
    public static boolean cameraBobbing() { return cameraBobbing; }
    public static boolean damageTilt() { return damageTilt; }
    public static int warpPercent() { return warpPercent; }
    public static boolean hideCursor() { return hideCursor; }
    public static boolean confineCursor() { return confineCursor; }

    public static void setMode(Mode value) { mode = value == null ? Mode.AUTO : value; }
    public static void setRenderScale(int value) { renderScale = Math.max(25, Math.min(200, value)); }
    public static void setDepthPercent(int value) { depthPercent = Math.max(0, Math.min(300, value)); }
    public static void setFocusDistance(float value) { focusDistance = Math.max(0f, value); }
    public static void setNearLimit(float value) { nearLimit = Math.max(0f, Math.min(3f, value)); }
    public static void setHandDepthPercent(int value) { handDepthPercent = Math.max(0, Math.min(200, value)); }
    public static void setHandReach(int value) { handReach = Math.max(0, Math.min(60, value)); }
    public static void setHandRaise(int value) { handRaise = Math.max(0, Math.min(40, value)); }
    public static void setHandInward(int value) { handInward = Math.max(0, Math.min(40, value)); }
    public static void setHudDepth(HudDepth value) { hudDepth = value == null ? HudDepth.SCENE : value; }
    public static void setHudDistance(float value) { hudDistance = Math.max(0f, value); }
    public static void setMenuDistance(float value) { menuDistance = Math.max(0f, value); }
    public static void setCrosshairAtTarget(boolean value) { crosshairAtTarget = value; }
    public static void setCrosshairRestOffset(float value) { crosshairRestOffset = Math.max(-4f, Math.min(16f, value)); }
    public static void setSwapEyes(boolean value) { swapEyes = value; }
    public static void setSteamVrScreen(boolean value) { steamVrScreen = value; }
    public static boolean syncToHeadset() { return syncToHeadset; }
    public static void setSyncToHeadset(boolean value) { syncToHeadset = value; }
    public static void setEyeResolution(int value) { eyeResolution = value <= 0 ? 0 : Math.max(640, Math.min(4096, value)); }
    public static void setScreenSize(ScreenSize value) { screenSize = value == null ? ScreenSize.CUSTOM : value; }
    public static void setHeadsetOffTo2D(boolean value) { headsetOffTo2D = value; }
    public static void setScreenWidth(float value) { screenWidth = Math.max(0.5f, Math.min(20f, value)); }
    public static void setScreenDistance(float value) { screenDistance = Math.max(0.5f, Math.min(20f, value)); }
    public static void setScreenHeight(float value) { screenHeight = Math.max(-3f, Math.min(3f, value)); }
    public static void setScreenCurvature(int value) { screenCurvature = Math.max(0, Math.min(100, value)); }
    public static void setFlipScreen(boolean value) { flipScreen = value; }
    public static void setPreviewBothEyes(boolean value) { previewBothEyes = value; }
    public static void setCameraBobbing(boolean value) { cameraBobbing = value; }
    public static void setDamageTilt(boolean value) { damageTilt = value; }
    public static void setWarpPercent(int value) { warpPercent = Math.max(0, Math.min(100, value)); }
    public static void setHideCursor(boolean value) { hideCursor = value; }
    public static void setConfineCursor(boolean value) { confineCursor = value; }

    /** Shows these screen settings live instead of the saved ones (clamped like the saved ones). */
    public static void preview(ScreenSize size, float width, float distance, float height, int curvature) {
        previewScreenSize = size == null ? ScreenSize.CUSTOM : size;
        previewScreenWidth = Math.max(0.5f, Math.min(20f, width));
        previewScreenDistance = Math.max(0.5f, Math.min(20f, distance));
        previewScreenHeight = Math.max(-3f, Math.min(3f, height));
        previewScreenCurvature = Math.max(0, Math.min(100, curvature));
    }

    /** Back to the saved screen settings. */
    public static void endPreview() {
        previewScreenSize = null;
        previewScreenWidth = null;
        previewScreenDistance = null;
        previewScreenHeight = null;
        previewScreenCurvature = null;
    }

    public static void load() {
        Properties props = new Properties();
        Path source = Files.isRegularFile(FILE) ? FILE
            : OLD_FILES.stream().filter(Files::isRegularFile).findFirst().orElse(FILE);
        if (Files.isRegularFile(source)) {
            try (Reader reader = Files.newBufferedReader(source)) {
                props.load(reader);
            } catch (IOException e) {
                System.out.println("[Blockoscope SteamVR] Could not read " + source + ", using defaults: " + e);
            }
        }
        // 0.2.1 and earlier had enabled (true/false, default true); true becomes auto.
        Mode oldMode = "false".equals(props.getProperty("enabled", "").trim()) ? Mode.OFF : Mode.AUTO;
        mode = parseEnum(Mode.class, props.getProperty("mode"), oldMode);
        setRenderScale((int) Math.round(parseDouble(props.getProperty("renderScale"), 100)));
        setDepthPercent((int) Math.round(parseDouble(props.getProperty("depthPercent"), 100)));
        setFocusDistance((float) parseDouble(props.getProperty("focusDistance"), DEFAULT_FOCUS_DISTANCE));
        setNearLimit((float) parseDouble(props.getProperty("nearLimit"), DEFAULT_NEAR_LIMIT));
        setHandDepthPercent((int) Math.round(parseDouble(props.getProperty("handDepthPercent"), DEFAULT_HAND_DEPTH)));
        setHandReach((int) Math.round(parseDouble(props.getProperty("handReach"), DEFAULT_HAND_REACH)));
        setHandRaise((int) Math.round(parseDouble(props.getProperty("handRaise"), 0)));
        setHandInward((int) Math.round(parseDouble(props.getProperty("handInward"), 0)));
        // 0.1.3 had hudFollowsAim (true = aim, false = fixed); its default now becomes scene.
        HudDepth oldDepth = "false".equals(props.getProperty("hudFollowsAim", "").trim()) ? HudDepth.FIXED : HudDepth.SCENE;
        hudDepth = parseEnum(HudDepth.class, props.getProperty("hudDepth"), oldDepth);
        setHudDistance((float) parseDouble(props.getProperty("hudDistance"), DEFAULT_HUD_DISTANCE));
        setMenuDistance((float) parseDouble(props.getProperty("menuDistance"), 0));
        crosshairAtTarget = parseBoolean(props.getProperty("crosshairAtTarget"), true);
        setCrosshairRestOffset((float) parseDouble(props.getProperty("crosshairRestOffset"), 0));
        swapEyes = parseBoolean(props.getProperty("swapEyes"), false);
        steamVrScreen = parseBoolean(props.getProperty("steamVrScreen"), true);
        setEyeResolution((int) Math.round(parseDouble(props.getProperty("eyeResolution"), 0)));
        // 0.2.0 had trueScale (true = the screen follows the field of view).
        ScreenSize oldSize = parseBoolean(props.getProperty("trueScale"), false) ? ScreenSize.TRUE_SCALE : ScreenSize.CUSTOM;
        screenSize = parseEnum(ScreenSize.class, props.getProperty("screenSize"), oldSize);
        syncToHeadset = parseBoolean(props.getProperty("syncToHeadset"), true);
        headsetOffTo2D = parseBoolean(props.getProperty("headsetOffTo2D"), true);
        setScreenWidth((float) parseDouble(props.getProperty("screenWidth"), DEFAULT_SCREEN_WIDTH));
        setScreenDistance((float) parseDouble(props.getProperty("screenDistance"), DEFAULT_SCREEN_DISTANCE));
        setScreenHeight((float) parseDouble(props.getProperty("screenHeight"), 0));
        setScreenCurvature((int) Math.round(parseDouble(props.getProperty("screenCurvature"), DEFAULT_SCREEN_CURVATURE)));
        flipScreen = parseBoolean(props.getProperty("flipScreen"), false);
        previewBothEyes = parseBoolean(props.getProperty("previewBothEyes"), false);
        cameraBobbing = parseBoolean(props.getProperty("cameraBobbing"), false);
        damageTilt = parseBoolean(props.getProperty("damageTilt"), false);
        setWarpPercent((int) Math.round(parseDouble(props.getProperty("warpPercent"), 40)));
        hideCursor = parseBoolean(props.getProperty("hideCursor"), true);
        confineCursor = parseBoolean(props.getProperty("confineCursor"), true);
        save(); // always list every setting
    }

    public static void save() {
        Properties out = new Properties();
        out.setProperty("mode", mode.name().toLowerCase(java.util.Locale.ROOT));
        out.setProperty("renderScale", String.valueOf(renderScale));
        out.setProperty("depthPercent", String.valueOf(depthPercent));
        out.setProperty("focusDistance", String.valueOf(focusDistance));
        out.setProperty("nearLimit", String.valueOf(nearLimit));
        out.setProperty("handDepthPercent", String.valueOf(handDepthPercent));
        out.setProperty("handReach", String.valueOf(handReach));
        out.setProperty("handRaise", String.valueOf(handRaise));
        out.setProperty("handInward", String.valueOf(handInward));
        out.setProperty("hudDepth", hudDepth.name().toLowerCase(java.util.Locale.ROOT));
        out.setProperty("hudDistance", String.valueOf(hudDistance));
        out.setProperty("menuDistance", String.valueOf(menuDistance));
        out.setProperty("crosshairAtTarget", String.valueOf(crosshairAtTarget));
        out.setProperty("crosshairRestOffset", String.valueOf(crosshairRestOffset));
        out.setProperty("swapEyes", String.valueOf(swapEyes));
        out.setProperty("steamVrScreen", String.valueOf(steamVrScreen));
        out.setProperty("eyeResolution", String.valueOf(eyeResolution));
        out.setProperty("screenSize", screenSize.name().toLowerCase(java.util.Locale.ROOT));
        out.setProperty("syncToHeadset", String.valueOf(syncToHeadset));
        out.setProperty("headsetOffTo2D", String.valueOf(headsetOffTo2D));
        out.setProperty("screenWidth", String.valueOf(screenWidth));
        out.setProperty("screenDistance", String.valueOf(screenDistance));
        out.setProperty("screenHeight", String.valueOf(screenHeight));
        out.setProperty("screenCurvature", String.valueOf(screenCurvature));
        out.setProperty("flipScreen", String.valueOf(flipScreen));
        out.setProperty("previewBothEyes", String.valueOf(previewBothEyes));
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
            System.out.println("[Blockoscope SteamVR] Could not write " + FILE + ": " + e);
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        try {
            return value == null ? fallback : Enum.valueOf(type, value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
