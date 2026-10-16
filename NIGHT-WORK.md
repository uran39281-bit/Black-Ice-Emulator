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

Current candidate version: 1.0.0-rc1, versionCode 10, package `com.blackice.bc1.preview` when built with `-Pbc1SideBySide`.

Adds UI Lock (editing/layout guard, still permits playback/scrub/export), 8 MiB background thumbnail cache, thumbnail clip strips, dedicated media browser for windows >=1100dp, cyan system controls/dialogs, correct left-docked divider resizing, audio-only source metadata/placement validation and clip-count guards. Source-time keyframes, easing curves, chroma controls, independent audio fades/waveforms, bundles, all v0.5 effects remain.

Candidate source commit: `7ce00cf10d12117a6f8bba4c89a85f731bfc16da`. Actions run: `38863834137` (check current status).

Local Java compilation and 30 unit tests pass. Full Android build, lint and instrumented tests for this candidate still must run. The workspace test checks real image preview pixels after playback and takes a paused-first-frame screenshot; inspect it. Verify UI Lock prevents Ctrl+D. Inspect portrait, large wide and compact wide screenshots. Check export decoding with ffprobe/ffmpeg. Fix concrete failures before promotion.

Existing v0.5 issues fixed: custom compositor settings rejected by single-video graphs; GainProcessor unity plateau boundary caused fades to fail. All v0.5 Android tests subsequently passed. Lint baseline: zero errors, 25 warnings.

## Build and delivery

GitHub workflow `.github/workflows/bc-1-android.yml` builds and runs API35 SwiftShader instrumentation. Local SDK/Gradle distributions are not available; cached Java dependencies can compile model/UI sources, but use Actions for authoritative APKs and emulator runs.

Download the artifact from the exact tested source commit. Actions debug signing is ephemeral. Retained preview signing key is local at `/root/.android/debug.keystore`; never commit, upload or print the key. Existing local signing utility: `/tmp/SignBcApk.java`. Certificate SHA256 must be `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d` for updates to v0.3/v0.4 Preview. If the key is unavailable, report update-signing limitation honestly and preserve existing APKs.

Publish a versioned APK, SHA256SUMS and QA/readme into the APK repository. Verify raw download bytes match. ChatGPT's native file-download page failed for the user, so final delivery should lead with a direct GitHub raw APK link. Save new user-facing APK deliverables persistently when supported. Update this checkpoint and release QA with exact commit, workflow run, tests and checksum after each completed gate.

No physical Snapdragon 8 Gen 3, Samsung DeX, thermal, battery or RTX testing has occurred. GLES3 and portable model are implemented; performance claims must remain qualified. Preserve prior APKs and compatibility, do not expand scope to unrelated emulator native core code.
