# Active development: bc-1-v1.0.2-media

Read NIGHT-WORK.md on `bc-1-v1.0.2-media` for the newest work. Candidate source `5e551384278f03270258ff7149c4f36a3fe0d1ce`, Actions run `38871745189`. Adds a V1 two-column desktop media browser, real type filters/search, recreation state and synchronized selection. Local compilation and 33 unit tests passed before the small selection-highlight follow-up. Full Android checks and screenshot review are pending; do not publish the candidate until verified. v1.0.1 below remains the latest verified APK.

---

# Latest verified development: bc-1-v1.0.1-ui

Read NIGHT-WORK.md on `bc-1-v1.0.1-ui` before continuing. The latest checkpoint is `5441aaf23a5932b90d6c8739572d071f834af5c7`; tested code `27e09676cd88f875353dd5e86409dafd2d6f4d1c`, Actions `38869704876`: 30 unit +17 Android tests pass, lint0errors28warnings, independent MP4 decode passes. All final phone, wide, compact-wide and initial paused-preview screenshots reviewed.

v1.0.1 is published in the separate APK repository, preserving v1.0 and older versions:
https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.1/BC-1-v1.0.1.apk
SHA256 `5b01c353f54f60f89b2345f5d6b51245581abff427d043fae840ea800442b871`; retained signing certificate verified. VersionCode13/name1.0.1. Not physically Snapdragon/DeX tested.

The official V1 phone/desktop references have been recovered and versioned under BC-1/design/v1/. v1.0.1 adds line icons, nine editing shortcuts, scrubber, pinned track labels and desktop undo/save controls. The latest branch checkpoint lists remaining useful UI improvements; any further APK needs a NEW version/path/code and verification.

The older v1.0 qualification below is retained as history.

---

# BC-1 overnight development checkpoint

Target report: 2026-10-16 04:30 America/Phoenix (11:30 UTC). User authorized continued development, testing and V1 interface improvements. Keep notifications quiet until delivery unless the user asks for status.

## v1.0 is built, verified and published

- Source repo: uran39281-bit/Black-Ice-Emulator. Branch `bc-1-v1.0`.
- Tested code commit: `1c60a8b3b1f963300d5b0f344ae95762133ac4e5`.
- Actions run: `38866426755`. Build and GPU jobs succeeded.
- 30 unit tests and 17 Android instrumentation tests passed. Lint: 0 errors / 27 warnings.
- Independent ffmpeg decode passed. ffprobe confirms H.264 1920x1080 + AAC.
- Actual paused-first-frame pixels verified BEFORE Play, initial playhead remains zero. Paused seek, phone/wide/compact-wide resize/recreation, UI Lock, proxy cancellation/cache reuse/edit invalidation checked. Phone and large-wide screenshots visually reviewed from the final run; compact-wide also passed the same pixel gate and was visually reviewed in RC2.
- Artifact IDs: APK `12031287189`, build reports `12031402040`, GPU reports `12031777241`.
- Signed APK: `/workspace/scratch/31791101a882/deliverables/BC-1-v1.0.apk`.
- Manifest verified: `com.blackice.bc1.preview`, versionName `1.0.0`, versionCode `12`, label `BC-1 v1.0`, requires GLES3.
- SHA256: `50ee96cd381abc3ce98e6cae27a822829327b814886233a24b533d530f0c2823`.
- Retained signing certificate SHA256: `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`.
- Separate APK repo: uran39281-bit/bc-1, main commit `b70fe8cfd697e5f71495105fc6a8994387d45689`.
- Direct download: https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0/BC-1-v1.0.apk . Downloaded back and byte-compared successfully.
- QA: https://github.com/uran39281-bit/bc-1/blob/main/v1.0/QA.md .
- Persistently saved file identity: `libfile_d442c26d9b14819192adc6219ba405bd`; local identity metadata applied.

## Further authorized work

The user requested work until 04:30. Hourly development checkpoints are scheduled at 00:30/01:30/02:30/03:30 America/Phoenix and a final report at 04:30. Continue only useful concrete review/improvements; do not repeatedly rerun already-passed gates without a specific risk. Preserve this tested download. Any further changed APK must use a NEW version/file/folder (for example 1.0.1, versionCode >12), with a separate branch and new verification. Never overwrite the v1.0 binary or force-push historical branches.

At delivery, lead with the direct GitHub APK link, latest verified version and concise test status. ChatGPT file downloads previously failed for the user with a cross-site file URL error. Be honest about limits and do not promise physical-device testing.

## Design and architecture contract

Continue the v0.2 Dual Workspace foundation. V1 direction: mountain mark, dark navy/black backgrounds, icy-blue highlights, large preview/transport, multilayer thumbnail timeline, UI Lock, desktop media/effects/properties panels. Phone 9:16, tablet/DeX 16:9; Auto/Phone/Wide preference persists. Original full V1 reference image is unavailable, so no pixel-perfect claim. Do not substitute the unrelated legacy emulator cube logo. No 3D Page Curl.

Portable EditProject/Motion model and GLES3 effects remain the backend boundary for future RTX integration. No RTX backend exists yet. No physical Snapdragon 8 Gen 3/DeX, thermal or battery measurements have been made.

Image/multilayer timelines use PreviewProxy: a cancellable, cached H.264/AAC preview rendered through the SAME GLES effects graph as export at the selected preview quality. ExoPlayer displays it paused and supports reliable seek/resizing. Single-video projects retain direct CompositionPlayer. Preparation costs time/storage on long timelines; do not claim instant real-time multilayer rendering. Cache keys ignore playhead changes; edits invalidate cache. Bump the preview cache revision if future renderer changes alter output for an unchanged project.

## History and regressions fixed

- v0.5 tested commit `559cc1202d551cd0fb389e1037716d104c588cef`, run `38863083635`: 29 unit +16 Android tests. Includes keyframes/easing, layers, chroma, audio fades/waveforms, project bundles and DeX controls.
- v0.5 fallback: https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v0.5/BC-1-v0.5-Preview.apk . SHA256 `4420b3b81efa5137de90af81bb70237b0cfab55170fce8b98cfb696b2c72729d`.
- v0.4 also preserved under v0.4/. Historical v0.2–v0.5 source branches remain intact.
- RC1 passed 46 automated tests but visual review found black initial preview and playhead advancement from the warmup workaround. It was NOT published.
- RC2 fixed actual paused/seek preview using cached GLES composition. Its workspace test passed and screenshots showed real frames at time zero. One new test fixture lacked a clip name, so the run failed; the fixture was corrected for the fully passing final run. RC2 was NOT published.
- v1.0 additionally culls offscreen thumbnail/waveform work, reuses drawing rectangles, guards stale player callbacks, clamps playhead position, adds UI Lock, desktop media browser, thumbnail cache and audio-only placement metadata.

## Build and signing

Use .github/workflows/bc-1-android.yml for authoritative builds/API35 SwiftShader tests. Local SDK/Gradle distributions are unavailable; cached Java dependencies can compile models/UI. Do not retry the earlier auto-review-denied Gradle distribution download; GitHub CI is the supported path.

Retained private preview key: /root/.android/debug.keystore. NEVER commit, upload or print the key. Sign with existing /tmp/SignBcApk.java/class plus cached apksig 8.10.1, then verify the certificate above. If the key is unavailable, report the update-signing limit honestly and preserve existing APKs.

Local QA: /workspace/scratch/31791101a882/qa/v10-final/. Save future APKs in versioned paths in the separate APK repo with checksum/QA, verify raw downloaded bytes, and save the user-facing file persistently when supported. Source is Git-backed and needs no duplicate file storage.
