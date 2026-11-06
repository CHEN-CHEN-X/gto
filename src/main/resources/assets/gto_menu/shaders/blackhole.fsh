#version 150 core
// ============================================================================
// GTO 主菜单黑洞背景 —— 史瓦西黑洞反向光线追踪
//
// 移植自 0xydev/blackhole (MIT License, https://github.com/0xydev/blackhole)
// 物理：Binet 方程 d2u/dφ² = 3Mu² - u，四阶 Runge-Kutta 积分零测地线，
//       精确冲击参数、引力透镜、光子环、吸积盘前后穿越、多普勒 g 因子。
// 定制（参考《霓虹二进制黑洞漩涡》效果图）：
//   内圈 = 始终面向镜头的 0/1 数字广告牌照亮视界边缘 + 连续霓虹环；
//   外侧 = 多条对数螺旋数字缎带 + 气体托底辉光；
//   稀疏三色星辰。
// 仅使用 Schwarzschild(KERR=0) 路径，单 pass、零纹理。
// ============================================================================

uniform vec2  uResolution;      // 渲染目标像素尺寸
uniform float uTime;            // 游戏时间（秒）
uniform vec3  uCamPos;          // 相机位置（r_s = 1 几何单位）
uniform mat3  uCamMat;          // 相机基矩阵（列：右、上、前）
uniform float uTanHalfFov;      // tan(FOV/2)
uniform float uStepScale;       // 积分步长缩放（越小越精细）

uniform float uDiskInner;       // 盘内缘（ISCO=3）
uniform float uDiskOuter;       // 盘外缘
uniform float uDiskBrightness;  // 数字自发光亮度
uniform float uBeaming;         // 多普勒集束强度 0~1
uniform float uOrbitDir;        // 公转方向 ±1
uniform float uTimeScale;       // 盘纹理旋转速度缩放

uniform float uDigitDensity;    // 数字角向密度（× 弧长 r）
uniform float uDigitDensityR;   // 数字径向密度
uniform float uSpiralTurns;     // 对数螺旋缠绕强度

uniform float uRingStrength;    // 内侧连续霓虹环强度（0=关闭，参考图内缘亮带）
uniform float uGasStrength;     // 外侧螺旋气体托底辉光强度（0=关闭）
uniform float uRibbonGain;      // 外侧数字所在螺旋臂的增亮系数

uniform float uStarIntensity;   // 星辰整体亮度
uniform float uStarDensity;     // 每格出现星辰的概率（很小）

out vec4 fragColor;

const float PI  = 3.14159265358979;
const float TAU = 6.28318530717959;
const float M   = 0.5;                // 引力质量（r_s = 2M = 1）
const float SQRT_M = 0.70710678118655;
const int   MAX_STEPS = 240;          // 与 low 画质档一致
// Lane count derives from uDigitDensityR; no fixed nine-ring limit.

// 三色体系
const vec3 CYAN       = vec3(0.0, 0.8314, 1.0);
const vec3 MAGENTA    = vec3(0.80, 0.015, 1.0);
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
    float stem = sdRoundBox(p, vec2(0.065, 0.38), 0.025);
    float cap  = sdRoundBox(p - vec2(-0.09, 0.22), vec2(0.14, 0.07), 0.045);
    float foot = sdRoundBox(p - vec2(0.0, -0.36), vec2(0.22, 0.055), 0.02);
    return min(min(stem, cap), foot);
}

float binaryDigit(vec2 p, float sel) {
    return sel < 0.5 ? digit0(p) : digit1(p);
}

// ==================== 星空（八面体映射 + 稀疏星点） ====================

vec2 octWrap(vec2 v) {
    return (1.0 - abs(v.yx)) * vec2(v.x >= 0.0 ? 1.0 : -1.0, v.y >= 0.0 ? 1.0 : -1.0);
}

vec2 dirToOct(vec3 d) {
    d /= abs(d.x) + abs(d.y) + abs(d.z);
    vec2 p = d.y >= 0.0 ? d.xz : octWrap(d.xz);
    return p * 0.5 + 0.5;
}

// 一层星点：三色（青/品红/暖白），稀疏、微小、低亮度
vec3 starLayer(vec2 uv, float cells) {
    vec2 grid = uv * cells;
    vec2 id = floor(grid);
    vec2 f  = fract(grid);
    if (hash12(id) > uStarDensity) return vec3(0.0);

    vec2 starPos = vec2(hash12(id + 4.7), hash12(id + 9.2)) * 0.6 + 0.2;
    float d = length(f - starPos);
    float magnitude = pow(hash12(id + 2.3), 4.0);

    // 颜色选择：60% 暖白 *，20% 青，20% 品红
    float colorSeed = hash12(id + 6.1);
    vec3 starColor = colorSeed < 0.60 ? WARM_WHITE
                   : colorSeed < 0.80 ? CYAN
                                      : MAGENTA;

    float twinkle = 0.7 + 0.3 * sin(uTime * (0.8 + colorSeed * 2.0) + colorSeed * TAU);
    return starColor * magnitude * twinkle * exp(-d * d * 320.0);
}

vec3 sampleSky(vec3 dir) {
    vec2 uv = dirToOct(dir);
    // 纯黑宇宙：只有星点，无星云、无银河带
    vec3 color = starLayer(uv, 60.0) * 1.2 + starLayer(uv + 0.31, 130.0) * 0.5;
    return color * uStarIntensity;
}

// ==================== 吸积盘着色 ====================

void shadeDisk(
    vec3 hitPoint,
    float r,
    float lz,
    float camGravFactor,
    float imageGain,
    inout vec3 color,
    inout float transmittance
) {
    float theta  = atan(hitPoint.z, hitPoint.x);
    float sqrtR  = sqrt(r);
    float r32    = r * sqrtR;

    // 开普勒角速度（史瓦西），内圈快、外圈慢——天然差速旋转
    float omega  = uOrbitDir * SQRT_M / r32;
    float utDenom = r32 - 3.0 * M * sqrtR;
    float ut = (r32) / (pow(r, 0.75) * sqrt(max(utDenom, 1e-5)));

    float edgeFade = smoothstep(uDiskInner, uDiskInner + 0.10, r)
                   * (1.0 - smoothstep(uDiskOuter * 0.85, uDiskOuter, r));

    // 相对论性 g 因子（引力红移 + 多普勒），靠近侧亮、远离侧暗
    // g 上限收紧到 1.7（g⁴≈8.4）避免 ISCO 过曝；下限 0.5 让远离侧品红数字仍可辨
    float gRaw = clamp(camGravFactor / (ut * (1.0 - omega * lz)), 0.5, 1.7);
    float g = mix(1.0, gRaw, uBeaming);
    float g4 = g * g * g * g;

    // Dense orbit lanes over a continuous, dark filament disk. Keep the
    // existing geodesic intersections and relativistic beaming unchanged.
    float span = max(uDiskOuter - uDiskInner, 0.01);
    float rows = max(4.0, floor(span * uDigitDensityR));
    float lane = clamp(floor((r - uDiskInner) / span * rows), 0.0, rows - 1.0);
    float rr = uDiskInner + (lane + 0.5) * span / rows;
    // Break uniform spacing while retaining stable orbit identities.
    rr += (hash12(vec2(lane, 117.0)) - 0.5) * span / rows * 0.28;
    float dr = r - rr;
    float ringT = lane / max(rows - 1.0, 1.0);
    float omegaR = uOrbitDir * SQRT_M / pow(rr, 1.5);
    float cells = max(16.0, floor(TAU * rr * uDigitDensity));
    float phase = (theta - omegaR * uTime * uTimeScale) / TAU * cells;
    phase += hash12(vec2(lane, 7.0));
    float idx = floor(phase);
    // Wrap the identity as well as position: no seam at atan's +/- PI.
    float id = mod(idx, cells);
    float arc = (fract(phase) - 0.5) * TAU * rr / cells;
    float sel = step(0.5, hash12(vec2(id, lane * 9.17)));
    float facing = dot(normalize(hitPoint.xz), normalize(uCamPos.xz));
    float size = min(0.38, 0.80 / uDigitDensity) * mix(0.52, 1.25, hash12(vec2(lane, 123.0))) * mix(0.55, 1.45, smoothstep(-0.7, 0.7, facing));
    float dd = binaryDigit(vec2(arc, dr) / size, sel);
    // Analytic footprint is valid even inside divergent ray marching paths.
    float aa = clamp(length(uCamPos - hitPoint) * 2.0 * uTanHalfFov
                     / uResolution.y / size, 0.025, 0.35);
    float glyph = 1.0 - smoothstep(-aa, aa, dd);
    float glow = exp(-max(dd, 0.0) * 12.0) * 0.12;
    float pulse = pow(0.5 + 0.5 * sin(theta * 4.0 - uTime * omegaR
                      * uTimeScale * 4.0 + lane * 1.7), 8.0);
    vec3 digitColor = mix(CYAN, MAGENTA, step(0.0, sin(theta * 2.0 + lane * 2.7) + 0.15 * (hash12(vec2(id, lane))-0.5)));
    float line = exp(-pow(dr / 0.012, 2.0));
    vec3 laneColor = mix(CYAN, MAGENTA, 0.5 + 0.5 * sin(lane * 2.3));
    // Multiple narrow, shearing filaments, never a uniform fog/nebula.
    float shear = theta - omega * uTime * uTimeScale;
    float filaments = pow(0.5 + 0.5 * sin(r * 89.0 + sin(shear * 7.0)
                          * 0.7 + uSpiralTurns * shear), 18.0);
    float knots = pow(0.5 + 0.5 * sin(shear * 17.0 + r * 7.0), 5.0);
    float inner = exp(-(r - uDiskInner) * 0.8);
    float rim = exp(-pow((r - uDiskInner - 0.055) / 0.023, 2.0));
    vec3 emit = digitColor * (glyph + glow) * (0.12 + 1.4 * pow(hash12(vec2(lane, 43.0)), 2.0) + 0.4 * pulse)
                * uDiskBrightness
              + laneColor * line * (0.025 + pulse * uRibbonGain * 0.15) * uRingStrength
              + mix(CYAN, MAGENTA, 0.5 + 0.5 * sin(shear * 3.0 + r))
                * filaments * (0.08 + 0.30 * knots) * (0.4 + inner)
              + mix(CYAN, MAGENTA, 0.5 + 0.5 * sin(theta * 2.0))
                * (rim * uRingStrength * 2.0 + inner * uGasStrength);
    // Analytic narrow filament bundle: nearest strands rather than a
    // texture atlas or a frame cache. Each strand has a stable independent
    // phase, angular velocity, gaps and small radial excursions.
    vec3 threads = vec3(0.0);
    float threadDensity = 28.0;
    float baseThread = floor((r-uDiskInner) * threadDensity);
    for (int j=-1; j<=1; ++j) {
        float strand = baseThread + float(j);
        float seed = hash12(vec2(strand, 182.3));
        float strandR = uDiskInner + (strand+0.5)/threadDensity;
        float flow = theta - uTime*uTimeScale*uOrbitDir*SQRT_M/pow(max(strandR,1.9),1.5);
        float wobble = (sin(flow*3.0+seed*TAU) + 0.4*sin(flow*11.0+seed*53.0))*0.012;
        float d = r-strandR-wobble;
        float pixelWidth = length(uCamPos)*2.0*uTanHalfFov/uResolution.y;
        float width = max(0.0025 + seed*0.003, pixelWidth*0.55);
        float light = exp(-d*d/(width*width));
        float fragments = pow(0.5+0.5*sin(flow*(17.0+floor(seed*70.0))+seed*63.0), 9.0);
        float stream = 0.08+fragments*(0.3+seed);
        vec3 tint = mix(vec3(0.015,0.05,0.8),mix(CYAN,MAGENTA,step(0.5,seed)),0.65);
        threads += tint * light * stream * 0.30;
    }
    emit += threads * (0.7 + 2.0*exp(-(r-uDiskInner)*0.5));
    float alpha = edgeFade * clamp(glyph * 0.8 + line * 0.12
                                  + filaments * 0.07 + rim * 0.3, 0.0, 0.95);
    // Keep the far-side lensed image fine and dark so it reads as a ring,
    // rather than a solid luminous dome above the shadow.
    float farSide = smoothstep(-0.15, 0.15, dot(normalize(hitPoint.xz), normalize(uCamPos.xz)));
    float farGain = 0.42;
    color += transmittance * edgeFade * emit * g4 * mix(farGain, 1.0, farSide) * imageGain;
    transmittance *= 1.0 - alpha;
}

// ==================== 输出 ====================
// Scene pass 只输出线性 HDR 光色；色调映射 / 暗角 / 压暗全部在 composite pass 完成。
void finalize(vec3 color) {
    fragColor = vec4(max(color, 0.0), 1.0);
}

// ==================== 主程序：史瓦西测地线追踪 ====================

void main() {
    // 调试模式：uTime < -999 时输出纯色验证渲染管线
    if (uTime < -999.0) {
        fragColor = vec4(0.0, 1.0, 0.0, 1.0);  // 绿色
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

    // 相机静止观者的引力蓝移因子
    float camGravFactor = inversesqrt(1.0 - 1.0 / r0);
    float skyBoost = camGravFactor * camGravFactor * camGravFactor * camGravFactor;

    // 轨道平面分解（Binet 方程在光线所在平面内积分）
    vec3 radialDir0 = rayOrigin / r0;
    float radialDot = dot(rayDir, radialDir0);
    vec3 tangential = rayDir - radialDot * radialDir0;
    float sinPsi = length(tangential);

    // 沿径向直射的光线
    if (sinPsi < 1e-5) {
        if (radialDot >= 0.0) {
            color = sampleSky(rayDir) * skyBoost;
        }
        finalize(color);
        return;
    }

    vec3 planeE2 = tangential / sinPsi;
    float orbitNormalY = cross(radialDir0, planeE2).y;

    // 精确初始条件：冲击参数 b 与 du/dφ
    float u = 1.0 / r0;
    float impactParameter = r0 * sinPsi * inversesqrt(1.0 - u);
    float dudphi = -sign(radialDot)
        * sqrt(max(0.0, 1.0 / (impactParameter * impactParameter) - u * u * (1.0 - u)));
    float lz = impactParameter * orbitNormalY;

    float escapeRadius = max(30.0, r0 * 1.6);
    float escapeU = 1.0 / escapeRadius;
    // Reference-matched hybrid: the wide primary disk uses its thin-plane
    // image, while the retained RK4 solver supplies the strong-field image.
    // This intentionally avoids turning the entire outer disk into a dome.
    vec3 primaryColor = vec3(0.0);
    float primaryTrans = 1.0;
    bool primaryFront = false;
    if (abs(rayDir.y) > 1e-5) {
        vec3 primaryOrigin = rayOrigin + uCamMat[0] * ndc.x * r0 * uTanHalfFov + uCamMat[1] * ndc.y * r0 * uTanHalfFov;
        vec3 primaryDir = uCamMat[2];
        float primaryT = -primaryOrigin.y / primaryDir.y;
        vec3 primaryHit = primaryOrigin + primaryT * primaryDir;
        float primaryR = length(primaryHit);
        primaryFront = dot(primaryHit.xz, rayOrigin.xz) > 0.0;
        if (primaryT > 0.0 && primaryR > uDiskInner && primaryR < uDiskOuter
            && (primaryFront || impactParameter > 2.98)) {
            shadeDisk(primaryHit, primaryR, lz, camGravFactor, 1.0,
                      primaryColor, primaryTrans);
        }
    }
    float phi = 0.0;
    vec3 prevPoint = rayOrigin;

    for (int i = 0; i < MAX_STEPS; i++) {
        float prevU  = u;
        float prevDu = dudphi;
        float prevPhi = phi;

        // 自适应步长：靠近视界时更密
        float dphi = uStepScale * 0.22 / (1.0 + 4.0 * u);

        // 四阶 Runge-Kutta：u'' = 3Mu² - u
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

        // 落入事件视界（纯黑）
        if (u > 1.0 || phi > 6.0 * PI) break;

        // 逃逸到远方 → 采样被透镜偏折后的星空
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

        // 当前光线点（世界坐标，盘面为 xz 平面 y=0）
        vec3 orbitRadial = cos(phi) * radialDir0 + sin(phi) * planeE2;
        vec3 point = orbitRadial / u;

        // 穿越吸积盘平面（同一束光可能穿越多次：近侧 + 透镜绕回的远侧）
        if (prevPoint.y * point.y < 0.0) {
            float t = prevPoint.y / (prevPoint.y - point.y);
            vec3 hitPoint = mix(prevPoint, point, t);
            float hitRadius = length(hitPoint);
            if (hitRadius > uDiskInner && hitRadius < 3.4 && impactParameter < 3.15) {
                shadeDisk(hitPoint, hitRadius, lz, camGravFactor, 0.65, color, transmittance);
                if (transmittance < 0.02) break;
            }
        }
        prevPoint = point;
    }

    if (escaped) {
        color += transmittance * sampleSky(escapeDir) * skyBoost;
    }

    if (primaryFront) color = primaryColor + color * primaryTrans;
    else color += primaryColor * transmittance;

    // Thin critical photon ring; finite-distance impact parameter already
    // comes from the retained Schwarzschild solver, not a screen ellipse.
    float critical = 1.5 * sqrt(3.0);
    float ring = exp(-pow((impactParameter - critical) / 0.018, 2.0));
    vec3 photonColor = mix(CYAN, MAGENTA, 0.5 + 0.5 * sin(atan(ndc.y, ndc.x) * 2.0));
    color += photonColor * ring * uRingStrength * 1.5;
    // Artistic data corona: reference digits face the camera around the
    // critical shadow. Its radius still follows finite-distance lensing.
    float angle = atan(ndc.y, ndc.x);
    float coronaCells = 64.0;
    float coronaPhase = (angle - uTime * 0.04 * uOrbitDir) / TAU * coronaCells;
    float coronaId = mod(floor(coronaPhase), coronaCells);
    float coronaR = critical + 0.20;
    vec2 coronaQ = vec2((fract(coronaPhase)-0.5) * TAU * coronaR / coronaCells,
                        impactParameter-coronaR) / 0.26;
    float coronaD = binaryDigit(coronaQ, step(0.5, hash12(vec2(coronaId, 91.0))));
    float coronaAA = clamp(r0 * 2.0 * uTanHalfFov / uResolution.y / 0.26, 0.025, 0.08);
    float coronaGlyph = 1.0 - smoothstep(-coronaAA, coronaAA, coronaD);
    color += mix(CYAN, MAGENTA, step(0.5, hash12(vec2(coronaId, 39.0))))
             * coronaGlyph * 2.0 * uRingStrength;
    float coronaThreads = exp(-pow((impactParameter-critical-0.07)/0.014, 2.0))
                        + exp(-pow((impactParameter-critical-0.36)/0.019, 2.0));
    color += photonColor * coronaThreads * uRingStrength * 0.7;
    // Long, thin caustic arcs at the two intersections, not white blobs.
    float crossing = exp(-pow(sin(angle - 0.26) / 0.23, 2.0))
                   * exp(-pow((impactParameter - coronaR) / 0.024, 2.0));
    color += vec3(0.65, 0.85, 1.0) * crossing * 7.0 * uRingStrength;
    for (int j=0; j<14; ++j) {
        float strand = float(j);
        float seed = hash12(vec2(strand, 209.0));
        float radius = critical + 0.04 + strand*0.035;
        radius += 0.01*sin(angle*7.0-uTime*0.18+seed*TAU);
        float line = exp(-pow((impactParameter-radius)/max(0.006, r0*uTanHalfFov/uResolution.y),2.0));
        float fragments = pow(0.5+0.5*sin(angle*(21.0+strand)-uTime*0.6+seed*63.0),6.0);
        color += mix(CYAN,MAGENTA,seed)*line*(0.08+0.5*fragments)*uRingStrength;
    }
    finalize(color);
}
