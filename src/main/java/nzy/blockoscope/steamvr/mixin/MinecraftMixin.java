package nzy.blockoscope.steamvr.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import nzy.blockoscope.steamvr.StereoRenderer;
import nzy.blockoscope.steamvr.VrScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Each frame is extracted once and then rendered once per eye. While the SteamVR screen is up the headset is the
 * display, so the monitor's VSync is left out of the window's present mode (it would pace the game to the monitor,
 * e.g. 60 Hz, below the headset's rate); the surface is reconfigured when the screen comes or goes.
 */
@Mixin(value = Minecraft.class, remap = false)
public abstract class MinecraftMixin {
    @Shadow
    private boolean windowSurfaceNeedsReconfiguring;

    @Unique
    private boolean blockoscopeSteamVr$screenWasActive;

    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void blockoscopeSteamVr$reconfigureForScreen(boolean advanceGameTime, CallbackInfo ci) {
        boolean active = VrScreen.active();
        if (active != blockoscopeSteamVr$screenWasActive) {
            blockoscopeSteamVr$screenWasActive = active;
            windowSurfaceNeedsReconfiguring = true;
        }
    }

    @ModifyArg(method = "renderFrame", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/GpuSurface$PresentMode;getSupportedVsyncMode(Ljava/util/Collection;Z)Lcom/mojang/blaze3d/systems/GpuSurface$PresentMode;"),
        index = 1)
    private boolean blockoscopeSteamVr$noVsyncForScreen(boolean vsync) {
        return vsync && !VrScreen.active();
    }

    @Redirect(method = "renderFrame", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private void blockoscopeSteamVr$renderEyes(GameRenderer gameRenderer, DeltaTracker deltaTracker, boolean renderLevel) {
        StereoRenderer.render(gameRenderer, deltaTracker, renderLevel);
    }
}
