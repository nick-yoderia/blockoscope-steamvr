package nzy.stereotheater.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import nzy.stereotheater.ToggleKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sees every key press as an event, so even a very short tap of the toggle key is never missed. */
@Mixin(value = KeyboardHandler.class, remap = false)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void stereoTheater$toggleKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        ToggleKey.onKey(event.key(), action);
    }
}
