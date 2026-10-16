# BC-1 v1.0.2 — V1 media browser

Continues the verified v1.0.1 editor, preserving previous branches, saved-project format and APKs.

## Changes
- Responsive two-column Project Media browser on large tablet/DeX windows; narrower wide windows retain one column.
- Working All/Video/Audio/Image filters and filename search.
- Search is case-insensitive and locale-independent; filtering never reorders or edits the project.
- Filtered cards select the original clip index. Browser highlights follow selections made in the inspector/timeline.
- Search/filter state survives activity recreation; thumbnails use the existing bounded cache.
- Phone and compact-wide workspaces retain their existing arrangement.

## Verification
- Source `5e551384278f03270258ff7149c4f36a3fe0d1ce` on `bc-1-v1.0.2-media`.
- [Actions run 38871745189](https://github.com/uran39281-bit/Black-Ice-Emulator/actions/runs/38871745189).
- 33 unit tests passed, none skipped; lint 0 errors /30 warnings. Two additional warnings concern localization of the media count text.
- 17 Android instrumentation tests passed. New checks verify side-by-side media cards, type-filter empty states, filename search, correct original-clip selection, selection highlights and filter/query restoration after activity recreation. Existing GPU effects/transitions, context loss, editing/audio/project bundles, export/cancellation and preview-cache checks passed.
- Independent ffmpeg decode passed; ffprobe confirms H.264 1920×1080 + AAC.
- Final phone 1080×1920, desktop 1920×1080, filtered desktop and compact-wide 780×420 screenshots visually reviewed. The new grid is readable and the preview remains undistorted.
- Artifacts: APK `12032814882`, build reports `12033765250`, GPU reports `12033875888`.
- Package `com.blackice.bc1.preview`, versionName `1.0.2`, versionCode `14`, GLES3 required.
- Retained signing certificate SHA256 `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`.
- APK SHA256 `de63efff3bd6442e26419ae1693c0196d79a98219a1b1edfdf014c96bce3fa34`.

## Limitations
This is a development-signed debug APK, not a production-stability certification. Tests use API35/SwiftShader, not physical Snapdragon 8 Gen 3 or Samsung DeX hardware; no measured thermal/battery performance claim. RTX remains a future backend. 3D Page Curl is excluded.

The official V1 reference images are versioned under `BC-1/design/v1/`. The media grid now follows their two-column structure, but the dedicated project-flow screen and category inspector remain follow-up work; no pixel-perfect claim.

Image/multilayer previews still require cancellable cached GLES preparation. Long projects cost preparation time/storage. No effects or project schema changed in this patch.
