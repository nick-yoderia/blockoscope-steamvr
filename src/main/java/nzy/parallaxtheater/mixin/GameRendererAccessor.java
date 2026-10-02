package nzy.parallaxtheater.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets each eye render into its own target: the main render target is swapped for the duration of an eye. */
@Mixin(value = GameRenderer.class, remap = false)
public interface GameRendererAccessor {
    @Mutable
    @Accessor("mainRenderTarget")
    void parallaxTheater$setMainRenderTarget(RenderTarget target);
}
