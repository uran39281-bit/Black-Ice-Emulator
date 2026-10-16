# BC-1 v0.3.0 — Dual Workspace / GPU Effects

Continues v0.2 at `ad4ac82` on `bc-1-v0.3-effects`. The previous development branch and emulator overlay remain unchanged.

BC-1 is an original Android **OpenGL ES 3.0 still-image effects lab**, designed for Snapdragon 8 Gen 3-class devices. The official Black Ice V1 direction remains: dark panels, icy-blue accents, a central monitor, timeline, media panel and inspector. The workspace adapts to the window; it never stretches images to fill the screen.

## Workspaces

- **Auto:** phone workspace on ordinary phones, including rotation; wide workspace on sufficiently wide tablets/external displays. Resizing can change the active workspace.
- **Phone:** optimized for 9:16 portrait, with a pinned monitor and timeline, scrolling parameters and persistent bottom navigation. Short landscape windows scroll the content safely.
- **Wide:** optimized for 16:9 tablets/DeX. Below 1100 dp, the media column folds away; the Media tab still imports both images. Timeline height adapts to short windows.
- Explicit Phone/Wide selections persist. A requested Wide mode falls back in narrow windows and resumes when sufficient room returns.
- Android system bars/cutouts receive insets. Primary controls have at least 48 dp touch targets. Switching tool tabs keeps the GPU surface attached.

Workspace layout is independent from the currently fixed **16:9 composition**. This release does not add project aspect-ratio selection or export resolution settings.

## GPU engine

Seven transitions: Dissolve, Directional Wipe, Checker Dissolve, Radial Reveal, **Slide Left, Zoom Dissolve, Dip to Black**. Linear and smooth motion curves. Transition endpoints explicitly resolve to A and B.

Effects on input A: Gaussian Blur, Glow/Bloom, green Chroma Key, Grayscale, legacy Opacity, **Vignette, Sharpen, Pixelate, Exposure, Saturation and Ice Grade**, plus None. A separate 0–100% opacity parameter works with every effect. Exposure and saturation are neutral at 50%; the other effect strengths increase from 0% (chroma key controls tolerance).

- On-demand rendering when paused; approximately 30 requested preview frames/second during playback.
- Blur is separable and cached until image A, intensity, quality or EGL context changes.
- Blur FBOs allocate only when needed, at 640×360 / 960×540 / 1280×720.
- Window resizing does not reallocate resolution-independent blur targets.
- Shader uniforms are resolved once per EGL context, not queried from the driver each frame.
- Bitmap decoding and local image saves run off the UI thread. Android 9+ imports use ImageDecoder for orientation handling. Inputs are fitted to a bounded 1024×576 canvas.
- These are architectural optimizations, **not Snapdragon performance measurements**. Real Adreno GPU, thermal, battery and DeX checks remain necessary.

## Session recovery

Latest input A/B images are atomically saved in private app storage. Effect, transition, opacity, quality, progress and motion curve restore on reopening. Existing `bc1-workspace` preferences and v0.2 parameter IDs stay compatible. Invalid restored values are bounded before use. This is one recoverable session, **not named project management**.

Keyboard: Space plays/pauses; Left/Right scrub 2.5%; Home/End seek endpoints; Ctrl+1–4 select tool tabs. Focused controls retain native keyboard navigation. Touch/mouse timeline scrubbing pauses playback.

## Portable boundaries

`engine/EngineSettings.java` is Android-independent and defines stable parameter IDs/ranges. GLSL files contain the effect and transition kernels. `BcRenderer` owns GLES textures, FBOs, context recreation and upload; `WorkbenchActivity` owns Android input/lifecycle and UI. A future RTX 4050 backend can reuse the parameter contract and port the shader kernels, replacing Android/GLES resource handling. No desktop or RTX backend is included.

## Build and checks

Java 17, Gradle 8.9, Android Gradle Plugin 8.7.3, SDK 35, minimum Android 8.0 / GLES 3.0.

```sh
cd BC-1
gradle testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
# With an Android GLES 3-capable device or emulator connected:
gradle connectedDebugAndroidTest
```

The workflow `.github/workflows/bc-1-android.yml` builds the APK, unit tests and lint reports, and runs instrumentation tests on an API 35 emulator. GPU tests execute the **production renderer/shaders**, checking all transitions at both endpoints, all effects across quality/strength combinations, opacity, dip midpoint, blur orientation, source replacement and EGL context recreation. Emulator results do not establish device performance.

Debug APK upgrades require the same signing certificate as the installed APK. Do not uninstall an older build to bypass a signature mismatch without preserving its data. CI generates an ephemeral debug certificate; distribute a consistently signed local APK for repeated testing. Signing keys must never be committed.

The distributed v0.3 preview uses `gradle -Pbc1SideBySide assembleDebug` (`com.blackice.bc1.preview`) because the saved v0.2 signing key is unavailable. It installs alongside v0.2 and leaves its data intact. Its private session is separate. Normal builds retain `com.blackice.bc1` for same-key upgrades.

## Deferred

No video decode/encode, editable multilayer timeline, audio, named projects, MP4 export, 3D Page Curl, or RTX backend. The timeline intentionally represents two still inputs.

License/provenance: independently authored BC-1 application and shaders; no proprietary Alight Motion resources are distributed.
