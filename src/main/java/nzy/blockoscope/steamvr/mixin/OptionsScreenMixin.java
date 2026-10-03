package nzy.blockoscope.steamvr.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import nzy.blockoscope.steamvr.StereoConfig;
import nzy.blockoscope.steamvr.StereoConfigScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A "3D: Auto / On / Off" button in the top left corner of Minecraft's Options screen (from the title screen and the
 * pause menu), like Vivecraft's VR switch, so 3D can be set without Mod Menu: someone playing a shared mod pack on a
 * monitor can see at a glance that it's off, and turn it off for good if they run SteamVR for something else.
 */
@Mixin(value = OptionsScreen.class, remap = false)
public abstract class OptionsScreenMixin extends Screen {
    protected OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void blockoscopeSteamVr$addModeButton(CallbackInfo ci) {
        addRenderableWidget(Button.builder(blockoscopeSteamVr$label(), button -> {
                StereoConfig.Mode[] modes = StereoConfig.Mode.values();
                StereoConfig.setMode(modes[(StereoConfig.mode().ordinal() + 1) % modes.length]);
                StereoConfig.save();
                button.setMessage(blockoscopeSteamVr$label());
            })
            .bounds(5, 5, 90, 20)
            .tooltip(Tooltip.create(Component.literal(
                "Blockoscope SteamVR. Auto: 3D in SteamVR while it runs, normal 2D otherwise. On: always 3D. Off: never.")))
            .build());
    }

    private static Component blockoscopeSteamVr$label() {
        return Component.literal("3D: " + StereoConfigScreen.modeName(StereoConfig.mode()));
    }
}
