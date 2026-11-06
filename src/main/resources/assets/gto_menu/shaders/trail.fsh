#version 150 core
// ============================================================================
// Trail pass（数字能量尾迹 / 运动残影）
//
//   新像素 = max(当前场景, 上一帧残影 × 衰减)
//
// max 的性质：
//   - 静态内容（星辰、静止相机下的盘结构）达到平衡后亮度不变，不会累积过曝
//   - 只有移动的 0/1 数字会在身后留下指数衰减的亮尾迹
//   - 天然 HDR 友好，尾迹再送入 bloom 形成光带
// ============================================================================

uniform sampler2D uCur;   // 本帧 scene（HDR）
uniform sampler2D uPrev;  // 上一帧 trail（HDR）
uniform float uDecay;     // 残影衰减 0.8~0.95

in vec2 vUv;
out vec4 fragColor;

void main() {
    vec3 cur  = texture(uCur,  vUv).rgb;
    vec3 prev = texture(uPrev, vUv).rgb;
    fragColor = vec4(max(cur, prev * uDecay), 1.0);
}
