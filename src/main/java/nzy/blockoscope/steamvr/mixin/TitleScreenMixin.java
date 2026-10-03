package nzy.blockoscope.steamvr.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import nzy.blockoscope.steamvr.StereoConfig;
import nzy.blockoscope.steamvr.StereoConfigScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A "3D: Auto / On / Off" button on the title screen, right of Singleplayer, where Vivecraft puts its VR switch (one
 * row lower, so both fit if someone has both). The user wanted it on the main menu like Vivecraft rather than in a
 * settings screen: it is the first thing someone playing a shared mod pack sees.
 */
@Mixin(value = TitleScreen.class, remap = false)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
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
            .bounds(width / 2 + 104, height / 4 + 48, 64, 20)
            .tooltip(Tooltip.create(Component.literal("Blockoscope SteamVR. Auto: 3D only while you use a headset in "
                + "SteamVR, normal Minecraft otherwise. On: always 3D. Off: normal Minecraft.")))
            .build());
    }

    private static Component blockoscopeSteamVr$label() {
        return Component.literal("3D: " + StereoConfigScreen.modeName(StereoConfig.mode()));
    }
}
