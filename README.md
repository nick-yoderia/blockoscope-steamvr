# NullVR Theater

> **Alpha.** Built and tested on one setup (see [Tested with](#tested-with)). Expect rough edges.

A small Fabric add-on for [Vivecraft](https://github.com/Vivecraft) that turns its **NullVR** mode
(VR rendering without a headset) into a proper **side-by-side (SBS) 3D screen**. Show the game window on a
virtual screen in your headset (for example in Bigscreen) and you get real stereoscopic depth without head
tracking: moving your head moves your view of the screen, not the game camera.

This is an unofficial add-on and is not affiliated with the Vivecraft project.

## What it changes

Vivecraft's NullVR is a testing mode, so out of the box it is not comfortable to play this way:

| Problem in stock NullVR | What NullVR Theater does |
|---|---|
| Each eye is a fixed 2048x2048 square, so it never matches a monitor's shape | Each eye matches the monitor the game window is on (or a profile you choose), with a matching projection |
| The NullVR FOV slider is vertical only | The slider can set the horizontal FOV instead (default) |
| Turning is speed-based with a dead zone (seated "keyhole" forced to 1 degree), and horizontal loses mouse movement every frame, so it feels sluggish next to vertical; the vanilla mouse sensitivity slider is ignored | 1:1 aiming that follows the vanilla Mouse Sensitivity slider, exact and equal on both axes |
| In menus the Windows cursor is drawn over the SBS window, so it shows in one eye only | The Windows cursor is hidden over the game window and kept inside it while the game is focused |
| FOV effects (sprinting, Speed, bows, spyglass) are disabled in VR | FOV effects work again, scaled by the vanilla FOV Effects slider |
| The seated HUD floats straight ahead, so the hotbar sits near the middle of the view | **HUD height** lowers the HUD (hotbar) toward the bottom of the view, plus HUD size and distance |
| Depth can only be set through Vivecraft's NullVR IPD (5 cm minimum), and everything pops out in front of the screen | **Depth strength** (0-300%, down to flat) and **Focus distance** (which distance sits at the screen surface) |
| All settings live in config files | An in-game settings screen in Mod Menu; saving applies changes immediately |

## Requirements

- Minecraft **26.2** with Fabric Loader
- **Vivecraft** for 26.2 (tested with `26.2-1.3.15`)
- Recommended: [Mod Menu](https://modrinth.com/mod/modmenu) and [Cloth Config](https://modrinth.com/mod/cloth-config) for the in-game settings screen
- Windows (cursor confinement uses the Windows API; everything else is cross-platform but untested)

## Setup

1. Put `nullvr-theater-<version>.jar` in your `mods` folder next to Vivecraft.
2. In Vivecraft, open **VR Settings > Stereo Rendering** and set:
   - **VR Plugin**: `NullVR`
   - **Desktop Mirror**: `Dual` (other mirror modes break the SBS layout)
3. Turn VR on from the title screen and run Minecraft **fullscreen on a 16:9 monitor**.
4. In your viewer, share that monitor and enable **half side-by-side** 3D.
   In Bigscreen that is the SBS toggle; the shared monitor must be the main display, and 21:9 ultrawide
   monitors do not work with Bigscreen's SBS mode.

If the depth looks inside-out, turn on **Dual Mirror Swap** in Vivecraft's Stereo Rendering settings.

## Configuration

Open **Mods > NullVR Theater > settings** in game. Settings are grouped into **Display**, **Depth**, **HUD**
and **Mouse & Cursor**, and pressing **Save** applies them immediately (eye size, field of view and depth
included), without toggling VR. The Display tab also has Vivecraft's NullVR field of view, the side-by-side
mirror (Dual for 3D, Cropped for one eye), Swap eyes and the FOV effects toggle. The HUD tab also has
Vivecraft's HUD size and distance; setting the HUD distance to your focus distance puts the HUD on the screen
surface.

Everything is stored in `config/nullvr-theater.properties`, which can also be edited by hand; it is
re-read every time NullVR starts.

| Key | Default | Meaning |
|---|---|---|
| `mode` | `auto` | `auto` sizes each eye to the monitor the game window is on; otherwise the name of a profile |
| `profile.<name>` | `standard=2560x1440`, `hd=1920x1080`, `square=2048x2048` | Size of **one** eye. Add as many as you like |
| `scale` | `1.0` | Multiplies the eye size (e.g. `0.75` for more FPS, same shape) |
| `fovIsHorizontal` | `true` | The NullVR FOV slider sets the horizontal FOV (`false` = Vivecraft's vertical behaviour) |
| `depthPercent` | `100` | 3D strength as a % of average eye spacing (6.4 cm); `0` = flat |
| `focusDistance` | `0` | Metres that sit exactly at the screen surface; `0` = infinity (everything in front of the screen) |
| `fovEffects` | `true` | Vanilla FOV changes (sprinting, Speed, bows, spyglass), scaled by the vanilla FOV Effects slider |
| `hudHeight` | `-12` | Degrees to tilt the in-game HUD from straight ahead; negative = lower. `0` = Vivecraft's placement |
| `vanillaMouse` | `true` | 1:1 aiming from the vanilla Mouse Sensitivity slider (`false` = Vivecraft's seated aiming) |
| `hideCursor` | `true` | Hide the Windows cursor over the game window |
| `confineCursor` | `true` | Keep the cursor inside the game window while it is focused |

Setting `mode` to a square profile with `fovIsHorizontal=false`, `vanillaMouse=false`, `focusDistance=0`,
`fovEffects=false` and `hudHeight=0` gives stock NullVR rendering and aiming.

If the game freezes while the cursor is confined, **Ctrl+Alt+Del** releases it.

## How it works

The mod only touches Vivecraft classes (plus GLFW and the Windows cursor API), using Mixin:

- `NullVRStereoRenderer.getRenderTextureSizes()`: replaces the hard-coded `2048x2048` eye size.
- `NullVRStereoRenderer.getProjectionMatrix()`: replaces the hard-coded aspect ratio of `1`, optionally
  converts the FOV slider from vertical to horizontal, applies FOV effects, and shifts each eye's frustum for
  the focus distance.
- `NullVR.getIPD()`: returns the eye spacing from the depth strength.
- `NullVR.poll()`: per frame, hides and confines the cursor, eases FOV effects the way vanilla does, and maps
  the vanilla sensitivity onto Vivecraft's seated aim (vertical).
- `MCVR.updateAim()`: turns horizontally by the exact cursor movement each frame and re-centres the cursor on a
  whole pixel, instead of Vivecraft's keyhole, which drops movement every frame.
- `GuiHandler.extractGui()`: tilts the in-game HUD panel down by the HUD height.

The Dual mirror stretches each eye into half of the window, so an eye rendered at the monitor's shape ends up
squeezed to half width, which is exactly what half-SBS viewers expect to stretch back out.

These hooks target Vivecraft internals by name and require them to exist, so a Vivecraft update that changes
them stops the game with a clear Mixin error instead of rendering incorrectly.

## Building

No Gradle needed. `build.sh` compiles with the JDK bundled with Prism Launcher against the Mixin, LWJGL,
JOML, Minecraft, Vivecraft, Cloth Config and Mod Menu jars already on disk (paths are set for a Prism instance named `26.2`; adjust as
needed):

```sh
bash build.sh   # -> build/nullvr-theater-<version>.jar
```

## Tested with

- Minecraft 26.2, Fabric Loader 0.19.5, Vivecraft 26.2-1.3.15, Sodium 0.9.1, Iris 1.11.2, Voxy 0.2.19-beta
- Windows 11, AMD Radeon RX 9070 XT
- Valve Steam Frame via Steam Link, viewing in Bigscreen Beta on a 2560x1440 monitor

## License

[MIT](LICENSE)
