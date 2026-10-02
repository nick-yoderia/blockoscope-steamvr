# Development notes

Engineering notes for Parallax Theater, kept up to date at the end of each work session. `CLAUDE.md` has the
workflow (build, test loop, rules); this file has the how and why.

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

The GUI is extracted once and drawn into both eyes; its projection is shifted per eye for the HUD distance
(`eyeGuiProjection`), the crosshair draw gets its own projection for the aimed distance (`eyeCrosshairProjection`),
and the hand projection gets the eye offset plus the focus shift in clip space (`eyeHandProjection`).

Crosshair depth (`CrosshairDepth`): after the first eye's world render (before the hand clears depth), the centre
pixel of the eye's depth texture is copied with `glGetTextureSubImage` into a pixel-pack buffer and read a frame or
two later after a fence (never stalls). Blaze3D's `copyTextureToBuffer` can't be used: it attaches the source as a
colour attachment, so depth reads back 0. Conventions: vanilla uses reversed depth straight from clip z (far = 0);
with a shader pack Iris (and Voxy) use a conventional projection (m22 near -1) stored as `clip * 0.5 + 0.5`, so
distance = `m32 / (clip + m22)` with `clip = 2d - 1` there. With a shader pack Voxy's terrain is not in that depth
buffer, so `VoxyCrosshairDepthMixin` adds Voxy's own depth texture as a fallback when the world's shows sky. Over real
sky the crosshair holds the last surface and eases to the screen surface over 1.5 s.

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
the previous frame; `config/parallax-theater.rightfirst` renders the right eye first to tell them apart.

## Comfort (vs Vivecraft NullVR)

Vivecraft cancels `bobView`/`bobHurt` for the camera, slows nausea, and draws a 3D crosshair. Parallax Theater does
the same in 3D only (settings in the Comfort tab); the hand keeps vanilla bobbing. Focus distance defaults to 10 m,
the user's setting in NullVR Theater.

## Testing

Tools in `..\mcdev` (outside the repo):

- `cycle.ps1 -Shot name`: close the dev instance, install the built jar, launch into the world, screenshot.
- `cmd.ps1 -Commands @('time set noon', ...)`: chat commands via the clipboard; `keys.ps1 -Keys @('{F9}')`.
  Both refuse to type unless the game window is in front (`focus.ps1`), so keystrokes can't leak elsewhere.
- `disp2.py shot.png name=y0,y1,x0,x1 ...`: sub-pixel disparity of a region (full-res coordinates, left-half x).
  Negative = in front of the screen. Compare against the formula above.
- Debug logging: create `config/parallax-theater.debug` (FPS every 5 s plus any `StereoDebug.log`).
- Test scene in the dev world copy: stone floor, a gold-block pillar ~5 m ahead and a NoAI iron golem beside it.
  `gamerule advance_time false`, `gamerule advance_weather false` keep lighting stable.
- Decompiled sources (Vineflower) in `mcdev\src\{mc,b3d,sodium-...,iris,voxy-...,vivecraft}`.

Measured (BSL + Voxy, 2560x1440, RX 9070 XT): about 200-250 FPS in 3D. Without shaders about 700-1600 FPS.

## Open items

- Block-entity breaking overlay is positioned relative to the centre camera at extraction (tiny error, not fixed).
- Hand depth at 100% is strong (about -52 px per eye for the held item); fine in testing, lower it if it strains.
- Menu background blur shows a thin bright line at the left edge of each eye.
- Voxy uses its original pipeline's uniform *values* for both eyes (camera position etc. of that eye); only
  draw targets follow the eye. No visible issue found.
