package nzy.parallaxscreen;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * The virtual screen in SteamVR: an OpenVR overlay that shows the two eyes side by side, one per eye.
 *
 * Minecraft runs as an OpenVR overlay application, not a VR game: SteamVR keeps drawing its own scene (SteamVR Home or
 * the void) and composites the screen into it at the headset's frame rate, so head movement stays smooth whatever the
 * game's frame rate is. The packed texture (left eye in the left half) is handed over by its OpenGL id every frame;
 * SteamVR copies it on the GPU, so nothing is read back to the CPU.
 */
public final class VrScreen {
    private static final String OVERLAY_KEY = "nzy.parallaxscreen.screen";
    /** Time between attempts to reach SteamVR while it isn't available. */
    private static final long RETRY_NANOS = 5_000_000_000L;

    private static boolean started;
    private static long overlay;
    private static long lastAttemptNanos;
    /** Set by the connecting thread; picked up on the render thread. */
    private static volatile boolean connecting;
    private static volatile boolean connected;
    private static boolean placed;
    /** Tracking space the screen was placed in (seated, or standing when there is no seated origin). */
    private static int universe = OpenVrApi.UNIVERSE_SEATED;
    private static boolean recenterRequested;
    private static volatile String lastError = "";
    private static boolean warnedNotOpenGl;
    private static boolean shutdownHookAdded;
    private static float appliedWidth = Float.NaN;
    private static float appliedCurvature = Float.NaN;
    private static boolean appliedFlip;
    private static boolean boundsSet;

    /** Native structs reused every frame (Texture_t, VRTextureBounds_t, VREvent_t, poses, HmdMatrix34_t). */
    private static final Arena ARENA = Arena.ofAuto();
    private static final MemorySegment TEXTURE = ARENA.allocate(16, 8);
    private static final MemorySegment BOUNDS = ARENA.allocate(16, 4);
    private static final MemorySegment EVENT = ARENA.allocate(OpenVrApi.EVENT_SIZE, 8);
    private static final MemorySegment POSES = ARENA.allocate((long) OpenVrApi.POSE_SIZE * OpenVrApi.MAX_DEVICES, 8);
    private static final MemorySegment TRANSFORM = ARENA.allocate(48, 4);

    private VrScreen() {}

    /** True while the screen is up in SteamVR (the eyes are then rendered for it rather than for the window). */
    public static boolean active() {
        return started;
    }

    /** Asks for the screen to be placed in front of the headset again (key or setting change). */
    public static void requestRecenter() {
        recenterRequested = true;
    }

    /** Called once per frame on the render thread: connects, disconnects and follows SteamVR's events. */
    public static void update() {
        if (!StereoConfig.enabled() || !StereoConfig.steamVrScreen()) {
            if (started) {
                stop("turned off");
            }
            return;
        }
        if (connected) {
            connected = false;
            finishStart();
        }
        if (!started) {
            long now = System.nanoTime();
            if (connecting || lastAttemptNanos != 0L && now - lastAttemptNanos < RETRY_NANOS) {
                return;
            }
            lastAttemptNanos = now;
            connecting = true;
            Thread thread = new Thread(VrScreen::connect, "Parallax Screen SteamVR connect");
            thread.setDaemon(true);
            thread.start();
            return;
        }
        try {
            pollEvents();
            if (!started) {
                return;
            }
            applyShape();
            if (!placed || recenterRequested) {
                placed = place();
                recenterRequested = false;
            }
        } catch (Throwable t) {
            logOnce("SteamVR screen error: " + t);
            stop("error");
        }
    }

    /**
     * Connects to SteamVR and creates the overlay, off the render thread (connecting can take a moment). Only connects
     * while SteamVR is already running: connecting would otherwise launch it, so playing on the monitor would start
     * SteamVR, and quitting SteamVR mid-game would bring it straight back. Starting SteamVR (as you do to use the
     * headset) is enough; the screen appears within a few seconds.
     */
    private static void connect() {
        try {
            OpenVrApi.load();
            if (!OpenVrApi.isRuntimeInstalled()) {
                logOnce("SteamVR is not installed; showing side-by-side in the window");
                return;
            }
            if (!steamVrRunning()) {
                logOnce("SteamVR is not running; showing side-by-side in the window until it starts");
                return;
            }
            String error = OpenVrApi.init();
            if (error != null) {
                logOnce("SteamVR is not available (" + error + "); showing side-by-side in the window");
                return;
            }
            try {
                overlay = OpenVrApi.createOverlay(OVERLAY_KEY, "Parallax Screen");
            } catch (IllegalStateException e) {
                logOnce("Could not create the SteamVR screen: " + e.getMessage());
                OpenVrApi.shutdown();
                return;
            }
            connected = true;
        } catch (Throwable t) {
            logOnce("SteamVR could not be started: " + t);
        } finally {
            connecting = false;
        }
    }

    /** True while SteamVR's server process runs (looked up without loading or starting anything of SteamVR's). */
    private static boolean steamVrRunning() {
        return ProcessHandle.allProcesses().anyMatch(process -> process.info().command()
            .map(command -> command.toLowerCase(java.util.Locale.ROOT).endsWith("vrserver.exe")).orElse(false));
    }

    /** Back on the render thread once connected: sets the screen up and shows it. */
    private static void finishStart() {
        started = true;
        try {
            OpenVrApi.setOverlayFlag(overlay, OpenVrApi.OVERLAY_FLAG_SIDE_BY_SIDE_PARALLEL, true);
            // The GUI leaves alpha below 1 in places; the screen is opaque.
            OpenVrApi.setOverlayFlag(overlay, OpenVrApi.OVERLAY_FLAG_IGNORE_TEXTURE_ALPHA, true);
            appliedWidth = Float.NaN;
            appliedCurvature = Float.NaN;
            boundsSet = false;
            applyShape();
            if (!shutdownHookAdded) {
                shutdownHookAdded = true;
                // Leave SteamVR cleanly when the game closes, so the screen doesn't linger in the headset.
                Runtime.getRuntime().addShutdownHook(new Thread(() -> stop("game closed"), "Parallax Screen SteamVR shutdown"));
            }
            placed = false;
            lastError = "";
            OpenVrApi.showOverlay(overlay);
            System.out.println("[Parallax Screen] SteamVR screen started");
        } catch (Throwable t) {
            logOnce("SteamVR screen could not be shown: " + t);
            stop("error");
        }
    }

    /** Screen size and curve from the settings; only sent to SteamVR when they change. */
    private static void applyShape() throws Throwable {
        float width = StereoConfig.trueScale() ? StereoRenderer.trueScaleScreenWidth() : StereoConfig.screenWidth();
        float curvature = StereoConfig.screenCurvature() / 100f;
        if (width != appliedWidth) {
            appliedWidth = width;
            OpenVrApi.setOverlayWidth(overlay, width);
        }
        if (curvature != appliedCurvature) {
            appliedCurvature = curvature;
            OpenVrApi.setOverlayCurvature(overlay, curvature);
        }
    }

    /**
     * Puts the screen straight ahead of the headset at the screen distance, level and facing it (only the headset's
     * heading counts, so looking down while recentering doesn't tilt the screen).
     */
    private static boolean place() throws Throwable {
        // Seated space if SteamVR has a seated origin (then "reset seated position" moves the screen along), standing
        // space otherwise.
        long pose = (long) OpenVrApi.HMD_INDEX * OpenVrApi.POSE_SIZE;
        universe = OpenVrApi.UNIVERSE_SEATED;
        OpenVrApi.getPoses(universe, POSES, OpenVrApi.MAX_DEVICES);
        if (POSES.get(JAVA_BYTE, pose + OpenVrApi.POSE_VALID_OFFSET) == 0) {
            universe = OpenVrApi.UNIVERSE_STANDING;
            OpenVrApi.getPoses(universe, POSES, OpenVrApi.MAX_DEVICES);
        }
        if (POSES.get(JAVA_BYTE, pose + OpenVrApi.POSE_VALID_OFFSET) == 0) {
            return false;
        }
        if (StereoDebug.ENABLED) {
            StereoDebug.log("headset at " + POSES.get(JAVA_FLOAT, pose + 3 * 4) + ", " + POSES.get(JAVA_FLOAT, pose + 7 * 4)
                + ", " + POSES.get(JAVA_FLOAT, pose + 11 * 4) + (universe == OpenVrApi.UNIVERSE_SEATED ? " (seated)" : " (standing)"));
        }
        // HmdMatrix34_t rows are (x, y, z, translation); the headset looks along its -Z axis.
        float forwardX = -POSES.get(JAVA_FLOAT, pose + 2 * 4);
        float forwardZ = -POSES.get(JAVA_FLOAT, pose + 10 * 4);
        float length = (float) Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
        if (length < 1e-4f) {
            forwardX = 0f;
            forwardZ = -1f;
        } else {
            forwardX /= length;
            forwardZ /= length;
        }
        float distance = StereoConfig.screenDistance();
        float x = POSES.get(JAVA_FLOAT, pose + 3 * 4) + forwardX * distance;
        float y = POSES.get(JAVA_FLOAT, pose + 7 * 4) + StereoConfig.screenHeight();
        float z = POSES.get(JAVA_FLOAT, pose + 11 * 4) + forwardZ * distance;
        // The overlay faces +Z; turn it about the vertical to face back along the heading.
        float angle = (float) Math.atan2(-forwardX, -forwardZ);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float[] matrix = {
            cos, 0f, sin, x,
            0f, 1f, 0f, y,
            -sin, 0f, cos, z};
        for (int i = 0; i < matrix.length; i++) {
            TRANSFORM.setAtIndex(JAVA_FLOAT, i, matrix[i]);
        }
        int result = OpenVrApi.setOverlayTransformAbsolute(overlay, universe, TRANSFORM);
        if (result != 0) {
            logOnce("Could not place the SteamVR screen: " + OpenVrApi.overlayErrorText(result));
        }
        return result == 0;
    }

    /** Hands the packed eyes (left eye in the left half) to SteamVR. */
    public static void submit(RenderTarget packed) {
        if (!started) {
            return;
        }
        GpuTexture color = packed.getColorTexture();
        if (!(color instanceof GlTexture gl)) {
            if (!warnedNotOpenGl) {
                warnedNotOpenGl = true;
                System.out.println("[Parallax Screen] The SteamVR screen needs the OpenGL renderer");
            }
            return;
        }
        try {
            boolean flip = StereoConfig.flipScreen();
            if (!boundsSet || flip != appliedFlip) {
                // SteamVR accounts for OpenGL's bottom-up rows itself (Vivecraft submits Minecraft's GL eye textures
                // with plain 0..1 bounds); the setting flips it in case a SteamVR version doesn't.
                boundsSet = true;
                appliedFlip = flip;
                BOUNDS.setAtIndex(JAVA_FLOAT, 0, 0f);
                BOUNDS.setAtIndex(JAVA_FLOAT, 1, flip ? 1f : 0f);
                BOUNDS.setAtIndex(JAVA_FLOAT, 2, 1f);
                BOUNDS.setAtIndex(JAVA_FLOAT, 3, flip ? 0f : 1f);
                OpenVrApi.setOverlayTextureBounds(overlay, BOUNDS);
            }
            TEXTURE.set(JAVA_LONG, 0, gl.glId());
            TEXTURE.set(JAVA_INT, 8, OpenVrApi.TEXTURE_OPENGL);
            TEXTURE.set(JAVA_INT, 12, OpenVrApi.COLOR_SPACE_AUTO);
            int result = OpenVrApi.setOverlayTexture(overlay, TEXTURE);
            if (result != 0) {
                logOnce("SteamVR did not take the frame: " + OpenVrApi.overlayErrorText(result));
            }
        } catch (Throwable t) {
            logOnce("SteamVR screen error: " + t);
            stop("error");
        }
    }

    private static void pollEvents() throws Throwable {
        while (started && OpenVrApi.pollEvent(EVENT)) {
            if (EVENT.get(JAVA_INT, 0) == OpenVrApi.EVENT_QUIT) {
                // SteamVR is closing: let it go and fall back to the window.
                OpenVrApi.acknowledgeQuit();
                stop("SteamVR closed");
            }
        }
    }

    /** Disconnects from SteamVR (the window shows side-by-side again). */
    public static synchronized void stop(String reason) {
        if (!started) {
            return;
        }
        started = false;
        try {
            if (overlay != 0L) {
                OpenVrApi.destroyOverlay(overlay);
            }
            OpenVrApi.shutdown();
        } catch (Throwable t) {
            System.out.println("[Parallax Screen] Error while leaving SteamVR: " + t);
        }
        overlay = 0L;
        lastAttemptNanos = System.nanoTime();
        System.out.println("[Parallax Screen] SteamVR screen stopped: " + reason);
    }

    private static void logOnce(String message) {
        if (!message.equals(lastError)) {
            lastError = message;
            System.out.println("[Parallax Screen] " + message);
        }
    }
}
