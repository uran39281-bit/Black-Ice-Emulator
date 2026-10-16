# BC-1 v1.0 — verification

- Source: [`1c60a8b3b1f963300d5b0f344ae95762133ac4e5`](https://github.com/uran39281-bit/Black-Ice-Emulator/commit/1c60a8b3b1f963300d5b0f344ae95762133ac4e5).
- [GitHub Actions run 38866426755](https://github.com/uran39281-bit/Black-Ice-Emulator/actions/runs/38866426755): build and GPU jobs passed.
- 30 JVM unit tests: passed, none skipped.
- 17 Android instrumentation tests: passed on API35 x86_64 SwiftShader.
- Lint: 0 errors, 27 warnings (primarily UI string localization, dependency/target-SDK notices, storage checks and existing accessibility/Exif notices).
- Actual paused preview pixels verified before pressing Play, at initial playhead zero; paused seek, phone/wide/compact-wide resize and recreation checked.
- Preview cache cancellation, cache reuse on seek and invalidation on edit checked.
- GPU effects, transitions, context recreation, layered motion/chroma/audio, project bundles, 1080p export and export cancellation checked.
- Export independently decoded with ffmpeg without errors; ffprobe confirms H.264 1920×1080 video and AAC audio.
- APK manifest verified: package com.blackice.bc1.preview; versionName 1.0.0; versionCode 12; OpenGL ES 3.0 required.
- Retained preview signing certificate verified: SHA256 `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`.
- APK SHA256: `50ee96cd381abc3ce98e6cae27a822829327b814886233a24b533d530f0c2823`.

## Scope

This continues the existing v0.2–v0.5 project. Previous branches and APK files remain intact. V1 design direction is preserved: mountain mark, dark navy panels, icy-blue controls, large preview, layered timeline, UI Lock and desktop media/tools panels. The original full V1 reference image was unavailable, so this is not a pixel-perfect comparison claim.

Image/multilayer compositions use a cancellable cached preview rendered through the export GLES graph. This fixes Media3 live multi-input paused/seek issues. Long timelines require preparation time and cache storage; this is not instant real-time compositing. Single-video projects retain direct playback.

No physical Snapdragon 8 Gen 3, Samsung DeX, battery/thermal or RTX 4050 measurements have been performed. RTX rendering remains future work. 3D Page Curl is excluded.
