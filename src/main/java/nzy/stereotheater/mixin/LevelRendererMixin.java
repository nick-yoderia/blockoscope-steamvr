package nzy.stereotheater.mixin;

import java.util.List;
import net.minecraft.client.renderer.LevelRenderer;
import nzy.stereotheater.StereoRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LevelRenderer.submitFeatures() empties the extracted entity, block entity and block breaking lists once they are
 * submitted, and drains the debug gizmos. With two eyes per frame both must still be there for the second eye.
 */
@Mixin(value = LevelRenderer.class, remap = false)
public abstract class LevelRendererMixin {
    @Redirect(method = "submitFeatures", at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V"))
    private void stereoTheater$keepForSecondEye(List<?> list) {
        if (!StereoRenderer.isFirstEye()) {
            list.clear();
        }
    }

    // Gizmos are collected in the first eye and reused by the second (draining again would leave it none).
    @Inject(method = "finalizeGizmoCollection", at = @At("HEAD"), cancellable = true)
    private void stereoTheater$reuseGizmos(CallbackInfo ci) {
        if (StereoRenderer.eye() == StereoRenderer.RIGHT) {
            ci.cancel();
        }
    }
}
