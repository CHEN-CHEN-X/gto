package com.gto.menu;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client title-screen effect. Forge screen events select when to render;
 * shader programs and reference-derived orbit assets live in render/.
 */
@Mod(GTOMenuMod.MOD_ID)
public class GTOMenuMod {

    /** 模组唯一标识，必须与 gradle.properties 的 mod_id、mods.toml 的 modId 保持一致 */
    public static final String MOD_ID = "gto_menu";

    /** 模组日志记录器 */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public GTOMenuMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT,MenuConfig.SPEC);
    }
}
