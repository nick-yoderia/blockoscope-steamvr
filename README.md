# Parallax Theater

> **Alpha.** Built and tested on one setup (see [Tested with](#tested-with)). Expect rough edges.

A Fabric mod that renders Minecraft in **half side-by-side (SBS) stereoscopic 3D**. Show the game window on a virtual
screen in a headset (for example in Bigscreen) and you get real depth, like a 3D movie: moving your head moves your
view of the screen, not the game camera. No VR mod is involved, so the game plays exactly like normal Minecraft:
vanilla HUD, menus, animations and mouse.

It grew out of [NullVR Theater](https://github.com/nick-yoderia/nullvr-theater), which does the same through
Vivecraft's NullVR mode. Parallax Theater drops Vivecraft and its VR-specific behaviour (VR body animations, floating
HUD panels, VR comfort changes) and renders the vanilla game twice instead.

## Features

- **Stereo rendering:** every frame is drawn once per eye and packed into half side-by-side. Each eye renders at half
  the window width, so the total pixel count is about the same as one normal frame.
- **Depth controls:** depth strength (eye spacing, 100% = natural) and **focus distance**, the distance that sits
  exactly on the screen surface (default 10 m). Nearer things pop out of the screen, farther things sit behind it.
- **HUD and menus in depth:** the HUD and menus float at a set distance (default 1.35 m), with a cursor drawn in both
  eyes in menus (the Windows cursor would only show in one).
- **Crosshair at the depth of what you aim at,** read from what is actually drawn under it (terrain at any
  distance, entities, Voxy's distant terrain), so the crosshair and the target never double. Over the sky it holds
  the last depth and eases to the screen surface instead of jumping to infinity.
- **Hand depth:** your hand and held item have real depth (adjustable, 0–200%), and in 3D the arm reaches a little
  further forward so more of what you hold is in view (Arm reach, plus optional raise/inward offsets).
- **Comfort options:** camera bobbing and damage tilt are off by default in 3D (the hand still bobs), and nausea and
  portal warp are toned down. Each can be turned back on.
- **F9** switches between 3D and normal 2D at any time.
- **Render scale** (25–200% of the half-window resolution) to trade sharpness for speed.

### Works with

- **Sodium**
- **Iris** shader packs (tested with BSL and Complementary Reimagined). Each eye gets its own shader pipeline, so
  temporal effects (TAA, clouds, previous-frame data) never mix the two eyes.
- **Voxy** distant terrain, with and without shader packs.
- Mod Menu + Cloth Config for the settings screen.

Every 3D correctness issue found so far was measured on screenshots: blocks, entities, particles, clouds, sky, Voxy
terrain, the hand and the crosshair all line up with the depth they should have in both eyes.

## Requirements

- Minecraft **26.2** with Fabric Loader
- Recommended: [Mod Menu](https://modrinth.com/mod/modmenu) and [Cloth Config](https://modrinth.com/mod/cloth-config)
- Not compatible with Vivecraft (use NullVR Theater for that)
- Windows for cursor confinement (everything else is cross-platform but untested)

## Setup

1. Put `parallax-theater-<version>.jar` in your `mods` folder.
2. Run Minecraft **fullscreen on a 16:9 monitor**.
3. In your viewer, share that monitor and enable **half side-by-side** 3D. In Bigscreen that is the SBS toggle; the
   shared monitor must be the main display (21:9 ultrawide monitors don't work with Bigscreen's SBS mode).

If the depth looks inside-out, turn on **Swap eyes**.

## Settings

**Mods > Parallax Theater** (needs Mod Menu and Cloth Config). Saving applies changes immediately. Everything is also
stored in `config/parallax-theater.properties`:

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | 3D on (F9 toggles it) |
| `renderScale` | `100` | % of the half-window resolution each eye renders at |
| `depthPercent` | `100` | 3D strength as a % of average eye spacing (6.4 cm); `0` = flat |
| `focusDistance` | `10` | Metres that sit exactly on the screen surface; `0` = infinity |
| `swapEyes` | `false` | Right eye on the left half (cross-eyed order) |
| `cameraBobbing` | `false` | Vanilla view bobbing of the camera in 3D |
| `damageTilt` | `false` | Camera roll when hurt or dying, in 3D |
| `warpPercent` | `40` | Nausea and portal warp strength in 3D, % of vanilla's |
| `hudDistance` | `1.35` | Metres at which the HUD and menus float; `0` = on the screen surface |
| `crosshairAtTarget` | `true` | Crosshair at the depth of what it points at (`false` = with the HUD) |
| `handDepthPercent` | `100` | Depth of your hand and held item, % of the world's |
| `handReach` | `30` | Centimetres the arm reaches further forward in 3D (as if longer); `0` = vanilla |
| `handRaise` | `0` | Centimetres the hand and held item are raised in 3D |
| `handInward` | `0` | Centimetres the hand and held item are moved towards the middle in 3D |
| `hideCursor` | `true` | Hide the Windows cursor and draw one in both eyes |
| `confineCursor` | `true` | Keep the cursor inside the game window while it is focused |

If the game freezes while the cursor is confined, **Ctrl+Alt+Del** releases it.

## How it works

Minecraft 26.2 extracts everything it draws into a render state once per frame, then renders from that state. Parallax
Theater runs the render half twice: for each eye the main render target is swapped for a half-width eye target, the
camera moves sideways by half the eye spacing, and the projection is shifted so things at the focus distance line up
in both eyes. The two eye images are then drawn into the left and right halves of the window.

Rendering the same frame twice exposes per-frame caches in the game and in other mods, which the mod works around:
entity and particle lists kept for the second eye, Sodium's terrain matrices written per eye, the sky and clouds drawn
into the right eye, a separate Iris pipeline and Voxy viewport per eye, and so on. `docs/DEV_NOTES.md` has the full
list.

## Building

No Gradle. `build.sh` compiles with the JDK bundled with Prism Launcher against jars already on disk (Minecraft,
Mixin, LWJGL, JOML, Fabric Loader, Cloth Config, Mod Menu, Iris, Sodium, Voxy); paths are set for the author's Prism
instances, adjust as needed:

```sh
bash build.sh   # -> build/parallax-theater-<version>.jar
```

Iris, Sodium and Voxy are only compiled against; their integration loads only when they are installed.

## Tested with

- Minecraft 26.2, Fabric Loader 0.19.5, Sodium 0.9.1, Iris 1.11.2 (BSL 10.1.8, Complementary Reimagined r5.9.3),
  Voxy 0.2.19-beta, Sodium Extra, MoreCulling, Lithium, FerriteCore, BadOptimizations
- Windows 11, AMD Radeon RX 9070 XT
- Valve Steam Frame via Steam Link, viewing in Bigscreen Beta on a 2560x1440 monitor

## License

[MIT](LICENSE)
