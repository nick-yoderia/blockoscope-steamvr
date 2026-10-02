package nzy.stereotheater.mixin;

import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import nzy.stereotheater.StereoRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The GUI is drawn once per eye from the same extracted state, so the state is kept after the first eye. Scissor
 * rectangles (scrolling lists and the like) are worked out in window pixels and are squeezed to the eye target.
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
        float scale = StereoRenderer.eyeScaleX();
        if (scale != 1f) {
            int left = (int) Math.floor(x * scale);
            int right = (int) Math.ceil((x + width) * scale);
            x = left;
            width = Math.max(0, right - left);
        }
        pass.enableScissor(x, y, width, height);
    }
}
