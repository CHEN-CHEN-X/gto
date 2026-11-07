package com.gto.menu.client;

import com.gto.menu.GTOMenuMod;
import com.gto.menu.render.BlackHoleRenderer;
import com.gto.menu.render.RenderConfig;
import com.gto.menu.render.VoltageBlockRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only Forge hooks for the effect-only title screen. */
@Mod.EventBusSubscriber(modid = GTOMenuMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MenuRenderEvents {

    private MenuRenderEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderTitleScreen(ScreenEvent.Render.Pre event) {
        if (!RenderConfig.EFFECT_ONLY || !(event.getScreen() instanceof TitleScreen)) return;

        // Drain queued GUI work before the renderer writes to Minecraft's target.
        event.getGuiGraphics().flush();
        BlackHoleRenderer.render();
        // Leave vanilla rendering and input available if both renderers fail.
        if (BlackHoleRenderer.isActive()) {
            VoltageBlockRenderer.render(event.getGuiGraphics(), event.getScreen().width, event.getScreen().height);
            PlanetMenu.render(event.getScreen(),event.getGuiGraphics(),event.getMouseX(),event.getMouseY());
            event.setCanceled(true);
        }
    }

    private static boolean hidesControls(Screen screen) {
        return RenderConfig.EFFECT_ONLY && screen instanceof TitleScreen && BlackHoleRenderer.isActive();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void preventHiddenMouseActions(ScreenEvent.MouseButtonPressed.Pre event) {
        if (hidesControls(event.getScreen())) {
            PlanetMenu.click(event.getScreen(),event.getMouseX(),event.getMouseY(),event.getButton());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void preventHiddenKeyboardActions(ScreenEvent.KeyPressed.Pre event) {
        if (hidesControls(event.getScreen())) {
            PlanetMenu.key(event.getScreen(),event.getKeyCode());
            event.setCanceled(true);
        }
    }
}
