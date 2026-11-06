package com.gto.menu.mixin;

import com.gto.menu.render.BlackHoleRenderer;
import com.gto.menu.render.RenderConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TitleScreen 渲染拦截器。
 *
 * 渲染分层（从下到上）：
 *   1. HEAD  → 黑洞着色器背景（替代全景图）
 *   2. Logo 之后 → 电压方块（在按钮后面）
 *   3. 按钮等原版 UI（不动）
 *
 * @Redirect 吞掉 panorama.render() 和 PANORAMA_OVERLAY blit()。
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    private static final ResourceLocation PANORAMA_OVERLAY =
            new ResourceLocation("textures/gui/title/background/panorama_overlay.png");

    /** 游戏 tick 计数器，用于平滑动画 */
    private static float gto_menu$tickTime = 0.0f;

    /** HEAD：绘制黑洞着色器背景 */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void gto_menu$drawBackground(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                         float partialTick, CallbackInfo ci) {
        BlackHoleRenderer.render();
        if (RenderConfig.EFFECT_ONLY && BlackHoleRenderer.isActive()) ci.cancel();
    }

    /**
     * 电压方块渲染暂时停用：先完成黑洞本体，后续用世界空间版本替换旧的 GUI 占位实现。
     */
    @Inject(
            method = "render",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V",
                     shift = At.Shift.AFTER)
    )
    private void gto_menu$drawBlocks(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                     float partialTick, CallbackInfo ci) {
        // TODO: 黑洞效果定稿后，在此接回 14 阶电压方块（世界空间、与盘绑定）
    }

    /** 吞掉全景图 */
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/renderer/PanoramaRenderer;render(FF)V")
    )
    private void gto_menu$skipPanorama(PanoramaRenderer instance, float partialTick, float f) {
        if (!BlackHoleRenderer.isActive()) {
            instance.render(partialTick, f);
        }
    }

    /** 吞掉全景图叠加层 */
    @Redirect(
            method = "render",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V")
    )
    private void gto_menu$skipPanoramaOverlay(GuiGraphics instance, ResourceLocation texture,
                                              int x, int y, int width, int height,
                                              float u, float v,
                                              int uWidth, int vHeight,
                                              int textureWidth, int textureHeight) {
        if (!BlackHoleRenderer.isActive() || !texture.equals(PANORAMA_OVERLAY)) {
            instance.blit(texture, x, y, width, height, u, v, uWidth, vHeight, textureWidth, textureHeight);
        }
    }
}
