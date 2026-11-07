> 历史记录（参考图匹配时期）：本文记录 2026-11-06 的迭代与验证，当时使用 Java 17、程序化 GLSL 管线和 Mixin + FancyMenu 接入。后续方案依次为 [ARTWORK_FLOW.md](ARTWORK_FLOW.md) → [CONTINUOUS_ORBIT.md](CONTINUOUS_ORBIT.md) → [INNER_ORBITS.md](INNER_ORBITS.md) → [SLOW_MATERIAL.md](SLOW_MATERIAL.md)。当前构建目标为 Java 21、默认渲染器为 `ReferenceOrbitRenderer`，见 [README](../README.md)。
>
> 本阶段关键结论保留：用户明确拒绝视频/逐帧图片播放，媒体方案未接入，本版继续使用纯程序 GLSL；改动前备份为 `backups/reference-media-20261106-120246`。Bloom 两个缓冲降至半宽半高，1080p RGBA16F 附件理论占用 55.37 MiB（原 79.10 MiB，降低 30%，不含游戏其他资源与驱动开销，也不是与视频解码器内存占用的比较）。build 成功、test 为 NO-SOURCE；四个 GLSL 阶段编译链接成功；30/60 FPS 同时刻 RGB 平均差约 0.0051/255，角落 RGB 为 0。当前视觉仍是近似，未达到与参考图片逐像素一致。
# 原始需求图匹配版本 · 2026-11-06

## 结果与范围

保留 Forge 1.20.1 / Java 17 / LWJGL / GLSL 150 和现有 scene → trail → bloom → composite 管线。以项目内「霓虹二进制黑洞漩涡.png」、10 秒视频和用户重新附上的同一张原图为目标。没有改动其他工程。

当前版本是艺术化近似，并非参考图的逐像素复刻：宽盘主像使用受控的正交薄盘投影，中央强场次级像沿用原有 Schwarzschild/Binet RK4 测地线。这样保留弯曲内环，同时避免外盘被透镜拱成厚穹顶。视界数字冠和两侧高亮属于艺术叠加，并非物理模拟结果。

## 备份

原工程没有 .git。改动前完整复制 src、scripts、docs、config/fancymenu 至 `backups/binary-disk-20261106-114125/`。备份中的 `config_fancymenu` 对应原 `config/fancymenu`。不要恢复整个工程覆盖其他文件；按下面清单逐项恢复即可。

## 修改文件

- `src/main/resources/assets/gto_menu/shaders/blackhole.fsh`：保留积分核心和 SDF 字形；密集动态轨道、多层细丝、主／次级像合成、青紫分段配色、细光子环、数字冠、交汇局部高亮；修正角坐标接缝的数字编号；用解析像素足迹替代发散循环内的字形导数。
- `src/main/java/com/gto/menu/render/BlackHoleRenderer.java`：增加相机 roll；帧反馈按真实 dt 指数衰减；窗口变化与间隔过长时清理历史；修正分配 FBO 前的状态保存，恢复纹理、深度写入、裁剪与 viewport；移除失效 uniform 绑定。
- `src/main/java/com/gto/menu/render/RenderConfig.java`：集中管理最终视觉参数，增加 EFFECT_ONLY。
- `src/main/java/com/gto/menu/mixin/TitleScreenMixin.java`：纯效果模式取消原版菜单绘制，Shader 初始化失败时保留原版回退。
- `config/fancymenu/customization/title_screen_layout.txt`：仅把布局 is_enabled 改为 false，避免 FancyMenu 独立绘制 GTO Logo 和文字；布局内容完整保留。
- `src/main/resources/assets/gto_menu/shaders/composite.fsh`：抖动在全黑像素处关闭，防止背景灰噪点。
- `scripts/preview.py`：从 Java 常量直接读参数，匹配 RGBA16F、纹理边界及历史清理；增加时间／尺寸／帧率／动画参数；检查非有限像素。
- `src/main/resources/META-INF/licenses/0xydev-blackhole.txt`：补充既有积分内核的上游 MIT 版权与许可，随 jar 打包。
- 本说明及 PROJECT_STATUS.md 顶部状态提示。

## 关键参数

| 参数 | 当前值 | 用途 |
|---|---:|---|
| CAM_DISTANCE / CAM_POLAR / CAM_ROLL | 13.4 / 1.18 / -0.26 | 构图、盘倾角与画面斜角 |
| DISK_INNER / DISK_OUTER | 1.9 / 10.5 | 发光盘范围，几何单位 r_s |
| DIGIT_DENSITY / DIGIT_DENSITY_R | 1.9 / 1.5 | 周向密度／径向层数，约 12 层 |
| TIME_SCALE | 0.85 | 轨道差速旋转 |
| TRAIL_DECAY | 0.80 | 每 1/60 秒历史衰减，半衰期约 52ms |
| BLOOM_THRESHOLD / BLOOM_STRENGTH | 0.85 / 0.7 | 局部辉光 |
| RENDER_SCALE | 1.0 | 原生内部渲染尺寸 |
| STAR_INTENSITY / CAM_AUTO_SPIN | 0 / 0 | 黑背景／固定机位 |

1.9 的发光内缘是艺术参数；Schwarzschild 稳定圆轨道 ISCO 实为 3。不能把这个造型当作物理稳定吸积盘。

## 运行

在项目目录的 PowerShell 中：

```powershell
$env:JAVA_HOME = '<当时的 JDK 17 路径>'
.\gradlew.bat --offline build
.\gradlew.bat --offline runClient -x downloadAssets
```

普通 `runClient` 尝试下载缺失的 minecraft/lang/af_za.json、ar_sa.json、bar.json 等资源，因下载或校验失败而中止。`-x downloadAssets` 在本机已有资源缓存下已实际启动成功；新机器应先正常下载资源。无需改变系统默认 Java 26。

纯效果模式隐藏控件，退出窗口可用 Alt+F4。恢复完整菜单：将 `RenderConfig.EFFECT_ONLY` 改为 false，并将 `config/fancymenu/customization/title_screen_layout.txt` 的布局 is_enabled 改回 true，重新构建启动。

GPU 离线预览：

```powershell
python scripts/preview.py --output preview_reference_match.png
python scripts/preview.py --width 960 --height 540 --frames 90 --fps 30 --animate --output preview_reference_motion.png
```

加载工程真实 Shader；`.pyenv` 已含 moderngl、glfw、numpy、Pillow。GIF 是时间片段预览，循环连接处不保证无缝。

## 验证

- Gradle build 通过，包含 compileJava、Mixin 注解处理、资源打包和 reobfJar；工程没有测试源，test 显示 NO-SOURCE。保留既有 ResourceLocation 弃用和 Gradle 9 兼容警告。
- GPU 为 NVIDIA GeForce RTX 5060 Ti，GL 3.3 上下文；四个 GLSL 150 程序编译／链接通过，完整 HDR 管线连续渲染并检查无 NaN/Inf。
- 同一动画时刻以 30／60 FPS 推进后比较像素，并检查角落保持 RGB 0；验证输出在 效果图/validation_30fps.png 和 validation_60fps.png。
- 实际 Minecraft 客户端管线初始化成功，先前 1920×1057 约 60 FPS（含垂直同步／整帧耗时，非独立 GPU 性能基准）。最终版本另有游戏截图与日志核验。

## 产物

- `效果图/preview_reference_match.png`：最终 GPU 静态预览。
- `效果图/preview_reference_motion.gif`：最终连续帧反馈动画。
- `效果图/game_reference_verified.png`：最终游戏窗口截图。
- `build/libs/gto_menu-1.0.0.jar`：构建产物。

上游：https://github.com/0xydev/blackhole ，沿用项目已有 MIT 内核；没有引入许可不明的 ShaderToy 代码。
