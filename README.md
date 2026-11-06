# GregTech Odyssey Menu

Minecraft **1.20.1 / Forge 47.4.5** 的主菜单黑洞渲染模组（mod id `gto_menu`，版本 1.0.0，仅客户端）。

它把原版主菜单的全景图替换为一个实时的 GLSL 黑洞背景，按钮与标题排版交给 FancyMenu 主题。

## 效果是什么

- 纯黑宇宙背景 + 稀疏三色（青 / 品红 / 暖白）星点
- 旋转黑洞：视界剪影 + 光子环
- 倾斜吸积盘，按 Patrick 风格的薄盘亮度与颜色梯度着色
- 环绕运行的 0/1 数字环带

### 渲染管线

4 个 pass，全部在 RGBA16F HDR 缓冲里跑，最后输出到 Minecraft 主 FBO：

| pass | 作用 |
|---|---|
| `scene` | Schwarzschild 零测地线积分（Binet 方程 RK4）求每条光线的落点；吸积盘、视界、星场在此着色 |
| `trail` | 帧反馈拖尾（上限帧反馈） |
| `bloom` | 可分离高斯，水平/垂直 × 2 轮共 4 趟 |
| `composite` | ACES 色调映射 + sRGB + 暗角 + 抖动 |

渲染分辨率为屏幕的 0.75 倍（`RenderConfig.RENDER_SCALE`），以换取 1080p 下的帧率。

## 依赖

| 组件 | 版本 | 说明 |
|---|---|---|
| Minecraft | 1.20.1 | 仅客户端 |
| Minecraft Forge | 47.4.5 | 必需 |
| FancyMenu | 3.9.6 及以上 | **声明为可选**（`mods.toml` 中 `mandatory=false`），缺失时模组仍能加载、黑洞背景正常渲染，但没有 GTO 的标题与按钮布局 |
| Konkrete | 1.8.0 | FancyMenu 前置 |
| Melody | 1.0.3 | FancyMenu 前置 |

`libs/` 下放的是这三个依赖的 jar（flatDir 仓库解析，文件名必须是 `<name>-<version>.jar`）。
**本仓库 Java 代码不引用 FancyMenu API**，它只负责加载 `config/fancymenu/customization/` 下的主题布局。

## 构建与运行

需要 JDK 17。

```bash
./gradlew build        # 产物在 build/libs/
./gradlew runClient    # 启动带模组的测试客户端
```

首次运行前如果 IDE 缺库：

```bash
./gradlew --refresh-dependencies
```

## 目录结构

```
src/main/java/com/gto/menu/      模组入口、渲染器、配置、Mixin
src/main/resources/assets/gto_menu/shaders/
                                 fsquad.vsh / blackhole.fsh / trail / bloom / composite
docs/                            设计与状态文档
scripts/preview.py               离线预览：不启动游戏即可渲染出 PNG（moderngl）
libs/                            FancyMenu / Konkrete / Melody 依赖 jar
```

## 文档

| 文件 | 内容 |
|---|---|
| [PROJECT_STATUS.md](docs/PROJECT_STATUS.md) | 项目当前状态、三版着色器对比、待办与风险 |
| [CURRENT_RENDER_AUDIT.md](docs/CURRENT_RENDER_AUDIT.md) | 当前着色器实现审计 |
| [ARTWORK_FLOW.md](docs/ARTWORK_FLOW.md) | 视觉设计与实现流程 |
| [REFERENCE_MATCH.md](docs/REFERENCE_MATCH.md) | 与参考图的逐项比对 |
| [CONTINUOUS_ORBIT.md](docs/CONTINUOUS_ORBIT.md) | 连续轨道环设计 |
| [INNER_ORBITS.md](docs/INNER_ORBITS.md) | 内圈数字环设计 |
| [SLOW_MATERIAL.md](docs/SLOW_MATERIAL.md) | 慢速材质/低速运动方案 |
| [CONTRIBUTION.md](docs/CONTRIBUTION.md) | 人机协作比例说明 |

## 未入库的内容

以下目录为本地素材，通过 `.gitignore` 排除，不随仓库分发：

- `效果/`、`效果图/`：效果参考图、Shadertoy 导出的对照源码、离线渲染成品
- `client/`、`.pyenv/`：本地测试客户端与 Python 环境
- `config/`：运行时配置，其中 `config/fancymenu/customization/title_screen_layout.txt` 是主题布局文件

因此从本仓库克隆后，需要自备主题布局文件才能得到完整的 GTO 主菜单外观。

## 许可证

MIT，见 [LICENSE.txt](LICENSE.txt)。与 `mods.toml` 中的 `license=MIT` 一致。

## 贡献

见 [docs/CONTRIBUTION.md](docs/CONTRIBUTION.md)。
