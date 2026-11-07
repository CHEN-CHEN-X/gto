# GregTech Odyssey Menu

GTO 客户端主菜单：二进制黑洞、背景数据星辰、密封机壳轨道与像素星球按钮。

## 安装

- Minecraft 1.20.1、Forge 47.4.20。
- 必须安装 GTCEu 和 GTOCore；本模组在它们之后加载。
- 将构建出的 JAR 放进整合包的 `mods/`。
- 已在 GTO 0.6.0-dev27、JDK 25 中验证主菜单。

## 操作与配置

菜单支持鼠标、Tab、方向键和 Enter。中央图标可打开百科。

首次启动会生成 `config/gto_menu-client.toml`：

```toml
[links]
wikiUrl = "https://gtodyssey.com/"
```

修改地址后重启游戏。

渲染参数位于 `src/main/java/com/gto/menu/render/RenderConfig.java`。
按钮布局位于 `src/main/java/com/gto/menu/client/PlanetMenu.java`。

## 构建

使用 JDK 21。先按本机环境修改 `gradle.properties` 中的 JDK 路径，然后运行：

```powershell
.\gradlew.bat build
```

输出：`build/libs/gto_menu-1.0.0.jar`。构建会检查成品不超过 10 MiB。
运行需要完整整合包环境；构建使用 JDK 21，游戏可使用 JDK 25。

## 渲染预览

安装 Python 依赖 `numpy`、`Pillow`、`moderngl`、`glfw` 后运行：

```powershell
python scripts/preview_reference_orbits.py --animate
```

结果写入 `效果图/`，不进入 JAR。素材生成脚本位于 `scripts/`。

## 许可与来源

项目采用 [MIT](LICENSE.txt) 许可。素材与参考来源见 [CREDITS.txt](CREDITS.txt)。
