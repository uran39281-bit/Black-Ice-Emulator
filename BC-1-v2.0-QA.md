# BC-1 v2.0.0 — selection-based workspace, layered timeline, transforms and animation

Candidate built from `claude/app-update-4vo05i`. It folds in the unpublished v1.1.0 timeline work and continues v1.0.4.
versionName 2.0.0 / versionCode 17, package `com.blackice.bc1.preview`. Not published: see **Signing** below.

## Implemented

**Workspace and selection**
- Three states: browsing (nothing selected), adding (+ Add drawer) and editing a selected layer. Clip tools stay hidden until a compatible layer is selected.
- Top bar: project name, + Add, Settings and Export normally; a selected layer shows its name (tap to rename), Duplicate, Delete and More. Multi-selection shows the count with Group, Delete and More.
- Lock ("Lock editing and layout") and Auto layout ("Workspace layout") live in Settings; project settings open on demand. Undo/Redo and playback stay in the transport row.

**+ Add drawer** — Media (device gallery with thumbnails, album filters and media-access management, including Android 14 selected-photos access; file browser; project media), Audio (music track, audio layers, device audio), Text (title / subtitle / caption), Shapes (rectangle, ellipse, triangle, line) and Saved elements. Inserts at the playhead by default or at project start, into the main sequence or a new layer above. Bottom panel on phones, right-side inspector on DeX.

**Contextual tools** — Move & Transform, Color & Fill, Border & Shadow, Blending & Opacity, Effects and Presets categories. Pictures: Duration, Transform, Opacity, Effects, Duplicate (+ Chroma Key, Animation). Videos: Split, Trim, Slip, Speed, Extract Audio, Transform, Opacity, Effects, Duplicate (+ Chroma Key, Volume, Animation). Audio: Split, Trim, Slip, Volume, Fade In, Fade Out, Duplicate. Text: Edit Text, Font, Size, Color, Alignment, Transform, Opacity, Duration (+ Animation). One focused panel at a time (for example Opacity: 0–100 % slider, exact entry, Reset); Back returns without changing selection or playhead. More: Rename, Replace media, copy/paste attributes and keyframes, Save as element, Duplicate Above / After, move to layer, close gaps, lock, hide, locate, centre, multi-select, delete.

**Duration and trimming** — selected-layer handles; pictures and text extend freely; video and audio stop at their source unless speed is changed; exact start / end / duration entry (for example 3.50 s); Trim Start to Playhead, Split at Playhead, Trim End to Playhead (disabled when they would create a clip under 0.10 s); slip with frame / second nudges; no slip for pictures.

**Timeline** — centred playhead with the current time and frame above it; drag empty space to scrub, drag vertically to scroll layers, tap empty space to deselect; pinch, − / + and Ctrl+wheel zoom around the playhead (view only); ruler steps from seconds to half seconds to project frames (24 / 25 / 30 fps setting); Fit Entire Project and Zoom to Selection; edge auto-scroll while dragging; markers and preview loop range on the ruler.

**Layers** — one row per text/shape/image layer (stacked above video layers 3–1, above the main sequence), then audio rows and music; pinned left column with ≡ reorder handle, colour, thumbnail, name, eye (or mute) and lock. Hidden rows are dimmed with a crossed eye; locked rows are hatched. Solo (Layers panel) isolates in the preview only. Per-layer locks block moving, trimming, property edits and deletion and are separate from the workspace lock. Multi-select (long-press, or Ctrl/Shift-click), groups (move together in time and on the canvas; text/shape groups collapse to one row), long-press on the preview lists overlapping layers.

**Arrangement** — move multiple layers preserving spacing; align starts / ends; match duration where source limits allow (the others are listed); Duplicate Above (overlapping copy on a free layer) and Duplicate After (sequential copy); Close Gap on the selected track or selected tracks only; snapping to playhead, clip edges and markers with a dashed guide, a Settings toggle, and the same on-screen tolerance at every zoom.

**Move & Transform** — Position / Rotation / Scale / Skew / Anchor / Align tabs; movement touchpad; X/Y in project pixels; horizontal- or vertical-only movement; rotation dial, exact angle, 15° / 45° / 90° snapping; width / height (linked by default, independent when unlinked); sliding value rulers with fine / coarse modes; Flip H/V; Fit, Fill (may crop) and Original Size; centre / edge alignment; centre, corner, edge and custom anchors that keep the visible placement; per-property resets and Reset All Transforms; arrow-key nudges (Shift = 10 px).

**Preview editing** — bounding box with corner (proportional unless unlinked), edge (when unlinked) and rotation handles; centre and edge guides; tap selects the topmost layer; navigation zoom/pan (pinch with nothing selected, Ctrl+wheel, "Fit" chip) independent of layer scale; Locate Selected Layer (view only) vs Center on Canvas (moves the layer); hold Compare for a before/after view.

**Animation** — position, scale, rotation, anchor, skew, opacity and effect intensity; a diamond beside each property: grey ◇ unanimated (edits change the static value), blue ◇ animated between keys, ◆ on a key; add, move, copy, paste, delete; Linear, Ease In, Ease Out, Ease In/Out and custom curves; previous/next keyframe buttons and [ / ] keys; editable motion paths in the preview; keyframe diamonds on the selected layer in the timeline. Keys use the layer's source time, so moving, trimming, splitting and duplicating keep animation attached to its content.

**Reusable workflows** — replace media keeping timing, transforms, effects and animation, with an explanation when the new source is shorter; selective copy/paste of transform, effects, styling and animation; saved effect and animation presets; saved grouped elements; real PCM waveforms for audio layers; editable markers with approximate beat detection; extract audio to an independent track with optional muting of the original.

**Compatibility** — schema 4 adds layer rows, transforms, markers, loop range and frame rate; schemas 1–3 load with neutral defaults and keep their versioned backup. Preview and export share one transform definition (shader and overlay renderer); hidden layers and muted audio are excluded from export; solo never affects export.

## Verification

Qualifying source `03dd1ce582954ebb0496876ff142b5f8daa02079`, [Actions run 39022975446](https://github.com/uran39281-bit/Black-Ice-Emulator/actions/runs/39022975446): build and GPU jobs both passed.

- 76 JVM unit tests (0 failures), lint with 0 errors, debug APK and test APK built. New unit coverage: schema 3 → 4 migration and round trip; layer visibility / solo rules; atomic multi-move, align, match duration (source limits), duplicate above / after, close gaps, trim to playhead, track reordering, groups, markers and marker snapping; forward / inverse transform maths, anchor-preserving placement, Fit / Fill / Original Size, alignment and offscreen detection; keyframe states, set / toggle / move / copy / paste / delete, easing presets, bulk key operations, animation presets; selective attribute copy with retimed animation; ruler steps and labels; beat detection.
- 25 Android instrumentation tests on the API 35 emulator (`OK (25 tests)`), including:
  - `LayerRenderingTest` renders real compositions and decodes pixels: an anchored, half-scale layer fills exactly the right half (matching the preview box's TransformMath prediction), text/shape layers render above video, hidden video and overlay layers stay hidden in export, solo is ignored in export and isolates the soloed layer in preview.
  - `WorkspaceV2Test` on phone 9:16 and DeX 16:9: + Add drawer categories and insertion at the playhead, contextual top bar, shape tools, Opacity focused panel, keyframe diamond enabling animation and a second key from an animated edit, Back keeping selection and playhead, per-layer lock blocking duplicate and movement (separate from the workspace lock), arrow-pixel nudge, delete + undo, text layers, multi-select and grouping, eye toggle, solo, save and reopen.
  - `TimelineInteractionTest` on phone and DeX: centred playhead, tap-select, half-second picture trim by handle, undo / redo, cancelled trim, empty-space deselect, empty-space scrubbing that keeps the timeline under the centred playhead, zoom, pinch, preview drag + undo, save / reopen, audio and text tool sets.
  - Existing v1.x suites (workspace layouts, media browser, thumbnails, diagnostics, GPU effects / transitions, export and recovery) still pass.
- All four independent ffmpeg / ffprobe gates passed (three sequential H.264 1920×1080 + AAC exports and the post-failure recovery export), plus the synchronized five-second speed-change export.
- Screenshots (phone and DeX: Add drawer, Move & Transform, Layers, timeline selection and text tools) were reviewed from the `qa-screenshots/claude/app-update-4vo05i` branch the CI publishes.
- APK artifact `BC-1-v2.0.0-apk` (ID 12097152536, zip SHA256 `1628e0cafbbbbd0728f46ef30347b67ca2eb96ff423d5abfba598cae07be117b`), debug-signed by CI.

Found and fixed during qualification: a Media3 1.11 race where a second visual sequence starting with a picture requested synthetic silence before the shared audio output existed (NullPointerException in `SequenceAssetLoader`), which intermittently failed previews and exports with pictures on several layers. Only the first video sequence now carries audio; other layers' sound uses parallel audio-only sequences.

## Signing

The retained BC-1 preview signing key is not available in this build environment. CI APKs are signed with an ephemeral debug key, so they cannot be installed over an existing BC-1 Preview without uninstalling it (which deletes its projects). The candidate is therefore not published to the APK repository.

## Limits

- Development-signed debug build, not production-stable certification. API 35 emulator only; no physical Snapdragon 8 Gen 3 / Samsung DeX, thermal, battery or FPS measurements.
- Blend modes combine text/shape/image layers with each other; video and picture layers use normal blending.
- Visual layers are the main sequence plus three independent video layers (the Media3 multi-input limit kept from v1.x) and up to 20 text/shape/image layers.
- Video and audio cannot loop past their source; use speed or Duplicate After instead.
- Beat detection is approximate (peaks of a 512-bin waveform); markers stay editable.
- Preview moves show a guide during the gesture; the full GLES preview is re-prepared on release.
- The intermittent emulator "Muxer error" (first seen in v1.0.3, seen once more on 2026-10-17 during this work) is still not root-caused; failures now print the full error chain into the CI log.
