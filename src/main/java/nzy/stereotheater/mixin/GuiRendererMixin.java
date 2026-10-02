package nzy.stereotheater.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import nzy.stereotheater.StereoRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The GUI is drawn once per eye from the same extracted state, so the state is kept after the first eye. Scissor
 * rectangles (scrolling lists and the like) are worked out in window pixels; they are squeezed to the eye target
 * and shifted with the GUI.
 */
@Mixin(value = GuiRenderer.class, remap = false)
public abstract class GuiRendererMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;reset()V"))
    private void stereoTheater$keepStateForSecondEye(GuiRenderState state) {
        if (!StereoRenderer.isFirstEye()) {
            state.reset();
        }
    }

    @Inject(method = "clearUnusedOversizedItemRenderers", at = @At("HEAD"), cancellable = true)
    private void stereoTheater$keepItemRenderers(CallbackInfo ci) {
        if (StereoRenderer.isFirstEye()) {
            ci.cancel();
        }
    }

    @Redirect(method = "enableScissor", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/RenderPass;enableScissor(IIII)V"))
    private void stereoTheater$squeezeScissor(RenderPass pass, int x, int y, int width, int height) {
        if (StereoRenderer.isRendering()) {
            // Squeeze to the eye target and follow the GUI's sideways shift for the HUD distance.
            float scale = StereoRenderer.eyeScaleX();
            float shift = StereoRenderer.guiOffsetPixels();
            int left = (int) Math.floor(x * scale + shift);
            int right = (int) Math.ceil((x + width) * scale + shift);
            // Rounding and the shift can push the edge past the eye target, which the render pass rejects.
            x = Math.min(Math.max(0, left), StereoRenderer.eyeWidth());
            width = Math.max(0, Math.min(right, StereoRenderer.eyeWidth()) - x);
            float scaleY = StereoRenderer.eyeScaleY();
            int bottom = (int) Math.floor(y * scaleY);
            int top = (int) Math.ceil((y + height) * scaleY);
            y = Math.min(Math.max(0, bottom), StereoRenderer.eyeHeight());
            height = Math.max(0, Math.min(top, StereoRenderer.eyeHeight()) - y);
        }
        pass.enableScissor(x, y, width, height);
    }

    @Unique
    private ProjectionMatrixBuffer stereoTheater$crosshairBuffer;
    @Unique
    private GpuBufferSlice stereoTheater$guiSlice;
    @Unique
    private GpuBufferSlice stereoTheater$crosshairSlice;
    @Unique
    private boolean stereoTheater$crosshairBound;

    @Redirect(method = "draw", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lnet/minecraft/client/renderer/Projection;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private GpuBufferSlice stereoTheater$guiDepth(ProjectionMatrixBuffer buffer, Projection projection) {
        stereoTheater$guiSlice = null;
        if (!StereoRenderer.isRendering()) {
            return buffer.getBuffer(projection);
        }
        Matrix4f matrix = projection.getMatrix(new Matrix4f());
        if (stereoTheater$crosshairBuffer == null) {
            stereoTheater$crosshairBuffer = new ProjectionMatrixBuffer("stereo crosshair");
        }
        stereoTheater$crosshairSlice = stereoTheater$crosshairBuffer.getBuffer(StereoRenderer.eyeCrosshairProjection(matrix));
        stereoTheater$guiSlice = buffer.getBuffer(StereoRenderer.eyeGuiProjection(matrix));
        return stereoTheater$guiSlice;
    }

    /** The crosshair is drawn with its own pipeline; it gets a projection that puts it at the depth of its target. */
    @Redirect(method = "executeDraw", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
    private void stereoTheater$crosshairDepth(RenderPass pass, RenderPipeline pipeline) {
        if (stereoTheater$guiSlice != null) {
            boolean crosshair = pipeline == RenderPipelines.CROSSHAIR;
            if (crosshair != stereoTheater$crosshairBound) {
                pass.setUniform("Projection", crosshair ? stereoTheater$crosshairSlice : stereoTheater$guiSlice);
                stereoTheater$crosshairBound = crosshair;
            }
        }
        pass.setPipeline(pipeline);
    }

    /** Each render pass starts with the GUI projection bound. */
    @Inject(method = "executeDrawRange", at = @At("HEAD"))
    private void stereoTheater$resetCrosshairBinding(CallbackInfo ci) {
        stereoTheater$crosshairBound = false;
    }
}
