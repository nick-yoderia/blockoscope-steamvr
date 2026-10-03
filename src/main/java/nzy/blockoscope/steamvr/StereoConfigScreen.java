package nzy.blockoscope.steamvr;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * In-game settings (Mod Menu > Blockoscope SteamVR), built with Cloth Config. Everything is read every frame, so saving
 * applies immediately. Tooltips and slider labels stay short so they never cover or scroll over the values.
 */
public final class StereoConfigScreen {
    private static final int FOCUS_MAX_METRES = 64;

    /** The open settings screen whose screen entries are shown live, and how to read them (see {@link #updatePreview}). */
    private static Screen previewScreen;
    private static Runnable previewSource;

    private StereoConfigScreen() {}

    /**
     * Called every frame: while this settings screen is open, the SteamVR screen follows its size, distance, height,
     * curve and edge entries as they are dragged, unsaved. Once it closes (saved or not), the saved values apply again.
     */
    public static void updatePreview() {
        if (previewScreen == null) {
            return;
        }
        if (Minecraft.getInstance().gui.screen() == previewScreen) {
            previewSource.run();
        } else {
            previewScreen = null;
            previewSource = null;
            StereoConfig.endPreview();
        }
    }

    /** Short name of a 3D mode, for the settings and the Options screen's button. */
    public static String modeName(StereoConfig.Mode mode) {
        return switch (mode) {
            case AUTO -> "Auto";
            case ON -> "On";
            case OFF -> "Off";
        };
    }

    public static Screen create(Screen parent) {
        StereoConfig.load();
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("Blockoscope SteamVR"));
        ConfigEntryBuilder entries = builder.entryBuilder();

        // --- 3D ---
        ConfigCategory depth = builder.getOrCreateCategory(Component.literal("3D"));
        depth.addEntry(entries.startTextDescription(Component.literal(
            "Nearer than the focus distance pops out; farther sits behind. F9 toggles 3D.")).build());

        depth.addEntry(entries.startEnumSelector(Component.literal("3D"), StereoConfig.Mode.class, StereoConfig.mode())
            .setDefaultValue(StereoConfig.Mode.AUTO)
            .setEnumNameProvider(value -> Component.literal(modeName((StereoConfig.Mode) value)))
            .setTooltip(Component.literal("Auto = 3D while SteamVR runs, normal 2D otherwise."),
                Component.literal("On without SteamVR = half side-by-side in the window."))
            .setSaveConsumer(StereoConfig::setMode)
            .build());

        depth.addEntry(entries.startIntSlider(Component.literal("Render scale"), StereoConfig.renderScale(), 25, 200)
            .setDefaultValue(100)
            .setTextGetter(value -> Component.literal(value + "%"))
            .setTooltip(Component.literal("Lower for more FPS, higher for sharper."))
            .setSaveConsumer(StereoConfig::setRenderScale)
            .build());

        depth.addEntry(entries.startIntSlider(Component.literal("Depth strength"), StereoConfig.depthPercent(), 0, 300)
            .setDefaultValue(100)
            .setTextGetter(value -> Component.literal(value == 0 ? "Flat" : value + "%"))
            .setTooltip(Component.literal("100% = natural eye spacing."))
            .setSaveConsumer(StereoConfig::setDepthPercent)
            .build());

        depth.addEntry(entries.startIntSlider(Component.literal("Focus distance"),
                Math.round(StereoConfig.focusDistance()), 0, FOCUS_MAX_METRES)
            .setDefaultValue(10)
            .setTextGetter(value -> Component.literal(value == 0 ? "Infinity" : value + " m"))
            .setTooltip(Component.literal("Distance shown on the screen."))
            .setSaveConsumer(value -> StereoConfig.setFocusDistance(value))
            .build());

        depth.addEntry(entries.startBooleanToggle(Component.literal("Swap eyes"), StereoConfig.swapEyes())
            .setDefaultValue(false)
            .setTooltip(Component.literal("Use if depth looks inside-out."))
            .setSaveConsumer(StereoConfig::setSwapEyes)
            .build());

        // --- SteamVR screen ---
        ConfigCategory screen = builder.getOrCreateCategory(Component.literal("SteamVR screen"));
        screen.addEntry(entries.startTextDescription(Component.literal(
            "Shown in SteamVR while a headset is connected. F8 puts it in front of you again. Size, distance, height and curve show live while you drag; Cancel undoes them.")).build());

        screen.addEntry(entries.startBooleanToggle(Component.literal("SteamVR screen"), StereoConfig.steamVrScreen())
            .setDefaultValue(true)
            .setYesNoTextSupplier(on -> Component.literal(on ? "On" : "Off (window only)"))
            .setTooltip(Component.literal("Off = half side-by-side in the window."))
            .setSaveConsumer(StereoConfig::setSteamVrScreen)
            .build());

        // 9 = automatic; 10..64 = 640..4096 pixels.
        screen.addEntry(entries.startIntSlider(Component.literal("Eye resolution"),
                StereoConfig.eyeResolution() <= 0 ? 9 : StereoConfig.eyeResolution() / 64, 9, 64)
            .setDefaultValue(9)
            .setTextGetter(value -> Component.literal(value <= 9 ? "Auto (" + VrScreen.eyeResolution() + " px)" : value * 64 + " px wide"))
            .setTooltip(Component.literal("Per eye. Auto = 1.5x what the headset shows,"),
                Component.literal("rounded so the HUD stays sharp."))
            .setSaveConsumer(value -> StereoConfig.setEyeResolution(value <= 9 ? 0 : value * 64))
            .build());

        screen.addEntry(entries.startBooleanToggle(Component.literal("Sync to headset"), StereoConfig.syncToHeadset())
            .setDefaultValue(true)
            .setYesNoTextSupplier(on -> Component.literal(on ? "On" : "Off (free-running)"))
            .setTooltip(Component.literal("One frame per headset refresh: smoother turning, less GPU load."))
            .setSaveConsumer(StereoConfig::setSyncToHeadset)
            .build());

        EnumListEntry<StereoConfig.ScreenSize> sizeEntry = entries.startEnumSelector(Component.literal("Screen size"), StereoConfig.ScreenSize.class,
                StereoConfig.screenSize())
            .setDefaultValue(StereoConfig.ScreenSize.CUSTOM)
            .setEnumNameProvider(value -> Component.literal(switch ((StereoConfig.ScreenSize) value) {
                case CUSTOM -> "Custom";
                case TRUE_SCALE -> "True scale (screen fits FOV)";
                case MATCH_FOV -> "True scale (FOV fits screen)";
            }))
            .setTooltip(Component.literal("True scale = life-size world, no stretching at the edges."),
                Component.literal("Screen fits FOV: the screen grows to your FOV setting."),
                Component.literal("FOV fits screen: your FOV follows the screen width and distance."))
            .setSaveConsumer(StereoConfig::setScreenSize)
            .build();
        screen.addEntry(sizeEntry);

        BooleanListEntry floatingEntry = entries.startBooleanToggle(Component.literal("Floating edges"), StereoConfig.floatingWindow())
            .setDefaultValue(true)
            .setYesNoTextSupplier(on -> Component.literal(on ? "On" : "Off"))
            .setTooltip(Component.literal("Screen edges float at your held item's depth,"),
                Component.literal("so it isn't cut off by an edge behind it."))
            .setSaveConsumer(StereoConfig::setFloatingWindow)
            .build();
        screen.addEntry(floatingEntry);

        IntegerSliderEntry widthEntry = entries.startIntSlider(Component.literal("Screen width"),
                Math.round(StereoConfig.screenWidth() * 10), 5, 200)
            .setDefaultValue(26)
            .setTextGetter(value -> Component.literal(String.format("%.1f m", value / 10f)))
            .setTooltip(Component.literal("Custom and FOV fits screen."))
            .setSaveConsumer(value -> StereoConfig.setScreenWidth(value / 10f))
            .build();
        screen.addEntry(widthEntry);

        IntegerSliderEntry distanceEntry = entries.startIntSlider(Component.literal("Screen distance"),
                Math.round(StereoConfig.screenDistance() * 10), 5, 200)
            .setDefaultValue(20)
            .setTextGetter(value -> Component.literal(String.format("%.1f m", value / 10f)))
            .setTooltip(Component.literal("From where you sat at the last recenter (F8)."))
            .setSaveConsumer(value -> StereoConfig.setScreenDistance(value / 10f))
            .build();
        screen.addEntry(distanceEntry);

        IntegerSliderEntry heightEntry = entries.startIntSlider(Component.literal("Screen height"),
                Math.round(StereoConfig.screenHeight() * 10), -30, 30)
            .setDefaultValue(0)
            .setTextGetter(value -> Component.literal(value == 0 ? "Eye level" : String.format("%+.1f m", value / 10f)))
            .setTooltip(Component.literal("Above or below your eyes."))
            .setSaveConsumer(value -> StereoConfig.setScreenHeight(value / 10f))
            .build();
        screen.addEntry(heightEntry);

        IntegerSliderEntry curveEntry = entries.startIntSlider(Component.literal("Curve"), StereoConfig.screenCurvature(), 0, 100)
            .setDefaultValue(0)
            .setTextGetter(value -> Component.literal(value == 0 ? "Flat" : value + "%"))
            .setTooltip(Component.literal("Bends the screen around you."))
            .setSaveConsumer(StereoConfig::setScreenCurvature)
            .build();
        screen.addEntry(curveEntry);

        screen.addEntry(entries.startBooleanToggle(Component.literal("Window shows"), StereoConfig.previewBothEyes())
            .setDefaultValue(false)
            .setYesNoTextSupplier(on -> Component.literal(on ? "Both eyes" : "Left eye"))
            .setTooltip(Component.literal("What the monitor shows meanwhile."))
            .setSaveConsumer(StereoConfig::setPreviewBothEyes)
            .build());

        screen.addEntry(entries.startBooleanToggle(Component.literal("Flip picture"), StereoConfig.flipScreen())
            .setDefaultValue(false)
            .setTooltip(Component.literal("Use if the screen shows upside down."))
            .setSaveConsumer(StereoConfig::setFlipScreen)
            .build());

        // --- Comfort ---
        ConfigCategory comfort = builder.getOrCreateCategory(Component.literal("Comfort"));
        comfort.addEntry(entries.startTextDescription(Component.literal(
            "Camera motion that is hard to watch in 3D. Only applies while 3D is on.")).build());
        comfort.addEntry(entries.startBooleanToggle(Component.literal("Camera bobbing"), StereoConfig.cameraBobbing())
            .setDefaultValue(false)
            .setTooltip(Component.literal("Walking sway. The hand bobs either way."))
            .setSaveConsumer(StereoConfig::setCameraBobbing)
            .build());
        comfort.addEntry(entries.startBooleanToggle(Component.literal("Damage tilt"), StereoConfig.damageTilt())
            .setDefaultValue(false)
            .setTooltip(Component.literal("Camera roll when hurt or dying."))
            .setSaveConsumer(StereoConfig::setDamageTilt)
            .build());
        comfort.addEntry(entries.startIntSlider(Component.literal("Nausea & portal warp"), StereoConfig.warpPercent(), 0, 100)
            .setDefaultValue(40)
            .setTextGetter(value -> Component.literal(value == 0 ? "Off" : value + "%"))
            .setTooltip(Component.literal("% of vanilla's warp."))
            .setSaveConsumer(StereoConfig::setWarpPercent)
            .build());

        // --- HUD ---
        ConfigCategory hud = builder.getOrCreateCategory(Component.literal("HUD & Hand"));
        hud.addEntry(entries.startEnumSelector(Component.literal("HUD depth"), StereoConfig.HudDepth.class,
                StereoConfig.hudDepth())
            .setDefaultValue(StereoConfig.HudDepth.SCENE)
            .setEnumNameProvider(value -> Component.literal(switch ((StereoConfig.HudDepth) value) {
                case SCENE -> "On scene";
                case AIM -> "At crosshair";
                case FIXED -> "Fixed";
            }))
            .setTooltip(Component.literal("On scene = on what is behind the hotbar."))
            .setSaveConsumer(StereoConfig::setHudDepth)
            .build());

        hud.addEntry(entries.startIntSlider(Component.literal("Fixed HUD distance"),
                Math.round(StereoConfig.hudDistance() * 100), 0, 1000)
            .setDefaultValue(135)
            .setTextGetter(value -> Component.literal(value == 0 ? "Screen" : String.format("%.2f m", value / 100f)))
            .setTooltip(Component.literal("Used when HUD depth is Fixed."))
            .setSaveConsumer(value -> StereoConfig.setHudDistance(value / 100f))
            .build());

        hud.addEntry(entries.startIntSlider(Component.literal("Menu distance"),
                Math.round(StereoConfig.menuDistance() * 100), 0, 1000)
            .setDefaultValue(0)
            .setTextGetter(value -> Component.literal(value == 0 ? "Screen" : String.format("%.2f m", value / 100f)))
            .setTooltip(Component.literal("Menus and inventories. Screen = no depth."))
            .setSaveConsumer(value -> StereoConfig.setMenuDistance(value / 100f))
            .build());

        hud.addEntry(entries.startBooleanToggle(Component.literal("Crosshair depth"), StereoConfig.crosshairAtTarget())
            .setDefaultValue(true)
            .setYesNoTextSupplier(on -> Component.literal(on ? "At target" : "With HUD"))
            .setTooltip(Component.literal("At target = what you aim at, in reach."))
            .setSaveConsumer(StereoConfig::setCrosshairAtTarget)
            .build());

        // Half-metre steps, relative to the block reach.
        hud.addEntry(entries.startIntSlider(Component.literal("Crosshair rest"),
                Math.round(StereoConfig.crosshairRestOffset() * 2f), -8, 32)
            .setDefaultValue(0)
            .setTextGetter(value -> Component.literal(value == 0 ? "At reach"
                : String.format("Reach %s%.1f m", value > 0 ? "+" : "-", Math.abs(value) / 2f)))
            .setTooltip(Component.literal("Depth when nothing is in reach."))
            .setSaveConsumer(value -> StereoConfig.setCrosshairRestOffset(value / 2f))
            .build());

        hud.addEntry(entries.startIntSlider(Component.literal("Arm reach"), StereoConfig.handReach(), 0, 60)
            .setDefaultValue(30)
            .setTextGetter(value -> Component.literal(value == 0 ? "Vanilla" : "+" + value + " cm"))
            .setTooltip(Component.literal("Longer arm: hand further out (3D only)."))
            .setSaveConsumer(StereoConfig::setHandReach)
            .build());

        hud.addEntry(entries.startIntSlider(Component.literal("Hand raise"), StereoConfig.handRaise(), 0, 40)
            .setDefaultValue(0)
            .setTextGetter(value -> Component.literal(value == 0 ? "Vanilla" : value + " cm"))
            .setTooltip(Component.literal("Lifts the hand into view (3D only)."))
            .setSaveConsumer(StereoConfig::setHandRaise)
            .build());

        hud.addEntry(entries.startIntSlider(Component.literal("Hand inward"), StereoConfig.handInward(), 0, 40)
            .setDefaultValue(0)
            .setTextGetter(value -> Component.literal(value == 0 ? "Vanilla" : value + " cm"))
            .setTooltip(Component.literal("Moves the hand towards the middle (3D only)."))
            .setSaveConsumer(StereoConfig::setHandInward)
            .build());

        hud.addEntry(entries.startIntSlider(Component.literal("Hand depth"), StereoConfig.handDepthPercent(), 0, 200)
            .setDefaultValue(50)
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
        Screen built = builder.build();
        previewScreen = built;
        previewSource = () -> StereoConfig.preview(sizeEntry.getValue(), widthEntry.getValue() / 10f,
            distanceEntry.getValue() / 10f, heightEntry.getValue() / 10f, curveEntry.getValue(), floatingEntry.getValue());
        return built;
    }
}
