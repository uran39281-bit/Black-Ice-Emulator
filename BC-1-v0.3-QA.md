# BC-1 v0.3 validation and installation notes

Source baseline: `ad4ac826f6adfea2db14065417efa48bd419b114` on `bc-1-v0.2-ui`.
Implementation: `3d7af82e600a8c68530174f7bde58f3a26a8cd89` on `bc-1-v0.3-effects`.
The baseline branch and pre-existing emulator overlay were not changed.

## APK

- `BC-1-v0.3-Preview.apk`: Android 8.0+ / OpenGL ES 3.0; version code 3, version name 0.3.0.
- Preview application ID: `com.blackice.bc1.preview`.
- Build with Java 17 / Gradle 8.9 / SDK 35 using `gradle -Pbc1SideBySide testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`.
- APK v2 signature verified with Android SDK apksigner.
- SHA-256: 074a97bd12bc78bc1bcb4e7d1ab812e7c0215fd01eaa8ae570f09e4fa2df842c

The available v0.2 APK's signing certificate differs from the current development key, and the v0.2 source archive has no key. The supplied v0.3 therefore installs alongside v0.2, leaving its app and private data intact. It cannot read the older app's private storage. Future preview upgrades should use the separately retained preview signing backup. Never commit the key. GitHub CI APKs use ephemeral debug keys and are primarily build/test artifacts.

## Automated checks

- 12 Java unit tests: passed (layout policy, transition endpoint reference math, parameter validation and compatibility).
- Android lint: passed with no errors; remaining warnings concern backup configuration and untranslated English UI text.
- Four production GLES renderer tests passed on API 35 / SwiftShader: all seven transitions with both curves and exact endpoints; all twelve effect options at three qualities and three strengths; opacity/dip midpoint; blur orientation, input replacement and EGL context recreation.
- Workspace instrumentation: passed on phone 1080×1920, desktop 1920×1080 and compact desktop 780×420 emulator configurations; tab changes retain the GPU monitor and recreation retains the selected workspace. The final API 35 run passed all 5 instrumentation tests.
- Screenshots: direct APK captures reviewed at 1080×1920, 1920×1080 and 780×420. The compact capture revealed a partly clipped Settings tab; its vertical padding was reduced from 14 dp to 6 dp, preserving 48 dp targets. The final APK was rebuilt and unit/lint checked after this cosmetic fix. That final spacing adjustment has not had a second screenshot capture. The phone emulator capture also shows partial clipping of system status-bar text after a density override; verify system bars on real hardware.

The first expanded UI test caught a preview-size failure in a 780×420 desktop window. The fix reduces compact chrome/timeline height, limits the GPU status line, and keeps short phone workspaces scrollable. Revalidation is recorded above.

Functional CI run: https://github.com/uran39281-bit/Black-Ice-Emulator/actions/runs/38848326712

Direct screenshot run: https://github.com/uran39281-bit/Black-Ice-Emulator/actions/runs/38848824943

The dedicated visual workflow captures directly from a previously built APK. Instrumentation captures in app storage were removed during test cleanup, so those older test reports do not include PNG files.

## Practical limits

This is still the v0.2 two-image engine lab, now extended with additional transitions, effects and single-session recovery. It is not yet a video decoder/encoder, editable multilayer timeline, audio editor, named-project manager or MP4 exporter. 3D Page Curl stays excluded. The backend-neutral settings contract and shader separation support future desktop work, but no RTX 4050 backend is implemented.

No physical Snapdragon 8 Gen 3 or Samsung DeX session was available. Emulator validation cannot establish Adreno frame rate, power draw, thermal behavior, external-monitor hotplug or real DeX keyboard/mouse behavior. Automatic and explicit layout selection are implemented; perform those hardware checks before calling the app device-certified.
