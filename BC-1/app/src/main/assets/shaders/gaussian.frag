#version 300 es
precision highp float;
in vec2 vUV;
out vec4 fragColor;
uniform sampler2D uSource;
uniform vec2 uTexelStep;
uniform float uRadius;
void main() {
    // 9 tap separable Gaussian (weights sum to 1): two passes, X then Y.
    vec2 d = uTexelStep * max(uRadius, 0.01);
    vec4 result = texture(uSource, vUV) * 0.2270270270;
    result += texture(uSource, vUV + d*1.0) * 0.1945945946;
    result += texture(uSource, vUV - d*1.0) * 0.1945945946;
    result += texture(uSource, vUV + d*2.0) * 0.1216216216;
    result += texture(uSource, vUV - d*2.0) * 0.1216216216;
    result += texture(uSource, vUV + d*3.0) * 0.0540540541;
    result += texture(uSource, vUV - d*3.0) * 0.0540540541;
    result += texture(uSource, vUV + d*4.0) * 0.0162162162;
    result += texture(uSource, vUV - d*4.0) * 0.0162162162;
    fragColor = result;
}
