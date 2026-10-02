package nzy.stereotheater.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The sky renderer keeps the main render target it was created with. Created while one eye was rendering, it drew
 * the sky into that eye's target for both eyes, leaving the other eye with only the fog colour (a washed-out sky).
 * It now always draws into the current main target.
 */
@Mixin(value = SkyRenderer.class, remap = false)
public abstract class SkyRendererMixin {
    @Redirect(method = "*", at = @At(value = "FIELD", opcode = 180 /* GETFIELD */,
        target = "Lnet/minecraft/client/renderer/SkyRenderer;renderTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget stereoTheater$currentTarget(SkyRenderer self) {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget();
    }
}
