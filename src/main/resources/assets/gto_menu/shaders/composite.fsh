#version 150 core
// ============================================================================
// Composite pass（原生全屏分辨率）
//   trail（含本帧 + 能量尾迹的 HDR 画面）+ bloom
//   → 曝光 → ACES 电影级色调映射 → sRGB → 暗角 → 底部标题区压暗 → 抖动
// ============================================================================

uniform sampler2D uTrail;
uniform sampler2D uBloom;
uniform vec2  uResolution;
uniform float uTime;
uniform float uExposure;
uniform float uBloomStrength;
uniform float uVignette;       // 暗角强度 0~1
uniform float uBottomStart;    // 底部压暗起始（NDC y）
uniform float uBottomEnd;
uniform float uBottomDark;

in vec2 vUv;
out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 acesToneMap(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

vec3 linearToSrgb(vec3 c) {
    vec3 lo = c * 12.92;
    vec3 hi = 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055;
    return mix(lo, hi, step(0.0031308, c));
}

void main() {
    vec3 hdr = texture(uTrail, vUv).rgb + texture(uBloom, vUv).rgb * uBloomStrength;

    // 曝光
    hdr *= uExposure;

    // 色调映射 + gamma
    vec3 color = linearToSrgb(acesToneMap(hdr));

    // 暗角（以屏幕中心为准，随宽高比修正）
    vec2 vp = (vUv - 0.5) * vec2(uResolution.x / uResolution.y, 1.0) * 1.25;
    float vig = 1.0 - uVignette * smoothstep(0.35, 1.15, dot(vp, vp));
    color *= vig;

    // 底部干净区域压暗（留给标题 / 副标题）
    float sy = vUv.y * 2.0 - 1.0;
    color *= 1.0 - uBottomDark * (1.0 - smoothstep(uBottomStart, uBottomEnd, sy));

    // 微抖动消除条带
    color += smoothstep(0.0, 0.02, max(max(color.r, color.g), color.b)) * (hash12(gl_FragCoord.xy + fract(uTime) * 61.7) - 0.5) / 255.0;

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
