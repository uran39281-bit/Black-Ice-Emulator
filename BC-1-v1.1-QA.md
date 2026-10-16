# BC-1 v1.1.0 — contextual editing and timeline controls

Candidate, not yet published. Continues v1.0.4 on `bc-1-v1.1-timeline`.

## Implemented
- True deselection; media-specific tools in compact two-column menus; focused adjustment dialogs.
- Selected clip trim handles; exact duration; still images can extend beyond their former synthetic source limit. Video/audio bounds validated.
- Slip preserves source span and timeline placement. 0.25×–4× A/V speed changes use Media3's shared speed pipeline. Splits and source-time keyframes account for speed.
- Pinch, buttons and Ctrl+wheel/keyboard timeline zoom with anchored scrolling, adaptive ruler and 30 fps frame grid. Display zoom never changes media timing.
- Main sequence reordering/ripple trim; independent track movement and trim. One undo entry per completed gesture; cancellation does not mutate the project.
- Screen-space snapping to playhead and clip boundaries, guide, persistent toggle.
- Jump-to-selected/project boundaries; visible Undo/Redo next to playback.
- Lock/layout controls in Settings; balanced/expanded timeline/expanded preview choices.
- Selected visual-layer transform gestures, pinch scale/rotation and mouse corner handles. Center guides; uniform resizing. Existing animated channels receive a key at the playhead.
- Text selection and focused text/font/size/color/alignment/opacity/duration panels. Overlay timing handles and movement.
- Schema 3 reads schemas 1/2 with defaults; old JSON receives a versioned backup before first migration. Prior branches/APKs preserved.

## Verification pending
Local Java compilation and 46 unit tests pass. GitHub Actions must pass build/lint, Android gesture/layout tests, GLES regression suite, four existing independent 1080p exports and the new independent speed-change A/V export. Review actual screenshots before publication.

## Limits
Development build. Physical Snapdragon/DeX tests remain outstanding. Main track is a ripple sequence: trimming its left source edge keeps its sequence position; independent tracks preserve the opposite timeline edge. Frame grid represents the editor's 30 fps timebase, not source-native frames. Preview shows a lightweight transform guide during gestures, then prepares the cached GLES result on release; it is not live full-composition GPU rendering. No Page Curl; no RTX backend. Earlier intermittent muxer cause remains unidentified.

Speed API reference: https://developer.android.com/reference/androidx/media3/transformer/EditedMediaItem.Builder#setSpeed(androidx.media3.common.audio.SpeedProvider)
