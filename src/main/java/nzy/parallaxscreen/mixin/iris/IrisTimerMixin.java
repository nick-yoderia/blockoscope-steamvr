package nzy.parallaxscreen.mixin.iris;

import nzy.parallaxscreen.StereoRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps Iris's frame time and frame time counter per frame rather than per eye. */
@Mixin(targets = "net.irisshaders.iris.uniforms.SystemTimeUniforms$Timer", remap = false)
public abstract class IrisTimerMixin {
    @Inject(method = "beginFrame", at = @At("HEAD"), cancellable = true)
    private void parallaxScreen$oncePerFrame(long frameStartTime, CallbackInfo ci) {
        if (StereoRenderer.isSecondEye()) {
            ci.cancel();
        }
    }
}
