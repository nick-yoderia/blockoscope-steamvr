package nzy.blockoscope.steamvr;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.Requirement;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * In-game settings (Mod Menu > Blockoscope SteamVR), built with Cloth Config. Everything is read every frame, so saving
 * applies immediately. Every setting has a tooltip saying in plain words what it does (the user wanted each one
 * to be understandable in game), and settings that don't apply are greyed out; tooltip lines stay short so they
 * never run off the screen.
 */
public final class StereoConfigScreen {
    private static final int FOCUS_MAX_METRES = 64;

    /** The open settings screen whose screen entries are shown live, and how to read them (see {@link #updatePreview}). */
    private static Screen previewScreen;
    private static Runnable previewSource;

    private StereoConfigScreen() {}

    /**
     * Called every frame: while this settings screen is open, the SteamVR screen follows its size, distance, height,
     * and curve entries as they are dragged, unsaved. Once it closes (saved or not), the saved values apply again.
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

    /** Tooltip lines (each short enough not to run off the screen). */
    private static Component[] tip(String... lines) {
        Component[] components = new Component[lines.length];
        for (int i = 0; i < lines.length; i++) {
            components[i] = Component.literal(lines[i]);
        }
        return components;
    }

    private static String metres(int tenths) {
        return String.format("%.1f m", tenths / 10f);
    }

    public static Screen create(Screen parent) {
        StereoConfig.load();
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("Blockoscope SteamVR"));
        ConfigEntryBuilder entries = builder.entryBuilder();

        // --- General: when 3D is on and where it goes ---
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));
        general.addEntry(entries.startTextDescription(Component.literal(
            "Shows Minecraft in 3D on a virtual screen in SteamVR. Hover over a setting to see what it does. "
                + "Keys: F9 turns 3D on/off, F8 puts the screen straight in front of you.")).build());

        general.addEntry(entries.startEnumSelector(Component.literal("3D"), StereoConfig.Mode.class, StereoConfig.mode())
            .setDefaultValue(StereoConfig.Mode.AUTO)
            .setEnumNameProvider(value -> Component.literal(modeName((StereoConfig.Mode) value)))
            .setTooltip(tip(
                "Auto: 3D only while you use a headset in SteamVR,",
                "  normal Minecraft otherwise. Best for a shared pack.",
                "On: always 3D, on the SteamVR screen. Without",
                "  SteamVR the window shows both eyes side by side.",
                "Off: normal Minecraft.",
                "Also on the title screen, next to Singleplayer."))
            .setSaveConsumer(StereoConfig::setMode)
            .build());

        general.addEntry(entries.startBooleanToggle(Component.literal("Headset off = normal view"), StereoConfig.headsetOffTo2D())
            .setDefaultValue(true)
            .setYesNoTextSupplier(on -> Component.literal(on ? "Yes" : "No"))
            .setTooltip(tip(
                "Yes: taking the headset off your head switches to",
                "  normal Minecraft on the monitor; putting it on",
                "  brings the 3D screen back.",
                "No: only when the headset is disconnected or asleep."))
            .setSaveConsumer(StereoConfig::setHeadsetOffTo2D)
            .build());

        general.addEntry(entries.startBooleanToggle(Component.literal("Use SteamVR"), StereoConfig.steamVrScreen())
            .setDefaultValue(true)
            .setYesNoTextSupplier(on -> Component.literal(on ? "Yes" : "No (window only)"))
            .setTooltip(tip(
                "Yes: show the 3D picture on a screen in SteamVR.",
                "No: never use SteamVR; with 3D On the window shows",
                "  both eyes side by side instead."))
            .setSaveConsumer(StereoConfig::setSteamVrScreen)
            .build());

        general.addEntry(entries.startBooleanToggle(Component.literal("Monitor shows"), StereoConfig.previewBothEyes())
            .setDefaultValue(false)
            .setYesNoTextSupplier(on -> Component.literal(on ? "Both eyes" : "Left eye"))
            .setTooltip(tip(
                "What the game window shows while you play in the",
                "headset. Left eye looks like normal Minecraft."))
            .setSaveConsumer(StereoConfig::setPreviewBothEyes)
            .build());

        // --- Screen: size and placement in SteamVR (shown live) ---
        ConfigCategory screen = builder.getOrCreateCategory(Component.literal("Screen"));
        screen.addEntry(entries.startTextDescription(Component.literal(
            "The screen in SteamVR. Changes here show in the headset while you drag; Save keeps them, Cancel undoes "
                + "them. F8 puts the screen straight in front of you.")).build());

        EnumListEntry<StereoConfig.ScreenSize> sizeEntry = entries.startEnumSelector(Component.literal("FOV link"),
                StereoConfig.ScreenSize.class, StereoConfig.screenSize())
            .setDefaultValue(StereoConfig.ScreenSize.CUSTOM)
            .setEnumNameProvider(value -> Component.literal(switch ((StereoConfig.ScreenSize) value) {
                case CUSTOM -> "Off";
                case TRUE_SCALE -> "Screen fits FOV";
                case MATCH_FOV -> "FOV fits screen";
            }))
            .setTooltip(tip(
                "Ties the screen's size to the game's field of view.",
                "Off: set the width yourself. A FOV wider than the",
                "  screen looks stretched towards the edges.",
                "Screen fits FOV: the width follows your FOV setting,",
                "  so the world is life-size. High FOV = huge screen.",
                "FOV fits screen: your FOV follows the width and",
                "  distance. Life-size, but a small screen is narrow.",
                "Both linked modes put Focus distance on the screen."))
            .setSaveConsumer(StereoConfig::setScreenSize)
            .build();
        screen.addEntry(sizeEntry);

        IntegerSliderEntry widthEntry = entries.startIntSlider(Component.literal("Width"),
                Math.round(StereoConfig.screenWidth() * 10), 5, 200)
            .setDefaultValue(Math.round(StereoConfig.DEFAULT_SCREEN_WIDTH * 10))
            .setTextGetter(value -> Component.literal(metres(value)))
            .setTooltip(tip(
                "Width of the screen in metres.",
                "Set by your FOV when FOV link is Screen fits FOV."))
            .setRequirement(Requirement.not(Requirement.isValue(sizeEntry, StereoConfig.ScreenSize.TRUE_SCALE)))
            .setSaveConsumer(value -> StereoConfig.setScreenWidth(value / 10f))
            .build();
        screen.addEntry(widthEntry);

        IntegerSliderEntry distanceEntry = entries.startIntSlider(Component.literal("Distance"),
                Math.round(StereoConfig.screenDistance() * 10), 5, 200)
            .setDefaultValue(Math.round(StereoConfig.DEFAULT_SCREEN_DISTANCE * 10))
            .setTextGetter(value -> Component.literal(metres(value)))
            .setTooltip(tip(
                "How far the screen is from you, in metres,",
                "measured from where you were at the last F8."))
            .setSaveConsumer(value -> StereoConfig.setScreenDistance(value / 10f))
            .build();
        screen.addEntry(distanceEntry);

        IntegerSliderEntry heightEntry = entries.startIntSlider(Component.literal("Height"),
                Math.round(StereoConfig.screenHeight() * 10), -30, 30)
            .setDefaultValue(0)
            .setTextGetter(value -> Component.literal(value == 0 ? "Eye level" : String.format("%+.1f m", value / 10f)))
            .setTooltip(tip("Raises or lowers the screen's centre", "relative to your eyes."))
            .setSaveConsumer(value -> StereoConfig.setScreenHeight(value / 10f))
            .build();
        screen.addEntry(heightEntry);

        IntegerSliderEntry curveEntry = entries.startIntSlider(Component.literal("Curve"), StereoConfig.screenCurvature(), 0, 100)
            .setDefaultValue(StereoConfig.DEFAULT_SCREEN_CURVATURE)
            .setTextGetter(value -> Component.literal(value == 0 ? "Flat" : value + "%"))
            .setTooltip(tip("Bends the screen around you, like a curved", "monitor. 0 = flat."))
            .setSaveConsumer(StereoConfig::setScreenCurvature)
            .build();
        screen.addEntry(curveEntry);

        // --- Picture: resolution and frame rate ---
        ConfigCategory picture = builder.getOrCreateCategory(Component.literal("Picture"));
        picture.addEntry(entries.startTextDescription(Component.literal(
            "Sharpness and smoothness. Higher resolution costs frame rate.")).build());

        // 9 = automatic; 10..64 = 640..4096 pixels.
        picture.addEntry(entries.startIntSlider(Component.literal("Eye resolution"),
                StereoConfig.eyeResolution() <= 0 ? 9 : StereoConfig.eyeResolution() / 64, 9, 64)
            .setDefaultValue(9)
            .setTextGetter(value -> Component.literal(value <= 9 ? "Auto (" + VrScreen.eyeResolution() + " px)" : value * 64 + " px wide"))
            .setTooltip(tip(
                "Pixels across each eye's picture in SteamVR (the",
                "height follows your game window's shape).",
                "Auto: 1.5x what your headset can show across the",
                "  screen, rounded so the HUD stays crisp.",
                "Lower it if the frame rate is too low."))
            .setSaveConsumer(value -> StereoConfig.setEyeResolution(value <= 9 ? 0 : value * 64))
            .build());

        picture.addEntry(entries.startBooleanToggle(Component.literal("Sync to headset"), StereoConfig.syncToHeadset())
            .setDefaultValue(true)
            .setYesNoTextSupplier(on -> Component.literal(on ? "Yes" : "No"))
            .setTooltip(tip(
                "Yes: one game frame per headset refresh. Turning",
                "  looks smoother and the GPU works less.",
                "No: the game runs as fast as it can.",
                "Turns itself off while the game can't keep up."))
            .setSaveConsumer(StereoConfig::setSyncToHeadset)
            .build());

        picture.addEntry(entries.startIntSlider(Component.literal("Window render scale"), StereoConfig.renderScale(), 25, 200)
            .setDefaultValue(100)
            .setTextGetter(value -> Component.literal(value + "%"))
            .setTooltip(tip(
                "Only for 3D in the window (no SteamVR): each eye's",
                "resolution as a % of half the window.",
                "Lower = faster, higher = sharper."))
            .setSaveConsumer(StereoConfig::setRenderScale)
            .build());

        picture.addEntry(entries.startBooleanToggle(Component.literal("Flip upside down"), StereoConfig.flipScreen())
            .setDefaultValue(false)
            .setTooltip(tip("Only if the screen in SteamVR shows upside down."))
            .setSaveConsumer(StereoConfig::setFlipScreen)
            .build());

        // --- Depth ---
        ConfigCategory depth = builder.getOrCreateCategory(Component.literal("Depth"));
        depth.addEntry(entries.startTextDescription(Component.literal(
            "How strong the 3D is. Things nearer than the focus distance pop out of the screen; farther things sit "
                + "behind it.")).build());

        depth.addEntry(entries.startIntSlider(Component.literal("Depth strength"), StereoConfig.depthPercent(), 0, 300)
            .setDefaultValue(100)
            .setTextGetter(value -> Component.literal(value == 0 ? "Flat" : value + "%"))
            .setTooltip(tip(
                "Distance between the two eye cameras.",
                "100% = normal human eyes. Lower if 3D strains your",
                "eyes, higher for a stronger effect."))
            .setSaveConsumer(StereoConfig::setDepthPercent)
            .build());

        depth.addEntry(entries.startIntSlider(Component.literal("Focus distance"),
                Math.round(StereoConfig.focusDistance()), 0, FOCUS_MAX_METRES)
            .setDefaultValue(Math.round(StereoConfig.DEFAULT_FOCUS_DISTANCE))
            .setTextGetter(value -> Component.literal(value == 0 ? "Infinity" : value + " m"))
            .setTooltip(tip(
                "How far into the world the screen surface is.",
                "Nearer things pop out, farther things sit behind.",
                "Infinity: everything pops out.",
                "Not used while FOV link is on (Screen tab)."))
            .setSaveConsumer(value -> StereoConfig.setFocusDistance(value))
            .build());

        depth.addEntry(entries.startBooleanToggle(Component.literal("Swap eyes"), StereoConfig.swapEyes())
            .setDefaultValue(false)
            .setTooltip(tip("Only if near things look far and far things near."))
            .setSaveConsumer(StereoConfig::setSwapEyes)
            .build());

        // --- HUD & hand ---
        ConfigCategory hud = builder.getOrCreateCategory(Component.literal("HUD & Hand"));
        hud.addEntry(entries.startTextDescription(Component.literal(
            "Where the hotbar, crosshair, menus and your hand appear in depth.")).build());

        EnumListEntry<StereoConfig.HudDepth> hudDepthEntry = entries.startEnumSelector(Component.literal("HUD depth"),
                StereoConfig.HudDepth.class, StereoConfig.hudDepth())
            .setDefaultValue(StereoConfig.HudDepth.SCENE)
            .setEnumNameProvider(value -> Component.literal(switch ((StereoConfig.HudDepth) value) {
                case SCENE -> "On scene";
                case AIM -> "At crosshair";
                case FIXED -> "Fixed";
            }))
            .setTooltip(tip(
                "Depth of the hotbar, health and other HUD.",
                "On scene: on whatever is behind the hotbar.",
                "At crosshair: at the depth of what you aim at.",
                "Fixed: at Fixed HUD distance."))
            .setSaveConsumer(StereoConfig::setHudDepth)
            .build();
        hud.addEntry(hudDepthEntry);

        hud.addEntry(entries.startIntSlider(Component.literal("Fixed HUD distance"),
                Math.round(StereoConfig.hudDistance() * 100), 0, 1000)
            .setDefaultValue(Math.round(StereoConfig.DEFAULT_HUD_DISTANCE * 100))
            .setTextGetter(value -> Component.literal(value == 0 ? "Screen" : String.format("%.2f m", value / 100f)))
            .setTooltip(tip("HUD distance when HUD depth is Fixed.", "Screen = on the screen surface."))
            .setRequirement(Requirement.isValue(hudDepthEntry, StereoConfig.HudDepth.FIXED))
            .setSaveConsumer(value -> StereoConfig.setHudDistance(value / 100f))
            .build());

        hud.addEntry(entries.startIntSlider(Component.literal("Menu distance"),
                Math.round(StereoConfig.menuDistance() * 100), 0, 1000)
            .setDefaultValue(0)
            .setTextGetter(value -> Component.literal(value == 0 ? "Screen" : String.format("%.2f m", value / 100f)))
            .setTooltip(tip("Depth of menus and inventories.", "Screen = flat on the screen surface (easiest to read)."))
            .setSaveConsumer(value -> StereoConfig.setMenuDistance(value / 100f))
            .build());

        BooleanListEntry crosshairEntry = entries.startBooleanToggle(Component.literal("Crosshair depth"),
                StereoConfig.crosshairAtTarget())
            .setDefaultValue(true)
            .setYesNoTextSupplier(on -> Component.literal(on ? "At target" : "With HUD"))
            .setTooltip(tip(
                "At target: the crosshair sits on the block or mob",
                "  you aim at, so it doesn't look doubled.",
                "With HUD: at the same depth as the hotbar."))
            .setSaveConsumer(StereoConfig::setCrosshairAtTarget)
            .build();
        hud.addEntry(crosshairEntry);

        // Half-metre steps, relative to the block reach.
        hud.addEntry(entries.startIntSlider(Component.literal("Crosshair rest"),
                Math.round(StereoConfig.crosshairRestOffset() * 2f), -8, 32)
            .setDefaultValue(0)
            .setTextGetter(value -> Component.literal(value == 0 ? "At reach"
                : String.format("Reach %s%.1f m", value > 0 ? "+" : "-", Math.abs(value) / 2f)))
            .setTooltip(tip("Crosshair depth when nothing is in reach:", "at the edge of your reach, or nearer/farther."))
            .setRequirement(Requirement.isTrue(crosshairEntry))
            .setSaveConsumer(value -> StereoConfig.setCrosshairRestOffset(value / 2f))
            .build());

        hud.addEntry(entries.startIntSlider(Component.literal("Hand depth"), StereoConfig.handDepthPercent(), 0, 200)
            .setDefaultValue(StereoConfig.DEFAULT_HAND_DEPTH)
            .setTextGetter(value -> Component.literal(value == 0 ? "Screen" : value + "%"))
            .setTooltip(tip(
                "How far your hand and held item pop out, as a %",
                "of real depth. Lower if the item looks odd at the",
                "screen edge. Screen = flat on the screen surface."))
            .setSaveConsumer(StereoConfig::setHandDepthPercent)
            .build());

        hud.addEntry(entries.startIntSlider(Component.literal("Arm length"), StereoConfig.handReach(), 0, 60)
            .setDefaultValue(StereoConfig.DEFAULT_HAND_REACH)
            .setTextGetter(value -> Component.literal(value == 0 ? "Vanilla" : "+" + value + " cm"))
            .setTooltip(tip("Holds the hand further out, so it isn't right", "in front of your eyes (3D only)."))
            .setSaveConsumer(StereoConfig::setHandReach)
            .build());

        hud.addEntry(entries.startIntSlider(Component.literal("Hand raise"), StereoConfig.handRaise(), 0, 40)
            .setDefaultValue(0)
            .setTextGetter(value -> Component.literal(value == 0 ? "Vanilla" : "+" + value + " cm"))
            .setTooltip(tip("Lifts the hand higher into view (3D only)."))
            .setSaveConsumer(StereoConfig::setHandRaise)
            .build());

        hud.addEntry(entries.startIntSlider(Component.literal("Hand inward"), StereoConfig.handInward(), 0, 40)
            .setDefaultValue(0)
            .setTextGetter(value -> Component.literal(value == 0 ? "Vanilla" : "+" + value + " cm"))
            .setTooltip(tip("Moves the hand towards the middle of the", "screen (3D only)."))
            .setSaveConsumer(StereoConfig::setHandInward)
            .build());

        // --- Comfort ---
        ConfigCategory comfort = builder.getOrCreateCategory(Component.literal("Comfort"));
        comfort.addEntry(entries.startTextDescription(Component.literal(
            "Camera motion that is uncomfortable to watch in 3D. Only changed while 3D is on.")).build());
        comfort.addEntry(entries.startBooleanToggle(Component.literal("Camera bobbing"), StereoConfig.cameraBobbing())
            .setDefaultValue(false)
            .setTooltip(tip("The camera swaying as you walk.", "Your hand bobs either way."))
            .setSaveConsumer(StereoConfig::setCameraBobbing)
            .build());
        comfort.addEntry(entries.startBooleanToggle(Component.literal("Damage tilt"), StereoConfig.damageTilt())
            .setDefaultValue(false)
            .setTooltip(tip("The camera rolling when you are hurt or dying."))
            .setSaveConsumer(StereoConfig::setDamageTilt)
            .build());
        comfort.addEntry(entries.startIntSlider(Component.literal("Nausea & portal warp"), StereoConfig.warpPercent(), 0, 100)
            .setDefaultValue(40)
            .setTextGetter(value -> Component.literal(value == 0 ? "Off" : value + "%"))
            .setTooltip(tip("Strength of the wobble from nausea and nether", "portals, as a % of normal."))
            .setSaveConsumer(StereoConfig::setWarpPercent)
            .build());

        // --- Cursor ---
        ConfigCategory cursor = builder.getOrCreateCategory(Component.literal("Cursor"));
        cursor.addEntry(entries.startTextDescription(Component.literal(
            "The mouse pointer in menus while 3D is on.")).build());
        cursor.addEntry(entries.startBooleanToggle(Component.literal("Cursor in both eyes"), StereoConfig.hideCursor())
            .setDefaultValue(true)
            .setTooltip(tip("Hides the Windows pointer and draws one in each", "eye, so you can see it in 3D."))
            .setSaveConsumer(StereoConfig::setHideCursor)
            .build());
        cursor.addEntry(entries.startBooleanToggle(Component.literal("Keep cursor in window"), StereoConfig.confineCursor())
            .setDefaultValue(true)
            .setTooltip(tip("Stops the pointer leaving the game window (you", "cannot see your desktop). Alt+Tab releases it."))
            .setSaveConsumer(StereoConfig::setConfineCursor)
            .build());

        builder.setSavingRunnable(StereoConfig::save);
        Screen built = builder.build();
        previewScreen = built;
        previewSource = () -> StereoConfig.preview(sizeEntry.getValue(), widthEntry.getValue() / 10f,
            distanceEntry.getValue() / 10f, heightEntry.getValue() / 10f, curveEntry.getValue());
        return built;
    }
}
