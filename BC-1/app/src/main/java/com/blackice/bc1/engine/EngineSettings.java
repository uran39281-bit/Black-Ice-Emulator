package com.blackice.bc1.engine;

/** User-facing settings. Volatile fields are read by the dedicated GL thread. */
public final class EngineSettings {
    public static final String[] TRANSITIONS = {"Dissolve", "Directional Wipe", "Checker Dissolve", "Radial Reveal"};
    public static final String[] EFFECTS = {"None", "Gaussian Blur", "Glow / Bloom", "Chroma Key (Green)", "Grayscale", "Opacity"};
    public static final String[] QUALITIES = {"Performance (50%)", "Balanced (75%)", "Quality (100%)"};

    public volatile int transition = 0;
    public volatile int effect = 0;
    public volatile int quality = 1;
    public volatile float progress = 0.0f;
    public volatile float intensity = 0.60f;

    private EngineSettings() { }

    public static EngineSettings defaults() { return new EngineSettings(); }

    public float renderScale() {
        if (quality == 0) return 0.5f;
        if (quality == 2) return 1.0f;
        return 0.75f;
    }
}
