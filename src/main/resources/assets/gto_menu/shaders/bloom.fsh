#version 150 core
// ============================================================================
// Bloom pass：可分离高斯模糊（9 tap）
// 首次调用 uThreshold>0：先做亮部软提取（knee 曲线）再横向模糊；
// 后续调用 uThreshold=0：纯模糊，横向/纵向交替迭代。
// ============================================================================

uniform sampler2D uTex;
uniform vec2  uTexel;      // 输入纹理 1/尺寸
uniform vec2  uDir;        // (1,0) 或 (0,1)
uniform float uThreshold;  // >0 时启用亮部提取

in vec2 vUv;
out vec4 fragColor;

// 软膝亮部提取（threshold 附近平滑过渡，保留一点点亮部以下能量做漫射）
vec3 softExtract(vec3 c) {
    float l = max(max(c.r, c.g), c.b);
    float knee = uThreshold * 0.5;
    float w = clamp((l - uThreshold + knee) / (2.0 * knee), 0.0, 1.0);
    w = w * w * (3.0 - 2.0 * w);
    return c * w;
}

vec3 fetchTap(vec2 uv) {
    vec3 c = texture(uTex, uv).rgb;
    return uThreshold > 0.0 ? softExtract(c) : c;
}

void main() {
    // 9 tap 高斯权重
    const float w0 = 0.227027;
    const float w1 = 0.1945946;
    const float w2 = 0.1216216;
    const float w3 = 0.0540540;
    const float w4 = 0.0162162;

    vec3 sum = fetchTap(vUv) * w0;

    vec2 o;
    o = uDir * uTexel * 1.3846;
    sum += (fetchTap(vUv + o) + fetchTap(vUv - o)) * w1;
    o = uDir * uTexel * 3.2308;
    sum += (fetchTap(vUv + o) + fetchTap(vUv - o)) * w2;
    o = uDir * uTexel * 5.077;
    sum += (fetchTap(vUv + o) + fetchTap(vUv - o)) * w3;
    o = uDir * uTexel * 6.923;
    sum += (fetchTap(vUv + o) + fetchTap(vUv - o)) * w4;

    fragColor = vec4(sum, 1.0);
}
