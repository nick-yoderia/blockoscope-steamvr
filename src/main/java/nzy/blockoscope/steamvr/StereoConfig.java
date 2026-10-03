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

    /** How the SteamVR screen's size and the game's field of view relate. */
    public enum ScreenSize {
        /** Screen width as set; the game's own field of view (the picture is stretched or squeezed to fit). */
        CUSTOM,
        /** The screen is sized to cover the game's field of view: life-size world, the screen may get big. */
        TRUE_SCALE,
        /** The game's field of view is set to what the screen covers: life-size world on a screen of the set width. */
        MATCH_FOV
    }

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
        "enabled: render in stereo 3D (false = normal 2D)",
        "renderScale: in the window (no SteamVR screen), % of the half-window resolution each eye renders at (lower = faster)",
        "depthPercent: 3D strength as a % of average eye spacing (100 = natural, 0 = flat)",
        "focusDistance: metres that sit exactly at the screen surface; 0 = infinity (everything in front of it)",
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
        "screenSize: custom (screenWidth, the game's FOV), true_scale (the screen grows to the game's FOV) or match_fov (the game's FOV shrinks to the screen); both life-size modes focus at the screen and ignore focusDistance",
        "floatingWindow: blank a thin strip at the outer edge of each eye so the screen's edges float in front, at the held item's depth (no item cut off by an edge behind it)",
        "screenWidth: width of the SteamVR screen in metres",
        "screenDistance: metres from your head (where it was at the last recenter, F8) to the SteamVR screen",
        "screenHeight: metres the SteamVR screen sits above (or below) your eyes",
        "screenCurvature: curve of the SteamVR screen in % (0 = flat)",
        "flipScreen: debug; flips the picture on the SteamVR screen upside down",
        "previewBothEyes: while the SteamVR screen is on, show both eyes side by side in the window (false = left eye only)",
        "hideCursor: hide the Windows cursor over the game window and draw one in both eyes instead",
        "confineCursor: keep the cursor inside the game window while it is focused");

    private static boolean enabled = true;
    private static int renderScale = 100;
    private static int depthPercent = 100;
    private static float focusDistance = 10f;
    private static int handDepthPercent = 50;
    private static int handReach = 30;
    private static int handRaise = 0;
    private static int handInward = 0;
    private static HudDepth hudDepth = HudDepth.SCENE;
    private static float hudDistance = 1.35f;
    private static float menuDistance = 0f;
    private static boolean crosshairAtTarget = true;
    private static float crosshairRestOffset = 0f;
    private static boolean swapEyes = false;
    private static boolean steamVrScreen = true;
    private static int eyeResolution = 0;
    private static boolean syncToHeadset = true;
    private static ScreenSize screenSize = ScreenSize.CUSTOM;
    private static boolean floatingWindow = true;
    private static float screenWidth = 2.6f;
    private static float screenDistance = 2.0f;
    private static float screenHeight = 0f;
    private static int screenCurvature = 0;
    private static boolean flipScreen = false;
    private static boolean previewBothEyes = false;
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
    public static ScreenSize screenSize() { return screenSize; }
    /** True in either life-size mode: the screen covers exactly the game's field of view and is the focus distance. */
    public static boolean lifeSize() { return screenSize != ScreenSize.CUSTOM; }
    public static boolean floatingWindow() { return floatingWindow; }
    public static float screenWidth() { return screenWidth; }
    public static float screenDistance() { return screenDistance; }
    public static float screenHeight() { return screenHeight; }
    public static int screenCurvature() { return screenCurvature; }
    public static boolean flipScreen() { return flipScreen; }
    public static boolean previewBothEyes() { return previewBothEyes; }
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
    public static void setFloatingWindow(boolean value) { floatingWindow = value; }
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

    /** Distance between the two eye cameras, in metres. */
    public static float ipd() {
        return AVERAGE_IPD * depthPercent / 100f;
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
        enabled = parseBoolean(props.getProperty("enabled"), true);
        setRenderScale((int) Math.round(parseDouble(props.getProperty("renderScale"), 100)));
        setDepthPercent((int) Math.round(parseDouble(props.getProperty("depthPercent"), 100)));
        setFocusDistance((float) parseDouble(props.getProperty("focusDistance"), 10));
        setHandDepthPercent((int) Math.round(parseDouble(props.getProperty("handDepthPercent"), 50)));
        setHandReach((int) Math.round(parseDouble(props.getProperty("handReach"), 30)));
        setHandRaise((int) Math.round(parseDouble(props.getProperty("handRaise"), 0)));
        setHandInward((int) Math.round(parseDouble(props.getProperty("handInward"), 0)));
        // 0.1.3 had hudFollowsAim (true = aim, false = fixed); its default now becomes scene.
        HudDepth oldDepth = "false".equals(props.getProperty("hudFollowsAim", "").trim()) ? HudDepth.FIXED : HudDepth.SCENE;
        hudDepth = parseEnum(HudDepth.class, props.getProperty("hudDepth"), oldDepth);
        setHudDistance((float) parseDouble(props.getProperty("hudDistance"), 1.35));
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
        floatingWindow = parseBoolean(props.getProperty("floatingWindow"), true);
        setScreenWidth((float) parseDouble(props.getProperty("screenWidth"), 2.6));
        setScreenDistance((float) parseDouble(props.getProperty("screenDistance"), 2.0));
        setScreenHeight((float) parseDouble(props.getProperty("screenHeight"), 0));
        setScreenCurvature((int) Math.round(parseDouble(props.getProperty("screenCurvature"), 0)));
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
        out.setProperty("enabled", String.valueOf(enabled));
        out.setProperty("renderScale", String.valueOf(renderScale));
        out.setProperty("depthPercent", String.valueOf(depthPercent));
        out.setProperty("focusDistance", String.valueOf(focusDistance));
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
        out.setProperty("floatingWindow", String.valueOf(floatingWindow));
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
