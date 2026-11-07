package com.gto.menu;

import net.minecraftforge.common.ForgeConfigSpec;
import java.net.URI;

/** Client-side menu options, saved as config/gto_menu-client.toml. */
public final class MenuConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> WIKI_URL;
    static {
        ForgeConfigSpec.Builder builder=new ForgeConfigSpec.Builder();
        builder.comment("Menu links. Changes take effect after the client configuration reloads.").push("links");
        WIKI_URL=builder.comment("Website opened by the center icon. Use a complete http or https URL.")
                .define("wikiUrl","https://gtodyssey.com/",MenuConfig::isWebUrl);
        builder.pop();SPEC=builder.build();
    }
    private MenuConfig() {}
    private static boolean isWebUrl(Object value) {
        if(!(value instanceof String text))return false;
        try {
            URI uri=URI.create(text);
            return uri.getHost()!=null && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch(IllegalArgumentException e) {return false;}
    }
}
