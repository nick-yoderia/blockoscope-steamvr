package nzy.parallaxscreen;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Adds the settings button in Mod Menu. The screen needs Cloth Config; without it there is no button. */
public final class StereoModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        try {
            Class.forName("me.shedaniel.clothconfig2.api.ConfigBuilder");
        } catch (ClassNotFoundException e) {
            return parent -> null;
        }
        return StereoConfigScreen::create;
    }
}
