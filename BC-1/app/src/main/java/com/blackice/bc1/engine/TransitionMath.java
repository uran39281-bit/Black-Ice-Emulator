package com.blackice.bc1.engine;

/** Pure-Java reference for validation of transition endpoints. */
public final class TransitionMath {
    private TransitionMath() { }
    public static float clamp01(float x) { return Math.max(0f, Math.min(1f, x)); }
    public static float dissolve(float progress) { return clamp01(progress); }
    public static float wipe(float progress, float u) {
        if (progress <= 0f) return 0f;
        if (progress >= 1f) return 1f;
        return clamp01((progress - u) * 40f + 0.5f);
    }
    public static float radial(float progress, float u, float v) {
        if (progress <= 0f) return 0f;
        if (progress >= 1f) return 1f;
        float dx=(u-.5f)*16f/9f;
        float r = (float)Math.sqrt(dx*dx + (v-.5f)*(v-.5f));
        return clamp01((progress*1.03f - r)*40f + .5f);
    }
}
