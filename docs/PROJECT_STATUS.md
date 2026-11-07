> 历史记录（程序化方案时期）：本文记录旧版 Mixin + FancyMenu 方案与三版着色器的对比，其中 Mixin 入口、FancyMenu 依赖和 `blackhole.fsh` 的线上状态均已改变。当前依赖、入口、参数与运行方式见 [README](../README.md)；版本对齐与 JDK 验证见 [PACK_VERSION_ALIGNMENT.md](PACK_VERSION_ALIGNMENT.md)。

# GTO 主菜单黑洞背景 —— 项目现状

> 最后更新：2026-11-06
> 范围：本仓库（`gto` 项目根目录）的 MC 主菜单黑洞背景；以及该效果复用到 NeoForge 1.21.1 物品 mod 的可行性研究。

---

## 1. 一句话现状

主菜单黑洞（1.20.1 Forge）已经跑通并落地；效果迭代有两个未接线的进化版本（v2 / v3）；物品侧（skyland-build 的「进入维度的道具」）只到可行性 + 尺寸样张阶段，**没有任何代码进入目标工程**。

---

## 2. 工程身份

| 项 | 值 | 来源 |
|---|---|---|
| mod id / name | `gto_menu` / GregTech Odyssey Menu | [gradle.properties](../gradle.properties) |
| 版本 | 1.0.0 | 同上 |
| MC / Forge | 1.20.1 / 47.4.20 | 同上 |
| 映射 | `official` 1.20.1 | 同上 L42-L45 |
| 复用目标 | NeoForge 21.1.77 / MC 1.21.1 | 跨工程研究，非本仓库依赖 |

---

## 3. 代码结构

以下为旧版结构；`mixin/TitleScreenMixin.java` 已移除，当前入口为 `client/MenuRenderEvents.java`，默认渲染器为 `render/ReferenceOrbitRenderer.java`。

```
src/main/java/com/gto/menu/
├── GTOMenuMod.java                    模组入口
├── mixin/TitleScreenMixin.java        注入主菜单 render；@Redirect 关掉原版 panorama
└── render/
    ├── BlackHoleRenderer.java         4-pass 渲染管线 + FBO 管理
    ├── RenderConfig.java              所有可调常量
    └── VoltageBlockRenderer.java      方块渲染（已停用）

src/main/resources/assets/gto_menu/shaders/
├── fsquad.vsh                         全屏三角形顶点着色器
├── blackhole.fsh                      ★ 线上版本（v1）
├── blackhole_disk_v2.fsh              v2：连续吸积盘 + 双层数字（未接线）
├── blackhole_volume_v3.fsh            v3：v2 + 场景内发光晕 + 差速细丝（未接线）
├── trail.fsh                          时序反馈（max 帧累积）
├── bloom.fsh                          9-tap 可分离高斯
└── composite.fsh                      ACES + sRGB + 暗角 + 底部压暗 + 抖动
```

### 3.1 渲染管线（[BlackHoleRenderer.java](../src/main/java/com/gto/menu/render/BlackHoleRenderer.java)）

`scene → trail → bloom(H/V/H/V) → composite`，全部 `RGBA16F`；`RenderConfig.RENDER_SCALE` 现为 1.0（原生分辨率），旧版曾用 0.75 降分辨率。

- 程序化回退路径的着色器加载点：[BlackHoleRenderer.java#L311](../src/main/java/com/gto/menu/render/BlackHoleRenderer.java#L311)，scene 绑定 `blackhole.fsh`。当前默认路径已改为 [ReferenceOrbitRenderer.java](../src/main/java/com/gto/menu/render/ReferenceOrbitRenderer.java)。
- 切换版本 = 改这一行字符串，Java 侧无需其它改动。
- 输出绑回 MC 主 FBO：[BlackHoleRenderer.java](../src/main/java/com/gto/menu/render/BlackHoleRenderer.java)。
- 时间源 `System.nanoTime()`，不依赖 `System.currentTimeMillis`。

---

## 4. 三版 shader 的状态

| 版本 | 文件 | 状态 | 内容 | 已知问题 |
|---|---|---|---|---|
| v1 | [blackhole.fsh](../src/main/resources/assets/gto_menu/shaders/blackhole.fsh) | **旧线上（现为程序化回退）** | 9 条数字细环（`RINGS = 9`，L48），环上均匀排 0/1，无连续盘面 | 视觉退化成"9 条数字带"，用户明确不满意 |
| v2 | [blackhole_disk_v2.fsh](../src/main/resources/assets/gto_menu/shaders/blackhole_disk_v2.fsh) | **离线验证通过，未接线** | 连续吸积盘（r^-2.6 亮度、开普勒剪切条纹、掠射光学厚度、内缘光子环）+ 双层数字（±0.28，4 行/环 16 个，独立相位，精确 billboard） | 盘面仍偏"干净正弦条纹"，缺气体感 |
| v3 | [blackhole_volume_v3.fsh](../src/main/resources/assets/gto_menu/shaders/blackhole_volume_v3.fsh) | **离线验证通过，未接线** | v2 + 每步 `ds` 积分的场景内发光晕（`inSceneGlow`）+ 三倍频反向旋转值噪声（`filament`） | 16×16 尺寸下这两项增益几乎不可见；**未进游戏验证（实验件）** |

数字层关键事实（纠正一个常见误判）：**每条环不是只有一个数字**，`cellW = 1/uDigitDensity`、`nD = max(floor(TAU*rr/cellW), 8.0)`([blackhole.fsh#L172-L173](../src/main/resources/assets/gto_menu/shaders/blackhole.fsh#L172-L173))，在默认 `uDigitDensity = 2.8` 下约为 **58~158 个/环**；问题在于 v1 没有连续盘面介质，视觉上塌成"数字带"。

---

## 5. 已核实的环境约束（可直接作为设计前提）

| 约束 | 事实 | 影响 |
|---|---|---|
| GL 上限 | MC 1.21.1 上下文为 GL 3.2 core → GLSL **150** | Shadertoy 的 `#version 300 es`、`layout(location=)`、MRT 全部不可用 |
| 物品渲染入口 | `IClientItemExtensions#getCustomRenderer()` → `BlockEntityWithoutLevelRenderer` | 1.21.1 仍是 BakedModel + BEWLR 路径 |
| 批次 | 物品渲染必须 `BufferSource#endBatch()` 后才能裸 GL | 否则外挂 shader 会破坏批次状态 |
| 坐标 | `gl_FragCoord` 需按 `uRect` 修正 | 物品矩形 ≠ 全屏，不修正直接错位 |
| SRG | 1.21.1 生产环境实测 400 个补丁类中 `m_\d+_` 命中 0 | mixin 不需要 refmap |
| FBO | 物品路径不能随便加 FBO | 多 pass（bloom / 时序反馈）在物品侧不可行 |

---

## 6. 离线验证链路

不启动游戏就能调参与出图，把"构建 + 启动 + 截图"从分钟级降到秒级。

| 工具 | 位置 | 用途 |
|---|---|---|
| 预览器 | [scripts/preview.py](../scripts/preview.py) | 加载工程真实 shader，跑 scene/trail/bloom/composite，输出 PNG |
| 运行时 | `.pyenv`（moderngl + glfw + numpy + pillow，Python 3.14） | 本地 GL 3.3 core 上下文 |

产物：

- `效果图/preview_v1.png` —— v1
- `效果图/preview_disk_v2.png` —— v2
- `效果图/icon_samples/` —— 16×16 / 64×64 物品样张（v2 vs v3 同机位对照）

（`效果图/` 为本地素材目录，不入库）

### 已知坑（踩过的）

1. moderngl 没有 `CLAMP_TO_EDGE` 常量 → 直接跳过 wrap 设置。
2. NVIDIA 在沙箱里写 shader 缓存失败 → 设 `__GL_SHADER_DISK_CACHE=0`。
3. `vao.program = prog` 无效（VAO 绑定 program）→ 每个 program 建独立 VAO。
4. uniform 被 GLSL 优化掉会抛 `KeyError` → `setu()` 里 try/except；同时保证所有 Java 侧 uniform 在 shader 里被引用。
5. 预览输出成噪点 → final 纹理必须是 `RGBA16F`，按 float32 解析后再转 uint8。

---

## 7. 外部参考与许可证状态

| 来源 | 用途 | 许可 | 结论 |
|---|---|---|---|
| 0xydev/blackhole | v1/v2 的 Binet + RK4 测地线内核、`g` 因子 | **MIT**（LICENSE 原文已核对） | 可用 |
| zhongjn/Messier87 | 备选 | **MIT** | 可用 |
| sayed1145/glsl-black-hole | 备选 | **MIT**，但 `#version 330` + MRT 3 输出 | MC GL3.2 下不可直接用 |
| Sun-sh902/Gargantua | 备选 | **MIT** + NOTICE | 可用（three.js） |
| Shadertoy `lstSRS` | v3 借鉴的两个做法 | **源码 5 个 pass 无任何署名/许可/链接；`/terms` 被 Cloudflare 403** | **不可核实 = 不进发行物，仅本地试验** |

`lstSRS` 技术事实（已从本地导出的 5 个 pass 源码核实，不是猜测）：

- 5 pass：Image + Buffer A/B/C/D；**无 MRT**；依赖 **2 张外部噪声纹理**（`iChannel0` 噪声 LUT、`iChannel1` 彩色噪声）+ **自身上一帧反馈**（`iChannel2`）；无音轨。
- **不是测地线**：`WarpSpace` 每步按 `1/r²` 把视线朝原点掰一下，是艺术化假透镜。
- 200 步定步长体积前向累积；`Haze` 是手放的 3D 圆环（`sdTorus(pos, vec2(1.0, 0.01))`），不是透镜成像的光子环。
- 90% 帧间累积（`mix(color, previous, 0.9)`）+ 8 级 bloom + 胶片 tonemap。

> v3 没有复制 `lstSRS` 的任何一行代码，只借鉴了"逐样本平方反比光晕"和"多倍频反向旋转噪声"两个做法，实现按 v2 的测地线内核重写。

---

## 8. 物品侧（skyland-build）研究结论

目标工程：`skyland-build`（独立的 NeoForge 1.21.1 工程，未入库），物品 `lastone_skyland:skyland_gateway`（16×16 图标，也会在手持/掉落/展示框放大）。

| 结论 | 内容 |
|---|---|
| 观感保留 | 16×16 只保留「剪影 + 视界 + 内缘亮环 + 三色霓虹」；体积感、丝状纹理、bloom 层次全部丢失 |
| 64×64 | 体积晕与丝状纹理可见；bloom 实测只贡献约 +5% 亮度，砍掉可接受 |
| 时序累积 | 物品路径拿不到 FBO → 必丢；这是"丝滑"的真正来源，只能靠离线烘焙 |
| 推荐落地 | **b) 单 pass 降级实时** 为主（16×16 仅 256 px，60~120 步 RK4 成本可忽略，且不碰 FBO）；**a) 预渲染循环帧** 兜底（想要 16px 也带 bloom 就只能烘焙）；**c) 离屏+缓存** 不推荐（踩 FBO 禁区，收益只有 bloom） |

样张（均已在离线渲染产出，非推测）：`效果图/icon_samples/` 下的 `v3_16_full_x8.png`、`v3_64_full_x4.png`、`v3_64_nobloom_x4.png`、`v2_vs_v3.png`。

`效果\` 目录里用户导出的 16 张 PNG 全是 **140×79 的 Shadertoy UI 缩略图**（两张噪声通道 + 各 pass 层图标），**没有成品画面**，所以无法做像素级对照。

---

## 9. 待办 / 待决

1. **决定主菜单挂哪个版本**：若采纳 v3，改 [BlackHoleRenderer.java#L311](../src/main/java/com/gto/menu/render/BlackHoleRenderer.java#L311) 一行即可。后续已由 `ReferenceOrbitRenderer` 的默认路径取代，此项仅作历史待办保留。当时记录的参数已变更；按当前 [RenderConfig.java](../src/main/java/com/gto/menu/render/RenderConfig.java) 实测：`RENDER_SCALE 1.0`、`DISK_INNER 1.9`、`DISK_OUTER 10.5`、`TRAIL_DECAY 0.80`、`BLOOM_THRESHOLD 0.85`、`BLOOM_STRENGTH 0.7`。切换版本后是否需要重调**尚未验证**。
2. **v2/v3 的条纹是否换成上游 `diskFbm`**（0xydev，MIT，4 阶 `pnoise2`）——这是许可证干净、且能提升"气体感"的唯一路径。
3. **物品侧是否开工**：需用户确认后才动 skyland-build；届时产物是「1 个 shader + BEWLR + `uRect` 修正」，需 `endBatch`。
4. **许可证风险**：任何来自 `lstSRS` 的做法都不得进入发行物。

---

## 10. 变更边界（当前有效）

- `blackhole.fsh`、`blackhole_disk_v2.fsh`、`BlackHoleRenderer.java`、`RenderConfig.java`：**未被改动**。
- `blackhole_volume_v3.fsh`：新增副本，实验性。
- skyland-build：**零改动**。
- 所有试验、抓取脚本、临时渲染器均在系统临时目录，不在工程内。
