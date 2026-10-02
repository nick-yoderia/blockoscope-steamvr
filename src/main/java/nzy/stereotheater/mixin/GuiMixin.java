package nzy.stereotheater.mixin;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import nzy.stereotheater.CursorControl;
import nzy.stereotheater.SoftwareCursor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Adds the software cursor as the last thing in the GUI, so it is drawn in both eyes. */
@Mixin(value = Gui.class, remap = false)
public abstract class GuiMixin {
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;applyCursor(Lcom/mojang/blaze3d/platform/Window;)V"))
    private void stereoTheater$drawCursor(GuiGraphicsExtractor graphics, Window window) {
        if (CursorControl.drawsCursor()) {
            Minecraft mc = Minecraft.getInstance();
            SoftwareCursor.draw(graphics, mc.mouseHandler.getScaledXPos(window), mc.mouseHandler.getScaledYPos(window));
        }
        graphics.applyCursor(window);
    }
}
