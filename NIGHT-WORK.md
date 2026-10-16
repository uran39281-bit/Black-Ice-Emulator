# BC-1 overnight development checkpoint

Target delivery: 2026-10-16 04:30 America/Phoenix (11:30 UTC). User authorized continued development, tests and improvements toward v1.0. Keep notifications quiet until delivery unless the user asks for status.

## Repositories and continuity

- Source: uran39281-bit/Black-Ice-Emulator, active branch `bc-1-v1.0`.
- APK distribution: uran39281-bit/bc-1, branch `main`, created by user and initialized for downloads.
- Historical v0.2/v0.3/v0.4/v0.5 branches remain intact. Never force-push or relabel a failing build as stable.
- Tested v0.5 source: `559cc1202d551cd0fb389e1037716d104c588cef`; Actions run `38863083635`: 29 unit tests and 16 Android instrumentation tests pass. Initial paused image preview was black until playback; the v1 candidate warms the first frame silently and pauses on the rendered-frame callback. Verify this specifically.

## Published fallback

v0.5 is published and byte-verified at https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v0.5/BC-1-v0.5-Preview.apk . SHA256 `4420b3b81efa5137de90af81bb70237b0cfab55170fce8b98cfb696b2c72729d`. APK repository commit `97e73f0489de5dc16ca1d3ee4f8b407638da6213`. v0.4 is also preserved under `v0.4/`. New v0.5 file saved locally under `deliverables/BC-1-v0.5-Preview.apk` and persistently as `libfile_c52e902bd61481919e327b18e640f450`. v0.5 is the tested fallback if the v1.0 candidate fails; do not replace its immutable versioned file with a different build.

## Design contract

Official V1 direction: black/dark navy background, icy-blue highlights, mountain mark, large central preview and transport, multilayer thumbnail timeline, phone bottom text tabs, desktop media/effects/properties panels, UI Lock. Phone 9:16 and tablet/DeX 16:9 workspaces must adapt to available window size and preserve Auto/Phone/Wide preferences. No 3D Page Curl. Do not substitute the unrelated faceted-cube emulator logo for the approved editor mountain mark.

Prior context confirms these requirements, but the original full UI reference image is not accessible in the repo or retrieved context. Do not claim pixel-perfect comparison to a missing reference.

## Candidate changes and gates

Current candidate version: 1.0.0-rc2, versionCode 11, package `com.blackice.bc1.preview` when built with `-Pbc1SideBySide`.

Adds UI Lock (editing/layout guard, still permits playback/scrub/export), 8 MiB background thumbnail cache, thumbnail clip strips, dedicated media browser for windows >=1100dp, cyan system controls/dialogs, correct left-docked divider resizing, audio-only source metadata/placement validation and clip-count guards. Source-time keyframes, easing curves, chroma controls, independent audio fades/waveforms, bundles, all v0.5 effects remain.

RC1 source `7ce00cf10d12117a6f8bba4c89a85f731bfc16da`, Actions run `38863834137`, passed 30 unit and 16 instrumented tests, lint 0 errors/28 warnings and ffmpeg export decode. However, screenshot review FOUND A RELEASE BLOCKER: initial paused preview was black and warmup advanced the playhead. RC1 is not published or qualified. The tests previously checked actual pixels only after playback.

RC2 replaces the warmup workaround. Complex/image compositions prepare a seekable H.264/AAC preview using the SAME GLES export graph at the selected preview resolution; ExoPlayer displays it paused at the original playhead. Cache keys ignore playhead changes; edits invalidate the cache; cancellation rejects stale callbacks and removes partial files. Single-video projects retain direct CompositionPlayer playback. The UI shows preparation progress and supports tap-to-cancel. This is a reliability fallback for Media3 1.11.1's known multi-input paused/seeking limitations, NOT an instant real-time multilayer preview promise. Long timelines need time/storage to prepare. Original sources/export remain unaffected.

RC2 also culls offscreen timeline thumbnails/waveforms and reuses draw rectangles, protects player callbacks by identity, and clamps persisted/displayed position to project duration. New Android tests verify paused first-frame pixels BEFORE playback, unchanged initial playhead, seeking, actual preview pixels after resize/recreation, and proxy cancellation/cache reuse/edit invalidation. Local compilation and 30 unit tests pass. RC2 source: `904463c9e1c84048d01e0d89fb536d4c2e55fecc`; Actions run `38865411990`. Build passed: 30 unit tests, 0 lint errors / 27 warnings. The 17 Android tests are still running as of 07:13 UTC. Inspect screenshots and the decoded acceptance MP4 after completion.

RC2 APK was downloaded from artifact `12031320752` and signed locally as `deliverables/BC-1-v1.0-RC2.apk`, SHA256 `ec5daf00d82c99a49584d7d130b1c94eb0a3c1c881b82acaacaa73f1aef462b3`. Certificate matches the retained preview key. Do not publish until Android checks and visual review pass. Build reports artifact `12031600119`.

For final v1.0 promotion, set versionName `1.0.0`, versionCode at least 12 and app label `BC-1 v1.0` in build.gradle. Also update the version string in EditorActivity Setup (currently still RC1). Build and verify that exact candidate; do not merely rename the RC APK. Consider adding ffmpeg decode of the acceptance MP4 to the CI script because it currently runs only during manual QA.

Existing v0.5 issues fixed: custom compositor settings rejected by single-video graphs; GainProcessor unity plateau boundary caused fades to fail. All v0.5 Android tests subsequently passed. Lint baseline: zero errors, 25 warnings.

## Build and delivery

GitHub workflow `.github/workflows/bc-1-android.yml` builds and runs API35 SwiftShader instrumentation. Local SDK/Gradle distributions are not available; cached Java dependencies can compile model/UI sources, but use Actions for authoritative APKs and emulator runs.

Download the artifact from the exact tested source commit. Actions debug signing is ephemeral. Retained preview signing key is local at `/root/.android/debug.keystore`; never commit, upload or print the key. Existing local signing utility: `/tmp/SignBcApk.java`. Certificate SHA256 must be `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d` for updates to v0.3/v0.4 Preview. If the key is unavailable, report update-signing limitation honestly and preserve existing APKs.

Publish a versioned APK, SHA256SUMS and QA/readme into the APK repository. Verify raw download bytes match. ChatGPT's native file-download page failed for the user, so final delivery should lead with a direct GitHub raw APK link. Save new user-facing APK deliverables persistently when supported. Update this checkpoint and release QA with exact commit, workflow run, tests and checksum after each completed gate.

No physical Snapdragon 8 Gen 3, Samsung DeX, thermal, battery or RTX testing has occurred. GLES3 and portable model are implemented; performance claims must remain qualified. Preserve prior APKs and compatibility, do not expand scope to unrelated emulator native core code.
