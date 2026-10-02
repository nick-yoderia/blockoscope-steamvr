package nzy.parallaxscreen;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import org.lwjgl.openvr.HmdMatrix34;
import org.lwjgl.openvr.OpenVR;
import org.lwjgl.openvr.Texture;
import org.lwjgl.openvr.TrackedDevicePose;
import org.lwjgl.openvr.VR;
import org.lwjgl.openvr.VREvent;
import org.lwjgl.openvr.VROverlay;
import org.lwjgl.openvr.VRSystem;
import org.lwjgl.openvr.VRTextureBounds;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

/**
 * The virtual screen in SteamVR: an OpenVR overlay that shows the two eyes side by side, one per eye.
 *
 * Minecraft runs as an OpenVR overlay application, not a VR game: SteamVR keeps drawing its own scene (SteamVR Home or
 * the void) and composites the screen into it at the headset's frame rate, so head movement stays smooth whatever the
 * game's frame rate is. The packed texture (left eye in the left half) is handed over by its OpenGL id every frame;
 * SteamVR copies it, so nothing is read back to the CPU.
 */
public final class VrScreen {
    private static final String OVERLAY_KEY = "nzy.parallaxscreen.screen";
    /** Seconds between attempts to reach SteamVR while it isn't available. */
    private static final long RETRY_NANOS = 10_000_000_000L;

    private static boolean started;
    private static long overlay;
    private static long lastAttemptNanos;
    private static boolean attempted;
    private static boolean placed;
    private static boolean recenterRequested;
    private static String lastError = "";
    private static Texture texture;
    private static VREvent event;
    private static boolean warnedNotOpenGl;

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
        if (!started) {
            long now = System.nanoTime();
            if (lastAttemptNanos != 0L && now - lastAttemptNanos < RETRY_NANOS) {
                return;
            }
            lastAttemptNanos = now;
            start();
            return;
        }
        pollEvents();
        if (!started) {
            return;
        }
        if (!placed || recenterRequested) {
            placed = place();
            recenterRequested = false;
        }
    }

    private static void start() {
        try {
            if (!VR.VR_IsRuntimeInstalled()) {
                logOnce("SteamVR is not installed");
                return;
            }
            // Starting an overlay application launches SteamVR if needed; without a headset that only shows an
            // error in SteamVR, so after the first try only try again once a headset is there.
            if (attempted && !VR.VR_IsHmdPresent()) {
                return;
            }
            attempted = true;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer error = stack.mallocInt(1);
                int token = VR.VR_InitInternal(error, VR.EVRApplicationType_VRApplication_Overlay);
                if (error.get(0) != 0) {
                    logOnce("SteamVR is not available (" + VR.VR_GetVRInitErrorAsEnglishDescription(error.get(0))
                        + "); showing side-by-side in the window instead");
                    return;
                }
                OpenVR.create(token);
                LongBuffer handle = stack.mallocLong(1);
                int result = VROverlay.VROverlay_CreateOverlay(OVERLAY_KEY, "Parallax Screen", handle);
                if (result != VR.EVROverlayError_VROverlayError_None) {
                    logOnce("Could not create the SteamVR screen: " + VROverlay.VROverlay_GetOverlayErrorNameFromEnum(result));
                    VR.VR_ShutdownInternal();
                    return;
                }
                overlay = handle.get(0);
            }
            VROverlay.VROverlay_SetOverlayFlag(overlay, VR.VROverlayFlags_SideBySide_Parallel, true);
            // The GUI leaves alpha below 1 in places; the screen is opaque.
            VROverlay.VROverlay_SetOverlayFlag(overlay, VR.VROverlayFlags_IgnoreTextureAlpha, true);
            applyShape();
            if (texture == null) {
                texture = Texture.calloc();
                event = VREvent.calloc();
                // Leave SteamVR cleanly when the game closes, so the screen doesn't linger in the headset.
                Runtime.getRuntime().addShutdownHook(new Thread(() -> stop("game closed"), "Parallax Screen SteamVR shutdown"));
            }
            started = true;
            placed = false;
            lastError = "";
            VROverlay.VROverlay_ShowOverlay(overlay);
            System.out.println("[Parallax Screen] SteamVR screen started");
        } catch (Throwable t) {
            logOnce("SteamVR could not be started: " + t);
        }
    }

    /** Screen size and curve from the settings (cheap; called again whenever they may have changed). */
    public static void applyShape() {
        if (overlay == 0L) {
            return;
        }
        VROverlay.VROverlay_SetOverlayWidthInMeters(overlay, StereoConfig.screenWidth());
        VROverlay.VROverlay_SetOverlayCurvature(overlay, StereoConfig.screenCurvature() / 100f);
    }

    /**
     * Puts the screen straight ahead of the headset at the screen distance, level and facing it (only the headset's
     * heading counts, so looking down while recentering doesn't tilt the screen).
     */
    private static boolean place() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            TrackedDevicePose.Buffer poses = TrackedDevicePose.calloc(VR.k_unMaxTrackedDeviceCount, stack);
            VRSystem.VRSystem_GetDeviceToAbsoluteTrackingPose(VR.ETrackingUniverseOrigin_TrackingUniverseStanding, 0f, poses);
            TrackedDevicePose hmd = poses.get(VR.k_unTrackedDeviceIndex_Hmd);
            if (!hmd.bPoseIsValid()) {
                return false;
            }
            HmdMatrix34 pose = hmd.mDeviceToAbsoluteTracking();
            // Rows are (x, y, z, translation); the headset looks along its -Z axis.
            float forwardX = -pose.m(2);
            float forwardZ = -pose.m(10);
            float length = (float) Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
            if (length < 1e-4f) {
                forwardX = 0f;
                forwardZ = -1f;
            } else {
                forwardX /= length;
                forwardZ /= length;
            }
            float distance = StereoConfig.screenDistance();
            float x = pose.m(3) + forwardX * distance;
            float y = pose.m(7) + StereoConfig.screenHeight();
            float z = pose.m(11) + forwardZ * distance;
            // The overlay faces +Z; turn it to face back along the heading.
            float angle = (float) Math.atan2(-forwardX, -forwardZ);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            HmdMatrix34 transform = HmdMatrix34.calloc(stack);
            transform.m(0, cos).m(1, 0f).m(2, sin).m(3, x)
                .m(4, 0f).m(5, 1f).m(6, 0f).m(7, y)
                .m(8, -sin).m(9, 0f).m(10, cos).m(11, z);
            VROverlay.VROverlay_SetOverlayTransformAbsolute(overlay,
                VR.ETrackingUniverseOrigin_TrackingUniverseStanding, transform);
            return true;
        }
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
        texture.handle(gl.glId()).eType(VR.ETextureType_TextureType_OpenGL).eColorSpace(VR.EColorSpace_ColorSpace_Auto);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // OpenGL textures start at the bottom row.
            VRTextureBounds bounds = VRTextureBounds.calloc(stack).uMin(0f).uMax(1f);
            if (StereoConfig.flipScreen()) {
                bounds.vMin(0f).vMax(1f);
            } else {
                bounds.vMin(1f).vMax(0f);
            }
            VROverlay.VROverlay_SetOverlayTextureBounds(overlay, bounds);
        }
        int result = VROverlay.VROverlay_SetOverlayTexture(overlay, texture);
        if (result != VR.EVROverlayError_VROverlayError_None) {
            logOnce("SteamVR did not take the frame: " + VROverlay.VROverlay_GetOverlayErrorNameFromEnum(result));
        }
    }

    private static void pollEvents() {
        while (started && VRSystem.VRSystem_PollNextEvent(event, VREvent.SIZEOF)) {
            if (event.eventType() == VR.EVREventType_VREvent_Quit) {
                // SteamVR is closing: let it go and fall back to the window.
                VRSystem.VRSystem_AcknowledgeQuit_Exiting();
                stop("SteamVR closed");
            }
        }
    }

    /** Disconnects from SteamVR (the window shows side-by-side again). */
    public static void stop(String reason) {
        if (!started) {
            return;
        }
        started = false;
        try {
            if (overlay != 0L) {
                VROverlay.VROverlay_DestroyOverlay(overlay);
            }
            VR.VR_ShutdownInternal();
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
