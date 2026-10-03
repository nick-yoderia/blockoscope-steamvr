package nzy.blockoscope.steamvr.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import nzy.blockoscope.steamvr.ShiftedVertexConsumer;
import nzy.blockoscope.steamvr.StereoRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The GUI is drawn once per eye from the same extracted state. It is prepared (text laid out into glyphs, items and
 * picture-in-picture elements turned into blits, everything meshed and uploaded) in the first eye only, and the
 * second eye draws the same meshes: preparing again added every glyph and item blit to the kept state a second time,
 * so the second eye drew them twice and the eyes no longer matched. Scissor rectangles (scrolling lists and the like)
 * are worked out in window pixels; they are squeezed to the eye's area and shifted with the GUI.
 *
 * The GUI is drawn either into each eye target during that eye's render (when it blurs the world behind it, as menus
 * do) or into the window after the eyes are packed (the in-game HUD); see StereoRenderer.drawGuiOverWindow.
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
    private void blockoscopeSteamVr$keepStateForSecondEye(GuiRenderState state) {
        if (!StereoRenderer.isFirstGuiPass()) {
            state.reset();
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/render/GuiRenderer;prepare()V"))
    private void blockoscopeSteamVr$prepareOnce(GuiRenderer self) {
        if (!StereoRenderer.isSecondGuiPass()) {
            prepare();
        }
    }

    // --- Edge elements (see StereoRenderer.edgeShiftPixels): moved inwards in both eyes while their meshes are built.

    /** Screen zone of each text, by its pose (each text gets its own copy, and its glyphs keep that copy). */
    @Unique
    private final Map<Object, Integer> blockoscopeSteamVr$textZones = new IdentityHashMap<>();
    @Unique
    private final ShiftedVertexConsumer blockoscopeSteamVr$shifted = new ShiftedVertexConsumer();
    @Unique
    private ScreenRectangle blockoscopeSteamVr$scissorRectangle;

    /** Glyphs have no bounds of their own, so a text's zone is worked out from the whole text while it is laid out. */
    @Redirect(method = "prepareText", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;forEachText(Ljava/util/function/Consumer;)V"))
    private void blockoscopeSteamVr$noteTextZones(GuiRenderState state, Consumer<GuiTextRenderState> layout) {
        blockoscopeSteamVr$textZones.clear();
        if (!StereoRenderer.isGuiPass()) {
            state.forEachText(layout);
            return;
        }
        state.forEachText(text -> {
            blockoscopeSteamVr$textZones.merge(text.pose, StereoRenderer.guiZone(text.bounds()),
                (a, b) -> a.equals(b) ? a : 0);
            layout.accept(text);
        });
    }

    @Redirect(method = "addElementToMesh", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/state/gui/GuiElementRenderState;buildVertices(Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
    private void blockoscopeSteamVr$shiftEdgeElement(GuiElementRenderState element, VertexConsumer consumer) {
        float shift = 0f;
        if (StereoRenderer.isGuiPass()) {
            int zone = element instanceof GlyphRenderState glyph
                ? blockoscopeSteamVr$textZones.getOrDefault(glyph.pose(), 0) : StereoRenderer.guiZone(element.bounds());
            shift = StereoRenderer.edgeShiftGui(zone);
        }
        element.buildVertices(shift == 0f ? consumer : blockoscopeSteamVr$shifted.set(consumer, shift));
    }

    @Inject(method = "enableScissor", at = @At("HEAD"))
    private void blockoscopeSteamVr$noteScissor(ScreenRectangle rectangle, RenderPass pass, CallbackInfo ci) {
        blockoscopeSteamVr$scissorRectangle = rectangle;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/StagedVertexBuffer;upload()V"))
    private void blockoscopeSteamVr$uploadOnce(StagedVertexBuffer buffer) {
        if (!StereoRenderer.isSecondGuiPass()) {
            buffer.upload();
        }
    }

    /** The uploaded meshes, the list of draws and the blur split stay until the second eye has drawn them. */
    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/StagedVertexBuffer;endDraw()V"))
    private void blockoscopeSteamVr$keepMeshes(StagedVertexBuffer buffer) {
        if (!StereoRenderer.isFirstGuiPass()) {
            buffer.endDraw();
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/StagedVertexBuffer;endFrame()V"))
    private void blockoscopeSteamVr$keepMeshBuffers(StagedVertexBuffer buffer) {
        if (!StereoRenderer.isFirstGuiPass()) {
            buffer.endFrame();
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V"))
    private void blockoscopeSteamVr$keepDraws(List<?> draws) {
        if (!StereoRenderer.isFirstGuiPass()) {
            draws.clear();
        }
    }

    @Redirect(method = "render", at = @At(value = "FIELD", opcode = 181 /* PUTFIELD */,
        target = "Lnet/minecraft/client/gui/render/GuiRenderer;firstDrawIndexAfterBlur:I"))
    private void blockoscopeSteamVr$keepBlurSplit(GuiRenderer self, int value) {
        if (!StereoRenderer.isFirstGuiPass()) {
            firstDrawIndexAfterBlur = value;
        }
    }

    @Inject(method = "clearUnusedOversizedItemRenderers", at = @At("HEAD"), cancellable = true)
    private void blockoscopeSteamVr$keepItemRenderers(CallbackInfo ci) {
        if (StereoRenderer.isFirstGuiPass()) {
            ci.cancel();
        }
    }

    @Redirect(method = "enableScissor", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/RenderPass;enableScissor(IIII)V"))
    private void blockoscopeSteamVr$squeezeScissor(RenderPass pass, int x, int y, int width, int height) {
        if (StereoRenderer.isGuiPass()) {
            // Squeeze to the eye's half and follow the GUI's sideways shift for its depth.
            float scale = StereoRenderer.guiScaleX();
            float origin = StereoRenderer.guiAreaLeft();
            float shift = StereoRenderer.guiOffsetPixels()
                + StereoRenderer.edgeShiftPixels(StereoRenderer.guiZone(blockoscopeSteamVr$scissorRectangle));
            int left = (int) Math.floor(origin + x * scale + shift);
            int right = (int) Math.ceil(origin + (x + width) * scale + shift);
            // Rounding and the shift can push the edge past the eye's area, which would draw into the other eye
            // (or be rejected by the render pass).
            int areaLeft = StereoRenderer.guiAreaLeft();
            int areaRight = areaLeft + StereoRenderer.guiAreaWidth();
            x = Math.min(Math.max(areaLeft, left), areaRight);
            width = Math.max(0, Math.min(right, areaRight) - x);
            float scaleY = StereoRenderer.guiScaleY();
            int bottom = (int) Math.floor(y * scaleY);
            int top = (int) Math.ceil((y + height) * scaleY);
            int areaHeight = StereoRenderer.guiAreaHeight();
            y = Math.min(Math.max(0, bottom), areaHeight);
            height = Math.max(0, Math.min(top, areaHeight) - y);
        }
        pass.enableScissor(x, y, width, height);
    }

    /** Drawing into the window, unclipped elements are still kept inside their eye's half. */
    @Redirect(method = "executeDraw", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/RenderPass;disableScissor()V"))
    private void blockoscopeSteamVr$keepInHalf(RenderPass pass) {
        if (StereoRenderer.guiDrawsIntoWindow()) {
            pass.enableScissor(StereoRenderer.guiAreaLeft(), 0, StereoRenderer.guiAreaWidth(), StereoRenderer.guiAreaHeight());
        } else {
            pass.disableScissor();
        }
    }

    @Unique
    private ProjectionMatrixBuffer blockoscopeSteamVr$crosshairBuffer;
    @Unique
    private GpuBufferSlice blockoscopeSteamVr$guiSlice;
    @Unique
    private GpuBufferSlice blockoscopeSteamVr$crosshairSlice;
    @Unique
    private boolean blockoscopeSteamVr$crosshairBound;

    @Redirect(method = "draw", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lnet/minecraft/client/renderer/Projection;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private GpuBufferSlice blockoscopeSteamVr$guiDepth(ProjectionMatrixBuffer buffer, Projection projection) {
        blockoscopeSteamVr$guiSlice = null;
        if (!StereoRenderer.isGuiPass()) {
            return buffer.getBuffer(projection);
        }
        Matrix4f matrix = projection.getMatrix(new Matrix4f());
        if (blockoscopeSteamVr$crosshairBuffer == null) {
            blockoscopeSteamVr$crosshairBuffer = new ProjectionMatrixBuffer("stereo crosshair");
        }
        blockoscopeSteamVr$crosshairSlice = blockoscopeSteamVr$crosshairBuffer.getBuffer(StereoRenderer.eyeCrosshairProjection(matrix));
        blockoscopeSteamVr$guiSlice = buffer.getBuffer(StereoRenderer.eyeGuiProjection(matrix));
        return blockoscopeSteamVr$guiSlice;
    }

    /** The crosshair is drawn with its own pipeline; it gets a projection that puts it at the depth of its target. */
    @Redirect(method = "executeDraw", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
    private void blockoscopeSteamVr$crosshairDepth(RenderPass pass, RenderPipeline pipeline) {
        if (blockoscopeSteamVr$guiSlice != null) {
            boolean crosshair = pipeline == RenderPipelines.CROSSHAIR;
            if (crosshair != blockoscopeSteamVr$crosshairBound) {
                pass.setUniform("Projection", crosshair ? blockoscopeSteamVr$crosshairSlice : blockoscopeSteamVr$guiSlice);
                blockoscopeSteamVr$crosshairBound = crosshair;
            }
        }
        pass.setPipeline(pipeline);
    }

    /** Each render pass starts with the GUI projection bound. */
    @Inject(method = "executeDrawRange", at = @At("HEAD"))
    private void blockoscopeSteamVr$resetCrosshairBinding(CallbackInfo ci) {
        blockoscopeSteamVr$crosshairBound = false;
    }
}
