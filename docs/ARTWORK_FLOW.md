> 当前版本已按新标注撤掉程序字形，改用慢速原图材质，速度降低 80%；以 [SLOW_MATERIAL.md](SLOW_MATERIAL.md) 为准。

> 后续修复：已移除短周期流动重置，改为完整轨道旋转；以 [CONTINUOUS_ORBIT.md](CONTINUOUS_ORBIT.md) 的实现、速度、验证和 JAR 大小为准。下面的交错相位说明属于旧版本。

# 原图材质实时流动 · 最终 JAR ≤ 10 MiB

## 当前方案

按用户最新授权，使用原效果图材质，并在 GLSL 150 中进行实时局部流动。默认路径为 ArtworkFlowRenderer，原程序生成黑洞作为可选模式和加载失败回退保留。原有 Forge 1.20.1、Java 17、LWJGL 技术栈不变。

这是一种固定视角的 2.5D 材质动画：原图负责构图、数字、透镜形状、细丝和辉光；着色器沿椭圆盘和视界圆环移动采样位置，通过交错相位保持连续。没有视频解码、动画帧序列、FBO 历史纹理或每帧 CPU 图片上传。光晕及原图中的光轨细节来自材质，不应描述成新的三维物理模拟或真实帧反馈拖尾。流动时局部会发生重采样和交叉混合；不能称为每一动态帧都与静态图片逐像素相同。

## 最终包体硬限制

- 最终 reobfJar 产物：`build/libs/gto_menu-1.0.0.jar`
- 实际大小：**10,152,876 字节 / 9.682537 MiB**。
- 上限：**10,485,760 字节（10 MiB）**；剩余 **332,884 字节**。
- `build` 自动执行 `verifyJarSize`，在最终重映射后检查实际文件大小，超过上限则构建失败。
- JAR 中的 reference.png 与原始素材 SHA-256 一致，没有二次压缩或改色。
- 验证 JAR 没有 MP4/GIF/WebM/AVI。GIF 仅是工程外观验证产物，不被打包。

## 运行时占用

原图为 1671×941；一张 RGBA8 GPU 纹理为 **6,289,644 字节（约 6.00 MiB）**，没有 mipmap。解码像素在上传后立即释放。输入 PNG 的临时 Java/直接缓冲由 JVM 回收。正常路径不初始化旧的 55 MiB 多 pass 缓冲。以上是效果资源体积，不是整个 Minecraft 进程的 RAM/显存读数，也未包含驱动内部开销。

## 文件变动

- `src/main/java/com/gto/menu/render/ArtworkFlowRenderer.java`：单纹理全屏渲染器；初始化一次、按单调时间驱动；保持比例与黑边；保存恢复调用方 GL 状态；失败回退旧 Shader。
- `src/main/resources/assets/gto_menu/shaders/artwork_flow.fsh`：盘面和视界的局部流动、交错相位、黑洞内部保护、原图色彩直出。
- `src/main/resources/assets/gto_menu/textures/reference.png`：原始效果图的无修改副本。
- `BlackHoleRenderer.java`：优先调用新路径。
- `RenderConfig.java`：`ARTWORK_FLOW = true` 默认开启；`ARTWORK_FLOW_STRENGTH = 1.0` 控制运动幅度。
- `build.gradle`：最终 JAR 10 MiB 校验。
- `scripts/preview_artwork.py`：实际 Shader GPU 验证与动画预览。
- 文档更新。

变动前备份：`backups/artwork-flow-20261106-124832/`，含 src、scripts、docs、build.gradle。

## 运行与调节

在项目目录执行：

```powershell
$env:JAVA_HOME = 'E:\JAVA\JAVA17\jdk-17.0.20.1+1'
.\gradlew.bat --offline build
.\gradlew.bat --offline runClient -x downloadAssets
```

本机使用已有资源缓存；此前普通 runClient 被缺失语言资源下载失败阻断。新机器需要先下载 Minecraft 资源。发布 jar 仍需项目现有 Forge/FancyMenu/Konkrete/Melody 依赖，10 MiB 限制检查的是本模组 JAR，不包含这些独立依赖。

`ARTWORK_FLOW_STRENGTH = 0` 显示未经变形的原图；`1` 为默认流动，超过 1 会增强扭曲。`ARTWORK_FLOW = false` 恢复程序生成版本。修改后重新构建。

菜单无文字模式保持开启；Alt+F4 退出。恢复菜单需同时把 EFFECT_ONLY 改为 false、FancyMenu title_screen_layout.txt 的 is_enabled 改为 true。

## 验证

- Gradle build / compileJava / reobfJar / verifyJarSize 通过。没有测试源，test 为 NO-SOURCE；仍有原有 ResourceLocation 弃用警告。
- RTX 5060 Ti 上实际 GLSL 编译与渲染通过。
- 原始尺寸、流动强度 0：最大像素误差 **0**，平均误差 **0.0**，与原图逐像素一致。
- 动画不同时间平均 RGB 变化 **7.9639/255**，确认不是静态贴图冒充动画。
- 相位切换前后平均字节变化 **0.010007/255**，无大幅跳帧。
- 竖窗口保持原始比例，额外区域实测 RGB 全黑。

复查命令：

```powershell
& 'C:\Program Files\Python314\python.exe' scripts/preview_artwork.py --animate
```

输出：`效果图/artwork_flow_preview.png`、`artwork_flow_preview.gif`、`artwork_flow_portrait.png`、`artwork_flow_validation.json`。这些验证文件不进入 JAR。
