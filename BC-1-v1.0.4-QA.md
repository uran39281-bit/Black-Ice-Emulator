# BC-1 v1.0.4 — export diagnostics and recovery checks

## Changes

- Setup → Export diagnostics opens a readable report; Copy report copies it only when tapped. Nothing is sent automatically.
- Summary contains stage (preparation/encoding/saving), Media3 error code and a bounded exception-class chain. Media filenames, paths, exception messages and project contents are excluded from the shareable summary. Full nested errors remain in debug-only device logs for QA.
- The last failure is retained through later successful exports. Cancellation does not replace it.
- Existing V1 layout, dual workspace modes, thumbnail fix, GLES effects, transitions and project schema are preserved. No 3D Page Curl; no RTX backend yet.

## Verification

Source `ae912eec2ca3a9d4a63212ae8646646bd30c24c5`, [Actions run 38888011311](https://github.com/uran39281-bit/Black-Ice-Emulator/actions/runs/38888011311).

- Local Java compilation and36 JVM tests passed, including diagnostic redaction, unknown-stage handling and bounded/cyclic causes.
- GitHub Actions build and GPU jobs passed:36 unit tests (0 failures/0 skipped),20 Android instrumentation tests; lint0errors30warnings. All four independent ffmpeg/ffprobe gates passed. Artifacts APK12039928235, build12040312524, GPU12040997551. Debug logs identify the deliberately missing media as FileNotFoundException; recovery succeeds.
- Final screenshots visually reviewed: real paused-first-frame at00:00, phone9:16, wide16:9, compact-wide, and the diagnostic dialog. Dialog text and Copy report/Close controls fit the small phone viewport; existing preview, timeline, media grid and V1 styling remain intact.
- New recovery case deliberately references missing media, checks failure reporting, no published partial MP4, temporary-file cleanup and service shutdown, then successfully exports a valid project. Successful export must retain the earlier redacted report.
- UI test opens the actual Setup diagnostic dialog and taps Copy report, verifies clipboard content/redaction and captures the dialog.
- Three required consecutive 1080p exports plus the post-failure recovery export are independently decoded by ffmpeg and checked by ffprobe for H.2641920×1080/AAC and duration. These are mandatory successes, not retries that hide failures.

## Earlier intermittent export failure

v1.0.3 source261c82d/run38874891062 attempt1 had one `Muxer error`; unchanged attempt2 passed. Investigation source664e2f2/run38881066895 passed all18 Android tests and three independent MP4 checks (3.486708s each). The initiating intermittent muxer cause remains unknown. v1.0.4 improves diagnostics and failure-recovery coverage; it is NOT claimed to fix that unidentified cause.

## APK

- Package `com.blackice.bc1.preview`, versionName1.0.4/code16, GLES3required.
- Signing certificate SHA256 `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d` must match retained identity.
- APK SHA256 `7e80ed1718bf84c2bf68749681c11a13c54ba9d5dbe558467316dfea1e732ffe`; retained signing certificate verified. [Download APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.4/BC-1-v1.0.4.apk).
- Versioned release; all earlier APKs retained. Install over the existing Preview app to retain projects; do not uninstall first.

## Limits and remaining work

Development-signed debug APK, not production-stable certification. API35/SwiftShader emulation is not physical Snapdragon8Gen3 or SamsungDeX validation; no measured hardware FPS, thermals or battery claim. Image/multilayer preview still uses cancellable cached GLES preparation and consumes time/storage on long projects. V1 project-flow/category-inspector differences remain; no pixel-perfect-copy claim. Diagnostic summaries intentionally omit detailed messages; debug logs may be needed for full root-cause work.
