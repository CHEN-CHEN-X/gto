# 独立 Forge 菜单接入

当前版本仅依赖 Minecraft 1.20.1 和 Forge 47.4.20；不依赖 FancyMenu、Konkrete 或 Melody。

`client/MenuRenderEvents.java` 通过客户端 Forge 事件总线注册：

- `ScreenEvent.Render.Pre`：在 `EFFECT_ONLY=true` 且当前是 `TitleScreen` 时绘制黑洞，成功后取消原版界面绘制；渲染失败则保留原版回退。
- 纯效果模式取消菜单的鼠标按下及键盘按下，防止操作隐藏按钮；其他界面不受影响。
- `EFFECT_ONLY=false` 恢复原版菜单。

已移除本项目的 Mixin、MixinGradle、FancyMenu 三项依赖、可选依赖声明、版本配置、主题打包/同步任务，以及用于第三方模组的 refmap 开发启动适配。旧 `libs/` 和 `config/` 文件保留在磁盘供历史参考，不参与构建或加载。

黑洞渲染器、材质、轨道数据及动画参数未改动。

Java 21 下构建：`gradlew.bat --offline build`。
启动：`gradlew.bat --offline runClient -x downloadAssets`（复用本机已有资源缓存）。
最终 JAR 仍由 `verifyJarSize` 检查不超过 10 MiB。

验证：离线构建成功；项目没有自动化测试源。客户端实际启动成功，启动日志未加载三个已移除模组，并确认 Forge 菜单事件与 1074 个数字贴片均已就绪。23 个渲染及材质文件哈希保持一致。最新版本验证和成品大小见 `pack_alignment_validation.json`。

版本及 JDK 25 验证入口见 [PACK_VERSION_ALIGNMENT.md](PACK_VERSION_ALIGNMENT.md)。
