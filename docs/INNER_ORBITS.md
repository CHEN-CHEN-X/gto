> 历史记录（内层数字补齐时期）：本文记录前景/内层数字动画的实现与验证，后继的降速与材质调整见 [SLOW_MATERIAL.md](SLOW_MATERIAL.md)；当前实现见 [README](../README.md)。

# 前景／内层数字运动补齐

用户用橙色箭头标出不动的左侧与下方数字，绿色箭头表示现有视界环与外侧效果可保留。

根因：旧版 diskMask 仅覆盖外圈，内层直接混合原图，原图上的数字当然静止。本次给前景和内层单独增加动画层，不再把这些区域作为原图直接显示。

## 实现

- 旧版视界环／外环采样公式保留。
- 新增 innerDigits：连续角度驱动的二进制 SDF 字形与细轨道线，30 秒完整一圈；数字编号与环位置稳定，使用取模避免角度接缝跳字。
- 原图在新增层只保留低亮度的模糊光照底层，主体数字由 Shader 生成。新增数字的排列与原静态图不逐字对应；这是为消除静止数字并避免直接拉伸原图造成模糊所作的实现调整。
- 轨道之间错开相位并控制间距、亮度。中心黑洞保护和原有视界环遮罩保留。
- 没有视频、帧序列或新纹理；运行时新增的是 Shader 算术与取样。

## 验证

GPU 对比不同时间，用户标注对应区域的平均 RGB 变化（0～255）：
{
  "left_foreground": 25.84279388083736,
  "lower_foreground": 26.58963675213675,
  "right_inner": 21.7654651638582
}

与变更前 Shader 在相同时刻对照，绿色标记附近两处抽样区域平均差：
{
  "corona_top": 0.0,
  "outer_right": 0.0
}

上述两处差异为 0；并不代表所有过渡边界都逐像素不变。
完整 60 秒周期闭合、原 2/30/60/120/256 秒时间边界连续性测试通过。
Gradle build / reobfJar / verifyJarSize 通过；最终 JAR 10,154,034 字节（9.683641 MiB），小于 10 MiB。

## 文件与复查

修改：artwork_flow.fsh、scripts/preview_artwork.py、文档。
备份：backups/inner-orbits-20261106-184210/。
GPU 动画：效果图/artwork_flow_preview.gif；整圈 10 倍速：效果图/orbit_full_turn_10x.gif。

```powershell
python scripts/preview_artwork.py --animate --orbit-cycle --baseline-shader 'backups/inner-orbits-20261106-184210/src/main/resources/assets/gto_menu/shaders/artwork_flow.fsh'
```
