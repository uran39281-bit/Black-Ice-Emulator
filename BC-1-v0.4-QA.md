# BC-1 v0.4 Preview — Video Timeline & Export

## Delivery

- Branch: `bc-1-v0.4-timeline`, based on the preserved v0.3 effects branch and v0.2 Dual Workspace history.
- Application: `com.blackice.bc1.preview`, version `0.4.0` (code 4), Android 8.0/API 26 or newer, GLES 3.0.
- Delivered file: `BC-1-v0.4-Preview.apk` (debug-signed preview).
- APK SHA-256: `9ca4a99e2b1ae91d3f9d3b83000eae292ea0963d338864fea1e64b6155aec807`.
- Signing certificate SHA-256: `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`.

Install the delivered APK over the earlier v0.3 Preview to preserve its app data. The original v0.2 package remains separate. Do not uninstall to update: uninstalling deletes private projects and imported sources. CI-generated APKs use a different, ephemeral signing key and are not interchangeable with the delivered preview for updates.

## Included

Saved projects with atomic JSON persistence and reopening; video/image import; non-destructive trim, split, duplicate, remove and reorder; timeline scrubbing, zoom and snapping; session undo/redo; twelve effect choices including None; seven overlapping transitions; timed text/image overlays; clip gain and independent trimmed/looping music. Preview and export consume the same composition. Foreground H.264/AAC export offers cancellation and publishes completed MP4 files to the selected destination. The previous still-image effects lab is available from Setup.

Phone and wide workspaces retain the Black Ice V1 dark/cyan design. Auto, Phone and Wide mode choices persist independently of project aspect. Export supports landscape or portrait 1080p with a 30 fps cap. 3D Page Curl remains excluded. The project model and renderer adapter are separated for a future desktop backend; RTX support is not implemented.

## Validation

Local build: JDK 17, Gradle 8.11.1, AGP 8.10.1, compile SDK 36, Media3 1.11.1. All 23 unit tests pass; lint reports no errors and 17 warnings. APK signature verification passes.

[Final CI run 38852632310](https://github.com/uran39281-bit/Black-Ice-Emulator/actions/runs/38852632310) passed both build and GPU-test jobs on source commit `01651d45dde8be7887ac48bbb297b4cb1a34d4eb`. The 12 Android instrumentation tests passed on an API 35 x86_64 emulator with SwiftShader. They cover all seven transition exports, timed image overlays, grayscale/opacity, saved project round-trip, trim/split, preview seek, foreground 1080p export and cancellation, workspace resizing, and the original still-image GPU suite.

The retained GPU-test artifact contains logs, screenshots and `acceptance-1080p.mp4`. Manual inspection confirmed populated 1080×1920 phone, 1920×1080 wide and 780×420 compact-wide workspaces with visible previews and accessible tool panels. The compact-wide inspector scrolls.

Independent ffprobe/ffmpeg checks found 1920×1080 H.264 video, mono 48 kHz AAC audio, and a 3.487-second container duration; the entire file decodes without errors. The fixture's 30 fps source exports at approximately 29.71 average fps because of boundary timing; the product promises a cap, not constant-frame-rate conversion. Frames at 0.5, 1.75 and 3.0 seconds confirmed correct orientation, a blended transition and the timed title disappearing. Audio spectrum checks confirmed the 440 Hz first clip, the independent 880 Hz music beginning after its 0.5-second offset and continuing when looped, and the 660 Hz second clip.

An earlier repeated-export emulator run exposed Goldfish codec buffer-pool failures and a muxer watchdog timeout. The final build excludes that host decoder only on Goldfish/Ranchu emulators; its logs confirm Android software AVC decoding. Physical devices retain default decoder ordering. The unchanged export assertions passed after this adjustment. Artifact collection was also corrected to use private test storage.

## First edit

1. Tap Import in Media and choose video clips or images. Imports make private copies.
2. Tap a timeline clip, then use Edit to trim/split or Effects to set its effect and outgoing transition.
3. Use Layers for timed text/images and music. Change project aspect and preview resolution in Setup.
4. Tap the project name to create, rename or reopen a project. Changes autosave.
5. Tap Export, choose the MP4 destination, and leave the foreground export running. The Export button becomes Cancel while it runs.

## Remaining device checks

A physical Snapdragon 8 Gen 3 phone and Samsung DeX session are not available in this environment. Thermal behavior, sustained frame rate, hardware codec coverage, external-monitor switching and vendor document-provider behavior still need device testing. Emulator results do not establish Snapdragon performance. This is a preview build, not a production release. Long/large projects and unusual HDR/codecs are not exhaustively tested. Audio mixing has no automatic loudness normalization. Imports retain private source copies; there is no project-media garbage collector or portable project-bundle export yet. Interrupted exports must be restarted.

See [architecture and build details](BC-1/V0.4-ARCHITECTURE.md).
