# BC-1 v1.0.3 — reliable thumbnail delivery

Continues v1.0.2 without changing the V1 layout, effects, export graph or saved-project schema.

## Fix
Media searches and workspace rebuilds create new views while decoding may still be pending. Per-card callbacks could fill the cache's bounded listener list with obsolete views, leaving the newest card as a placeholder. The browser and timeline now use stable activity-level observers. Decoded images update current cards in place; background decoding and the 8 MiB cache remain bounded.

## Regression evidence
- Baseline source `31661164b2d0e53166b6842ace987a3860ec95d6`, [run 38874632718](https://github.com/uran39281-bit/Black-Ice-Emulator/actions/runs/38874632718): FAILED as expected: 18 tests ran, exactly one failure (`rapidSearchDuringDecodeUpdatesCurrentThumbnail`) at the decoded-pixel assertion. The old current card stays a placeholder after obsolete callbacks fill the bounded list.
- Candidate source `261c82dea4bc2628953c62d2a0b262f37945764b`, [run 38874891062](https://github.com/uran39281-bit/Black-Ice-Emulator/actions/runs/38874891062): Attempt 1: build passed (33 unit tests, lint 0 errors /30 warnings). The new thumbnail regression PASSED, including pixel and retained-view checks. Android suite: 18 tests ran, one unrelated export failure in `projectRoundTripEditsMusicTextAnd1080pExport`: `Export failed: Muxer error`. All other Android tests passed. Independent MP4 validation was not reached. Attempt 2, unchanged source, completed at08:54 UTC: all18 Android tests passed; independent ffmpeg decode plus ffprobe H.2641920×1080/AAC/duration gate passed. The first-attempt export failure remains an unresolved intermittent reliability limitation; a passing rerun does not prove its cause fixed.
- The controlled Android test blocks the decoder, changes media search 32 times, releases decoding and verifies the latest card receives the expected pixels. It also checks that the card object is retained.
- Attempt-1 screenshots visually reviewed: phone9:16, wide16:9, compact-wide and `v103-thumbnails-loaded.png`. The filtered current media card shows the expected blue pixels; phone/wide previews show the layered fixture. The stress screenshot was captured while preview preparation was in progress; it qualifies thumbnail delivery, not preview readiness. V1 styling and responsive layout are unchanged.

## APK
- Package `com.blackice.bc1.preview`; versionName `1.0.3`, versionCode `15`, requires GLES3.
- Retained signing certificate SHA256 `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`.
- APK SHA256: 11b5a3cb9119492afc75c78dd4953a36cfcf8d2da13352376be2810b0876d653
- [Download v1.0.3](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.3/BC-1-v1.0.3.apk). Development build with the intermittent export limitation above; v1.0.2 remains available.
- Artifacts: APK12035420232, build12035250628, attempt-1 GPU12035510441, passing attempt-2 GPU12036120835.
- Previous APKs remain intact; the failing regression-baseline artifact is not published.

## Limits
Development-signed debug APK, not a production-stability certification. Tests use API35/SwiftShader, not physical Snapdragon 8 Gen 3 or Samsung DeX. No measured battery, thermal or hardware frame-rate claim. RTX integration remains future work; 3D Page Curl is excluded.

Image/multilayer previews still need cancellable cached GLES preparation, with time/storage cost on long projects. V1 project-flow and category-inspector differences remain documented follow-ups.

## Export investigation
Attempt-1 logcat contains Codec2 bufferpool receive errors at08:43:30 and Media3 AudioGraphInput IllegalStateException during teardown at08:44:10. These do not establish the initiating muxer cause; ExportService currently surfaces only the top-level message. Do not assume this is fixed or purely emulator-related. The unchanged rerun passed, so the initiating cause is still unknown. Follow-up should capture full ExportException/cause in debug logs and exercise repeated exports before choosing a targeted fix. Do not weaken or skip the test.


Passing-attempt screenshots reviewed on2026-10-16: paused initial frame at00:00, phone9:16, wide16:9, compact-wide and thumbnail stress. Real layered preview pixels and current-card thumbnails confirmed; no layout change. The thumbnail stress capture intentionally precedes preview preparation completion.
