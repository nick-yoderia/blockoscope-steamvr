package nzy.parallaxscreen.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import nzy.parallaxscreen.VrScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla throttles the frame rate when it thinks nobody is watching: 30 FPS after a minute without keyboard or mouse
 * input (10 after ten minutes) and 10 FPS while the window is minimised. With the SteamVR screen up someone may well be
 * watching without touching anything, and the window may be minimised on purpose, so no throttling then (the frame
 * rate is paced to the headset instead, see VrScreen.pace).
 */
@Mixin(value = FramerateLimitTracker.class, remap = false)
public abstract class FramerateLimitTrackerMixin {
    @Inject(method = "getThrottleReason", at = @At("HEAD"), cancellable = true)
    private void parallaxScreen$noThrottleForScreen(CallbackInfoReturnable<FramerateLimitTracker.FramerateThrottleReason> cir) {
        if (VrScreen.active()) {
            cir.setReturnValue(FramerateLimitTracker.FramerateThrottleReason.NONE);
        }
    }
}
