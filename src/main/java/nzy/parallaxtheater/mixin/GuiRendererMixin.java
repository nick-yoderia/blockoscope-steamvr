package nzy.parallaxtheater.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import nzy.parallaxtheater.StereoRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * The GUI is drawn once per eye from the same extracted state. It is prepared (text laid out into glyphs, items and
 * picture-in-picture elements turned into blits, everything meshed and uploaded) in the first eye only, and the
 * second eye draws the same meshes: preparing again added every glyph and item blit to the kept state a second time,
 * so the second eye drew them twice and the eyes no longer matched. Scissor rectangles (scrolling lists and the like)
 * are worked out in window pixels; they are squeezed to the eye target and shifted with the GUI.
 */
@Mixin(value = GuiRenderer.class, remap = false)
public abstract class GuiRendererMixin {
    @Shadow
    private int firstDrawIndexAfterBlur;

    @Shadow
    private void prepare() {
        throw new AssertionError();
    }

    /** Kept for the second eye: it still needs the panorama state, and preparing once relies on nothing being reset. */
    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;reset()V"))
    private void parallaxTheater$keepStateForSecondEye(GuiRenderState state) {
        if (!StereoRenderer.isFirstEye()) {
            state.reset();
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/render/GuiRenderer;prepare()V"))
    private void parallaxTheater$prepareOnce(GuiRenderer self) {
        if (!StereoRenderer.isSecondEye()) {
            prepare();
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/StagedVertexBuffer;upload()V"))
    private void parallaxTheater$uploadOnce(StagedVertexBuffer buffer) {
        if (!StereoRenderer.isSecondEye()) {
            buffer.upload();
        }
    }

    /** The uploaded meshes, the list of draws and the blur split stay until the second eye has drawn them. */
    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/StagedVertexBuffer;endDraw()V"))
    private void parallaxTheater$keepMeshes(StagedVertexBuffer buffer) {
        if (!StereoRenderer.isFirstEye()) {
            buffer.endDraw();
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/StagedVertexBuffer;endFrame()V"))
    private void parallaxTheater$keepMeshBuffers(StagedVertexBuffer buffer) {
        if (!StereoRenderer.isFirstEye()) {
            buffer.endFrame();
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V"))
    private void parallaxTheater$keepDraws(List<?> draws) {
        if (!StereoRenderer.isFirstEye()) {
            draws.clear();
        }
    }

    @Redirect(method = "render", at = @At(value = "FIELD", opcode = 181 /* PUTFIELD */,
        target = "Lnet/minecraft/client/gui/render/GuiRenderer;firstDrawIndexAfterBlur:I"))
    private void parallaxTheater$keepBlurSplit(GuiRenderer self, int value) {
        if (!StereoRenderer.isFirstEye()) {
            firstDrawIndexAfterBlur = value;
        }
    }

    @Inject(method = "clearUnusedOversizedItemRenderers", at = @At("HEAD"), cancellable = true)
    private void parallaxTheater$keepItemRenderers(CallbackInfo ci) {
        if (StereoRenderer.isFirstEye()) {
            ci.cancel();
        }
    }

    @Redirect(method = "enableScissor", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/RenderPass;enableScissor(IIII)V"))
    private void parallaxTheater$squeezeScissor(RenderPass pass, int x, int y, int width, int height) {
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
    private ProjectionMatrixBuffer parallaxTheater$crosshairBuffer;
    @Unique
    private GpuBufferSlice parallaxTheater$guiSlice;
    @Unique
    private GpuBufferSlice parallaxTheater$crosshairSlice;
    @Unique
    private boolean parallaxTheater$crosshairBound;

    @Redirect(method = "draw", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lnet/minecraft/client/renderer/Projection;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private GpuBufferSlice parallaxTheater$guiDepth(ProjectionMatrixBuffer buffer, Projection projection) {
        parallaxTheater$guiSlice = null;
        if (!StereoRenderer.isRendering()) {
            return buffer.getBuffer(projection);
        }
        Matrix4f matrix = projection.getMatrix(new Matrix4f());
        if (parallaxTheater$crosshairBuffer == null) {
            parallaxTheater$crosshairBuffer = new ProjectionMatrixBuffer("stereo crosshair");
        }
        parallaxTheater$crosshairSlice = parallaxTheater$crosshairBuffer.getBuffer(StereoRenderer.eyeCrosshairProjection(matrix));
        parallaxTheater$guiSlice = buffer.getBuffer(StereoRenderer.eyeGuiProjection(matrix));
        return parallaxTheater$guiSlice;
    }

    /** The crosshair is drawn with its own pipeline; it gets a projection that puts it at the depth of its target. */
    @Redirect(method = "executeDraw", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
    private void parallaxTheater$crosshairDepth(RenderPass pass, RenderPipeline pipeline) {
        if (parallaxTheater$guiSlice != null) {
            boolean crosshair = pipeline == RenderPipelines.CROSSHAIR;
            if (crosshair != parallaxTheater$crosshairBound) {
                pass.setUniform("Projection", crosshair ? parallaxTheater$crosshairSlice : parallaxTheater$guiSlice);
                parallaxTheater$crosshairBound = crosshair;
            }
        }
        pass.setPipeline(pipeline);
    }

    /** Each render pass starts with the GUI projection bound. */
    @Inject(method = "executeDrawRange", at = @At("HEAD"))
    private void parallaxTheater$resetCrosshairBinding(CallbackInfo ci) {
        parallaxTheater$crosshairBound = false;
    }
}
