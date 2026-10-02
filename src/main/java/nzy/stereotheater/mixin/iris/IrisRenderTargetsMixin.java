package nzy.stereotheater.mixin.iris;

import com.mojang.blaze3d.textures.GpuTexture;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iris only re-reads the main render target's depth texture when that target's "depth buffer version" changes. The
 * version is counted per render target and starts at 0 for each, so switching the main target to an eye target (or
 * back for 2D) can look unchanged, leaving the pipeline reading the wrong depth buffer: shader packs then lose
 * clouds, fog and anything else that reads depth. Here a different depth texture always counts as a change.
 */
@Mixin(value = RenderTargets.class, remap = false)
public abstract class IrisRenderTargetsMixin {
    @Shadow
    private GpuTexture currentDepthTexture;

    @Shadow
    private int cachedDepthBufferVersion;

    @Inject(method = "resizeIfNeeded", at = @At("HEAD"))
    private void stereoTheater$followDepthTexture(int depthBufferVersion, GpuTexture depthTexture, int width, int height,
                                                  DepthBufferFormat depthFormat, PackDirectives directives,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (depthTexture != currentDepthTexture) {
            cachedDepthBufferVersion = depthBufferVersion + 1;
        }
    }
}
