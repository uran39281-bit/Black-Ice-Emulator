# BC-1 overnight development checkpoint

## Active follow-up: v1.0.3 thumbnail delivery

Continue `bc-1-v1.0.3-thumbnails`. A new Android test deliberately blocks the thumbnail worker, changes search 32 times, then checks that the CURRENT card receives decoded pixels. Baseline `31661164b2d0e53166b6842ace987a3860ec95d6` exercises the v1.0.2 callback implementation; do not publish its artifact (old manifest). The candidate uses stable activity-level observers for current browser/timeline views and updates thumbnail images in place, retaining bounded caching/background decoding and avoiding obsolete-card references. Candidate versionName1.0.3/code15. Baseline failure, candidate success, all tests and screenshots must be checked before publishing a NEW v1.0.3 APK. v1.0.2 remains the latest verified download until then.

## Latest verified work: v1.0.2 V1 media browser

Continue `bc-1-v1.0.2-media`. Tested source `5e551384278f03270258ff7149c4f36a3fe0d1ce`, Actions run `38871745189`: both jobs succeeded. 33 unit +17 Android tests passed, lint 0 errors /30 warnings. Independent ffmpeg decode and ffprobe H.264 1920×1080/AAC/duration gate passed. Final phone, wide, filtered-wide and compact-wide screenshots visually reviewed.

New: responsive two-column Project Media grid, All/Video/Audio/Image filters and filename search. Search/filter state survives recreation. Filtered selection targets the original clip, and browser highlights follow inspector/timeline selection. Existing phone/compact-wide layouts, preview surface, effects and project schema are preserved.

APK: https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.2/BC-1-v1.0.2.apk
QA: https://github.com/uran39281-bit/bc-1/blob/main/v1.0.2/QA.md
SHA256: `de63efff3bd6442e26419ae1693c0196d79a98219a1b1edfdf014c96bce3fa34`
Package `com.blackice.bc1.preview`, versionName `1.0.2`, versionCode `14`, GLES3 required.
Retained signing certificate verified; see below. Published APK repo commit `13de548b6c1a93555feb91cf8c894664376bebee`. Raw download byte-compared successfully.
Artifacts: APK12032814882, build12033765250, GPU12033875888.
Local APK: /workspace/scratch/9d8e062dc089/deliverables/BC-1-v1.0.2.apk
Saved identity: `libfile_079c61f0980c8191ae6f51cf52ab3025`.

## Preserved v1.0.1 V1 interface polish

Historical branch `bc-1-v1.0.1-ui`. Tested code commit `27e09676cd88f875353dd5e86409dafd2d6f4d1c`; Actions run `38869704876`. This continues v0.2–v1.0; never restart or force-push older branches.

Both jobs passed: 30 unit tests +17 Android tests, lint 0 errors /28 warnings. Independent ffmpeg decode and ffprobe H.264 1920×1080/AAC/duration check passed. Final paused-first-frame, phone9:16, wide16:9 and compact-wide screenshots visually reviewed; zoom/scroll pinned labels and corrected shortcut label confirmed. Artifact IDs: APK12032329690, build12032284915, GPU12032987524.

APK: https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.1/BC-1-v1.0.1.apk
QA: https://github.com/uran39281-bit/bc-1/blob/main/v1.0.1/QA.md
SHA256: `5b01c353f54f60f89b2345f5d6b51245581abff427d043fae840ea800442b871`
Manifest: `com.blackice.bc1.preview`, versionName `1.0.1`, versionCode `13`, GLES3 required.
Signing certificate SHA256: `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`.
Local signed APK: /workspace/scratch/31791101a882/deliverables/BC-1-v1.0.1.apk.
Published in APK repo commit `5dc96c3c5e8d7ad93bda443986e001e12aaf1ec6`; raw download was byte-compared to the signed file. Persistently saved identity: `libfile_a99729c13ae88191a027ed9c611c18b1`.

## Official V1 references recovered

The actual user-selected reference images are now committed:
- `BC-1/design/v1/phone.png` (original 1000009084.png, 864×1536).
- `BC-1/design/v1/desktop.png` (original 1000009085.png, 1536×864).
- `BC-1/design/V1-REFERENCE.md` records direction and remaining differences.

Earlier v1.0 notes saying references were unavailable describe that older qualification only. Preserve the mountain mark, dark navy/black, icy cyan, monoline controls, large preview, thumbnail timeline, pinned track labels, phone bottom navigation and wide media/inspector panels. User-selectable Auto/Phone/Wide and UI Lock remain. No fake controls/taskbars; no 3D Page Curl.

v1.0.1 adds monoline icons, nine functional editing shortcuts, preview scrubber, pinned timeline gutter, larger timeline where space permits and desktop Undo/Redo/Save. Tests now zoom/scroll the phone timeline and guard shortcuts with UI Lock. Visual review caught/fixed a wrapped Chroma Key label and hardware-accelerated gutter scrolling. No project schema or effects changes.

## Next useful work

User authorized ongoing improvements until 2026-10-16 04:30 America/Phoenix (11:30 UTC); suppress notifications until then. Deliver the latest VERIFIED direct GitHub APK link at that time, with concise test results and physical-device limits. ChatGPT file links previously failed with a cross-site URL error.

Remaining V1 differences are the dedicated project-flow screen and category-specific inspector. The desktop media grid is now implemented. A useful performance follow-up is a stress test of rapid search while thumbnail decoding is pending: ThumbnailCache caps pending callbacks at eight, and card callbacks capture individual views. Investigate whether stable browser observers avoid stale placeholders without rebuilding all cards; this is a code-review risk, not a reproduced failure. Prefer a small complete, tested improvement next. Inspect the existing code and actual references before editing. Do not merely bump the version. Any new APK must increment versionCode above 14 and use a new versioned directory/file (for example v1.0.3), a new development branch, and full verification. Preserve this tested download. Read fresh branch heads before guarded GitHub ref updates. Update this checkpoint AND the pointer on `bc-1-v1.0` so later runs find the latest branch.

## Architecture and limits

Portable EditProject/Motion models and GLES3 effects form the boundary for future RTX integration. No RTX backend exists. No physical Snapdragon 8 Gen 3, Samsung DeX, thermal or battery measurements have been made. API35/SwiftShader emulator tests are not hardware performance measurements. Published APKs are development-signed debug builds; do not call them production-stable.

Image/multilayer timelines use PreviewProxy: cancellable cached H.264/AAC rendered through the SAME GLES graph as export at selected preview height. ExoPlayer displays paused frames with reliable seeks/resizing. Single-video projects retain direct CompositionPlayer. Preparation costs time/storage on long timelines. Cache key ignores playhead; edits invalidate it. Bump preview cache revision if renderer output changes for an unchanged project.

## Verification and build workflow

Use `.github/workflows/bc-1-android.yml` and `.github/scripts/bc1-device-checks.sh`: build/lint/33 unit tests, 17 Android tests, screenshots, independent ffmpeg decode + ffprobe H.264 1920×1080/AAC/duration check. Build and GPU jobs both must pass. Review actual paused first-frame, phone, wide and compact-wide screenshots, not just test totals.

Local SDK/Gradle distributions unavailable. Do not retry earlier auto-review-denied Gradle download; use GitHub CI. Cached Java dependencies can compile UI/model code but do not replace Android tests.

Retained private preview key: /root/.android/debug.keystore. NEVER print/upload/commit it. Sign with existing /tmp/SignBcApk.java/class and cached apksig 8.10.1; verify certificate above. If key unavailable, report the signing limitation and preserve old APKs.

Local QA: /workspace/scratch/9d8e062dc089/qa/v102-final/.
Separate APK repository: uran39281-bit/bc-1, main. Publish versioned APK, checksum and QA; verify raw downloaded bytes. Save the user-facing APK persistently when available; Git-backed source needs no duplicate file storage.

## Preserved history

- v1.0 source `1c60a8b3b1f963300d5b0f344ae95762133ac4e5`, run `38866426755`: 30 unit +17 Android tests passed, independent MP4 decode passed, lint0errors27warnings. Initial paused preview and proxy cancellation/cache checks fixed. Download https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0/BC-1-v1.0.apk ; SHA256 `50ee96cd381abc3ce98e6cae27a822829327b814886233a24b533d530f0c2823`.
- v0.5 source `559cc1202d551cd0fb389e1037716d104c588cef`, run `38863083635`:29 unit +16 Android tests. Keyframes/easing, layers, chroma, audio fades/waveforms, project bundles and DeX controls. Download https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v0.5/BC-1-v0.5-Preview.apk ; SHA256 `4420b3b81efa5137de90af81bb70237b0cfab55170fce8b98cfb696b2c72729d`.
- v0.4 APK and v0.2–v0.5 source branches preserved.
- v1.0 RC1 passed tests but black initial preview failed visual review; not published. RC2 fixed pixels but one fixture failed; final v1.0 corrected it and passed.
