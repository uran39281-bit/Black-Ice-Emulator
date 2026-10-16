# BC-1 v1.0.1 — V1 interface polish

Continues the existing v0.2–v1.0 editor. Earlier APKs and branches are preserved.

## Changes
- Recovered and versioned the user's official phone and desktop V1 reference images under `BC-1/design/v1/`.
- Monoline navigation and editing icons, darker panels, nine editing shortcuts and desktop Undo/Redo/Save controls.
- Seekable preview scrubber, larger timelines when space permits, and track labels that remain visible while scrolling.
- Compact-wide windows retain their preview space. UI Lock also guards shortcut actions.
- Corrected a wrapped Chroma Key label found during phone screenshot review.

## Evidence
- Tested source: `27e09676cd88f875353dd5e86409dafd2d6f4d1c` on `bc-1-v1.0.1-ui`.
- [GitHub Actions run 38869704876](https://github.com/uran39281-bit/Black-Ice-Emulator/actions/runs/38869704876).
- 30 JVM unit tests and 17 Android instrumentation tests passed, none skipped; both Actions jobs succeeded. Lint: 0 errors, 28 warnings.
- Actual paused first-frame pixels at time zero, paused seek, phone timeline zoom/scroll, UI Lock, wide and compact-wide resizing checked. Final screenshots at 1080×1920, 1920×1080 and 780×420 visually reviewed; Chroma Key label alignment and pinned track gutter confirmed.
- GPU effects/transitions/context recreation, layered motion/chroma/audio, project bundles, export cancellation and preview cache behavior passed.
- Independent ffmpeg decoding passed; ffprobe confirmed H.264 1920×1080 + AAC.
- APK package `com.blackice.bc1.preview`, versionName `1.0.1`, versionCode `13`, requires OpenGL ES 3.0.
- Retained preview signing certificate SHA256: `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`.
- APK SHA256: `5b01c353f54f60f89b2345f5d6b51245581abff427d043fae840ea800442b871`

- Artifact IDs: APK `12032329690`, build reports `12032284915`, GPU reports `12032987524`.

## Limits
This is a development-signed, sideloadable debug APK; the version number is not a production-stability certification.

API35 emulator/SwiftShader checks do not measure Snapdragon 8 Gen 3, physical Samsung DeX, thermals or battery usage. RTX 4050 remains a future backend. No 3D Page Curl.

The V1 visual direction is closer, but this is not a pixel-perfect claim. The dedicated project-flow screen, category inspector and two-column desktop media grid remain follow-up work. Image/multilayer preview preparation uses cached GLES rendering and takes time/storage on long projects; edits invalidate the cache. No project schema or rendering effects changed in this patch.
