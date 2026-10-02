# CLAUDE.md

Guidance for Claude Code sessions in this repo. Read `docs/DEV_NOTES.md` too: it holds the architecture, every
two-eye workaround and why, the test method and open items. Update it at the end of every work session.

## Project

**Parallax Screen** (mod id `parallax_screen`, package `nzy.parallaxscreen`): renders Minecraft 26.2 (Fabric) in
stereo and shows it on a virtual screen in SteamVR (an OpenVR overlay fed each eye at full resolution), with no
monitor, Bigscreen or VR mod needed.

Started on 2026-10-02 as a copy of **Parallax Theater** 0.1.5-alpha (https://github.com/nick-yoderia/parallax-theater,
local `..\parallax-theater`), the half side-by-side version for Bigscreen. The user keeps that one for
watching together with someone in Bigscreen. The two are separate mods: **never merge this repo into Parallax
Theater.** Renderer fixes may flow the other way: the `theater` remote points at the local parallax-theater repo
(`git fetch theater && git merge theater/main`; git follows the package rename).

## Rules from the user

- Commits are authored as "Nick Y" (repo-local `user.name`). **Never** add `Co-Authored-By`, "Generated with
  Claude" or any other Claude attribution to commits, tags, PRs or release notes.
- Commit locally as you go; push / create the GitHub repo only when the user approves.
- Versions are alpha (`0.x.y-alpha`, starting at 0.1.0-alpha); the version lives in `src/main/resources/fabric.mod.json`.
- License: MIT, "Copyright (c) 2026 Nick Y".
- If a tool call (e.g. a game relaunch) is rejected, the user is probably playing: skip it, keep working on
  something that doesn't need the game, and mention it in the report. Don't stop.
- Don't leave the game running when finishing a session (the user may be away from the computer).

## Build

No Gradle. `bash build.sh` (Git Bash; from PowerShell `bash` may resolve to WSL, so use the Bash tool) compiles with
the JDK bundled with Prism Launcher against jars already on disk and writes `build/parallax-screen-<version>.jar`.
Optional-mod classes are only referenced from mixins in `nzy.parallaxscreen.mixin.<modid>`, which
`CompatMixinPlugin` applies only when that mod is loaded. LWJGL's OpenVR bindings are bundled (see DEV_NOTES).

## Test loop

- Test instance: see DEV_NOTES "Testing". The user's real instance **26.2** runs Vivecraft + NullVR Theater; never
  modify it.
- Helper scripts in `..\mcdev`: `cycle.ps1`, `cmd.ps1`, `keys.ps1`, `shot.ps1`, `disp2.py`.
- The game log is `minecraft/logs/latest.log` in the instance; the mod logs with the prefix `[Parallax Screen]`.

## Code style

- Minecraft 26.2 is not obfuscated: mixins use `remap = false` and real names; mixin members are prefixed
  `parallaxScreen$`.
- Javadoc-style comments explain *why* (what the game or another mod does that needs changing); keep that density.
- Settings live in `StereoConfig` (properties file + Cloth Config screen in `StereoConfigScreen`).
