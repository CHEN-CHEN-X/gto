# 与 GTO 新版对齐

对照来源（2026-11-07 检查）：

- 整合包 `GregTech-Odyssey/GregTech-Odyssey` 的 `main/pack.toml`：Minecraft 1.20.1、Forge 47.4.20。
- 核心 `GregTech-Odyssey/GTOCore-Main` 的 `0.6.0/build.gradle.kts`：Java toolchain 21、发行字节码 release 21；该项目明确不以 Java 25 字节码发行，避免 reobf/ART 解析失败。
- 本菜单模组只使用 Minecraft/Forge API，保留已有 Gradle 8.8 / ForgeGradle 6 构建，不复制核心工程的依赖和插件。

本项目设置：Minecraft 1.20.1、Forge 47.4.20、Java 21 字节码。
仅依赖 Forge 和 Minecraft，不恢复 FancyMenu、Konkrete、Melody 或本项目 Mixin。

## 构建及运行

Gradle 运行在 JDK 21 上；本机路径已写入 `gradle.properties`，换机器请更新该文件中的本地路径。

```powershell
$env:JAVA_HOME='<你的 JDK 21 路径>'
.\gradlew.bat --offline build
.\gradlew.bat --offline runClient -x downloadAssets
```

仅将游戏进程改用本机 JDK 25 的验证入口（构建 JVM 仍为 JDK 21）：

```powershell
.\gradlew.bat --offline runClient -x downloadAssets -Pgame_java_home=E:/JAVA/JAVA25
```

游戏 JVM 参数与编译目标独立；使用 JDK 25 运行不意味着生成 Java 25 字节码。
`verifyJarSize` 继续检查成品不超过 10 MiB。菜单接入、画面、速度和材质均不在本次版本对齐中修改。

## 本机验证结果

- 初次构建联网补齐 Forge 47.4.20 的 ASM 9.9.1 依赖，随后 `--offline build` 成功。换机器若缺缓存，请先执行一次不带 `--offline` 的构建。
- 成品全部 9 个 class 均为 major 65（Java 21），重混淆通过；没有 Java 25 字节码。
- 实际独立开发客户端以 Oracle JDK 25.0.3、Forge 47.4.20 启动成功，日志确认 1074 个数字贴片及 Forge 主菜单事件已加载。
- 未加载 FancyMenu、Konkrete、Melody，无本项目 Mixin。
- 源码检查确认新版 GTOCore 的 `GTOTitleScreen extends TitleScreen`，现有 `instanceof TitleScreen` 事件判断可匹配该界面，无需增加 GTOCore 硬依赖。
- 本次未安装并启动完整整合包；上述启动验证范围为独立 Forge 开发客户端。
- 23 个应用源码和材质文件哈希保持一致；成品 3915584 字节，低于 10 MiB。
