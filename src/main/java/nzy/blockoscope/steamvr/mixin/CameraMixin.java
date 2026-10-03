package nzy.blockoscope.steamvr.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import nzy.blockoscope.steamvr.StereoRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "FOV fits screen": the camera's field of view becomes what the SteamVR screen covers (StereoRenderer.matchedFov)
 * instead of the FOV setting. Scaled at the end of calculateFov, so sprinting, spyglass, water and dying still change it
 * the way they change vanilla's. Culling uses the larger of this and the setting, so nothing is culled too early.
 */
@Mixin(value = Camera.class, remap = false)
public abstract class CameraMixin {
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    private boolean isPanoramicMode;

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void blockoscopeSteamVr$fovFollowsScreen(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (!isPanoramicMode && StereoRenderer.fovFollowsScreen()) {
            int setting = minecraft.options.fov().get();
            if (setting > 0) {
                cir.setReturnValue(cir.getReturnValueF() * StereoRenderer.matchedFov() / setting);
            }
        }
    }
}
