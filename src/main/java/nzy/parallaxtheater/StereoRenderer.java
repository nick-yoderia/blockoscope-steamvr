package nzy.parallaxtheater;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Camera;
import nzy.parallaxtheater.mixin.CameraAccessor;
import nzy.parallaxtheater.mixin.GameRendererAccessor;
import nzy.parallaxtheater.mixin.GuiRenderStateAccessor;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/**
 * Renders each frame twice, once per eye, and packs the two views into the window as half side-by-side.
 *
 * Minecraft 26.2 extracts everything it draws (camera, level, GUI) into a render state once per frame and then
 * renders from that state. Here the render half runs twice. For each eye the main render target is swapped for a
 * half-width eye target, the extracted camera is moved sideways by half the eye spacing, and the projection is
 * sheared so things at the focus distance line up in both eyes. The GUI is drawn for both eyes, shifted sideways
 * per eye to give it a depth (see {@link #updateGuiDepth}); menus go into the eye targets, the in-game HUD into the
 * window after packing (see {@link #drawGuiOverWindow}). Projections keep the window's aspect ratio, so each eye
 * is squeezed to half width, which is what half side-by-side viewers stretch back out.
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
    private static long lastFrameNanos;
    /** 1 / distance at which the GUI sits this frame, smoothed; see {@link #updateGuiDepth}. */
    private static float guiInverseDistance;
    /** 1 / distance of the nearest thing behind the hotbar and status bars this frame, or -1 for nothing. */
    private static float hudSceneInverseDistance = -1f;
    /** Length of the HUD's depth rays, in blocks; anything farther has next to no disparity anyway. */
    private static final double HUD_RAY_LENGTH = 64.0;
    /** This frame the GUI is drawn into the window after the eyes are packed, not into the eye targets. */
    private static boolean guiOverWindow;
    /** The eye whose GUI is being drawn into the window right now ({@link #NONE} otherwise), and its pass (0 or 1). */
    private static int guiEye = NONE;
    private static int guiPass;
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
        Vec3 centre = camera.pos;
        Vec3 mainCentre = mainCamera.position();
        Matrix4f projection = camera.projectionMatrix == null ? null : new Matrix4f(camera.projectionMatrix);
        long now = System.nanoTime();
        float seconds = lastFrameNanos == 0L ? 1f : (now - lastFrameNanos) / 1.0e9f;
        lastFrameNanos = now;
        updateCrosshairDepth(mainCamera, seconds);
        hudSceneInverseDistance = hudSceneDepth(mainCamera, projection, window);
        updateGuiDepth(seconds);
        // A GUI that blurs the world behind it (menus) or draws the title panorama needs the eye's own world under it,
        // so it stays in the eye targets. Everything else is drawn into the window at full resolution afterwards.
        GuiRenderState guiState = gameRenderer.gameRenderState().guiRenderState;
        guiOverWindow = guiState.panoramaRenderState == null
            && ((GuiRenderStateAccessor) guiState).parallaxTheater$firstStratumAfterBlur() == Integer.MAX_VALUE;
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
        if (guiOverWindow) {
            drawGuiOverWindow(access, main, encoder);
        }
    }

    /**
     * Draws the GUI into each half of the window after the eyes are packed, instead of into the eye targets.
     *
     * The eye targets are rendered at the render scale and then stretched into the window, and the GUI's per-eye
     * shift is generally a fraction of a pixel. Thin lines (the crosshair's) then landed on different pixel fractions
     * in the two eyes, one or two pixels wide, which reads as a slightly doubled crosshair. Drawn into the window
     * with the shift rounded to whole window pixels, both eyes get exactly the same pixels, just moved, and the HUD
     * stays sharp at any render scale.
     */
    private static void drawGuiOverWindow(GameRendererAccessor access, RenderTarget main, CommandEncoder encoder) {
        if (main.useDepth) {
            encoder.clearDepthTexture(main.getDepthTexture(), 0.0);
        }
        access.parallaxTheater$setUseUiLightmap(true);
        try {
            for (guiPass = 0; guiPass < 2; guiPass++) {
                guiEye = RIGHT_FIRST ? 1 - guiPass : guiPass;
                access.parallaxTheater$guiRenderer().render();
            }
        } finally {
            guiEye = NONE;
            access.parallaxTheater$setUseUiLightmap(false);
        }
        access.parallaxTheater$guiRenderer().endFrame();
    }

    /** True while the GUI is drawn for an eye, into the eye target or into the window. */
    public static boolean isGuiPass() {
        return guiEye != NONE || eye != NONE;
    }

    /** The first of the two GUI passes: the GUI is prepared once and its state kept for the second. */
    public static boolean isFirstGuiPass() {
        return guiEye != NONE ? guiPass == 0 : isFirstEye();
    }

    public static boolean isSecondGuiPass() {
        return guiEye != NONE ? guiPass == 1 : isSecondEye();
    }

    /** True while an eye renders this frame without its GUI, which is drawn into the window afterwards. */
    public static boolean guiDrawnOverWindow() {
        return eye != NONE && guiOverWindow;
    }

    /** True while the GUI is drawn into the window for one eye. */
    public static boolean guiDrawsIntoWindow() {
        return guiEye != NONE;
    }

    /** Left edge, in pixels of the target being drawn, of the area the current eye's GUI is drawn into. */
    public static int guiAreaLeft() {
        if (guiEye == NONE) {
            return 0;
        }
        int leftHalf = StereoConfig.swapEyes() ? RIGHT : LEFT;
        return guiEye == leftHalf ? 0 : windowWidth / 2;
    }

    public static int guiAreaWidth() {
        return guiEye != NONE ? windowWidth / 2 : eyeWidth;
    }

    public static int guiAreaHeight() {
        return guiEye != NONE ? windowHeight : eyeHeight;
    }

    /** Horizontal factor from window pixels to pixels of the current eye's GUI area. */
    public static float guiScaleX() {
        return windowWidth <= 0 ? 1f : (float) guiAreaWidth() / windowWidth;
    }

    /** Vertical factor from window pixels to pixels of the current eye's GUI area. */
    public static float guiScaleY() {
        return windowHeight <= 0 ? 1f : (float) guiAreaHeight() / windowHeight;
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
     * Shifts the GUI sideways in each eye so it appears at its distance (see {@link #updateGuiDepth}): the same
     * disparity a point straight ahead at that distance gets in the world. A flat panel facing the viewer looks the
     * same to both eyes apart from this shift, so nothing else changes per eye.
     */
    public static Matrix4f eyeGuiProjection(Matrix4f projection) {
        return guiProjection(projection, guiOffset());
    }

    /** The GUI's sideways shift in this eye, in pixels of its area (for scissor rectangles); a whole number. */
    public static float guiOffsetPixels() {
        return guiOffset() * guiAreaWidth() / 2f;
    }

    /**
     * The GUI projection for the current eye: shifted by {@code offset} (clip units of the eye's area) and, when drawn
     * into the window, squeezed into the eye's half.
     */
    private static Matrix4f guiProjection(Matrix4f projection, float offset) {
        Matrix4f matrix = new Matrix4f();
        if (guiEye != NONE) {
            matrix.translation(guiAreaLeft() == 0 ? -0.5f : 0.5f, 0f, 0f).scale(0.5f, 1f, 1f);
        }
        return matrix.translate(offset, 0f, 0f).mul(projection);
    }

    /** Rounds a sideways shift to whole pixels, so both eyes rasterise the GUI identically (just moved). */
    private static float snapToPixels(float offset) {
        float pixelsPerUnit = guiAreaWidth() / 2f;
        return pixelsPerUnit > 0f ? Math.round(offset * pixelsPerUnit) / pixelsPerUnit : offset;
    }

    /** -1 for the left eye, +1 for the right eye of the GUI being drawn (0 outside). */
    private static float guiSide() {
        int which = guiEye != NONE ? guiEye : eye;
        return which == LEFT ? -1f : which == RIGHT ? 1f : 0f;
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
        float offset = guiSide() * worldProjectionScale * StereoConfig.ipd() / 2f * (inverseFocus - crosshairInverseDistance);
        return guiProjection(projection, snapToPixels(offset));
    }

    /**
     * Moves the crosshair to the depth of what it aims at: the point on the block's outline or the entity's hitbox the
     * game picks (so grass counts, not what is behind it), within reach. With nothing in reach it rests at the edge of
     * your block reach (moved nearer or farther by the rest setting), rather than following far-away scenery.
     */
    private static void updateCrosshairDepth(Camera camera, float seconds) {
        Minecraft minecraft = Minecraft.getInstance();
        double reach = minecraft.player != null ? minecraft.player.blockInteractionRange() : 4.5;
        float target = (float) (1.0 / Math.max(0.5, reach + StereoConfig.crosshairRestOffset()));
        HitResult hit = minecraft.hitResult;
        if (camera != null && hit != null && hit.getType() != HitResult.Type.MISS) {
            Vec3 from = camera.position();
            Vector3fc forward = camera.forwardVector();
            double distance = hit.getLocation().subtract(from).dot(new Vec3(forward.x(), forward.y(), forward.z()));
            target = (float) (1.0 / Math.max(0.3, distance));
        }
        // A couple of frames of easing, so it snaps without flickering along block edges.
        crosshairInverseDistance += (target - crosshairInverseDistance) * (1f - (float) Math.exp(-seconds / 0.025f));
        if (StereoDebug.ENABLED && eyePassCounter % 240 == 0) {
            StereoDebug.log("crosshair " + (hit == null ? "none" : hit.getType()) + " distance " + 1f / target);
        }
    }

    /**
     * Picks the depth of the GUI for this frame.
     *
     * Two eyes can only fuse a narrow range of depths around where they converge; anything much nearer or farther is
     * seen double. A HUD floating at a fixed distance in front of the world (0.1.x) doubled whenever you looked at the
     * world through it: the hotbar and crosshair at different depths, and menus whose full-screen backdrop stuck out
     * of the screen and was cut off by its edges. Now:
     * <ul>
     * <li>Menus and other screens sit on the screen surface (or at the menu distance), where the eyes rest anyway.</li>
     * <li>The in-game HUD sits on the nearest thing behind the hotbar and status bars ({@link #hudSceneDepth}), so it
     * is never behind something it covers. 0.1.3 put it at the crosshair's depth, which still clashed when a block
     * covered the hotbar but not the crosshair. With nothing behind it (sky) it takes the crosshair's depth. It can
     * also follow the crosshair (aim) or stay at the fixed HUD distance. It eases over ~120 ms so it doesn't flicker
     * along block edges.</li>
     * </ul>
     * The chat screen counts as HUD: it opens over the game and the hotbar stays in view.
     */
    private static void updateGuiDepth(float seconds) {
        Minecraft minecraft = Minecraft.getInstance();
        float focus = StereoConfig.focusDistance();
        float inverseFocus = focus > 0f ? 1f / focus : 0f;
        float target;
        Screen screen = minecraft.gui.screen();
        boolean menu = screen != null && !(screen instanceof ChatScreen) || minecraft.level == null;
        if (menu) {
            target = inverseDistanceOrScreen(StereoConfig.menuDistance(), inverseFocus);
        } else {
            target = switch (StereoConfig.hudDepth()) {
                case SCENE -> hudSceneInverseDistance >= 0f ? hudSceneInverseDistance : crosshairInverseDistance;
                case AIM -> crosshairInverseDistance;
                case FIXED -> inverseDistanceOrScreen(StereoConfig.hudDistance(), inverseFocus);
            };
        }
        guiInverseDistance += (target - guiInverseDistance) * (1f - (float) Math.exp(-seconds / 0.12f));
    }

    /** 1 / distance, where 0 m means the screen surface (the focus distance). */
    private static float inverseDistanceOrScreen(float distance, float inverseFocus) {
        return distance > 0f ? 1f / distance : inverseFocus;
    }

    /**
     * Finds the nearest block (outline, so grass counts) or entity behind the hotbar and status bars: rays from the
     * centre camera through a grid of points over that area of the screen. Returns 1 / distance along the view, or
     * -1 when nothing is within {@link #HUD_RAY_LENGTH}. Fluids are left out, as for the crosshair (a ray starting
     * under water would hit the water around the camera).
     */
    private static float hudSceneDepth(Camera camera, Matrix4f projection, WindowRenderState window) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || camera == null || !camera.isInitialized() || projection == null
            || window.guiScale <= 0 || projection.m00() == 0f || projection.m11() == 0f) {
            return -1f;
        }
        // The hotbar is 182 x 22 GUI pixels at the bottom centre; hearts, food and the XP bar sit up to ~40 above.
        float guiWidth = (float) window.width / window.guiScale;
        float guiHeight = (float) window.height / window.guiScale;
        float[] xs = {-88f, -45f, 0f, 45f, 88f};
        float[] ys = {3f, 12f, 32f};
        Vec3 from = camera.position();
        Vector3fc forward = camera.forwardVector();
        Vector3fc up = camera.upVector();
        Vector3fc left = camera.leftVector();
        Vec3[] ends = new Vec3[xs.length * ys.length];
        double minX = from.x, minY = from.y, minZ = from.z, maxX = from.x, maxY = from.y, maxZ = from.z;
        for (int j = 0; j < ys.length; j++) {
            for (int i = 0; i < xs.length; i++) {
                float ndcX = (guiWidth / 2f + xs[i]) / guiWidth * 2f - 1f;
                float ndcY = 1f - (guiHeight - ys[j]) / guiHeight * 2f;
                float rightAmount = ndcX / projection.m00();
                float upAmount = ndcY / projection.m11();
                // One unit along the view per unit of direction, so the ray ends HUD_RAY_LENGTH ahead in depth.
                Vec3 direction = new Vec3(forward.x() - left.x() * rightAmount + up.x() * upAmount,
                    forward.y() - left.y() * rightAmount + up.y() * upAmount,
                    forward.z() - left.z() * rightAmount + up.z() * upAmount);
                Vec3 end = from.add(direction.scale(HUD_RAY_LENGTH));
                ends[j * xs.length + i] = end;
                minX = Math.min(minX, end.x);
                minY = Math.min(minY, end.y);
                minZ = Math.min(minZ, end.z);
                maxX = Math.max(maxX, end.x);
                maxY = Math.max(maxY, end.y);
                maxZ = Math.max(maxZ, end.z);
            }
        }
        Vec3 forwardVec = new Vec3(forward.x(), forward.y(), forward.z());
        Entity viewer = camera.entity();
        double nearest = Double.MAX_VALUE;
        for (Vec3 end : ends) {
            HitResult hit = minecraft.level.clip(new ClipContext(from, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, viewer));
            if (hit.getType() != HitResult.Type.MISS) {
                nearest = Math.min(nearest, hit.getLocation().subtract(from).dot(forwardVec));
            }
        }
        AABB area = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        for (Entity entity : minecraft.level.getEntities(viewer, area, entity -> !entity.isSpectator())) {
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
            for (Vec3 end : ends) {
                java.util.Optional<Vec3> point = box.clip(from, end);
                if (point.isPresent()) {
                    nearest = Math.min(nearest, point.get().subtract(from).dot(forwardVec));
                }
            }
        }
        return nearest == Double.MAX_VALUE ? -1f : (float) (1.0 / Math.max(0.3, nearest));
    }

    /** The GUI's sideways shift for the eye being drawn, in clip units of its area, rounded to whole pixels. */
    private static float guiOffset() {
        float side = guiSide();
        if (side == 0f) {
            return 0f;
        }
        float focus = StereoConfig.focusDistance();
        float inverseFocus = focus > 0f ? 1f / focus : 0f;
        return snapToPixels(side * worldProjectionScale * StereoConfig.ipd() / 2f * (inverseFocus - guiInverseDistance));
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
