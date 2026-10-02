package nzy.stereotheater.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import nzy.stereotheater.StereoRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Each frame is extracted once and then rendered once per eye. */
@Mixin(value = Minecraft.class, remap = false)
public abstract class MinecraftMixin {
    @Redirect(method = "renderFrame", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private void stereoTheater$renderEyes(GameRenderer gameRenderer, DeltaTracker deltaTracker, boolean renderLevel) {
        StereoRenderer.render(gameRenderer, deltaTracker, renderLevel);
    }
}
