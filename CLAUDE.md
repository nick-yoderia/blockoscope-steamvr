# CLAUDE.md

Guidance for Claude Code sessions working in this repo. Read `docs/DEV_NOTES.md` too: it holds the
architecture, findings and current status, and must be kept up to date at the end of every work session.

## Branches

- `main`: **NullVR Theater**, an add-on for Vivecraft's NullVR (released as alpha tags, e.g. `v0.0.3-alpha`).
  Considered "good enough"; don't change it unless asked.
- `dev`: **Stereo Theater** (mod id `stereo_theater`, package `nzy.stereotheater`), a standalone half
  side-by-side stereo renderer with no Vivecraft. All new work happens here.
- Stay on `dev` until the user approves merging into `main` or moving to a separate repo/fork.

## Rules from the user

- Commits are authored as "Nick Y" (repo-local `user.name`). **Never** add `Co-Authored-By`, "Generated with
  Claude" or any other Claude attribution to commits, tags, PRs or release notes.
- Commit locally as you go, but **do not push** until the user says the build works.
- Versions are alpha (`0.x.y-alpha`); the version lives in `src/main/resources/fabric.mod.json`.
- License: MIT, "Copyright (c) 2026 Nick Y".
- If a tool call (e.g. a game relaunch) is rejected, the user is probably playing: skip it, keep working on
  something that doesn't need the game, and mention it in the report. Don't stop.

## Build

No Gradle. `bash build.sh` compiles with the JDK bundled with Prism Launcher against jars already on disk
(Minecraft client, Mixin, LWJGL, JOML, Fabric Loader, Cloth Config, Mod Menu, Iris, Voxy, Sodium) and writes
`build/stereo-theater-<version>.jar`. Optional-mod classes are only referenced from mixins in
`nzy.stereotheater.mixin.<modid>`, which `CompatMixinPlugin` applies only when that mod is loaded.

## Test loop

- Test instance: Prism instance **26.2-Stereo-Dev** (`%APPDATA%\PrismLauncher\instances\26.2-Stereo-Dev`),
  world "vr craft" (a copy). The user's real instance **26.2** runs Vivecraft + NullVR Theater; never modify
  it (it is a reference for mod versions and the user's settings).
- Helper scripts live outside the repo in `..\mcdev` (research area, delete when done):
  - `cycle.ps1 [-Shot name] [-Enable]`: closes only the dev instance, installs the built jar, launches straight
    into the world, waits for "joined the game", prints Stereo/ERROR log lines and saves a screenshot to
    `mcdev\shots\<name>.png`.
  - `shot.ps1 name`, `keys.ps1 -Keys ...` (SendKeys), `click.ps1 -X -Y`.
  - `disparity.py shot.png y0,y1,x0,x1 ...`: measures left/right disparity of image regions in an SBS shot
    (positive = behind the screen).
  - `src/mc`, `src/vivecraft`, ...: Vineflower decompiles (`tools/vineflower.jar`) for reading game/mod code.
- The game log is `minecraft/logs/latest.log` in the instance; the mod logs with the prefix `[Stereo Theater]`.

## Code style

- Minecraft 26.2 is not obfuscated: mixins use `remap = false` and real names.
- Javadoc-style comments explain *why* (what the game does that needs changing); keep that density.
- Keep settings in `StereoConfig` (properties file + Cloth Config screen in `StereoConfigScreen`).
