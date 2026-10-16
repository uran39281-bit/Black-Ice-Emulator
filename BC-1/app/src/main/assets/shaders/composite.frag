#version 300 es
precision highp float;
in vec2 vUV;
out vec4 fragColor;
uniform sampler2D uInputA;
uniform sampler2D uInputB;
uniform sampler2D uBlurredA;
uniform float uProgress;
uniform float uIntensity;
uniform int uTransition;
uniform int uEffect;

float rnd(vec2 p) {
    return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453);
}
float transitionMask(vec2 uv, float p) {
    // Explicit endpoints prevent stray pixels on the first or last frame.
    if (p <= 0.0) return 0.0;
    if (p >= 1.0) return 1.0;
    if (uTransition == 1) return 1.0 - smoothstep(p-.012, p+.012, uv.x);
    if (uTransition == 2) {
        vec2 tile = floor(uv * vec2(12.0, 7.0));
        return smoothstep(rnd(tile)-.035, rnd(tile)+.035, p);
    }
    if (uTransition == 3) {
        float d = length(uv - vec2(.5));
        return 1.0 - smoothstep(p*.75-.012, p*.75+.012, d);
    }
    return p;
}
void main() {
    vec4 a = texture(uInputA, vUV);
    vec4 b = texture(uInputB, vUV);
    float k = clamp(uIntensity, 0.0, 1.0);
    if (uEffect == 1) a = texture(uBlurredA, vUV);
    if (uEffect == 2) {
        vec3 blurred = texture(uBlurredA, vUV).rgb;
        a.rgb += max(blurred - vec3(.16), vec3(0.0))*k*1.7;
        a.rgb = clamp(a.rgb, 0.0, 1.0);
    }
    if (uEffect == 3) {
        // Key against a vivid green backdrop, with intensity-dependent tolerance.
        float dist = distance(a.rgb, vec3(.10,.84,.29));
        float tolerance = mix(.08,.42,k);
        float keep = smoothstep(tolerance-.045, tolerance+.045, dist);
        a.rgb = mix(b.rgb, a.rgb, keep);
    }
    if (uEffect == 4) {
        float luma = dot(a.rgb, vec3(.2126,.7152,.0722));
        a.rgb = mix(a.rgb, vec3(luma), k);
    }
    if (uEffect == 5) a.rgb = mix(b.rgb, a.rgb, k);
    float m = transitionMask(vUV, clamp(uProgress,0.0,1.0));
    fragColor = vec4(mix(a.rgb,b.rgb,m), 1.0);
}
