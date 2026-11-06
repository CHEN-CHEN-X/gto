#version 150 core
// ============================================================================
// 黑洞 v2 —— 连续吸积盘 + 环绕数字层（独立副本，原 blackhole.fsh 未改动）
//
// 相对原版的差异：
//   1) 盘面从"9 条数字细环"改为真正的连续吸积盘：
//      薄盘辐射 ∝ r^-3（内热外冷）、细密环状纹理（随开普勒剪切流动）、
//      掠射角光学厚度（边缘发亮、正面偏薄）、内缘光子环、外缘淡出。
//   2) 0/1 数字成为独立的一层：分布在盘面上/下两张平行"数字层"上，
//      每环多个数字、相位各自错开、角速度按开普勒 ω ∝ r^-1.5 差速。
//   3) 遮挡关系由光线步进的穿越顺序自然给出：
//      近侧数字层先着色 → 压住盘面；远侧数字层在盘面之后着色 → 被盘面衰减；
//      落入视界的光线直接 break → 视界后方的数字不可见。
//
// uniform 复用（Java 侧只需把 blackhole.fsh 换成 blackhole_disk_v2.fsh，一行）：
//   uDigitDensity    → 每环数字个数（2.8 → 16 个/环）
//   uDigitDensityR   → 数字径向行数（3.0 → 4 行）
//   uSpiralTurns     → 盘面纹理的螺旋剪切强度
//   uBillboardRadius → 数字层距盘面的垂直距离（内部 ×0.25）
//   uRibbonGain      → 字形揭示度：1=完整字形，0=亮点
//                      （16×16 物品图标建议 0.15~0.25，手持大尺寸用 1.0）
//   uRingStrength    → 内缘光子环强度
//   uGasStrength     → 盘外薄雾强度
//
// 物理内核沿用 0xydev/blackhole (MIT)：Binet 方程 + RK4 零测地线、g 因子。
// ============================================================================

uniform vec2  uResolution;
uniform float uTime;
uniform vec3  uCamPos;
uniform mat3  uCamMat;
uniform float uTanHalfFov;
uniform float uStepScale;

uniform float uDiskInner;
uniform float uDiskOuter;
uniform float uDiskBrightness;
uniform float uBeaming;
uniform float uOrbitDir;
uniform float uTimeScale;

uniform float uDigitDensity;     // 每环数字个数
uniform float uDigitDensityR;    // 数字径向行数
uniform float uSpiralTurns;      // 盘面纹理螺旋剪切
uniform float uBillboardRadius;  // 数字层高度
uniform float uRingStrength;     // 内缘光子环
uniform float uGasStrength;      // 盘外薄雾
uniform float uRibbonGain;       // 字形揭示度

uniform float uStarIntensity;
uniform float uStarDensity;

out vec4 fragColor;

const float PI  = 3.14159265358979;
const float TAU = 6.28318530717959;
const float M   = 0.5;
const float SQRT_M = 0.70710678118655;
const int   MAX_STEPS = 120;

// ============================================================================
// 调参块（副本内集中修改；物品侧可直接改这里的 #define 再编译）
// ============================================================================
#define DISK_TEMP_POW        2.6    // 亮度径向衰减指数（薄盘 T^4 ∝ r^-3）
#define DISK_STRIA_FREQ      13.0   // 细密环状纹理主频（越小条纹越粗）
#define DISK_STRIA_CONTRAST  0.45   // 条纹对比度 0~1
#define DISK_TURB_FREQ       7.0    // 低频湍流格密度
#define DISK_OPACITY         1.75   // 盘面不透明度（越大远侧越被遮住）
#define DISK_INNER_RAMP      0.28   // ISCO 内缘过渡宽度（r_s）
#define DISK_RIM_WIDTH       0.085  // 内缘光子环宽度（r_s）
#define DISK_RIM_GAIN        1.15   // 内缘光子环亮度
#define DISK_OUTER_SOFT      0.20   // 外缘淡出占盘宽比例

#define DIGIT_SHEETS         2      // 数字层数量（盘面上 1 张、下 1 张）
#define DIGIT_SIZE           0.44   // 数字世界尺寸（字高 ≈ size）
#define DIGIT_SIZE_SHRINK    0.28   // 外行相对内行缩小比例 0~1
#define DIGIT_SHEET_Y_MIN    0.12   // 数字层最小高度
#define DIGIT_STRETCH_X      0.78   // <1 时沿运动方向横向拉伸（0.78 ≈ 拉宽 1.28 倍）
#define DIGIT_BRIGHT         1.75   // 数字自发光增益（相对盘面）
#define DIGIT_GLOW           0.28   // 数字霓虹光晕
#define DIGIT_ALPHA          0.92   // 数字不透明度（决定对盘面的遮挡强度）

// 三色体系（与原版一致）
const vec3 CYAN       = vec3(0.0, 0.8314, 1.0);
const vec3 MAGENTA    = vec3(0.698, 0.420, 1.0);
const vec3 WARM_WHITE = vec3(1.0, 0.91, 0.78);

// ==================== 哈希 ====================

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// ==================== 二进制数字 SDF ====================

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 d = abs(p) - b + r;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
}

float digit0(vec2 p) {
    float outer = sdRoundBox(p, vec2(0.30, 0.43), 0.12);
    float inner = sdRoundBox(p, vec2(0.15, 0.29), 0.07);
    return max(outer, -inner);
}

float digit1(vec2 p) {
    float stem = sdRoundBox(p, vec2(0.075, 0.34), 0.045);
    float cap  = sdRoundBox(p - vec2(-0.09, 0.22), vec2(0.14, 0.07), 0.045);
    return min(stem, cap);
}

float binaryDigit(vec2 p, float sel) {
    return sel < 0.5 ? digit0(p) : digit1(p);
}

// ==================== 星空 ====================

vec2 octWrap(vec2 v) {
    return (1.0 - abs(v.yx)) * vec2(v.x >= 0.0 ? 1.0 : -1.0, v.y >= 0.0 ? 1.0 : -1.0);
}

vec2 dirToOct(vec3 d) {
    d /= abs(d.x) + abs(d.y) + abs(d.z);
    vec2 p = d.y >= 0.0 ? d.xz : octWrap(d.xz);
    return p * 0.5 + 0.5;
}

vec3 starLayer(vec2 uv, float cells) {
    vec2 grid = uv * cells;
    vec2 id = floor(grid);
    vec2 f  = fract(grid);
    if (hash12(id) > uStarDensity) return vec3(0.0);

    vec2 starPos = vec2(hash12(id + 4.7), hash12(id + 9.2)) * 0.6 + 0.2;
    float d = length(f - starPos);
    float magnitude = pow(hash12(id + 2.3), 4.0);

    float colorSeed = hash12(id + 6.1);
    vec3 starColor = colorSeed < 0.60 ? WARM_WHITE
                   : colorSeed < 0.80 ? CYAN
                                      : MAGENTA;

    float twinkle = 0.7 + 0.3 * sin(uTime * (0.8 + colorSeed * 2.0) + colorSeed * TAU);
    return starColor * magnitude * twinkle * exp(-d * d * 320.0);
}

vec3 sampleSky(vec3 dir) {
    vec2 uv = dirToOct(dir);
    vec3 color = starLayer(uv, 60.0) * 1.2 + starLayer(uv + 0.31, 130.0) * 0.5;
    return color * uStarIntensity;
}

// ==================== 连续吸积盘 ====================
// incidence = 光线与盘面法线的夹角余弦（|dir.y|），掠射时光学厚度更大

void shadeDisk(
    vec3 hitPoint,
    float r,
    float lz,
    float camGravFactor,
    float incidence,
    inout vec3 color,
    inout float transmittance
) {
    float theta = atan(hitPoint.z, hitPoint.x);
    float sqrtR = sqrt(r);
    float r32   = r * sqrtR;

    // 开普勒角速度（史瓦西）：ω ∝ r^-1.5，内快外慢
    float omega  = uOrbitDir * SQRT_M / r32;
    float utDenom = r32 - 3.0 * M * sqrtR;
    float ut = r32 / (pow(r, 0.75) * sqrt(max(utDenom, 1e-5)));

    // 相对论 g 因子：靠近侧（蓝移/集束）亮，远离侧暗
    float gRaw = clamp(camGravFactor / (ut * (1.0 - omega * lz)), 0.5, 2.2);
    float g = mix(1.0, gRaw, uBeaming);
    float g4 = g * g * g * g;

    float rn = clamp((r - uDiskInner) / (uDiskOuter - uDiskInner), 0.0, 1.0);

    // 亮度梯度：薄盘单位面积辐射 ∝ r^-3，内缘最亮
    float flux = pow(uDiskInner / r, DISK_TEMP_POW);
    float innerRamp = smoothstep(uDiskInner, uDiskInner + DISK_INNER_RAMP, r);
    float outerFade = 1.0 - smoothstep(1.0 - DISK_OUTER_SOFT, 1.0, rn);

    // 随开普勒剪切的角坐标：内圈转得快，纹理被拉成细丝
    float shear  = theta - omega * uTime * uTimeScale;
    float spiral = shear + uSpiralTurns * log(r / uDiskInner);

    // 细密环状纹理（两层正弦 + 低频湍流）
    float b1 = 0.5 + 0.5 * sin(r * DISK_STRIA_FREQ + spiral * 2.2);
    float b2 = 0.5 + 0.5 * sin(r * DISK_STRIA_FREQ * 3.4 + spiral * 3.6 + 1.7);
    float turb = hash12(vec2(floor(r * DISK_TURB_FREQ), floor(spiral * 3.0)));
    float tex = 0.60 + DISK_STRIA_CONTRAST * (0.72 * b1 + 0.28 * b2) + 0.12 * (turb - 0.5);

    // 霓虹配色（与原版三色一致）：内热暖白 → 中品红 → 外青
    vec3 col = mix(WARM_WHITE, MAGENTA, smoothstep(0.00, 0.42, rn));
    col = mix(col, CYAN, smoothstep(0.42, 1.00, rn));

    // 内缘光子环（视界边缘一圈亮线）
    float rim = exp(-pow((r - uDiskInner) / DISK_RIM_WIDTH, 2.0)) * uRingStrength;

    // 盘外薄雾（很淡，外缘不至于硬切）
    float gas = exp(-rn * 2.2) * uGasStrength;

    // 光学厚度：掠射角更厚 → 盘边缘发光、正面看更薄
    float cosI = max(abs(incidence), 0.05);
    float tau  = DISK_OPACITY * tex * (1.0 / cosI) * (0.55 + 0.45 * flux);
    float alpha = (1.0 - exp(-tau)) * innerRamp * outerFade;

    vec3 emit = col * flux * tex * g4 * uDiskBrightness * 1.15
              + mix(WARM_WHITE, CYAN, 0.30) * rim * g4 * DISK_RIM_GAIN
              + mix(col, WARM_WHITE, 0.25) * gas * g4;

    color += transmittance * emit * innerRamp * outerFade;
    transmittance *= 1.0 - alpha;
}

// ==================== 环绕数字层 ====================
// 数字位于 y = ±sheetY 的两张平行层上；每环 N 个，相位独立，角速度按开普勒差速。
// 字形用精确 billboard：把光线与该数字的"面向相机平面"求交后采样 SDF。

void shadeDigitSheet(
    vec3 hitPoint,
    vec3 rayPrev,
    vec3 rayCur,
    float sheetY,
    float r,
    float lz,
    float camGravFactor,
    inout vec3 color,
    inout float transmittance
) {
    // ---- 径向行（每行的半径、字号）----
    float rows  = clamp(floor(uDigitDensityR * 1.35), 2.0, 6.0);
    float span  = max(uDiskOuter * 0.96 - uDiskInner, 1e-4);
    float t     = clamp((r - uDiskInner) / span, 0.0, 0.9999);
    float rowF  = floor(t * rows);
    float rowR  = uDiskInner + span * ((rowF + 0.5) / rows);

    float shrink = 1.0 - DIGIT_SIZE_SHRINK * (rowF / max(rows - 1.0, 1.0));
    float size   = DIGIT_SIZE * shrink;

    // 只在该行的径向带内评估字形
    float rowGate = 1.0 - smoothstep(size * 0.90, size * 1.40, abs(r - rowR));
    if (rowGate <= 0.001) return;

    // ---- 该行的开普勒角速度与 g 因子 ----
    float sqrtR = sqrt(rowR);
    float r32   = rowR * sqrtR;
    float omega = uOrbitDir * SQRT_M / r32;
    float utDenom = r32 - 3.0 * M * sqrtR;
    float ut = r32 / (pow(rowR, 0.75) * sqrt(max(utDenom, 1e-5)));
    float gRaw = clamp(camGravFactor / (ut * (1.0 - omega * lz)), 0.5, 2.0);
    float g = mix(1.0, gRaw, uBeaming);
    float g4 = g * g * g * g;

    // ---- 该行的数字排布：均分 + 每格独立相位抖动 ----
    float theta = atan(hitPoint.z, hitPoint.x);
    float nF    = clamp(floor(uDigitDensity * 6.0), 6.0, 28.0);   // 每环数字数
    float cell  = TAU / nF;
    float a     = theta - omega * uTime * uTimeScale;

    float idx      = floor(a / cell);
    float jit      = (hash12(vec2(idx, rowF * 3.7 + sheetY * 11.0)) - 0.5) * 0.45;
    float centerA  = (idx + 0.5 + jit) * cell;
    vec3  center   = vec3(cos(centerA) * rowR, sheetY, sin(centerA) * rowR);

    // ---- 精确 billboard：光线与该数字的面向相机平面求交 ----
    vec3 dir   = rayCur - rayPrev;
    vec3 toCam = normalize(uCamPos - center);
    float denom = dot(dir, toCam);
    if (abs(denom) < 1e-5) return;
    float tp = dot(center - rayPrev, toCam) / denom;
    vec3  bp = rayPrev + tp * dir;

    vec3 bRight = normalize(cross(toCam, vec3(0.0, 1.0, 0.0)));
    vec3 bUp    = cross(bRight, toCam);

    // 字形局部坐标（±1 = 数字外框）
    vec2 q = vec2(dot(bp - center, bRight), dot(bp - center, bUp)) / (size * 0.5);
    q.x /= DIGIT_STRETCH_X;

    // ---- 字形 ----
    float sel  = step(0.5, hash12(vec2(idx * 1.37, rowF * 9.1 + sheetY * 3.0)));
    float dd   = binaryDigit(q * 0.9, sel);
    float aa   = max(fwidth(dd), 0.05);
    float glyph = 1.0 - smoothstep(-aa, aa, dd);

    // 极小尺寸退化：uRibbonGain 低时字形 → 发光点（物品 16×16 图标可读）
    float reveal = clamp(uRibbonGain, 0.0, 1.0);
    float dotMode = exp(-dot(q, q) * 3.0);
    float shape = mix(dotMode, glyph, reveal);

    float glow = exp(-max(dd, 0.0) * 6.0) * DIGIT_GLOW * reveal;

    // 颜色：青/品红为主，少量暖白
    float cs = hash12(vec2(idx * 3.1, rowF * 17.3 + sheetY * 7.0));
    vec3 dcol = cs < 0.45 ? CYAN : (cs < 0.90 ? MAGENTA : WARM_WHITE);

    float flicker = 0.82 + 0.18 * hash12(vec2(idx * 5.3, rowF + sheetY));

    vec3 emit = dcol * (shape * DIGIT_BRIGHT * flicker + glow) * g4 * uDiskBrightness;
    float alpha = min(shape * DIGIT_ALPHA + glow * 0.20, 1.0) * rowGate;

    color += transmittance * emit * rowGate;
    transmittance *= 1.0 - alpha;
}

// ==================== 输出 ====================
// 只输出线性 HDR；tonemap / 暗角 / 压暗仍在 composite pass 完成。
void finalize(vec3 color) {
    fragColor = vec4(max(color, 0.0), 1.0);
}

// ==================== 主程序 ====================

void main() {
    if (uTime < -999.0) {
        fragColor = vec4(0.0, 1.0, 0.0, 1.0);
        return;
    }

    vec2 ndc = (2.0 * gl_FragCoord.xy - uResolution) / uResolution.y;
    vec3 rayDir = normalize(uCamMat * vec3(ndc * uTanHalfFov, 1.0));
    vec3 rayOrigin = uCamPos;
    float r0 = length(rayOrigin);

    vec3 color = vec3(0.0);
    float transmittance = 1.0;
    bool escaped = false;
    vec3 escapeDir = vec3(0.0);

    float camGravFactor = inversesqrt(1.0 - 1.0 / r0);
    float skyBoost = camGravFactor * camGravFactor * camGravFactor * camGravFactor;

    vec3 radialDir0 = rayOrigin / r0;
    float radialDot = dot(rayDir, radialDir0);
    vec3 tangential = rayDir - radialDot * radialDir0;
    float sinPsi = length(tangential);

    if (sinPsi < 1e-5) {
        if (radialDot >= 0.0) color = sampleSky(rayDir) * skyBoost;
        finalize(color);
        return;
    }

    vec3 planeE2 = tangential / sinPsi;
    float orbitNormalY = cross(radialDir0, planeE2).y;

    float u = 1.0 / r0;
    float impactParameter = r0 * sinPsi * inversesqrt(1.0 - u);
    float dudphi = -sign(radialDot)
        * sqrt(max(0.0, 1.0 / (impactParameter * impactParameter) - u * u * (1.0 - u)));
    float lz = impactParameter * orbitNormalY;

    float escapeRadius = max(30.0, r0 * 1.6);
    float escapeU = 1.0 / escapeRadius;
    float phi = 0.0;
    vec3 prevPoint = rayOrigin;

    float sheetY = max(uBillboardRadius * 0.25, DIGIT_SHEET_Y_MIN);

    for (int i = 0; i < MAX_STEPS; i++) {
        float prevU  = u;
        float prevDu = dudphi;
        float prevPhi = phi;

        float dphi = uStepScale * 0.22 / (1.0 + 4.0 * u);

        float k1u = dudphi;
        float k1v = 1.5 * u * u - u;
        float u2 = u + 0.5 * dphi * k1u;
        float v2 = dudphi + 0.5 * dphi * k1v;
        float k2v = 1.5 * u2 * u2 - u2;
        float u3 = u + 0.5 * dphi * v2;
        float v3 = dudphi + 0.5 * dphi * k2v;
        float k3v = 1.5 * u3 * u3 - u3;
        float u4 = u + dphi * v3;
        float v4 = dudphi + dphi * k3v;
        float k4v = 1.5 * u4 * u4 - u4;

        u += dphi / 6.0 * (k1u + 2.0 * v2 + 2.0 * v3 + v4);
        dudphi += dphi / 6.0 * (k1v + 2.0 * k2v + 2.0 * k3v + k4v);
        phi += dphi;

        if (u > 1.0 || phi > 6.0 * PI) break;

        if (u < escapeU && dudphi < 0.0) {
            float t = clamp((prevU - escapeU) / max(prevU - u, 1e-9), 0.0, 1.0);
            float ue = max(mix(prevU, u, t), 0.0);
            float ve = mix(prevDu, dudphi, t);
            float pe = mix(prevPhi, phi, t);
            vec3 escRadial  = cos(pe) * radialDir0 + sin(pe) * planeE2;
            vec3 escTangent = -sin(pe) * radialDir0 + cos(pe) * planeE2;
            escapeDir = normalize(ue * escTangent - ve * escRadial);
            escaped = true;
            break;
        }

        vec3 orbitRadial = cos(phi) * radialDir0 + sin(phi) * planeE2;
        vec3 point = orbitRadial / u;

        // ---- 吸积盘（y = 0 平面）----
        if (prevPoint.y * point.y < 0.0) {
            float t = prevPoint.y / (prevPoint.y - point.y);
            vec3 hitPoint = mix(prevPoint, point, t);
            float hitRadius = length(hitPoint);
            if (hitRadius > uDiskInner && hitRadius < uDiskOuter) {
                vec3 seg = point - prevPoint;
                float incidence = abs(normalize(seg).y);
                shadeDisk(hitPoint, hitRadius, lz, camGravFactor, incidence, color, transmittance);
                if (transmittance < 0.02) break;
            }
        }

        // ---- 数字层（y = ±sheetY）----
        // 光线按穿越顺序着色，因此近侧数字压住盘面、远侧数字被盘面衰减
        for (int s = 0; s < DIGIT_SHEETS; s++) {
            float sy = (s == 0) ? sheetY : -sheetY;
            float y0 = prevPoint.y - sy;
            float y1 = point.y - sy;
            if (y0 * y1 < 0.0) {
                float t = y0 / (y0 - y1);
                vec3 hitPoint = mix(prevPoint, point, t);
                float hitRadius = length(hitPoint);
                if (hitRadius > uDiskInner && hitRadius < uDiskOuter) {
                    shadeDigitSheet(hitPoint, prevPoint, point, sy, hitRadius,
                                    lz, camGravFactor, color, transmittance);
                    if (transmittance < 0.02) break;
                }
            }
        }

        prevPoint = point;
    }

    if (escaped) {
        color += transmittance * sampleSky(escapeDir) * skyBoost;
    }

    finalize(color);
}
