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

### JDK 版本说明

| 项 | 实际情况 |
|---|---|
| 构建目标 | **Java 17**。[build.gradle#L36](build.gradle#L36) 的 `java.toolchain.languageVersion = JavaLanguageVersion.of(17)` 强制指定；仓库内没有 `sourceCompatibility` / `targetCompatibility` 覆盖，因此编译产物就是 17 |
| Gradle | wrapper 使用 **Gradle 8.8**（`gradle/wrapper/gradle-wrapper.properties`） |
| 已记录的编译验证 | **无**。仓库内没有构建日志，`docs/` 也未记录曾用哪个 JDK 完成过构建 |
| 当前开发机 | PATH 中的 `java` 为 **JDK 26.0.1**，本机另装有 JDK 21 与 JDK 24，**未找到 JDK 25** |

关于 JDK 25：

- **已确认的事实**：当前开发者设备因环境与网络综合问题，**未能用 JDK 25 完成编译验证**。
- **推测（尚未实测）**：预计用 JDK 25 编译问题不大——toolchain 已强制目标为 17，源码本身也不依赖 25 的新特性，只要 Gradle 能在该 JDK 上启动即可。但下面两点没有任何实测依据：Gradle 8.8 能否在 JDK 25 上正常运行、以及在缺少 JDK 17 时 toolchain 能否解析成功。若真出问题，最可能落在 Gradle 启动或 toolchain 查找阶段，而不是源码本身。

需要强调：以上除"未能用 JDK 25 验证"这一条外，其余都是推断，不构成可用性承诺。

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

## 贡献与来源

本项目由人工与 AI 协作完成。整体来看，人工与 AI 大致各占一半，AI 略多（约占一大半）。按模块拆开后，两者的分工并不平均：

| 模块 | 人工 | AI |
|---|---|---|
| 渲染部分（shader 编写、渲染管线、效果调参与取舍） | 约 60% | 约 40% |
| 其他部分（工程搭建、构建与依赖配置、工具脚本、文档整理） | 约 30% | 约 70% |

以上为按工作量估算的大致比例，不是精确统计。更完整的说明见 [docs/CONTRIBUTION.md](docs/CONTRIBUTION.md)。

`效果/` 与 `效果图/` 是本地素材，**不入版本库**（已写入 `.gitignore`）：前者是用户提供的参考素材（Shadertoy pass 源码导出与截图缩略图），后者是离线渲染产出的成品图与预览 GIF。它们只用于本地对照与试验，不随仓库分发。
