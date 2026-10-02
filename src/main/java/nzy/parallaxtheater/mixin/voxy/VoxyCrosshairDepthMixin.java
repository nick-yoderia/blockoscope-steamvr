package nzy.parallaxtheater.mixin.voxy;

import me.cortex.voxy.client.core.AbstractRenderPipeline;
import me.cortex.voxy.client.core.IrisVoxyRenderPipeline;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.rendering.Viewport;
import nzy.parallaxtheater.CrosshairDepth;
import nzy.parallaxtheater.StereoRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Without a shader pack Voxy writes its distant terrain into the world's depth buffer, which the crosshair reads. With
 * one it keeps that depth to itself, so the crosshair also reads Voxy's depth buffer when the world's shows sky.
 */
@Mixin(value = VoxyRenderSystem.class, remap = false)
public abstract class VoxyCrosshairDepthMixin {
    @Shadow
    @Final
    private AbstractRenderPipeline pipeline;

    @Inject(method = "renderOpaque", at = @At("RETURN"))
    private void parallaxTheater$voxyDepth(Viewport<?> viewport, int sourceDepthTexture, int sourceColourTexture,
                                           CallbackInfo ci) {
        if (viewport != null && StereoRenderer.isFirstEye() && pipeline instanceof IrisVoxyRenderPipeline
            && pipeline.fb.getDepthTex() != null) {
            CrosshairDepth.setFarDepthSource(pipeline.fb.getDepthTex().id, viewport.width, viewport.height, viewport.projection);
        }
    }
}
