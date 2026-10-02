package nzy.parallaxscreen.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets each eye render into its own target: the main render target is swapped for the duration of an eye. */
@Mixin(value = GameRenderer.class, remap = false)
public interface GameRendererAccessor {
    @Mutable
    @Accessor("mainRenderTarget")
    void parallaxScreen$setMainRenderTarget(RenderTarget target);

    /** For drawing the HUD into the window after the eyes are packed (see StereoRenderer.drawGuiOverWindow). */
    @Accessor("guiRenderer")
    GuiRenderer parallaxScreen$guiRenderer();

    /** GUI items are lit with the UI lightmap while the GUI renders. */
    @Accessor("useUiLightmap")
    void parallaxScreen$setUseUiLightmap(boolean value);
}
