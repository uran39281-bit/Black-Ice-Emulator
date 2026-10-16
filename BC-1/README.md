# BC-1 Engine v0.2.0 — Dual Workspace Preview

BC-1 is an original Android OpenGL ES 3.0 effects engine testbed for Snapdragon 8 Gen 3-class phones. The V0.2 update introduces the **Black Ice V1-inspired dual workspace**, based on supplied phone and Samsung DeX reference images.

## User-selectable and automatic layouts

- **Auto** (default): selects Phone UI for narrow portrait windows and Wide UI for sufficiently large landscape/tablet/DeX windows. Uses current window dimensions, smallest screen width (tablet detection), and external-display context. A rotated ordinary phone stays in Phone UI in Auto mode; DeX resizes can trigger layout changes.
- **Phone**: portrait-oriented, scrollable single-column interface, preview + demo timeline + bottom tools navigation.
- **Wide**: tablet/DeX-oriented editor chrome with left media bin, center preview, right inspector, and bottom demo timeline.
- Selection is saved in app preferences. Tap **LAYOUT** in the top bar, or choose Settings → Choose UI Layout.
- To avoid unusable controls, a *requested* wide layout temporarily displays Phone UI on narrow windows; the choice is retained and becomes Wide when the window is wide enough.
- Neither layout changes the **16:9 image composition**, FBO resolution preset, or an eventual export resolution. Workspace ratio is not project ratio.

## Actually interactive in v0.2

- Import image A and B, keep images and transition progress during layout switches and window resize.
- Four original GPU transitions: Dissolve, Directional Wipe, Checker Dissolve, Radial Reveal.
- Image A processing: Gaussian Blur, Glow/Bloom, Green Chroma Key, Grayscale, Opacity.
- Play/pause, touch-scrub demo timeline, effect intensity and processing quality selectors.
- DeX/keyboard testing: Space plays/pauses, Left/Right arrows scrub the transition by 2.5%.
- GPU name, approximate FPS, offscreen processing size.

## Known limitations

The timeline is a **non-editable demonstrator showing 2 still inputs**. There is no video decoder/encoder, multilayer editing, audio, project persistence, or MP4 export yet. Layouts are inspired by user-supplied mockups, not pixel-identical copies. 3D Page Curl is deferred. The RTX 4050 desktop backend is future work and is not contained here.

## Build

Android Gradle Plugin 8.7.3, Gradle 8.9, Java 17, Android SDK 35.

```
gradle testDebugUnitTest assembleDebug
```

When checked out as `Black-Ice-Emulator/BC-1`, run `cd BC-1` first. On GitHub, `.github/workflows/build-android.yml` builds a debug APK artifact on pushes to `bc-1-v0.2-ui`.

License/provenance: BC-1 application source and shaders are independently authored. No extracted proprietary Alight Motion resources are distributed.
