# CLAUDE.md

Guidance for Claude Code sessions in this repo. Read `docs/DEV_NOTES.md` too: it holds the architecture, every
two-eye workaround and why, the test method and open items. Update it at the end of every work session.

## Project

**Parallax Theater** (mod id `parallax_theater`, package `nzy.parallaxtheater`): a standalone half side-by-side
stereo renderer for Minecraft 26.2 (Fabric), no Vivecraft. Split on 2026-10-02 from the `dev` branch of
`nullvr-theater` (private, https://github.com/nick-yoderia/nullvr-theater), where it was called "Stereo Theater".
NullVR Theater (the Vivecraft add-on) is maintained separately in that repo; don't change it from here.

## Rules from the user

- Commits are authored as "Nick Y" (repo-local `user.name`). **Never** add `Co-Authored-By`, "Generated with
  Claude" or any other Claude attribution to commits, tags, PRs or release notes.
- Commit locally as you go; push only when the user approves (the initial push of the split was requested).
- Versions are alpha (`0.x.y-alpha`); the version lives in `src/main/resources/fabric.mod.json`.
- License: MIT, "Copyright (c) 2026 Nick Y".
- If a tool call (e.g. a game relaunch) is rejected, the user is probably playing: skip it, keep working on
  something that doesn't need the game, and mention it in the report. Don't stop.
- Don't leave the game running when finishing a session (the user may be away from the computer).

## Build

No Gradle. `bash build.sh` (Git Bash; from PowerShell `bash` may resolve to WSL, so use the Bash tool) compiles with
the JDK bundled with Prism Launcher against jars already on disk and writes `build/parallax-theater-<version>.jar`.
Optional-mod classes are only referenced from mixins in `nzy.parallaxtheater.mixin.<modid>`, which
`CompatMixinPlugin` applies only when that mod is loaded.

## Test loop

- Test instance: Prism instance **26.2-Stereo-Dev** (`%APPDATA%\PrismLauncher\instances\26.2-Stereo-Dev`), world
  "vr craft" (a copy) with Sodium, Iris (BSL/Complementary), Voxy and the user's other performance mods. The user's
  real instance **26.2** runs Vivecraft + NullVR Theater; never modify it.
- Helper scripts in `..\mcdev` (see DEV_NOTES "Testing"): `cycle.ps1`, `cmd.ps1`, `keys.ps1`,
  `shot.ps1`, `disp2.py`.
- The game log is `minecraft/logs/latest.log` in the instance; the mod logs with the prefix `[Parallax Theater]`.

## Code style

- Minecraft 26.2 is not obfuscated: mixins use `remap = false` and real names; mixin members are prefixed
  `parallaxTheater$`.
- Javadoc-style comments explain *why* (what the game or another mod does that needs changing); keep that density.
- Settings live in `StereoConfig` (properties file + Cloth Config screen in `StereoConfigScreen`).
