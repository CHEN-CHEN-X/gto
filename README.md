# GregTech Odyssey Menu

Minecraft 1.20.1 / Forge 47.4.20 的客户端黑洞菜单模组（mod id `gto_menu`，版本 1.0.0，仅客户端）。Java 21 编译，已验证 JDK 25 的独立客户端启动；完整整合包仍需单独测试。

通过 Forge 界面事件接入，无 FancyMenu、Konkrete、Melody 或本项目 Mixin 依赖。画面采用参考图的固定光丝与透镜外形，加上实时运行的数字贴片和轻微轨迹拖尾；没有视频、帧序列或播放缓存。

## 画面实现

全盘静态图像使用两张位置对齐的 RGBA 素材：`plate_bright_digits.png` 通过数字形状遮罩显露亮版静态大数字，`plate_dark_lines.png` 保护数字、只显露暗版线条。八条路径共分布 34 处亮区和 34 处暗区（比前版各增加 10 处），长宽均放大 10%；暗区由五条错开的细长椭圆组成，随轨道切线连续转向。亮暗区域沿完整闭合轨道运行，上下半盘均显示且互斥，素材 UV 不移动，原有运动数字保留。素材由 `scripts/bake_plate_materials.py` 生成。

参数在 `src/main/java/com/gto/menu/render/RenderConfig.java`：

- `ARTWORK_FLOW_STRENGTH=0.2`：吸积盘 300 秒/圈，中央光环 150 秒/圈。
- `ARTWORK_TRAIL_STRENGTH=0.05`：轻微历史位置残影。
- `ARTWORK_SPOT_ORBIT_SECONDS=300`：小范围亮暗区域沿轨道运行一圈的时间。
- `ARTWORK_SPOT_BRIGHT_GAIN=1.0`：亮版素材显露强度（0～1），素材自身最高为底图亮度的 2.4 倍。
- `ARTWORK_SPOT_DARK_DROP=1.0`：暗版素材显露强度（0～1），素材自身保留 15% 线条亮度；两项都设为 0 恢复原底图。
- `EFFECT_ONLY=true`：纯效果显示，并屏蔽隐藏按钮输入；设为 false 恢复原版或整合包菜单。

源码入口为 `client/MenuRenderEvents.java`，当前渲染器为 `render/ReferenceOrbitRenderer.java`；`ARTWORK_FLOW` 关闭或初始化失败时回退到 `render/BlackHoleRenderer.java` 的程序化渲染器。日志保留警告和错误，不输出周期帧率或注册表调试内容。

## 依赖

| 组件 | 版本 | 说明 |
|---|---|---|
| Minecraft | 1.20.1 | 仅客户端 |
| Minecraft Forge | 47.4.20 | 必需，`mods.toml` 中唯一的模组依赖 |

`libs/` 下保留的 FancyMenu / Konkrete / Melody jar 是历史依赖，当前构建与运行都不再使用，也不再声明任何可选依赖。

## 构建与运行

构建使用 JDK 21。`gradle.properties` 中配置了本机 JDK 路径，换机器需修改 `org.gradle.java.home` 和 `org.gradle.java.installations.paths`。

```powershell
$env:JAVA_HOME='<你的 JDK 21 路径>'
.\gradlew.bat build
.\gradlew.bat --offline runClient -x downloadAssets
```

仅让游戏使用 JDK 25（Gradle 仍运行在 JDK 21）：

```powershell
.\gradlew.bat --offline runClient -x downloadAssets -Pgame_java_home=<你的 JDK 25 路径>
```

成品为 `build/libs/gto_menu-1.0.0.jar`；`verifyJarSize` 强制检查不超过 10 MiB。首次构建需要下载 Forge 依赖，缓存齐全后可使用 `--offline build`。

### JDK 版本说明

| 项 | 实际情况 |
|---|---|
| 构建目标 | **Java 21**。[build.gradle#L17](build.gradle#L17) 的 toolchain 与 [build.gradle#L192](build.gradle#L192) 的 `options.release = 21` 共同固定 |
| Gradle | wrapper 使用 **Gradle 8.8** |
| 编译验证 | 已离线构建通过；成品 9 个 class 均为 major 65（Java 21），没有 Java 25 字节码 |
| 运行验证 | 已用 Oracle **JDK 25.0.3** 启动独立 Forge 开发客户端成功 |
| 未验证 | **完整 GTO 整合包未安装并启动测试**，上述运行验证仅为独立开发客户端 |

数据与日志摘录见 [pack_alignment_validation.json](docs/pack_alignment_validation.json)。用 JDK 25 启动游戏不等于生成 Java 25 字节码，发行字节码固定为 21。完整对照理由见 [PACK_VERSION_ALIGNMENT.md](docs/PACK_VERSION_ALIGNMENT.md)。

## 目录结构

```
src/main/java/com/gto/menu/        模组入口与渲染实现
  client/MenuRenderEvents.java     Forge 界面事件接入
  render/ReferenceOrbitRenderer.java  当前渲染器（数字贴片轨道 + 固定透镜材质）
  render/BlackHoleRenderer.java   程序化回退 + 4 pass HDR 管线
  render/RenderConfig.java        所有可调参数
src/main/resources/assets/gto_menu/
  shaders/ glyph_orbit / orbit_plate / blackhole 等
  textures/ 轨道实例、字形图集、亮暗材质与参考图
scripts/                           离线预览与素材烘焙
docs/                              设计与状态文档
libs/                              历史依赖 jar（当前构建不使用）
```

已有 `.pyenv` 包含 numpy、Pillow、moderngl 和 glfw 时，可以验证正式着色器：

```powershell
python scripts/preview_reference_orbits.py --animate
```

预览输出到忽略的 `效果图/`，不进入 JAR。`scripts/bake_reference_orbits.py` 从参考图重新生成数字素材到 `build/reference_orbits/`；`scripts/bake_light_tracks.py` 从参考图数字位置拟合轨道，生成 `build/reference_orbits/light_tracks.bin.gz`，小路径数据随模组打包，不增加 GPU 纹理。

## 文档

| 文件 | 内容 | 状态 |
|---|---|---|
| [PACK_VERSION_ALIGNMENT.md](docs/PACK_VERSION_ALIGNMENT.md) | 与 GTO 整合包版本对齐、JDK 25 启动验证 | 当前 |
| [FORGE_EVENT_INTEGRATION.md](docs/FORGE_EVENT_INTEGRATION.md) | Forge 事件接入与第三方依赖移除 | 当前 |
| [CONTRIBUTION.md](docs/CONTRIBUTION.md) | 人机协作比例与本地素材说明 | 当前 |
| [PROJECT_STATUS.md](docs/PROJECT_STATUS.md) | 旧版程序化方案状态、三版着色器对比 | 历史 |
| [CURRENT_RENDER_AUDIT.md](docs/CURRENT_RENDER_AUDIT.md) | 原图材质动画版本的问题审计 | 历史 |
| [REFERENCE_MATCH.md](docs/REFERENCE_MATCH.md) | 与参考图匹配的迭代记录 | 历史 |
| [ARTWORK_FLOW.md](docs/ARTWORK_FLOW.md) | 原图材质实时流动方案 | 历史 |
| [CONTINUOUS_ORBIT.md](docs/CONTINUOUS_ORBIT.md) | 连续整圈旋转修复 | 历史 |
| [INNER_ORBITS.md](docs/INNER_ORBITS.md) | 前景与内层数字运动补齐 | 历史 |
| [SLOW_MATERIAL.md](docs/SLOW_MATERIAL.md) | 慢速原图材质版本 | 历史 |

历史文档保留当时的实现、验证数据与已知问题，其中的构建命令、JDK 版本和依赖可能已过时；当前使用说明以本文件为准。

## 未入库的内容

以下目录通过 `.gitignore` 排除，不随仓库分发：

- `效果/`、`效果图/`：效果参考图、Shadertoy 导出的对照源码、离线渲染成品
- `client/`、`.pyenv/`：本地测试客户端与 Python 环境
- `backups/`：各次改动前的本地备份
- `config/`：运行时配置；其中的 FancyMenu 布局已不再被本项目加载

## 许可证

MIT，见 [LICENSE.txt](LICENSE.txt)。与 `mods.toml` 中的 `license=MIT` 一致。

## 贡献与来源

本项目由人工与 AI 协作完成。整体来看，人工与 AI 大致各占一半，AI 略多（约占一大半）。按模块拆开后，两者的分工并不平均：

| 模块 | 人工 | AI |
|---|---|---|
| 渲染部分（shader 编写、渲染管线、效果调参与取舍） | 约 60% | 约 40% |
| 其他部分（工程搭建、构建与依赖配置、工具脚本、文档整理） | 约 30% | 约 70% |

以上为按工作量估算的大致比例，不是精确统计。`效果/` 与 `效果图/` 是用户提供的参考素材与离线渲染产物，只用于本地对照，不入版本库。更完整的说明见 [docs/CONTRIBUTION.md](docs/CONTRIBUTION.md)。
