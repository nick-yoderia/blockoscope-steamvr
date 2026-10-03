# Blockoscope SteamVR

> **Alpha.** Built and tested on one setup (see [Tested with](#tested-with)). Expect rough edges.

A Fabric mod that shows Minecraft in **stereoscopic 3D on a virtual screen in SteamVR**. Each eye is rendered at full
resolution and handed straight to SteamVR, which shows it on a floating screen in your headset: real depth, like a 3D
movie, without a monitor in the loop and without a VR mod. The game plays exactly like normal Minecraft (vanilla HUD,
menus, animations, keyboard and mouse); moving your head moves your view of the screen, not the game camera.

It is the SteamVR sibling of [Blockoscope SBS](https://github.com/nick-yoderia/blockoscope-sbs), which packs both
eyes into one half side-by-side monitor image for viewing in Bigscreen (and so can be watched together with someone).
Packing halves each eye's horizontal resolution, and the viewer stretches it back; Blockoscope SteamVR skips that step.

## Features

- **Full resolution per eye:** each eye renders, in the window's shape, at 1.5x as many pixels as your headset shows
  across the screen, rounded so the HUD lands on whole pixels (worked out from SteamVR's render size and field of view and the screen's size and distance), or at a
  width you choose. Independent of your monitor.
- **SteamVR screen:** an OpenVR overlay in SteamVR Home or the void, with adjustable size, distance, height and curve.
  SteamVR draws it at the headset's refresh rate, so head movement stays smooth whatever the game's frame rate is.
  **F8** puts it straight in front of you again.
- **Synced to the headset:** the game makes one frame per headset refresh, so turning looks even instead of
  stuttering against the headset's fixed rate, and the GPU isn't busy with frames nobody sees. It runs free whenever
  it can't keep up. While the screen is up, the monitor's VSync and Minecraft's AFK and minimised-window frame-rate
  limits are ignored: you may be watching without touching anything.
- **True scale** (optional): the screen covers exactly the game's field of view and the focus is at the screen, so
  with depth strength 100% the world is life-size, as if looking through a window, with no stretching towards the
  edges. Either the screen grows to your FOV setting, or your FOV follows the screen's width and distance.
- **Automatic, and safe to share:** with 3D on Auto (the default) the game is plain Minecraft until SteamVR is
  running with a headset connected, then the screen appears; quit SteamVR, unplug the headset or (by default) just
  take it off your head, and it's plain Minecraft on the monitor again. Friends playing the same mod pack on a
  monitor don't have to change anything. A "3D: Auto / On / Off" button sits on the title screen next to
  Singleplayer, like Vivecraft's VR switch; On also shows half side-by-side 3D in the window without SteamVR. The mod never starts SteamVR itself.
- **Live screen adjustment:** while the settings are open, the screen moves and reshapes as you drag its size,
  distance, height and curve sliders; Cancel undoes it.
- **Window preview:** while the SteamVR screen is on, the window shows the left eye in plain 2D.
- **Depth controls:** depth strength (eye spacing, 100% = natural) and focus distance, the distance that sits exactly
  on the screen surface. Nearer things pop out of the screen, farther things sit behind it.
- **HUD and menus in depth:** the HUD sits on whatever is behind the hotbar, menus on the screen surface, and the
  crosshair at the depth of what you aim at, so it never looks doubled. A cursor is drawn in both eyes in menus.
- **Hand depth, comfort options** (camera bobbing and damage tilt off in 3D, toned-down nausea and portal warp), and
  **F9** to switch between 3D and normal 2D.

### Works with

- **Sodium**, **Iris** shader packs (tested with BSL and Complementary Reimagined; each eye gets its own pipeline),
  **Voxy** distant terrain.
- Mod Menu + Cloth Config for the settings screen.

## Requirements

- Minecraft **26.2** with Fabric Loader, **Java 25** (what 26.2 ships with)
- **SteamVR** on **Windows** (the OpenGL renderer, Minecraft's default)
- Recommended: [Mod Menu](https://modrinth.com/mod/modmenu) and [Cloth Config](https://modrinth.com/mod/cloth-config)
- Not compatible with Vivecraft, and don't install it together with Blockoscope SBS (they replace the same rendering)

## Modpack

[**Download the Blockoscope SteamVR modpack**](https://github.com/nick-yoderia/blockoscope-steamvr/raw/main/modpack/Blockoscope-SteamVR.mrpack) (always the
latest; `.mrpack`). It is the setup the mod is tested with: Blockoscope SteamVR with Sodium, Sodium Extra, Iris (BSL selected;
Complementary Reimagined and Photon included), Voxy with Voxy World Gen, Lithium, FerriteCore, MoreCulling,
BadOptimizations, Clumps, Chunky, Mod Menu and Cloth Config.

In Prism Launcher: **Add Instance > Import**, then pick the downloaded file or paste the link above. (The Modrinth
App opens it too.) The pack only lists the other mods and shader packs, which the launcher downloads from Modrinth.
Give the instance at least 8 GB of memory (**Edit > Settings > Java**) for Voxy and shaders.

## Setup

1. Put `blockoscope-steamvr-<version>.jar` in your `mods` folder, or import the [modpack](#modpack).
2. Start SteamVR with your headset, then Minecraft (any order; the screen appears within a few seconds of both
   running). Keep the game window focused for keyboard and mouse.
3. Press **F8** to bring the screen in front of you. Adjust it under **Mods > Blockoscope SteamVR > Screen** (it moves live while you drag).

If the picture is upside down in the headset, turn on **Picture > Flip upside down**; if the depth looks inside-out,
**Depth > Swap eyes**.
Java may print a one-time warning about "restricted methods": that is the mod calling SteamVR's library and is harmless.

## Settings

**Mods > Blockoscope SteamVR** (needs Mod Menu and Cloth Config), in tabs General, Screen, Picture, Depth, HUD & Hand,
Comfort and Cursor. Hover over a setting to see what it does; settings that don't apply are greyed out. Saving applies
changes immediately. Everything is also stored in `config/blockoscope-steamvr.properties` (the first time, settings
are copied from Blockoscope SBS, if present):

| Key | Default | Meaning |
|---|---|---|
| `mode` | `auto` | 3D: `auto` = only while you use a headset in SteamVR, normal Minecraft otherwise (safe in a mod pack shared with people without VR); `on` = always (half side-by-side in the window without SteamVR); `off`. Also the title screen's 3D button; F9 switches between on and off |
| `steamVrScreen` | `true` | Use SteamVR: show 3D on a screen in SteamVR (`false` = window only) |
| `eyeResolution` | `0` | Width in pixels each eye renders at for the SteamVR screen (height follows the window); `0` = automatic (1.5x what the headset shows, rounded so the HUD lands on whole pixels) |
| `syncToHeadset` | `true` | One game frame per headset refresh while the SteamVR screen is on |
| `headsetOffTo2D` | `true` | Headset off = normal view: normal Minecraft on the monitor while the headset is off your head (proximity sensor), like Vivecraft's hot switching; `false` = only when it is disconnected or asleep |
| `screenSize` | `custom` | FOV link: `custom` (Off: set the width yourself), `true_scale` (Screen fits FOV: the width follows your FOV) or `match_fov` (FOV fits screen: your FOV follows the width and distance); both linked modes are life-size and focus on the screen |
| `screenWidth` | `4.3` | Screen width in metres (not used with `true_scale`) |
| `screenDistance` | `2.8` | Metres from your head (where it was at the last F8) to the screen |
| `screenHeight` | `0` | Metres above (or below) your eyes |
| `screenCurvature` | `10` | Curve of the screen in % (`0` = flat) |
| `previewBothEyes` | `false` | Window shows both eyes side by side instead of the left eye |
| `flipScreen` | `false` | Flip the picture in the headset upside down |
| `renderScale` | `100` | Window side-by-side only: % of the half-window resolution each eye renders at |
| `depthPercent` | `100` | 3D strength as a % of average eye spacing (6.4 cm); `0` = flat |
| `focusDistance` | `4` | Metres that sit exactly on the screen surface; `0` = infinity |
| `swapEyes` | `false` | Swap the eyes (if the depth looks inside-out) |
| `hudDepth` | `scene` | In-game HUD depth: `scene` (on what is behind the hotbar), `aim` (crosshair's depth), `fixed` |
| `hudDistance` | `1.2` | Metres for a fixed HUD depth; `0` = on the screen surface |
| `menuDistance` | `0` | Metres at which menus float; `0` = on the screen surface |
| `crosshairAtTarget` | `true` | Crosshair at the depth of what it aims at within reach (`false` = with the HUD) |
| `crosshairRestOffset` | `0` | Metres nearer (negative) or farther than your reach where the crosshair rests |
| `handDepthPercent` | `50` | Depth of your hand and held item, % of the world's |
| `handReach` | `0` | Arm length: centimetres the hand is held further out in 3D; `0` = vanilla |
| `handRaise` | `0` | Centimetres the hand and held item are raised in 3D |
| `handInward` | `0` | Centimetres the hand and held item are moved towards the middle in 3D |
| `cameraBobbing` | `false` | Vanilla view bobbing of the camera in 3D |
| `damageTilt` | `false` | Camera roll when hurt or dying, in 3D |
| `warpPercent` | `40` | Nausea and portal warp strength in 3D, % of vanilla's |
| `hideCursor` | `true` | Hide the Windows cursor and draw one in both eyes |
| `confineCursor` | `true` | Keep the cursor inside the game window while it is focused |

## How it works

Minecraft 26.2 extracts everything it draws into a render state once per frame, then renders from that state. The mod
runs the render half twice, once per eye, with the camera moved sideways by half the eye spacing and the projection
shifted so things at the focus distance line up. The eyes are packed side by side into one texture, which is copied on the GPU into a Direct3D 11
texture (OpenGL/Direct3D interop owned by the mod) and handed to an OpenVR overlay flagged as side-by-side stereo;
SteamVR shows each half to one eye. Nothing is read back to the CPU.

SteamVR is called directly through Valve's `openvr_api.dll` (bundled, BSD licence) with Java's foreign function API:
LWJGL's OpenVR bindings were last released for LWJGL 3.3 and don't load on the LWJGL 3.4 that Minecraft 26.2 ships.
Rendering the same frame twice exposes per-frame caches in the game and in other mods (entity and particle lists,
Sodium's terrain matrices, Iris pipelines, Voxy viewports, the sky and clouds), which the mod works around;
`docs/DEV_NOTES.md` has the details.

## Building

No Gradle. `build.sh` compiles with the JDK bundled with Prism Launcher against jars already on disk (Minecraft,
Mixin, LWJGL, JOML, Fabric Loader, Cloth Config, Mod Menu, Iris, Sodium, Voxy); paths are set for the author's Prism
instances, adjust as needed:

```sh
bash build.sh   # -> build/blockoscope-steamvr-<version>.jar
```

The modpack is rebuilt from a Prism instance with `modpack/make-pack.py` (see the top of the script). It looks every
third-party file up on Modrinth instead of bundling it, and bundles only this mod's jar and an allowlist of config
files, checked for personal information.

## Tested with

- Minecraft 26.2, Fabric Loader 0.19.5, Sodium 0.9.1, Iris 1.11.2 (BSL), Voxy 0.2.19-beta
- Windows 11, AMD Radeon RX 9070 XT, SteamVR 2.18, Valve Steam Frame via Steam Link

## License

[MIT](LICENSE). `openvr_api.dll` is Valve's, under the BSD licence in `natives/windows-x64/OPENVR_LICENSE.txt`.
