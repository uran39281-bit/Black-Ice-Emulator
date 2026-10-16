## BC-1 v1.0.2 — V1 media browser

Large tablet/DeX windows now use a two-column Project Media grid, All/Video/Audio/Image filters and filename search. Filtered cards select the original clip; highlights follow other selection controls. Query/filter state survives recreation. Phone and compact-wide layouts retain their existing arrangement.

VersionName `1.0.2`, versionCode `14`: 33 unit +17 Android tests passed; lint0errors30warnings; exported H.264/AAC MP4 independently decoded. [Download APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.2/BC-1-v1.0.2.apk) · [QA and limits](../BC-1-v1.0.2-QA.md). Retained signing identity; older APKs preserved. Physical Snapdragon/DeX testing remains outstanding.

---

## BC-1 v1.0.1 — V1 interface polish

Monoline navigation/tool icons, nine editing shortcuts, seekable preview scrubber, pinned timeline labels and desktop Undo/Redo/Save bring the editor closer to the recovered [official V1 references](design/V1-REFERENCE.md). Compact-wide preview space is preserved.

VersionName `1.0.1`, versionCode `13`: 30 unit +17 Android tests passed, lint0errors28warnings, independently decoded 1080p H.264/AAC export. Phone/wide/compact-wide screenshots reviewed. [Download APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.1/BC-1-v1.0.1.apk) · [QA and limitations](../BC-1-v1.0.1-QA.md). Previous versions remain available. Physical Snapdragon/DeX testing remains outstanding.

Earlier release notes follow for history.

---

## BC-1 v1.0 — verified release

Continues the v0.2 Dual Workspace project, retaining its V1 mountain mark, dark navy panels and icy-blue accents. Phone and tablet/DeX workspaces support automatic selection and manual overrides. UI Lock, thumbnail timelines and a desktop media browser are included alongside the v0.5 keyframes, easing curves, layered audio, chroma controls and project bundles. 3D Page Curl remains excluded.

The initial paused-preview issue is fixed and visually verified across phone, large wide and compact wide layouts. Image/multilayer projects prepare a cancellable cached preview; playback/seek then reuse it until the project changes. Long projects take time to prepare. Single-video projects keep direct playback. Export remains H.264/AAC at 1080p through the same GLES effects graph.

Released build: `1.0.0`, code `12`. All 30 unit tests and 17 Android tests pass; lint has no errors and the exported MP4 independently decodes without errors. [Download v1.0](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0/BC-1-v1.0.apk). Physical Snapdragon/DeX performance is not yet measured. APKs are kept separately at https://github.com/uran39281-bit/bc-1 .

## v1.0 RC2 preview reliability

Image and multilayer timelines now prepare a cached preview with the same GLES effects graph used for export. Preparation shows progress and can be cancelled; once ready, playback starts paused without moving the playhead. Seeking and workspace resizing reuse the cache. An edit invalidates the cached preview. Single-video timelines retain direct playback. Complex previews take time to prepare, especially on long timelines; this is not a claim of instant real-time compositing. Source media and final 1080p export stay unchanged.

RC1 passed automated tests but failed visual review (black initial preview). RC2 adds first-frame, paused-seek, resize, cancellation and cache tests. Do not promote until these gates pass.

# BC-1 v1.0 release candidate

Development continues on `bc-1-v1.0` from the tested v0.5 Motion & Layers engine. This candidate adds V1 UI Lock, real thumbnail timelines, a dedicated media browser in large DeX windows, consistent cyan controls, and safer audio-only lane placement. Release status and remaining checks are in [NIGHT-WORK.md](../NIGHT-WORK.md).

APK downloads have moved to [the dedicated bc-1 repository](https://github.com/uran39281-bit/bc-1). Previous source branches remain preserved.

---

# BC-1 v0.5 — Motion & Layers

Development branch: `bc-1-v0.5-motion`. See [v0.5 architecture and controls](V0.5-ARCHITECTURE.md). Adds independent layers, source-time keyframes, cubic Bézier easing, chroma controls, audio extraction/fades/waveforms, project bundles and DeX interactions while retaining v0.4 project compatibility.

# BC-1 v0.4 Preview — Video Timeline & Export

The current development branch is `bc-1-v0.4-timeline`, continued from v0.3. See [the v0.4 architecture and build guide](V0.4-ARCHITECTURE.md) for the editor, media pipeline, saved projects, export behavior and current limits.

The v0.3 effects lab is preserved under **Setup → Open v0.3 effects lab**. Earlier release documentation follows for history.

---

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
