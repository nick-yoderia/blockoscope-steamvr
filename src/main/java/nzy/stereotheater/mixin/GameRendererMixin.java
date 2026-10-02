package nzy.stereotheater.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.fog.FogRenderer;
import nzy.stereotheater.StereoRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * GameRenderer.render() runs once per eye. It must not resize the (half-width) eye target back to the window
 * size, screen-size uniforms should describe the eye target, and the end-of-frame cleanup should only run after
 * the second eye.
 */
@Mixin(value = GameRenderer.class, remap = false)
public abstract class GameRendererMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GameRenderer;resize(II)V"))
    private void stereoTheater$keepEyeSize(GameRenderer self, int width, int height) {
        if (!StereoRenderer.isRendering()) {
            self.resize(width, height);
        }
    }

    @ModifyArg(method = "render", index = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GlobalSettingsUniform;update(IIDJLnet/minecraft/client/DeltaTracker;ILnet/minecraft/world/phys/Vec3;Z)V"))
    private int stereoTheater$eyeScreenWidth(int width) {
        return StereoRenderer.isRendering() ? StereoRenderer.eyeWidth() : width;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/render/GuiRenderer;endFrame()V"))
    private void stereoTheater$guiEndFrame(GuiRenderer renderer) {
        if (!StereoRenderer.isFirstEye()) {
            renderer.endFrame();
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/RenderBuffers;endFrame()V"))
    private void stereoTheater$buffersEndFrame(RenderBuffers buffers) {
        if (!StereoRenderer.isFirstEye()) {
            buffers.endFrame();
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/resource/CrossFrameResourcePool;endFrame()V"))
    private void stereoTheater$poolEndFrame(CrossFrameResourcePool pool) {
        if (!StereoRenderer.isFirstEye()) {
            pool.endFrame();
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void stereoTheater$fogEndFrame(FogRenderer fog) {
        if (!StereoRenderer.isFirstEye()) {
            fog.endFrame();
        }
    }
}
