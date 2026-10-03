# Development notes

Engineering notes for Blockoscope SteamVR (named Parallax Screen until 0.1.0-alpha), kept up to date at the end of each work session. `CLAUDE.md` has the
workflow (build, test loop, rules); this file has the how and why. Everything up to "SteamVR screen" is shared with
Blockoscope SBS (this repo started from its 0.1.5-alpha); version history before 0.1.0 is Blockoscope SBS's.

## SteamVR screen (what Blockoscope SteamVR adds)

Output: `StereoRenderer.render` asks `VrScreen.active()`. When the SteamVR screen is up, each eye target is
`VrScreen.eyeResolution()` wide in the window's aspect (setting, or automatic: SteamVR's recommended render width over
the eye's tangent span (`GetRecommendedRenderTargetSize`, `GetProjectionRaw`) times screen width / distance, i.e. the
headset pixels across the screen at the centre of view; the null driver gives 926 px per tangent, 1200 px for 2.6 m at
2 m) (so the projection and the GUI layout, which follow the window, still
fit), and the eyes are packed into `screenTarget` (2 x eye width); the GUI-over-window pass draws into it (`packedWidth`
/`packedHeight` drive `guiArea*`), `VrScreen.submit` hands it to the overlay (through `D3dShare`), and the window gets a preview
(`EyeBlit.drawFull`, left half only unless `previewBothEyes`). Without SteamVR everything is Blockoscope SBS's half
side-by-side window output.

`VrScreen`: OpenVR **overlay application** (not a scene app): SteamVR keeps its own scene and composites the overlay
at the headset rate, so head motion is smooth regardless of game FPS, and the game doesn't need to track the head.
Overlay flags `SideBySide_Parallel` (left half to the left eye) and `IgnoreTextureAlpha` (the GUI leaves alpha < 1).
Placement: `place()` puts it `screenDistance` ahead of the HMD along its heading only (yaw), level, at eye height +
`screenHeight`; F8 (`ToggleKey`) or changing distance/height re-places it. Tracking space: seated when SteamVR has a
seated origin (so "reset seated position" carries the screen along), standing otherwise (the null driver has no seated
origin: its seated HMD pose is flagged invalid although tracking is "Running_OK"). Width/curve are re-sent when they change;
true scale computes the width from the game's FOV setting and window aspect (`StereoRenderer.trueScaleScreenWidth`)
and uses the screen distance as focus distance (`StereoRenderer.focusDistance()`).

Connecting (`VrScreen.connect`, daemon thread, every 5 s while not connected): only when `vrserver.exe` is running
(`ProcessHandle`), so the mod never launches SteamVR (initialising an overlay app would), and quitting SteamVR
(`VREvent_Quit` -> `AcknowledgeQuit_Exiting`, `stop`) doesn't bring it back. Verified: detach on quit, re-attach
within 5 s of SteamVR starting. A shutdown hook calls `VR_ShutdownInternal` when the game exits.

Texture handoff (`D3dShare`, 0.1.0): SteamVR gets a **Direct3D 11 texture**, not the GL one. Handing it the GL id
(the first version) makes vrclient share the texture itself through the driver's GL/D3D interop ("Scene create
OpenGL" in `vrclient_javaw.txt`); on the RX 9070 XT that once froze the render thread for good inside
`SetOverlayTexture` (native stack: vrclient_x64 -> atio6axx -> WaitForSingleObject; no CPU use; still stuck after
SteamVR was killed; the compositor kept running). It happened 1.5 min after a SteamVR restart while opening Mod Menu
and could not be reproduced on demand (idle, menus, re-attach, F8, ~10 min at 225 FPS). Now the mod creates its own
D3D11 device (`D3D11CreateDevice`, default adapter, i.e. the desktop GPU the GL context runs on), one
R8G8B8A8_UNORM texture registered with GL via `WGL_NV_DX_interop` (`wglDXOpenDeviceNV`/`RegisterObjectNV`, procs from
`wglGetProcAddress`), and each frame locks it, `glCopyImageSubData`s the packed target into it, unlocks and submits
it as `TextureType_DirectX` ("Scene create D3D11"). Only this process touches that texture, so the lock waits only on
our own GPU work, and SteamVR's D3D11 overlay path is the one most overlay apps use. Stress-tested
(`mcdev\stress-screen.sh`: 140 menu open/close cycles, 14 SteamVR force-kills and restarts) without a hang. If any
step fails, the GL id is submitted as before and the log says why. COM calls go through vtable slots
(`ID3D11Device::CreateTexture2D` 5, `IUnknown::Release` 2).

Frame pacing (`VrScreen.pace`, `syncToHeadset`, default on): after each submit, `IVROverlay::WaitFrameSync` (slot
45, timeout one refresh + 5 ms) blocks until the compositor's next frame, so the game makes one frame per headset
refresh instead of beating against it (uneven motion) and leaves GPU time to the compositor. It is skipped while the
averaged frame work (time from the end of one wait to the next submit, EMA 0.1) exceeds 85% of a refresh at the
headset's `Prop_DisplayFrequency_Float` (2002, via `GetFloatTrackedDeviceProperty`, IVRSystem slot 22), so a slow
game runs free rather than halving like VSync; three timeouts in a row pause it for 3 s. Measured with the null
driver: its compositor draws at the *monitor's* rate (144 Hz here, although the null HMD reports 90 Hz), and the game
locked to it: ~137-140 FPS, 5 ms work + 1.9 ms wait per frame (debug log line `pace:` every 500 frames). While the
screen is up `FramerateLimitTrackerMixin` returns no throttle reason (vanilla drops to 30 FPS after 60 s without
input, 10 after 10 min or when minimised; someone watching in the headset gives no input), verified at 140 FPS after
80 s idle, and `MinecraftMixin` passes vsync=false to `PresentMode.getSupportedVsyncMode` and reconfigures the window
surface when the screen comes or goes (the monitor's VSync would pace the game to the monitor).

Texture bounds: the GL id needs plain 0..1 (SteamVR accounts for GL's bottom-up rows itself, as for Vivecraft's
eye textures); the D3D11 copy arrives upside down (GL row 0 is the bottom one), so `D3dShare.FLIPPED` flips the
bounds; `flipScreen` flips once more. Both verified upright in the null headset's compositor window.

Also seen in the compositor: `screenCurvature` 30 bends the screen around the viewer in both eyes; `trueScale` with
the instance's FOV 90 makes the screen ~7 m wide at 2 m (it fills the null headset's view), auto resolution follows
(3296 px per eye, a 6592x1854 texture, ~75 FPS, so pacing correctly runs free at 13 ms of work per frame). True scale
is expensive at high FOV settings because everything the game's FOV covers is rendered at headset density.

Seeing the null headset's output: capture the compositor's "Headset Window" (`winshot.ps1 -Process vrcompositor`)
while the PC is unlocked; it shows the overlay in both eyes. Disable the dashboard in the test config
(`dashboard.enableDashboard false`) or it covers the screen; "Room Setup / Waiting" remains (no room setup in the test
config). First compositor measurement (hotbar against the screen frame): -15 headset px, distant hills +4: the eyes
are the right way round. What did *not* work: SteamVR's stereo screenshot (needs a scene app's textures), PrintWindow
while locked, and the compositor mirror texture (`GetMirrorTextureGL`), which leaves overlays out.

### OpenVR binding (`OpenVrApi`)

LWJGL's OpenVR bindings (last release 3.3.6, which Vivecraft bundles) don't load on LWJGL 3.4 (Minecraft 26.2):
`VR.<clinit>` reads `Configuration.OPENVR_LIBRARY_NAME`, removed in 3.4, and many calls use JNI helpers whose
signatures changed. Vivecraft patches about ten LWJGL classes with mixins for that. Instead, `OpenVrApi` calls Valve's
`openvr_api.dll` (bundled at `natives/windows-x64/`, from the LWJGL 3.3.6 natives jar, OpenVR commit ae46a8dd, BSD) with
Java 25's foreign function API (hence `--release 25`): the exports `VR_InitInternal2`, `VR_ShutdownInternal`,
`VR_GetGenericInterface("FnTable:IVRSystem_022" / "FnTable:IVROverlay_027")`, then function pointers by slot. Slot
numbers were read from LWJGL 3.3.6's `OpenVR$IVROverlay`/`$IVRSystem` constructors (generated from openvr_capi.h);
struct sizes (VREvent_t 64, TrackedDevicePose_t 80 with bPoseIsValid at 76, Texture_t 16) from LWJGL's struct classes.
The DLL is copied to `.blockoscope-steamvr/openvr_api.dll` in the game folder and loaded from there.

## Render flow

`MinecraftMixin` replaces `GameRenderer.render(deltaTracker, renderLevel)` in `Minecraft.renderFrame` with
`StereoRenderer.render`, which (when 3D is on and nothing is loading):

1. Resizes/creates two eye `TextureTarget`s (half window width x height, times the render scale).
2. For each eye (`pass` 0 = first rendered, `eye` LEFT/RIGHT): swaps `GameRenderer.mainRenderTarget` for the eye
   target, moves `CameraRenderState.pos` *and* the `Camera` object (`CameraAccessor.setPosition`) sideways by half
   the eye spacing, shears `CameraRenderState.projectionMatrix` (m20) so the focus distance has zero disparity, then
   calls the vanilla `GameRenderer.render`.
3. Between the eyes calls `LevelRenderer.endFrame()` so per-frame ring buffers move on (see below).
4. Restores everything and packs the eyes into the window with `EyeBlit` (a custom pipeline whose vertex shader
   squeezes a full-screen triangle into one half; 26.2 can't copy into a texture at an offset on OpenGL).

The GUI is extracted once, prepared (meshed and uploaded) in the first eye and drawn into both; its projection is
shifted per eye for the GUI depth (`eyeGuiProjection`), the crosshair draw gets its own projection for the aimed
distance (`eyeCrosshairProjection`), and the hand projection gets the eye offset plus the focus shift in clip space
(`eyeHandProjection`).

GUI depth (`StereoRenderer.updateGuiDepth`): menus and other screens (not chat) sit on the screen surface
(`menuDistance`, 0 = screen). The in-game HUD (`hudDepth`, 0.1.4) sits on the nearest block outline or entity hitbox
behind the hotbar and status bars (`hudSceneDepth`: 15 rays from the centre camera through a 5 x 3 grid over that
screen area, depth along the view, 64 m, no fluids; nothing hit = crosshair depth), or at the crosshair's depth
(`aim`, 0.1.3's behaviour), or at the fixed `hudDistance`; eased over ~120 ms. The user found 0.1.3's aim-locked
hotbar still disorienting when a block covered the hotbar but not the crosshair.

GUI target (0.1.4, `drawGuiOverWindow`): a GUI without blur or panorama (the in-game HUD, chat) is not drawn in the
eye renders (`GameRendererMixin` skips `GuiRenderer.render`/`endFrame`) but into the window after the eyes are packed,
once per half: `guiProjection` squeezes the GUI into the eye's half and `GuiRendererMixin` keeps every draw scissored
to that half. Each eye's shift is rounded to whole pixels (`snapToPixels`). Before, at render scale 80 the crosshair's
2-window-pixel lines landed on different pixel fractions in each eye (1 vs 2 px wide), which the user saw as a
slightly doubled crosshair; now both eyes get identical pixels, just moved, and the HUD is sharp at any render scale.
Menus (blur) still render into the eye targets, since the blur needs the eye's own world. A flat panel facing the viewer differs between the eyes only by a
uniform sideways shift, so the shift is the whole stereo treatment; the problem in 0.1.2 was *which* depth. With the
HUD fixed at 1.2 m (user's setting; measured -11 px per eye at FOV 90, focus 4 m, depth 81%) and the world at 4 m+,
the hotbar was seen double whenever the eyes converged on the world/crosshair, and menus' full-screen backdrop stuck
out of the screen and was cut off by its edges (frame violation). The user reported "doubling in the menus and in game
hotbar and crosshair".

Measured 0.1.3 (BSL, FOV 90, focus 4 m, depth 81%): menus, pause screen, creative inventory and tooltip 0.00 px and
pixel-identical between the eyes apart from the blurred world behind; aimed at nothing (creative reach 5 m) hotbar
+1.0 px, crosshair +0.7 px (formula +0.9); aimed at grass 0.99 m away hotbar -14.7 px, crosshair -14.2 px (formula
-14.2). Chat keeps the HUD depth. The user's dev instance has E/Q swapped (Q = inventory, E = drop).

Measured 0.1.4 (same settings): crosshair identical 2 px lines in both eyes; looking 55 degrees down the hotbar at
-34 px over ground measured at -30 px; level view with a grass block under the hotbar's left side: hotbar -24 px
(that block -24 px) while the crosshair rests at +2 px; pause menu and inventory 0 px; FPS unchanged (~225).

Crosshair depth (`StereoRenderer.updateCrosshairDepth`): within reach, the distance (along the view) to the game's
own `hitResult` (block outline shape, so grass counts; entity hitboxes); with nothing in reach, the edge of the
player's block reach (`Player.blockInteractionRange()`, 4.5 in survival) plus `crosshairRestOffset`. Eased over ~25 ms. History: 0.1.1 read the depth buffer under the
crosshair instead (GPU read-back via `glGetTextureSubImage`; Blaze3D's `copyTextureToBuffer` can't read depth). The
user found that worse: it looked through grass and followed far scenery, which was disorienting, so it was removed.

Hand position (`ItemInHandRendererMixin`, 3D only): the arm's pose is translated forward (`handReach`, default
30 cm), optionally up/inward, right after `submitArmWithItem` pushes its pose (used by vanilla and Iris).

Depth math: an eye at x = side * ipd/2 sees a point straight ahead at distance d shifted by
`m00 * ipd/2 * (1/d)` in NDC; the focus shear subtracts `m00 * ipd/2 * (1/focus)`. With a 70 degree vertical FOV at
16:9 and 1280 px per eye, disparity in eye pixels is `-32.9 * (1/d - 1/focus)` (negative = in front of the screen).

## Two renders per frame: what breaks and the fix

| Symptom | Cause | Fix |
|---|---|---|
| Entities/block entities/breaking only in the first eye | `LevelRenderer.submitFeatures` clears the extracted lists | `LevelRendererMixin` keeps them until the second eye |
| GUI empty in the second eye | `GuiRenderer.render` resets its state | `GuiRendererMixin` resets only after the second eye |
| Text, item icons, picture-in-picture drawn twice in the second eye (until 0.1.2) | With the state kept, `GuiRenderer.prepare` ran again and appended every glyph and item blit to it a second time | `GuiRendererMixin` prepares/uploads in the first eye only and keeps the draws, meshes and blur split for the second (`endDraw`, `endFrame`, `draws.clear`, `firstDrawIndexAfterBlur` deferred) |
| Per-frame cleanup twice | `GuiRenderer/RenderBuffers/CrossFrameResourcePool/FogRenderer.endFrame` | `GameRendererMixin` runs them after the second eye |
| Blocks much nearer than entities at the same distance | Sodium writes terrain matrices once per frame (`UniformBufferManager.hasUpdatedThisFrame`); second eye used the first eye's projection | `SodiumUniformBufferManagerMixin` re-writes per eye, tracked per uniform storage (Iris swaps in a shadow storage) |
| Washed-out sky in one eye (no shaders) | `SkyRenderer` keeps the render target it was created with | `SkyRendererMixin` always uses the current main target |
| Left-eye clouds twitch now and then | Cloud UBO (camera offset within a 12-block cell) rewritten by the second eye before the GPU drew the first; jumps a cell when a boundary falls between the eyes | `LevelRenderer.endFrame()` between eyes rotates the cloud ring buffer (and Sodium's uniform storage) |
| Particles at the wrong depth | Particle quads are stored relative to the centre camera at extraction | `QuadParticleRenderStateMixin`, `ItemPickupParticleStateMixin` add `StereoRenderer.eyeShift()` |
| Shader shadows/lighting slightly off per eye | Iris reads `Camera.position()` (centre) for uniforms and the shadow pass | the `Camera` object itself is moved per eye (Sodium's culling already ran during extraction) |
| Shader TAA/cloud history smeared between eyes | One Iris pipeline shared by both eyes | `IrisPipelineManagerMixin` gives the right eye its own pipeline; frame counter and timer advance once per frame (`IrisFrameCounterMixin`, `IrisTimerMixin`) |
| Missing clouds/fog with shaders | Iris only re-reads the depth texture when the target's "depth buffer version" changes | `IrisRenderTargetsMixin` treats a different depth texture as a change |
| No hand depth with shaders | Iris draws the hand with its own projection | `IrisHandRendererMixin` |
| Voxy LODs (and BSL clouds) missing in one eye with shaders | Voxy binds to the one Iris pipeline that existed when it started | `VoxyIrisPipelineMixin` points Voxy's framebuffers at the current eye's draw targets and links that eye's Voxy data. **It must keep Voxy's original data for the uniform block**: each pipeline lists the same uniforms in a different order, and writing another pipeline's layout made Voxy's culling read garbage (empty render list) |
| Voxy culling against the other eye | Occlusion data (HiZ, frame ids) lives in the viewport | `VoxyViewportSelectorMixin` gives the right eye its own viewport (as Voxy does for Vivecraft) |

Rule of thumb when something shows in only one eye: look for (a) state computed once per frame or at extraction
relative to the centre camera, (b) objects that cache the main render target or a pipeline, (c) ring buffers rotated
per frame but written per eye. The first eye failing (not the left eye) points at (c) or at state left by the end of
the previous frame; `config/blockoscope-sbs.rightfirst` renders the right eye first to tell them apart.

## Comfort (vs Vivecraft NullVR)

Vivecraft cancels `bobView`/`bobHurt` for the camera, slows nausea, and draws a 3D crosshair. Blockoscope SBS does
the same in 3D only (settings in the Comfort tab); the hand keeps vanilla bobbing. Focus distance defaults to 10 m,
the user's setting in NullVR Theater.

## Testing

Tools in `..\mcdev` (outside the repo):

- `cycle-screen.ps1 -Shot name`: close the **26.2-Blockoscope-SteamVR-Dev** instance (a copy of 26.2-Blockoscope-SBS-Dev,
  the Blockoscope SBS test instance), install the built jar, launch into the world, screenshot. (`cycle.ps1` is Blockoscope SBS's.)
- SteamVR without a headset: the user's `steamvr.vrsettings` must not be changed (the permission system refused it).
  Instead `mcdev\vrtest\config\steamvr.vrsettings` forces SteamVR's null driver, and SteamVR is pointed at that
  folder with `VR_CONFIG_PATH` (logs: `VR_LOG_PATH=mcdev\vrtest\logs`). The game sets those variables itself before
  connecting when `config/blockoscope-steamvr.vrenv` (KEY=VALUE lines, development only) exists in the instance; to start
  SteamVR by hand use PowerShell with `$env:VR_CONFIG_PATH`/`$env:VR_LOG_PATH` set and
  `Start-Process ...\SteamVR\bin\win64\vrstartup.exe`. The null headset's view is the compositor's "Headset Window"
  (`winshot.ps1 -Process vrcompositor` captures it with PrintWindow; not while the PC is locked). Quit it by closing
  the `vrmonitor` window. SteamVR's stereo screenshot (`vrshot\VrShot.java`) fails without a scene app.
- What the mod hands SteamVR: create `config/blockoscope-steamvr.dump` in the instance; the next frame's screen texture is
  saved to `.blockoscope-steamvr/screen.png` (`TextureDump`, development only) and the file deleted. Works while the PC is
  locked; `disp2.py` measures it like a window screenshot (eye pixels). Note Blaze3D leaves `GL_PACK_ROW_LENGTH` at
  2048 after its own read-backs; reset it (and skips/alignment) around any `glGetTextureImage`, or rows come out
  sheared. First measurement (null driver, auto 1200 px eyes, focus 4 m): hotbar -20 px on the dirt edge below it,
  crosshair +2 px resting at reach, far terrain +5 px; picture upright with GL rows read bottom-up.
- `stress-screen.sh [rounds]` (Git Bash, game and test SteamVR running): per round, 10 times Escape + Mod Menu +
  Escape with the game focused, then SteamVR force-killed and restarted; fails as soon as the game stops responding.
  For a hang: `jcmd <pid> Thread.print` for the Java side, `python nstack.py <pid> <tid>` for the native stack of a
  thread (dbghelp StackWalk64, module+offset) and `waitchain.ps1 -ThreadId <tid>` for lock owners.
- `cmd.ps1 -Commands @('time set noon', ...)`: chat commands via the clipboard; `keys.ps1 -Keys @('{F9}')`.
  Both refuse to type unless the game window is in front (`focus.ps1`), so keystrokes can't leak elsewhere.
- `disp2.py shot.png name=y0,y1,x0,x1 ...`: sub-pixel disparity of a region (full-res coordinates, left-half x).
  Negative = in front of the screen. Compare against the formula above.
- Debug logging: create `config/blockoscope-steamvr.debug` (FPS every 5 s plus any `StereoDebug.log`).
- Test scene in the dev world copy: stone floor, a gold-block pillar ~5 m ahead and a NoAI iron golem beside it.
  `gamerule advance_time false`, `gamerule advance_weather false` keep lighting stable.
- Decompiled sources (Vineflower) in `mcdev\src\{mc,b3d,sodium-...,iris,voxy-...,vivecraft}`.

Measured (BSL + Voxy, 2560x1440, RX 9070 XT): about 200-250 FPS in window 3D (Blockoscope SBS). With the SteamVR
screen at 1920 per eye (3840x1080 texture) and the null-driver compositor running: about 150 FPS.

## Open items

- Headset check of the SteamVR screen in the Steam Frame: placement and F8, sharpness, comfort of the default size
  (2.6 m at 2 m). Orientation and eye order are verified in the null headset's compositor.
- D3D11 device on the default adapter: on a multi-GPU PC where the game runs on another GPU, the interop fails and
  the GL fallback is used (could match the adapter by LUID from `IVRSystem::GetDXGIOutputInfo`).
- A Voxy "Section mesh generation service ... Not running" exception appeared once at world load in the Screen
  instance and not again on relaunch; watch for it.

- Block-entity breaking overlay is positioned relative to the centre camera at extraction (tiny error, not fixed).
- Hand depth at 100% is strong (about -52 px per eye for the held item); fine in testing, lower it if it strains.
- Menu background blur showed a thin bright line at the left edge of each eye; menus now sit on the screen surface
  (no shift) by default, which should remove it (not looked at closely yet).
- Title screen / panorama with 0.1.3+ not checked yet.
- Edge elements (0.1.5, `StereoRenderer.edgeShiftPixels`): with the HUD in front of the screen the right eye's copy
  moves left, so the chat box was cut off at the left edge in that eye (and right-edge elements in the left eye).
  Elements in an edge zone (start in the outer third, don't reach past the middle third; text classified as a whole
  via its pose object, since glyphs have no bounds) now get a common sideways shift baked into their vertices at
  prepare time (`ShiftedVertexConsumer`), so the eye that would move them outwards leaves them in place and the other
  eye takes the whole disparity. Scissor rectangles get the same shift. Measured: chat, effect icons and hotbar all
  -8 px, chat at its vanilla position in the right eye and 8 px further in in the left. Full-width elements (the chat
  input bar's background) stay centred and still lose a few pixels at both edges.
- One depth for the whole HUD (per-element depth would need per-element shifts per eye).
- Voxy uses its original pipeline's uniform *values* for both eyes (camera position etc. of that eye); only
  draw targets follow the eye. No visible issue found.
