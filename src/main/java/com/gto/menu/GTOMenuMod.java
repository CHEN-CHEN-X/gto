package com.gto.menu;

import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GregTech Odyssey Menu Mod
 *
 * 自定义黑洞主菜单模组（基于 FancyMenu）。
 *
 * 主题内容说明：
 * - 菜单的实际界面由 FancyMenu 加载，布局文件位于 config/fancymenu/customization/title_screen_layout.txt
 * - 视觉核心是内联在布局中的 GLSL 着色器：纯黑宇宙背景 + 三色星辰闪烁、
 *   0/1 二进制黑洞 + 吸积盘 + 引力透镜、14 阶电压方块螺旋漏斗轨道
 * - 本类仅承担模组入口标识职责，不包含游戏逻辑（界面渲染全部由 FancyMenu/Konkrete 负责）
 *
 * 版本号由 Gradle 统一管理（gradle.properties）。
 */
@Mod(GTOMenuMod.MOD_ID)
public class GTOMenuMod {

    /** 模组唯一标识，必须与 gradle.properties 的 mod_id、mods.toml 的 modId 保持一致 */
    public static final String MOD_ID = "gto_menu";

    /** 模组日志记录器 */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public GTOMenuMod() {
        LOGGER.info("GregTech Odyssey Menu initialized - Black Hole Shader Active");
    }
}
