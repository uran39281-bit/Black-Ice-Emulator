# BC-1 • Custom GPU Engine v0.1.0

An **original** experimental Android effects/rendering engine for the Snapdragon 8 Gen 3 / Adreno 750 generation. This is the rendering technology testbed for a potential future integration with **Black Ice Editor**, not the final Black Ice editing UI.

## Included in v0.1

- Real-time Android OpenGL ES 3.0 renderer (GPU shader compilation; fullscreen VBO/VAO; offscreen FBOs)
- Two image inputs, including system photo/document picker and built-in demo scenes
- Four independent transitions: Dissolve, Directional Wipe, Checker Dissolve, Radial Reveal
- Five effects on layer A: Gaussian Blur, Glow/Bloom, Green Chroma Key, Grayscale, Opacity
- Adjustable transition progress and effect intensity
- Separable 9-tap, two-pass Gaussian filter (horizontal + vertical)
- Offscreen render resolution presets: 50% (640×360), 75% (960×540), 100% (1280×720)
- Live GPU renderer name, processing resolution, and approximate FPS readout
- Letterboxed 16:9 preview to avoid stretching on mobile and DeX windows
- Simple GLSL 300 ES source assets + pure-Java endpoint tests
- No proprietary Alight Motion XML, binaries, shaders, or assets included

## What is NOT implemented yet

- Not a multi-track editor or the Black Ice V1 interface
- No video decode/encode, audio, timeline, or MP4 export
- No project saving, motion tracking, keyframes, or scene graph
- No 3D Page Curl (intentionally deferred)
- No Vulkan backend, native NDK compute path, RTX 4050/Windows backend, or feature parity with desktop GPUs
- No adaptive GPU governor or thermal management; performance modes only scale offscreen effects
- Not yet validated on physical Snapdragon 8 Gen 3 hardware

## Project / build

```text
BC-1/
  settings.gradle
  build.gradle
  app/build.gradle
  app/src/main/AndroidManifest.xml
  app/src/main/java/com/blackice/bc1/MainActivity.java
  app/src/main/java/com/blackice/bc1/engine/BcRenderer.java
  app/src/main/java/com/blackice/bc1/engine/EngineSettings.java
  app/src/main/java/com/blackice/bc1/engine/TransitionMath.java
  app/src/main/assets/shaders/{fullscreen.vert,gaussian.frag,composite.frag}
  app/src/test/java/com/blackice/bc1/engine/TransitionMathTest.java
```

Requires Android SDK 35, Android Gradle Plugin 8.7.3, JDK 17, Gradle 8.9. From the `BC-1` folder:

```bash
gradle testDebugUnitTest assembleDebug
```

Result: `app/build/outputs/apk/debug/app-debug.apk` (unsigned debug-signed APK; debug builds use an automatically generated key). On GitHub, the `build-android.yml` workflow builds a downloadable artifact automatically for the `bc-1-v0.1` branch.

On phone, you can download the **GitHub Actions artifact ZIP** and open the contained APK. Installing a debug APK may require approving installation from your browser/files app. The source ZIP itself is *not* an installable app.

## Build architecture / future portability

Current pipeline: `Bitmap A/B -> GL textures -> optional 2-pass Gaussian FBO -> composite/transition shader -> Android GLSurfaceView`.

Engine responsibilities are isolated under `engine/`; GLSL shaders are separate assets, not embedded as app UI strings. For a future desktop engine, keep effect math/semantics and recreate the platform host and the GL backend using desktop OpenGL or Vulkan. The existing Java GLES driver **will not** automatically run on an RTX 4050; a desktop implementation will be required.

## Controls

- **Import A/B:** Open an image using Android's document picker.
- **Transition:** Select one of four effects transitioning scene A into B.
- **Effect on layer A:** Independent image treatment before transition.
- **Effect strength:** Controls intensity; for Green Chroma Key it adjusts tolerance.
- **Rendering quality:** Changes offscreen filter resolution only; output remains fitted to screen.
- **Play:** Auto-animates the transition over about 3.5 seconds; **Pause** or scrub to inspect frames.

## License / provenance

All v0.1 application code and shaders were written for BC-1. Alight Motion APK resources were used only to help understand categories and desired behavior; no extracted proprietary files are redistributed here. The user owns the resulting BC-1 original source subject to applicable third-party build tool licenses.
