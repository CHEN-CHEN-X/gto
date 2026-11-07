> 历史记录（连续整圈旋转时期）：本文记录的完整轨道旋转随后由 [INNER_ORBITS.md](INNER_ORBITS.md) 补齐前景与内层数字动画，再经 [SLOW_MATERIAL.md](SLOW_MATERIAL.md) 降速调整；当前实现见 [README](../README.md)。

# 连续整圈旋转修复

用户指出“移动一小段就回到原位”。原因已确认：上一版使用两秒周期交错采样，虽然像素衔接平滑，但纹理并没有真实绕行一圈。此前仅检查相位边界像素连续，未验证特征完整轨道，这是验证遗漏。

## 当前实现

- 删除短周期相位重置、双相时间交叉淡化和启动时回原图的淡化。
- Java 使用单调时间和 double 精度计算已完成圈数；仅在整圈后将角度取模到 0～2π。三角函数在此边界连续。
- 删除旧的 256 秒计时归零，避免长期运行时跳动。
- 外盘 60 秒一圈，视界数字环 30 秒一圈（ARTWORK_FLOW_STRENGTH=1）。该参数现在控制速度；0 为原图静态模式。
- 保留固定的中心黑洞、内侧透镜与交汇结构；外侧盘面和视界数字带采用独立角坐标材质。它们从原图未遮挡弧段取样，在空间上做接缝重叠；空间材质有重复纹样，但没有定时回到初始位置的动作。
- 材质弧重复以保持数字宽高比例，不旋转整张包含透镜与遮挡的场景图。仍属于固定视角 2.5D 材质动画，不是三维物理模型；部分内层结构固定，动态外观也不等于原静态图逐像素复刻。

## 验证与产物

- GLSL 150 GPU 编译、Gradle build、reobfJar、verifyJarSize 通过。
- 最终 JAR **10,153,078 字节 / 9.682730 MiB**，小于 10,485,760 字节。
- 0 秒与 60 秒的 GPU 图像平均字节差 **0.0**，完整一圈闭合。
- 2、6、15、30、45、58 秒均与 0 秒不同，防止短周期提前回原位。
- 2、30、60、120、256 秒边界前后逐项测试，平均像素差约 0.01/255，无明显时间边界跳变。
- 没有视频或帧序列进入 JAR，仍是一张约 6 MiB 的 RGBA8 纹理。
- `效果图/orbit_full_turn_10x.gif`：60 秒完整外盘周期的 10 倍速 GPU 预览，仅用于检查，不参与运行时渲染和打包。
- `效果图/artwork_flow_preview.gif`：正常速度短预览。
- `效果图/orbit_quarter.png` 与 `orbit_half.png`：四分之一圈、半圈截图。

复查：
```powershell
python scripts/preview_artwork.py --orbit-cycle --animate
```

备份：`backups/continuous-orbit-20261106-130954/`。

修改文件：artwork_flow.fsh、ArtworkFlowRenderer.java、RenderConfig.java、scripts/preview_artwork.py，以及本说明。默认启动及菜单隐藏设置不变。
