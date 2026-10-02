package nzy.parallaxtheater;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Camera;
import nzy.parallaxtheater.mixin.CameraAccessor;
import nzy.parallaxtheater.mixin.GameRendererAccessor;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Renders each frame twice, once per eye, and packs the two views into the window as half side-by-side.
 *
 * Minecraft 26.2 extracts everything it draws (camera, level, GUI) into a render state once per frame and then
 * renders from that state. Here the render half runs twice. For each eye the main render target is swapped for a
 * half-width eye target, the extracted camera is moved sideways by half the eye spacing, and the projection is
 * sheared so things at the focus distance line up in both eyes. The GUI is drawn into both eyes unchanged, so it
 * sits on the screen surface. Projections keep the window's aspect ratio, so each eye is squeezed to half width,
 * which is what half side-by-side viewers stretch back out.
 */
public final class StereoRenderer {
    public static final int NONE = -1;
    public static final int LEFT = 0;
    public static final int RIGHT = 1;

    private static final Vector4f BLACK = new Vector4f(0f, 0f, 0f, 1f);

    private static final RenderTarget[] targets = new RenderTarget[2];
    private static int eye = NONE;
    /** 0 for the first eye rendered this frame, 1 for the second. */
    private static int pass;
    /** Debug: render the right eye first (to tell per-eye problems from first-render problems). */
    private static final boolean RIGHT_FIRST = Boolean.getBoolean("parallaxtheater.rightFirst")
        || java.nio.file.Files.exists(java.nio.file.Path.of("config", "parallax-theater.rightfirst"));
    private static int eyeWidth;
    private static int eyeHeight;
    private static int windowHeight;
    private static int windowWidth;
    private static String lastReason = "";
    /** Horizontal scale of the world projection (m00) this frame, used to give the GUI matching disparity. */
    private static float worldProjectionScale = 1f;
    /**
     * Centre camera minus the current eye, in world space. Some things (particles) are positioned relative to the
     * centre camera when the frame is extracted; adding this moves them to where they belong for the eye.
     */
    private static final Vector3f eyeShift = new Vector3f();
    /** 1 / distance of what the crosshair points at (0 = nothing, infinitely far), smoothed. */
    private static float crosshairInverseDistance;
    private static long lastCrosshairNanos;
    /** Counts eye renders, so per-frame caches in other mods can tell one eye from the next. */
    private static int eyePassCounter;

    private StereoRenderer() {}

    /** The eye being rendered right now, or {@link #NONE} outside the stereo render. */
    public static int eye() {
        return eye;
    }

    /** True while the first of the two eyes renders; per-frame cleanup is held back until the second. */
    public static boolean isFirstEye() {
        return eye != NONE && pass == 0;
    }

    /** True while the second eye renders: the same frame again, so per-frame bookkeeping must not repeat. */
    public static boolean isSecondEye() {
        return eye != NONE && pass == 1;
    }

    public static boolean isRendering() {
        return eye != NONE;
    }

    /** Horizontal factor from window pixels to eye target pixels while an eye renders (1 otherwise). */
    public static float eyeScaleX() {
        return eye == NONE || windowWidth <= 0 ? 1f : (float) eyeWidth / windowWidth;
    }

    /** Vertical factor from window pixels to eye target pixels while an eye renders (1 otherwise). */
    public static float eyeScaleY() {
        return eye == NONE || windowHeight <= 0 ? 1f : (float) eyeHeight / windowHeight;
    }

    /** Increases every time an eye starts rendering. */
    public static int eyePassCounter() {
        return eyePassCounter;
    }

    /** See {@link #eyeShift}; zero outside the stereo render. */
    public static Vector3f eyeShift() {
        return eyeShift;
    }

    public static int eyeWidth() {
        return eyeWidth;
    }

    public static int eyeHeight() {
        return eyeHeight;
    }

    /** Replaces GameRenderer.render(deltaTracker, renderLevel) in Minecraft.renderFrame. */
    public static void render(GameRenderer gameRenderer, DeltaTracker deltaTracker, boolean renderLevel) {
        CursorControl.update();
        logFps();
        WindowRenderState window = gameRenderer.gameRenderState().windowRenderState;
        int width = window.width;
        int height = window.height;
        String reason = !StereoConfig.enabled() ? "disabled" : window.isMinimized || width < 2 || height < 1
            ? "window minimized" : loading() ? "loading" : null;
        if (!java.util.Objects.equals(reason, lastReason)) {
            lastReason = reason;
            System.out.println("[Parallax Theater] " + (reason == null ? "stereo on, " + width + "x" + height : "2D: " + reason));
        }
        if (reason != null) {
            gameRenderer.render(deltaTracker, renderLevel);
            return;
        }

        RenderTarget main = gameRenderer.mainRenderTarget();
        if (main.width != width || main.height != height) {
            gameRenderer.resize(width, height); // what render() would do; it is skipped while an eye renders
        }
        // Each eye covers half the window; the render scale trades sharpness for speed (the blit rescales).
        int halfWidth = width / 2;
        float scale = StereoConfig.renderScale() / 100f;
        int targetWidth = Math.max(1, Math.round(halfWidth * scale));
        int targetHeight = Math.max(1, Math.round(height * scale));
        ensureTargets(targetWidth, targetHeight);

        CameraRenderState camera = gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        Camera mainCamera = gameRenderer.mainCamera();
        updateCrosshairDepth();
        Vec3 centre = camera.pos;
        Vec3 mainCentre = mainCamera.position();
        Matrix4f projection = camera.projectionMatrix == null ? null : new Matrix4f(camera.projectionMatrix);
        GameRendererAccessor access = (GameRendererAccessor) gameRenderer;
        windowWidth = width;
        windowHeight = height;
        eyeWidth = targetWidth;
        eyeHeight = targetHeight;
        worldProjectionScale = projection != null ? projection.m00()
            : (float) (1.0 / ((double) width / height * Math.tan(Math.toRadians(35.0))));
        try {
            for (pass = 0; pass < 2; pass++) {
                int i = RIGHT_FIRST ? 1 - pass : pass;
                eye = i;
                eyePassCounter++;
                access.parallaxTheater$setMainRenderTarget(targets[i]);
                placeEye(camera, centre, projection, i);
                // Mods that read the camera object rather than the extracted state (Iris's camera position uniform
                // and shadow pass, for one) must see the eye too, or shadows and lighting sit at the wrong depth.
                // Terrain culling has already run during extraction, so moving it here costs nothing.
                if (centre != null && mainCentre != null) {
                    ((CameraAccessor) mainCamera).parallaxTheater$setPosition(mainCentre.add(camera.pos.subtract(centre)));
                    eyeShift.set((float) (centre.x - camera.pos.x), (float) (centre.y - camera.pos.y),
                        (float) (centre.z - camera.pos.z));
                }
                gameRenderer.render(deltaTracker, renderLevel);
                if (pass == 0 && renderLevel && Minecraft.getInstance().level != null) {
                    // Per-frame buffers that are rewritten for every eye (the clouds' camera offset, Sodium's terrain
                    // uniforms) move on to a fresh copy, or the second eye's values would overwrite the first eye's
                    // before the GPU draws it: the left eye's clouds jumped a cloud cell whenever the cell boundary
                    // fell between the eyes.
                    Minecraft.getInstance().levelRenderer.endFrame();
                }
            }
        } finally {
            eye = NONE;
            eyeShift.zero();
            access.parallaxTheater$setMainRenderTarget(main);
            if (mainCentre != null) {
                ((CameraAccessor) mainCamera).parallaxTheater$setPosition(mainCentre);
            }
            camera.pos = centre;
            if (projection != null) {
                camera.projectionMatrix.set(projection);
            }
        }

        // Pack the eyes into the window target: left eye on the left half unless swapped.
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(main.getColorTexture(), BLACK);
        int leftHalf = StereoConfig.swapEyes() ? RIGHT : LEFT;
        EyeBlit.draw(encoder, targets[leftHalf], main, 0);
        EyeBlit.draw(encoder, targets[1 - leftHalf], main, 1);
    }

    private static long lastFpsLog;

    private static void logFps() {
        if (!StereoDebug.ENABLED) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastFpsLog >= 5000L) {
            lastFpsLog = now;
            System.out.println("[Parallax Theater] fps " + Minecraft.getInstance().getFps()
                + (StereoConfig.enabled() ? " (3D)" : " (2D)"));
        }
    }

    /** -1 for the left eye, +1 for the right eye (0 outside the stereo render). */
    private static float side() {
        return eye == LEFT ? -1f : eye == RIGHT ? 1f : 0f;
    }

    /**
     * The first-person hand and held item use their own projection with the camera at the origin. Gives it the same
     * eye offset and focus shear as the world, so it has real depth instead of sitting on the screen surface.
     */
    public static Matrix4f eyeHandProjection(Matrix4f projection) {
        // Scaling the eye offset and the shear together scales the hand's disparity (0 = on the screen surface).
        float halfIpd = StereoConfig.ipd() / 2f * StereoConfig.handDepthPercent() / 100f;
        float side = side();
        float focus = StereoConfig.focusDistance();
        // Shift in clip space (x += k * w) rather than editing one element, so it stays right when the matrix
        // already contains other transforms (Iris scales the hand's depth and adds view bobbing).
        float shift = focus > 0f ? side * projection.m00() * halfIpd / focus : 0f;
        return new Matrix4f().m30(shift).mul(projection).translate(-side * halfIpd, 0f, 0f);
    }

    /**
     * Shifts the GUI sideways in each eye so it appears at the HUD distance: the same disparity a point straight
     * ahead at that distance gets in the world.
     */
    public static Matrix4f eyeGuiProjection(Matrix4f projection) {
        return new Matrix4f().translation(guiOffset(), 0f, 0f).mul(projection);
    }

    /** The GUI's sideways shift in this eye, in eye target pixels (for scissor rectangles). */
    public static float guiOffsetPixels() {
        return guiOffset() * eyeWidth / 2f;
    }

    /**
     * The crosshair gets the depth of whatever it points at instead of the HUD's, so the target and the crosshair can
     * be looked at together without either one doubling (Vivecraft does the same with its 3D crosshair).
     */
    public static Matrix4f eyeCrosshairProjection(Matrix4f projection) {
        if (!StereoConfig.crosshairAtTarget()) {
            return eyeGuiProjection(projection);
        }
        float focus = StereoConfig.focusDistance();
        float inverseFocus = focus > 0f ? 1f / focus : 0f;
        float offset = side() * worldProjectionScale * StereoConfig.ipd() / 2f * (inverseFocus - crosshairInverseDistance);
        return new Matrix4f().translation(offset, 0f, 0f).mul(projection);
    }

    /** Eases the crosshair towards the depth of what it points at (see {@link CrosshairDepth}). */
    private static void updateCrosshairDepth() {
        long now = System.nanoTime();
        float seconds = lastCrosshairNanos == 0L ? 1f : (now - lastCrosshairNanos) / 1.0e9f;
        lastCrosshairNanos = now;
        float focus = StereoConfig.focusDistance();
        float target = CrosshairDepth.targetInverseDistance(seconds, focus > 0f ? 1f / focus : 0f);
        // A few frames of easing, so the crosshair doesn't jitter along block edges.
        crosshairInverseDistance += (target - crosshairInverseDistance) * (1f - (float) Math.exp(-seconds / 0.06f));
    }

    /** The GUI's sideways shift in this eye, in clip space. */
    private static float guiOffset() {
        float hudDistance = StereoConfig.hudDistance();
        if (eye == NONE || hudDistance <= 0f) {
            return 0f;
        }
        float focus = StereoConfig.focusDistance();
        float inverseFocus = focus > 0f ? 1f / focus : 0f;
        return side() * worldProjectionScale * StereoConfig.ipd() / 2f * (inverseFocus - 1f / hudDistance);
    }

    /**
     * Shaders are only available once resources have loaded; compiling the composite pipeline before that marks it
     * as broken for good, so the loading screens stay 2D.
     */
    private static boolean loading() {
        Minecraft minecraft = Minecraft.getInstance();
        return !minecraft.isGameLoadFinished() || minecraft.gui.overlay() != null;
    }

    /** Moves the extracted camera to one eye and shears its projection for the focus distance. */
    private static void placeEye(CameraRenderState camera, Vec3 centre, Matrix4f projection, int which) {
        if (centre == null || projection == null || camera.orientation == null) {
            return;
        }
        float halfIpd = StereoConfig.ipd() / 2f;
        float side = which == LEFT ? -1f : 1f;
        Vector3f right = camera.orientation.transform(new Vector3f(1f, 0f, 0f));
        camera.pos = centre.add(right.x * side * halfIpd, right.y * side * halfIpd, right.z * side * halfIpd);

        camera.projectionMatrix.set(projection);
        float focus = StereoConfig.focusDistance();
        if (focus > 0f) {
            // A point straight ahead of the centre at the focus distance sits halfIpd to the other side of this
            // eye; shifting clip x by m00 * halfIpd / focus puts it in the middle of the view in both eyes.
            camera.projectionMatrix.m20(projection.m20() - side * projection.m00() * halfIpd / focus);
        }
    }

    private static void ensureTargets(int width, int height) {
        for (int i = LEFT; i <= RIGHT; i++) {
            if (targets[i] == null) {
                targets[i] = new TextureTarget(i == LEFT ? "Stereo left eye" : "Stereo right eye",
                    width, height, true, GpuFormat.RGBA8_UNORM);
            } else if (targets[i].width != width || targets[i].height != height) {
                targets[i].resize(width, height);
            }
        }
    }
}
