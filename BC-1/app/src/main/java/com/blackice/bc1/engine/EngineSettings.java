package com.blackice.bc1.engine;

/** Backend-neutral parameter contract. IDs are append-only to preserve saved sessions. */
public final class EngineSettings {
    public static final String[] TRANSITIONS = {"Dissolve", "Directional Wipe", "Checker Dissolve", "Radial Reveal", "Slide Left", "Zoom Dissolve", "Dip to Black"};
    public static final String[] EFFECTS = {"None", "Gaussian Blur", "Glow / Bloom", "Chroma Key (Green)", "Grayscale", "Opacity", "Vignette", "Sharpen", "Pixelate", "Exposure", "Saturation", "Ice Grade"};
    public static final String[] QUALITIES = {"Performance (50%)", "Balanced (75%)", "Quality (100%)"};
    public static final String[] EASINGS = {"Linear", "Smooth"};
    public volatile int transition = 0, effect = 0, quality = 1, easing = 0;
    public volatile float progress = 0f, intensity = .6f, opacity = 1f;
    private EngineSettings() { }
    public static EngineSettings defaults() { return new EngineSettings(); }
    public float renderScale() { return quality == 0 ? .5f : quality == 2 ? 1f : .75f; }
    public static int index(int value, int count) { return Math.max(0, Math.min(count - 1, value)); }
    public static float unit(float value, float fallback) {
        return Float.isNaN(value) || Float.isInfinite(value) ? fallback : Math.max(0f, Math.min(1f, value));
    }
    public void sanitize() {
        transition=index(transition,TRANSITIONS.length); effect=index(effect,EFFECTS.length);
        quality=index(quality,QUALITIES.length); easing=index(easing,EASINGS.length);
        progress=unit(progress,0f); intensity=unit(intensity,.6f); opacity=unit(opacity,1f);
    }
}
