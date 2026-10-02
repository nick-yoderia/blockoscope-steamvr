package nzy.stereotheater;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * In-game settings (Mod Menu > Stereo Theater), built with Cloth Config. Everything is read every frame, so saving
 * applies immediately. Tooltips and slider labels stay short so they never cover or scroll over the values.
 */
public final class StereoConfigScreen {
    private static final int FOCUS_MAX_METRES = 64;

    private StereoConfigScreen() {}

    public static Screen create(Screen parent) {
        StereoConfig.load();
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("Stereo Theater"));
        ConfigEntryBuilder entries = builder.entryBuilder();

        // --- 3D ---
        ConfigCategory depth = builder.getOrCreateCategory(Component.literal("3D"));
        depth.addEntry(entries.startTextDescription(Component.literal(
            "Nearer than the focus distance pops out; farther sits behind. F9 toggles 3D.")).build());

        depth.addEntry(entries.startBooleanToggle(Component.literal("3D"), StereoConfig.enabled())
            .setDefaultValue(true)
            .setYesNoTextSupplier(on -> Component.literal(on ? "On" : "Off (2D)"))
            .setTooltip(Component.literal("Half side-by-side."))
            .setSaveConsumer(StereoConfig::setEnabled)
            .build());

        depth.addEntry(entries.startIntSlider(Component.literal("Depth strength"), StereoConfig.depthPercent(), 0, 300)
            .setDefaultValue(100)
            .setTextGetter(value -> Component.literal(value == 0 ? "Flat" : value + "%"))
            .setTooltip(Component.literal("100% = natural eye spacing."))
            .setSaveConsumer(StereoConfig::setDepthPercent)
            .build());

        depth.addEntry(entries.startIntSlider(Component.literal("Focus distance"),
                Math.round(StereoConfig.focusDistance()), 0, FOCUS_MAX_METRES)
            .setDefaultValue(4)
            .setTextGetter(value -> Component.literal(value == 0 ? "Infinity" : value + " m"))
            .setTooltip(Component.literal("Distance shown on the screen."))
            .setSaveConsumer(value -> StereoConfig.setFocusDistance(value))
            .build());

        depth.addEntry(entries.startBooleanToggle(Component.literal("Swap eyes"), StereoConfig.swapEyes())
            .setDefaultValue(false)
            .setTooltip(Component.literal("Use if depth looks inside-out."))
            .setSaveConsumer(StereoConfig::setSwapEyes)
            .build());

        // --- HUD ---
        ConfigCategory hud = builder.getOrCreateCategory(Component.literal("HUD & Hand"));
        hud.addEntry(entries.startIntSlider(Component.literal("HUD distance"),
                Math.round(StereoConfig.hudDistance() * 100), 0, 1000)
            .setDefaultValue(135)
            .setTextGetter(value -> Component.literal(value == 0 ? "Screen" : String.format("%.2f m", value / 100f)))
            .setTooltip(Component.literal("Also moves menus. Screen = no depth."))
            .setSaveConsumer(value -> StereoConfig.setHudDistance(value / 100f))
            .build());

        hud.addEntry(entries.startIntSlider(Component.literal("Hand depth"), StereoConfig.handDepthPercent(), 0, 200)
            .setDefaultValue(100)
            .setTextGetter(value -> Component.literal(value == 0 ? "Screen" : value + "%"))
            .setTooltip(Component.literal("Your hand and held item."))
            .setSaveConsumer(StereoConfig::setHandDepthPercent)
            .build());

        // --- Cursor ---
        ConfigCategory cursor = builder.getOrCreateCategory(Component.literal("Cursor"));
        cursor.addEntry(entries.startBooleanToggle(Component.literal("Draw cursor in both eyes"), StereoConfig.hideCursor())
            .setDefaultValue(true)
            .setTooltip(Component.literal("Hides the Windows cursor."))
            .setSaveConsumer(StereoConfig::setHideCursor)
            .build());
        cursor.addEntry(entries.startBooleanToggle(Component.literal("Keep cursor in game window"), StereoConfig.confineCursor())
            .setDefaultValue(true)
            .setTooltip(Component.literal("Alt+Tab releases it."))
            .setSaveConsumer(StereoConfig::setConfineCursor)
            .build());

        builder.setSavingRunnable(StereoConfig::save);
        return builder.build();
    }
}
