# CLAUDE.md

Guidance for Claude Code sessions in this repo. Read `docs/DEV_NOTES.md` too: it holds the architecture, every
two-eye workaround and why, the test method and open items. Update it at the end of every work session.

## Project

**Blockoscope SteamVR** (mod id `blockoscope_steamvr`, package `nzy.blockoscope.steamvr`): renders Minecraft 26.2 (Fabric) in
stereo and shows it on a virtual screen in SteamVR (an OpenVR overlay fed each eye at full resolution), with no
monitor, Bigscreen or VR mod needed.

Started on 2026-10-02 as a copy of **Blockoscope SBS** 0.1.5-alpha (then named Parallax Theater;
https://github.com/nick-yoderia/blockoscope-sbs, local `..\blockoscope-sbs`), the half side-by-side
version for Bigscreen. The user keeps that one for watching together with someone in Bigscreen. The two are separate
mods: **never merge this repo into Blockoscope SBS.** Renderer fixes may flow the other way: the `sbs` remote points
at the local blockoscope-sbs repo (`git fetch sbs && git merge sbs/main`; git follows the package rename).

Renamed from **Parallax Screen** (`parallax_screen`, `nzy.parallaxscreen`) to Blockoscope SteamVR in 0.2.0-alpha; the
GitHub repo was renamed too (old URLs redirect), and `StereoConfig` reads `config/parallax-screen.properties` once
if there is no settings file yet. The development files moved with it (`config/blockoscope-steamvr.vrenv`,
`.blockoscope-steamvr/` for the extracted DLL and texture dumps).

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
the JDK bundled with Prism Launcher against jars already on disk and writes `build/blockoscope-steamvr-<version>.jar`.
Optional-mod classes are only referenced from mixins in `nzy.blockoscope.steamvr.mixin.<modid>`, which
`CompatMixinPlugin` applies only when that mod is loaded. SteamVR is called through Java's foreign function API
on the bundled `openvr_api.dll` (`OpenVrApi`), and the screen goes over as a D3D11 texture (`D3dShare`); see DEV_NOTES.

## Modpack

`modpack/Blockoscope-SteamVR.mrpack` is linked from the README as the rolling download (raw link on `main`); rebuild
it after every release and commit it:
`python modpack/make-pack.py --instance "$APPDATA/PrismLauncher/instances/26.2-Blockoscope-SteamVR-Dev" --jar build/blockoscope-steamvr-<version>.jar
--name "Blockoscope SteamVR" --summary "..." --out modpack/Blockoscope-SteamVR.mrpack`. Never bundle third-party files
or anything outside the script's config allowlist (worlds, options.txt, logs, sodium-fingerprint.json hold personal data).

## Test loop

- Test instance: see DEV_NOTES "Testing". The user's real instance **26.2** runs Vivecraft + NullVR Theater; never
  modify it.
- Helper scripts in `..\mcdev`: `cycle-screen.ps1`, `stress-screen.sh`, `winshot.ps1`, `cmd.ps1`, `keys.ps1`, `shot.ps1`, `disp2.py`.
- The game log is `minecraft/logs/latest.log` in the instance; the mod logs with the prefix `[Blockoscope SteamVR]`.

## Code style

- Minecraft 26.2 is not obfuscated: mixins use `remap = false` and real names; mixin members are prefixed
  `blockoscopeSteamVr$`.
- Javadoc-style comments explain *why* (what the game or another mod does that needs changing); keep that density.
- Settings live in `StereoConfig` (properties file + Cloth Config screen in `StereoConfigScreen`).
