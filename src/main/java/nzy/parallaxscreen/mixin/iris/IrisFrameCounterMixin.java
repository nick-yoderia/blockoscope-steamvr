package nzy.parallaxscreen.mixin.iris;

import nzy.parallaxscreen.StereoRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Iris counts a frame every time GameRenderer.render runs; the right eye is the same frame, not a new one. */
@Mixin(targets = "net.irisshaders.iris.uniforms.SystemTimeUniforms$FrameCounter", remap = false)
public abstract class IrisFrameCounterMixin {
    @Inject(method = "beginFrame", at = @At("HEAD"), cancellable = true)
    private void parallaxScreen$oncePerFrame(CallbackInfo ci) {
        if (StereoRenderer.isSecondEye()) {
            ci.cancel();
        }
    }
}
