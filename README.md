# Blockoscope SteamVR

*Formerly Parallax Screen.*

> **Alpha.** Built and tested on one setup (see [Tested with](#tested-with)). Expect rough edges.

A Fabric mod that shows Minecraft in **stereoscopic 3D on a virtual screen in SteamVR**. Each eye is rendered at full
resolution and handed straight to SteamVR, which shows it on a floating screen in your headset: real depth, like a 3D
movie, without a monitor in the loop and without a VR mod. The game plays exactly like normal Minecraft (vanilla HUD,
menus, animations, keyboard and mouse); moving your head moves your view of the screen, not the game camera.

It is the SteamVR sibling of [Blockoscope SBS](https://github.com/nick-yoderia/blockoscope-sbs), which packs both
eyes into one half side-by-side monitor image for viewing in Bigscreen (and so can be watched together with someone).
Packing halves each eye's horizontal resolution, and the viewer stretches it back; Blockoscope SteamVR skips that step.

## Features

- **Full resolution per eye:** each eye renders, in the window's shape, at as many pixels as your headset shows across
  the screen (worked out from SteamVR's render size and field of view and the screen's size and distance), or at a
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
- **Automatic:** whenever SteamVR is running the screen is there; when it isn't (or you quit it) the window shows half
  side-by-side 3D instead. The mod never starts SteamVR itself.
- **Window preview:** while the SteamVR screen is on, the window shows the left eye in plain 2D.
- Everything from Blockoscope SBS: depth strength and focus distance, a HUD that sits on whatever is behind the hotbar,
  menus on the screen surface, a crosshair at the depth of what you aim at, hand depth, comfort options, F9 for 2D.

### Works with

- **Sodium**, **Iris** shader packs (tested with BSL and Complementary Reimagined; each eye gets its own pipeline),
  **Voxy** distant terrain.
- Mod Menu + Cloth Config for the settings screen.

## Requirements

- Minecraft **26.2** with Fabric Loader, **Java 25** (what 26.2 ships with)
- **SteamVR** on **Windows** (the OpenGL renderer, Minecraft's default)
- Recommended: [Mod Menu](https://modrinth.com/mod/modmenu) and [Cloth Config](https://modrinth.com/mod/cloth-config)
- Not compatible with Vivecraft, and don't install it together with Blockoscope SBS (they replace the same rendering)

## Setup

1. Put `blockoscope-steamvr-<version>.jar` in your `mods` folder.
2. Start SteamVR with your headset, then Minecraft (any order; the screen appears within a few seconds of both
   running). Keep the game window focused for keyboard and mouse.
3. Press **F8** to bring the screen in front of you. Adjust it under **Mods > Blockoscope SteamVR > SteamVR screen**.

If the picture is upside down in the headset, turn on **Flip picture**; if the depth looks inside-out, **Swap eyes**.
Java may print a one-time warning about "restricted methods": that is the mod calling SteamVR's library and is harmless.

## Settings

**Mods > Blockoscope SteamVR** (needs Mod Menu and Cloth Config). Saving applies changes immediately. Everything is also
stored in `config/blockoscope-steamvr.properties` (the first time, settings are taken over from Parallax Screen or
copied from Blockoscope SBS / Parallax Theater, if present):

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | 3D on (F9 toggles it) |
| `steamVrScreen` | `true` | Show the game on a screen in SteamVR while it runs (`false` = window only) |
| `eyeResolution` | `0` | Width in pixels each eye renders at for the SteamVR screen (height follows the window); `0` = automatic (1.5x what the headset shows, rounded so the HUD lands on whole pixels) |
| `syncToHeadset` | `true` | One game frame per headset refresh while the SteamVR screen is on |
| `screenSize` | `custom` | `custom`; `true_scale` (the screen grows to the game's FOV) or `match_fov` (the game's FOV follows the screen): both life-size, focused at the screen |
| `floatingWindow` | `true` | Blank a thin strip at each eye's outer edge so the screen edges float at the held item's depth |
| `screenWidth` | `2.6` | Screen width in metres (`custom` and `match_fov`) |
| `screenDistance` | `2.0` | Metres from your head to the screen, applied when it is placed or recentered (F8) |
| `screenHeight` | `0` | Metres above (or below) your eyes |
| `screenCurvature` | `0` | Curve of the screen in % (`0` = flat) |
| `previewBothEyes` | `false` | Window shows both eyes side by side instead of the left eye |
| `flipScreen` | `false` | Flip the picture in the headset upside down |
| `renderScale` | `100` | Window side-by-side only: % of the half-window resolution each eye renders at |
| `depthPercent` | `100` | 3D strength as a % of average eye spacing (6.4 cm); `0` = flat |
| `focusDistance` | `10` | Metres that sit exactly on the screen surface; `0` = infinity |
| `swapEyes` | `false` | Swap the eyes (if the depth looks inside-out) |
| `hudDepth` | `scene` | In-game HUD depth: `scene` (on what is behind the hotbar), `aim` (crosshair's depth), `fixed` |
| `hudDistance` | `1.35` | Metres for a fixed HUD depth; `0` = on the screen surface |
| `menuDistance` | `0` | Metres at which menus float; `0` = on the screen surface |
| `crosshairAtTarget` | `true` | Crosshair at the depth of what it aims at within reach (`false` = with the HUD) |
| `crosshairRestOffset` | `0` | Metres nearer (negative) or farther than your reach where the crosshair rests |
| `handDepthPercent` | `50` | Depth of your hand and held item, % of the world's |
| `handReach` | `30` | Centimetres the arm reaches further forward in 3D; `0` = vanilla |
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
`docs/DEV_NOTES.md` has the details, and every two-renders-per-frame workaround (shared with Blockoscope SBS).

## Building

No Gradle. `build.sh` compiles with the JDK bundled with Prism Launcher against jars already on disk (Minecraft,
Mixin, LWJGL, JOML, Fabric Loader, Cloth Config, Mod Menu, Iris, Sodium, Voxy); paths are set for the author's Prism
instances, adjust as needed:

```sh
bash build.sh   # -> build/blockoscope-steamvr-<version>.jar
```

## Tested with

- Minecraft 26.2, Fabric Loader 0.19.5, Sodium 0.9.1, Iris 1.11.2 (BSL), Voxy 0.2.19-beta
- Windows 11, AMD Radeon RX 9070 XT, SteamVR 2.18 (so far with SteamVR's null test headset; Valve Steam Frame via
  Steam Link pending)

## License

[MIT](LICENSE). `openvr_api.dll` is Valve's, under the BSD licence in `natives/windows-x64/OPENVR_LICENSE.txt`.
