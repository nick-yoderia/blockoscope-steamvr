package nzy.parallaxscreen.mixin.voxy;

import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.rendering.ViewportSelector;
import me.cortex.voxy.client.core.util.IrisUtil;
import nzy.parallaxscreen.StereoRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Voxy keeps occlusion data from the previous frame in its viewport (a depth pyramid and frame counter). With one
 * viewport for both eyes, each eye would cull against the other eye's view. The right eye gets a viewport of its own,
 * the same way Voxy gives each Vivecraft eye one.
 */
@Mixin(value = ViewportSelector.class, remap = false)
public abstract class VoxyViewportSelectorMixin<T extends Viewport<?>> {
    @Unique
    private static final Object STEREO_THEATER_RIGHT_EYE = new Object();

    @Shadow
    private T getOrCreate(Object holder) {
        throw new AssertionError();
    }

    @Inject(method = "getViewport", at = @At("HEAD"), cancellable = true)
    private void parallaxScreen$rightEyeViewport(CallbackInfoReturnable<T> cir) {
        if (StereoRenderer.eye() == StereoRenderer.RIGHT && !IrisUtil.irisShadowActive()) {
            cir.setReturnValue(getOrCreate(STEREO_THEATER_RIGHT_EYE));
        }
    }
}
