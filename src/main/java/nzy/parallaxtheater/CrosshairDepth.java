package nzy.parallaxtheater;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import java.nio.FloatBuffer;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL45;

/**
 * How far away the thing under the crosshair is, read from the depth buffer the first eye just rendered: whatever is on
 * screen counts (terrain at any distance, entities, Voxy's distant terrain), with no reach limit.
 *
 * The centre pixel is copied into a small buffer on the GPU and read once a fence says the copy is done (a frame or two
 * later), so it never stalls rendering. Blaze3D's own texture-to-buffer copy attaches the source as a colour target and
 * can't read depth, so this uses OpenGL directly.
 */
public final class CrosshairDepth {
    private static final int GL_PIXEL_PACK_BUFFER = 0x88EB;
    private static final int GL_DEPTH_COMPONENT = 0x1902;
    private static final int GL_FLOAT = 0x1406;
    private static final int GL_STREAM_READ = 0x88E1;
    /** Over the sky, how long it takes to drift from the last surface to the screen surface. */
    private static final float SKY_RELAX_SECONDS = 1.5f;

    private static int buffer;
    private static long fence;
    private static float pendingM22;
    private static float pendingM32;
    private static boolean pendingFar;
    private static float pendingFarM22;
    private static float pendingFarM32;
    private static final FloatBuffer RESULT = BufferUtils.createFloatBuffer(2);
    /** A second depth buffer with distant terrain (Voxy's, with a shader pack), set for the current frame. */
    private static int farTexture;
    private static int farWidth;
    private static int farHeight;
    private static float farM22;
    private static float farM32;
    /** 1 / distance of the last surface seen under the crosshair; NaN until one has been seen. */
    private static float surfaceInverseDistance = Float.NaN;
    private static boolean overSky = true;
    private static int debugCounter;

    private CrosshairDepth() {}

    /** Also look at this depth texture (drawn with {@code projection}) when the world's depth shows sky. */
    public static void setFarDepthSource(int texture, int width, int height, Matrix4fc projection) {
        farTexture = texture;
        farWidth = width;
        farHeight = height;
        farM22 = projection.m22();
        farM32 = projection.m32();
    }

    /** Copies the depth under the crosshair (the centre pixel) of {@code target}, rendered with {@code projection}. */
    public static void request(RenderTarget target, Matrix4fc projection) {
        poll();
        int far = farTexture;
        farTexture = 0;
        if (fence != 0L || !(target.getDepthTexture() instanceof GlTexture depthTexture)) {
            return;
        }
        if (buffer == 0) {
            buffer = GL45.glCreateBuffers();
            GL45.glNamedBufferData(buffer, 8L, GL_STREAM_READ);
        }
        // Perspective projection: clip z = m22 * z + m32 and w = -z, so z / w = (m22 * z + m32) / -z.
        pendingM22 = projection.m22();
        pendingM32 = projection.m32();
        GlStateManager._glBindBuffer(GL_PIXEL_PACK_BUFFER, buffer);
        GL45.glGetTextureSubImage(depthTexture.glId(), 0, target.width / 2, target.height / 2, 0, 1, 1, 1,
            GL_DEPTH_COMPONENT, GL_FLOAT, 4, 0L);
        pendingFar = far != 0;
        if (pendingFar) {
            pendingFarM22 = farM22;
            pendingFarM32 = farM32;
            GL45.glGetTextureSubImage(far, 0, farWidth / 2, farHeight / 2, 0, 1, 1, 1, GL_DEPTH_COMPONENT, GL_FLOAT, 4, 4L);
        }
        GlStateManager._glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    /** Reads the last copy once the GPU has finished it. */
    private static void poll() {
        if (fence == 0L) {
            return;
        }
        int status = GL32.glClientWaitSync(fence, 0, 0L);
        if (status != GL32.GL_ALREADY_SIGNALED && status != GL32.GL_CONDITION_SATISFIED) {
            return;
        }
        GL32.glDeleteSync(fence);
        fence = 0L;
        RESULT.clear();
        GL45.glGetNamedBufferSubData(buffer, 0L, RESULT);
        float depth = RESULT.get(0);
        float distance = distance(depth, pendingM22, pendingM32);
        float farDepth = pendingFar ? RESULT.get(1) : Float.NaN;
        if (Float.isNaN(distance) && pendingFar) {
            distance = distance(farDepth, pendingFarM22, pendingFarM32);
        }
        overSky = Float.isNaN(distance);
        if (!overSky) {
            surfaceInverseDistance = 1f / Math.max(0.3f, distance);
        }
        if (StereoDebug.ENABLED && ++debugCounter % 120 == 0) {
            StereoDebug.log("crosshair depth=" + depth + " far=" + farDepth + " distance=" + distance + " sky=" + overSky);
        }
    }

    /** Distance for a depth-buffer value, or NaN for a cleared pixel (the sky). */
    private static float distance(float depth, float m22, float m32) {
        if (depth == 0f || depth == 1f || Float.isNaN(depth)) {
            return Float.NaN;
        }
        // Vanilla uses reversed depth (far = 0) straight from clip space. Iris (with a shader pack) and Voxy use a
        // conventional projection (m22 near -1) whose clip z runs from -1 to 1 and is stored as depth * 2 - 1.
        float clipDepth = m22 < -0.5f ? depth * 2f - 1f : depth;
        float distance = m32 / (clipDepth + m22);
        return distance > 0f && distance < 100_000f ? distance : Float.NaN;
    }

    /**
     * The 1 / distance the crosshair should move towards. Over the sky it holds the last surface and slowly drifts to
     * the screen surface, instead of jumping to infinity.
     */
    public static float targetInverseDistance(float seconds, float inverseFocus) {
        if (Float.isNaN(surfaceInverseDistance)) {
            surfaceInverseDistance = inverseFocus;
        }
        if (overSky) {
            surfaceInverseDistance += (inverseFocus - surfaceInverseDistance)
                * (1f - (float) Math.exp(-seconds / SKY_RELAX_SECONDS));
        }
        return surfaceInverseDistance;
    }
}
