package nzy.parallaxscreen.mixin;

import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Tells whether this frame's GUI blurs the world behind it (menus do; the in-game HUD doesn't). */
@Mixin(value = GuiRenderState.class, remap = false)
public interface GuiRenderStateAccessor {
    @Accessor("firstStratumAfterBlur")
    int parallaxScreen$firstStratumAfterBlur();
}
