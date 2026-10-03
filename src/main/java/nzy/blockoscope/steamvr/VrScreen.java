package nzy.blockoscope.steamvr;

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
 * game's frame rate is. The packed texture (left eye in the left half) is copied on the GPU into a Direct3D 11 texture
 * that SteamVR takes every frame ({@link D3dShare}), so nothing is read back to the CPU.
 */
public final class VrScreen {
    private static final String OVERLAY_KEY = "nzy.blockoscope.steamvr.screen";
    /** Time between attempts to reach SteamVR while it isn't available. */
    private static final long RETRY_NANOS = 5_000_000_000L;

    private static boolean started;
    private static long overlay;
    private static long lastAttemptNanos;
    /** Set by the connecting thread; picked up on the render thread. */
    private static volatile boolean connecting;
    private static volatile boolean connected;
    private static boolean placed;
    /** Headset pixels per unit of tan(angle), for the automatic eye resolution (0 = unknown). */
    private static float pixelsPerTangent;
    /** Headset refresh rate (0 = unknown) and the frame pacing state (see {@link #pace}). */
    private static float displayHz;
    private static long frameStartNanos;
    private static float frameWorkNanos;
    private static int syncTimeouts;
    private static long syncPausedUntilNanos;
    /** Tracking space the screen was placed in (seated, or standing when there is no seated origin). */
    private static int universe = OpenVrApi.UNIVERSE_SEATED;
    private static boolean recenterRequested;
    /**
     * Where the screen was last centred from: the headset's position and level heading when it was placed in front of
     * you (first placement, F8). Distance and height changes move the screen from here, not from wherever you happen
     * to be looking at that moment.
     */
    private static boolean anchored;
    private static float anchorX;
    private static float anchorY;
    private static float anchorZ;
    private static float anchorForwardX;
    private static float anchorForwardZ;
    private static float appliedDistance = Float.NaN;
    private static float appliedHeight = Float.NaN;
    private static volatile String lastError = "";
    private static boolean warnedNotOpenGl;
    private static boolean warnedNoDirect3d;
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

    /**
     * How much finer than the headset's own pixels the automatic eye resolution renders. The compositor resamples the
     * screen onto the curved lens image, and bilinear sampling at 1:1 loses about a third of the sharpness; Blockoscope
     * SBS in Bigscreen (half of a 3440x1440 window per eye, 1720x1440) had well over twice the pixels of 0.2.0's 1:1
     * auto setting (1536x643 for a 3.6 m screen at 2 m) and the user found it sharper.
     */
    private static final double AUTO_SUPERSAMPLE = 1.5;
    private static final int MAX_EYE_WIDTH = 4096;

    /**
     * Height each eye renders at for the screen, for a window of the given size (the eyes keep the window's shape).
     * With the setting, the set width over the window's aspect. Automatic (the setting at 0): {@link #AUTO_SUPERSAMPLE}
     * times as many pixels as the headset shows across the screen (from SteamVR's render size and field of view and
     * the screen's size and distance), rounded to a whole number of pixels per GUI pixel, so the HUD and its pixel font
     * stay crisp when drawn into the eyes (at 1.8 screen pixels per GUI pixel the letters came out uneven and blurred).
     * Without SteamVR's numbers, 1920 wide.
     */
    public static int eyeHeight(int windowWidth, int windowHeight) {
        double aspect = windowWidth > 0 && windowHeight > 0 ? (double) windowWidth / windowHeight : 16.0 / 9.0;
        int setting = StereoConfig.eyeResolution();
        if (setting > 0) {
            return Math.max(1, (int) Math.round(setting / aspect));
        }
        double wanted = pixelsPerTangent > 0f
            ? pixelsPerTangent * StereoRenderer.screenWidth() / StereoConfig.screenDistance() * AUTO_SUPERSAMPLE
            : 1920;
        int guiScale = Math.max(1, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
        double guiHeight = (double) windowHeight / guiScale;
        int perGuiPixel = (int) Math.max(1, Math.round(wanted / aspect / guiHeight));
        while (perGuiPixel > 1 && perGuiPixel * guiHeight * aspect > MAX_EYE_WIDTH) {
            perGuiPixel--;
        }
        return Math.max(1, (int) Math.round(perGuiPixel * guiHeight));
    }

    /** Width each eye renders at for the screen with the game window's current shape (for the settings and log). */
    public static int eyeResolution() {
        com.mojang.blaze3d.platform.Window window = net.minecraft.client.Minecraft.getInstance().getWindow();
        int height = eyeHeight(window.getWidth(), window.getHeight());
        return window.getHeight() > 0 ? (int) Math.round((double) height * window.getWidth() / window.getHeight()) : height;
    }

    /** Asks for the screen to be placed in front of the headset again, along where it now faces (F8). */
    public static void requestRecenter() {
        recenterRequested = true;
        anchored = false;
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
            Thread thread = new Thread(VrScreen::connect, "Blockoscope SteamVR connect");
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
            if (StereoConfig.screenDistance() != appliedDistance || StereoConfig.screenHeight() != appliedHeight) {
                // Distance or height changed in the settings: move the screen along the same line, keeping its heading.
                recenterRequested = true;
            }
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
                overlay = OpenVrApi.createOverlay(OVERLAY_KEY, "Blockoscope SteamVR");
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
                Runtime.getRuntime().addShutdownHook(new Thread(() -> stop("game closed"), "Blockoscope SteamVR shutdown"));
            }
            placed = false;
            anchored = false;
            lastError = "";
            pixelsPerTangent = OpenVrApi.pixelsPerTangent();
            displayHz = OpenVrApi.displayFrequency();
            frameStartNanos = 0L;
            OpenVrApi.showOverlay(overlay);
            System.out.println("[Blockoscope SteamVR] SteamVR screen started (" + Math.round(pixelsPerTangent)
                + " headset pixels per tangent; eyes render " + eyeResolution() + " wide; headset " + Math.round(displayHz) + " Hz)");
        } catch (Throwable t) {
            logOnce("SteamVR screen could not be shown: " + t);
            stop("error");
        }
    }

    /** Screen size and curve from the settings; only sent to SteamVR when they change. */
    private static void applyShape() throws Throwable {
        float width = StereoRenderer.screenWidth();
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
     * heading counts, so looking down while recentering doesn't tilt the screen). The headset is only read when
     * (re)centering; a distance or height change reuses that anchor. It used to read the headset every time, and since
     * Cloth Config saves every entry (which requested a recenter) when you press Save at the bottom right, the screen
     * followed your gaze towards the button and drifted to the right with each change.
     */
    private static boolean place() throws Throwable {
        if (!anchored && !anchor()) {
            return false;
        }
        float distance = StereoConfig.screenDistance();
        float height = StereoConfig.screenHeight();
        float x = anchorX + anchorForwardX * distance;
        float y = anchorY + height;
        float z = anchorZ + anchorForwardZ * distance;
        // The overlay faces +Z; turn it about the vertical to face back along the heading.
        float angle = (float) Math.atan2(-anchorForwardX, -anchorForwardZ);
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
            return false;
        }
        appliedDistance = distance;
        appliedHeight = height;
        return true;
    }

    /** Reads the headset's position and level heading as the point the screen is placed from. */
    private static boolean anchor() throws Throwable {
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
        anchorX = POSES.get(JAVA_FLOAT, pose + 3 * 4);
        anchorY = POSES.get(JAVA_FLOAT, pose + 7 * 4);
        anchorZ = POSES.get(JAVA_FLOAT, pose + 11 * 4);
        anchorForwardX = forwardX;
        anchorForwardZ = forwardZ;
        anchored = true;
        return true;
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
                System.out.println("[Blockoscope SteamVR] The SteamVR screen needs the OpenGL renderer");
            }
            return;
        }
        try {
            // Preferably as a Direct3D 11 texture (see D3dShare); the OpenGL texture itself if that isn't possible.
            MemorySegment shared = D3dShare.frame(gl.glId(), packed.width, packed.height);
            boolean direct3d = shared.address() != 0L;
            if (!direct3d && D3dShare.failure() != null && !warnedNoDirect3d) {
                warnedNoDirect3d = true;
                System.out.println("[Blockoscope SteamVR] Handing SteamVR the OpenGL texture (Direct3D 11 sharing: "
                    + D3dShare.failure() + ")");
            }
            // SteamVR accounts for OpenGL's bottom-up rows itself (Vivecraft submits Minecraft's GL eye textures with
            // plain 0..1 bounds); the D3D11 copy arrives upside down. The setting flips it once more.
            boolean flip = StereoConfig.flipScreen() ^ (direct3d && D3dShare.FLIPPED);
            if (!boundsSet || flip != appliedFlip) {
                boundsSet = true;
                appliedFlip = flip;
                BOUNDS.setAtIndex(JAVA_FLOAT, 0, 0f);
                BOUNDS.setAtIndex(JAVA_FLOAT, 1, flip ? 1f : 0f);
                BOUNDS.setAtIndex(JAVA_FLOAT, 2, 1f);
                BOUNDS.setAtIndex(JAVA_FLOAT, 3, flip ? 0f : 1f);
                OpenVrApi.setOverlayTextureBounds(overlay, BOUNDS);
            }
            TEXTURE.set(JAVA_LONG, 0, direct3d ? shared.address() : gl.glId());
            TEXTURE.set(JAVA_INT, 8, direct3d ? OpenVrApi.TEXTURE_DIRECTX : OpenVrApi.TEXTURE_OPENGL);
            TEXTURE.set(JAVA_INT, 12, OpenVrApi.COLOR_SPACE_AUTO);
            int result = OpenVrApi.setOverlayTexture(overlay, TEXTURE);
            if (result != 0) {
                logOnce("SteamVR did not take the frame: " + OpenVrApi.overlayErrorText(result));
            }
            pace();
        } catch (Throwable t) {
            logOnce("SteamVR screen error: " + t);
            stop("error");
        }
    }

    /**
     * Sync to headset: after handing over a frame, waits for the compositor's next frame, so the game makes one frame
     * per headset refresh. A free-running game (often 150-250 FPS) beats against the headset's fixed rate: each
     * refresh then shows a frame of a different age and turning looks uneven; it also takes GPU time the compositor
     * needs. When the game can't keep up (a frame takes over 85% of a refresh, averaged), it runs free instead, so it
     * never drops to every other refresh the way VSync would. Repeated timeouts (the headset asleep, the compositor not
     * drawing) pause the waiting for a few seconds.
     */
    private static void pace() throws Throwable {
        long now = System.nanoTime();
        if (frameStartNanos != 0L) {
            frameWorkNanos += ((now - frameStartNanos) - frameWorkNanos) * 0.1f;
        }
        if (StereoConfig.syncToHeadset() && displayHz > 0f && now >= syncPausedUntilNanos) {
            float interval = 1e9f / displayHz;
            if (frameWorkNanos < interval * 0.85f) {
                long waitStart = System.nanoTime();
                int result = OpenVrApi.waitFrameSync(Math.round(interval / 1e6f) + 5);
                if (StereoDebug.ENABLED) {
                    debugWaits++;
                    debugWaitNanos += System.nanoTime() - waitStart;
                    debugLastResult = result;
                    if (result != 0) {
                        debugErrors++;
                    }
                }
                if (result == 0) {
                    syncTimeouts = 0;
                } else if (++syncTimeouts >= 3) {
                    syncTimeouts = 0;
                    syncPausedUntilNanos = System.nanoTime() + 3_000_000_000L;
                }
            }
        }
        frameStartNanos = System.nanoTime();
        if (StereoDebug.ENABLED && ++debugFrames >= 500) {
            StereoDebug.log("pace: " + debugFrames + " frames, " + debugWaits + " waits averaging "
                + (debugWaits > 0 ? debugWaitNanos / debugWaits / 1000 : 0) + " us, " + debugErrors + " errors (last "
                + debugLastResult + "), work " + Math.round(frameWorkNanos / 1000f) + " us");
            debugFrames = debugWaits = debugErrors = 0;
            debugWaitNanos = 0L;
        }
    }

    private static int debugFrames;
    private static int debugWaits;
    private static int debugErrors;
    private static int debugLastResult;
    private static long debugWaitNanos;

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
            System.out.println("[Blockoscope SteamVR] Error while leaving SteamVR: " + t);
        }
        overlay = 0L;
        lastAttemptNanos = System.nanoTime();
        System.out.println("[Blockoscope SteamVR] SteamVR screen stopped: " + reason);
    }

    private static void logOnce(String message) {
        if (!message.equals(lastError)) {
            lastError = message;
            System.out.println("[Blockoscope SteamVR] " + message);
        }
    }
}
