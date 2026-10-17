# Active development — BC-1 v2.0.0 (candidate, not published)

Branch `claude/app-update-4vo05i`: v2.0.0 / versionCode 17, folding in the unpublished v1.1.0 timeline work (v1.1 branch `bc-1-v1.1-timeline` preserved). Selection-based workspace, + Add drawer, contextual tools with focused panels, layered timeline with centred playhead, layer visibility / solo / locks, arrangement tools, Move & Transform, direct preview editing, keyframe animation and reusable presets/elements. Read BC-1-v2.0-QA.md.

CI: dispatch `bc-1-android.yml` on the branch (it is not in the push list). Reduced QA screenshots are pushed to `qa-screenshots/<branch>` by the GPU job; failure summaries are repeated at the end of the job log. Local checks used during development: JVM unit tests against real org.json, and a javac pass against Robolectric android-all filtering Media3-only errors.

IMPORTANT: the retained preview signing key was not available, so v2.0.0 was not published to the APK repo; CI APKs use an ephemeral debug key and cannot update an existing Preview install. Fixed in this work: a Media3 forced-audio race with several visual sequences (NullPointerException in SequenceAssetLoader) — non-primary layers' sound now uses separate audio sequences. The older intermittent "Muxer error" remains unexplained (seen once more on 2026-10-17 before these changes).

---

# BC-1 overnight development checkpoint

## Latest verified development build: v1.0.4

Continue `bc-1-v1.0.4-diagnostics`; preserve all older branches. Tested source `ae912eec2ca3a9d4a63212ae8646646bd30c24c5`, Actions `38888011311`: build and GPU jobs both passed.36 unit tests (0 failures/0 skipped),20 Android tests, lint0errors30warnings. FOUR independent ffmpeg decode/ffprobe gates passed: three sequential H.2641920×1080/AAC exports and one valid export after a deliberately missing-media failure. Expected missing-file error is logged; failure cleanup, service shutdown, retained redacted report and successful recovery all passed. Final diagnostic-dialog, paused-first-frame, phone9:16, wide16:9 and compact-wide screenshots reviewed against the V1 direction.

New: Setup → Export diagnostics → Copy report. Copy is user-triggered; no automatic sending. Bounded report contains stage, Media3 code and cause class names, excluding media paths/names and exception messages. Last failure stays available after successful exports. Full exception chains are debug-only logs. Layout, GLES graph, transition policy, portable project schema and no-Page-Curl rule are unchanged.

APK: https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.4/BC-1-v1.0.4.apk
QA: https://github.com/uran39281-bit/bc-1/blob/main/v1.0.4/QA.md
SHA256: `7e80ed1718bf84c2bf68749681c11a13c54ba9d5dbe558467316dfea1e732ffe`.
Package `com.blackice.bc1.preview`, versionName1.0.4/code16; retained certificate SHA256 `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d` verified.
APK repo commit `a149cde3a9957f1b54c32f8861054e783c1b603e`; raw download compared byte-for-byte. Local `/workspace/scratch/9d8e062dc089/deliverables/BC-1-v1.0.4.apk`; saved identity `libfile_b86329d0f7588191a7ff933406ce87b1`.
Artifacts: APK12039928235, build12040312524, GPU12040997551. Local QA `/workspace/scratch/9d8e062dc089/qa/v104-final/`.

IMPORTANT: v1.0.3 attempt1's intermittent muxer error was not reproduced by the unchanged rerun, three-export investigation or v1.0.4 qualification. Its initiating cause remains unknown, not claimed fixed. This remains a development-signed debug build, NOT production-stable certification. Physical Snapdragon8Gen3/SamsungDeX validation, thermal/battery/FPS measurements and RTX backend remain outstanding. Do not hide those limits in delivery.

## Completed investigation evidence

`bc-1-export-reliability` source664e2f2, Actions38881066895 succeeded:18 Android tests and all THREE independent H.2641920×1080/AAC/duration checks passed, build succeeded. Artifacts GPU12037617816/build12037586895. This did not reproduce or establish the cause of the earlier intermittent muxer failure. Preserve that caveat. Investigation artifact remains DO-NOT-PUBLISH (old manifest), and all older APKs remain intact.

## Preserved v1.0.3 thumbnail reliability

Continue `bc-1-v1.0.3-thumbnails` unless a newer active follow-up is recorded above. Tested source `261c82dea4bc2628953c62d2a0b262f37945764b`, Actions `38874891062` attempt2: 33 unit +18 Android tests, lint0errors30warnings; independent MP4 decode/format gate passed. Final paused-first-frame, phone9:16, wide16:9, compact-wide and thumbnail-stress screenshots reviewed. Baseline run38874632718 reproduced the stale thumbnail; current-view observer fix passes in both candidate attempts.

IMPORTANT: candidate attempt1 failed one export with `Muxer error`; unchanged rerun passed. This does not prove the initiating cause fixed. Keep this intermittent reliability caveat in QA; do not call the build production-stable. A useful next step is debug ExportException cause logging and repeat-export tests without skipping or weakening any gate.

APK: https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.3/BC-1-v1.0.3.apk
QA: https://github.com/uran39281-bit/bc-1/blob/main/v1.0.3/QA.md
SHA256: `11b5a3cb9119492afc75c78dd4953a36cfcf8d2da13352376be2810b0876d653`.
Package `com.blackice.bc1.preview`, code15/name1.0.3; retained signing certificate matches. Published APK repo commit `df158f449c8ae7318801aa92fe81f0abdb2b5e91`; raw downloaded bytes compared identical. Saved identity `libfile_459142f5a7188191a1cbaddb8f965758`. Local APK `/workspace/scratch/9d8e062dc089/deliverables/BC-1-v1.0.3.apk`. Artifacts: APK12035420232, build12035250628, failing GPU12035510441, passing GPU12036120835.

## Preserved v1.0.2 V1 media browser

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

Delivery: use the verified v1.0.4 direct APK link above, concise test totals and physical-device limitation. The04:30 delivery task is already enabled; do not create duplicates. Preserve this tested build rather than making a version-only change. Further work should start a new branch/versionCode>16: physical Snapdragon/DeX QA, capture actual recurring export failure reports, dedicated V1 project-flow screen and category-specific inspector. Read fresh branch heads before guarded updates; keep bc-1-v1.0's pointer current.

## Architecture and limits

Portable EditProject/Motion models and GLES3 effects form the boundary for future RTX integration. No RTX backend exists. No physical Snapdragon 8 Gen 3, Samsung DeX, thermal or battery measurements have been made. API35/SwiftShader emulator tests are not hardware performance measurements. Published APKs are development-signed debug builds; do not call them production-stable.

Image/multilayer timelines use PreviewProxy: cancellable cached H.264/AAC rendered through the SAME GLES graph as export at selected preview height. ExoPlayer displays paused frames with reliable seeks/resizing. Single-video projects retain direct CompositionPlayer. Preparation costs time/storage on long timelines. Cache key ignores playhead; edits invalidate it. Bump preview cache revision if renderer output changes for an unchanged project.

## Verification and build workflow

Use `.github/workflows/bc-1-android.yml` and `.github/scripts/bc1-device-checks.sh`: build/lint/36 unit tests,20 Android tests in v1.0.4, screenshots, independent ffmpeg decode + ffprobe H.264 1920×1080/AAC/duration check. Build and GPU jobs both must pass. Review actual paused first-frame, phone, wide and compact-wide screenshots, not just test totals.

Local SDK/Gradle distributions unavailable. Do not retry earlier auto-review-denied Gradle download; use GitHub CI. Cached Java dependencies can compile UI/model code but do not replace Android tests.

Retained private preview key: /root/.android/debug.keystore. NEVER print/upload/commit it. Sign with existing /tmp/SignBcApk.java/class and cached apksig 8.10.1; verify certificate above. If key unavailable, report the signing limitation and preserve old APKs.

Local QA: /workspace/scratch/9d8e062dc089/qa/v102-final/.
Separate APK repository: uran39281-bit/bc-1, main. Publish versioned APK, checksum and QA; verify raw downloaded bytes. Save the user-facing APK persistently when available; Git-backed source needs no duplicate file storage.

## Preserved history

- v1.0 source `1c60a8b3b1f963300d5b0f344ae95762133ac4e5`, run `38866426755`: 30 unit +17 Android tests passed, independent MP4 decode passed, lint0errors27warnings. Initial paused preview and proxy cancellation/cache checks fixed. Download https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0/BC-1-v1.0.apk ; SHA256 `50ee96cd381abc3ce98e6cae27a822829327b814886233a24b533d530f0c2823`.
- v0.5 source `559cc1202d551cd0fb389e1037716d104c588cef`, run `38863083635`:29 unit +16 Android tests. Keyframes/easing, layers, chroma, audio fades/waveforms, project bundles and DeX controls. Download https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v0.5/BC-1-v0.5-Preview.apk ; SHA256 `4420b3b81efa5137de90af81bb70237b0cfab55170fce8b98cfb696b2c72729d`.
- v0.4 APK and v0.2–v0.5 source branches preserved.
- v1.0 RC1 passed tests but black initial preview failed visual review; not published. RC2 fixed pixels but one fixture failed; final v1.0 corrected it and passed.
