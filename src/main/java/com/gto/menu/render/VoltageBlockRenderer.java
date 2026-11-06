package com.gto.menu.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 14 阶电压方块轨道渲染器（太阳系式排布）。
 *
 * 方块在倾斜吸积盘的同一平面上环绕黑洞，支持多轨道多方块。
 * 所有参数从 RenderConfig 读取，坐标系与 blackhole.fsh 一致。
 */
public final class VoltageBlockRenderer {

    private static ItemStack[] tierItems;

    private VoltageBlockRenderer() {}

    private static void initItems() {
        tierItems = new ItemStack[14];
        tierItems[0]  = new ItemStack(Items.RED_CONCRETE);      // ULV
        tierItems[1]  = new ItemStack(Items.PINK_CONCRETE);     // LV
        tierItems[2]  = new ItemStack(Items.BLUE_CONCRETE);     // MV
        tierItems[3]  = new ItemStack(Items.ORANGE_CONCRETE);   // HV
        tierItems[4]  = new ItemStack(Items.MAGENTA_CONCRETE);  // EV
        tierItems[5]  = new ItemStack(Items.PURPLE_CONCRETE);   // IV
        tierItems[6]  = new ItemStack(Items.RED_WOOL);          // LuV
        tierItems[7]  = new ItemStack(Items.PURPLE_WOOL);       // ZPM
        tierItems[8]  = new ItemStack(Items.ORANGE_WOOL);       // UV
        tierItems[9]  = new ItemStack(Items.MAGENTA_WOOL);      // UHV
        tierItems[10] = new ItemStack(Items.PURPLE_WOOL);       // UEV
        tierItems[11] = new ItemStack(Items.YELLOW_WOOL);       // UIV
        tierItems[12] = new ItemStack(Items.RED_WOOL);          // UXV
        tierItems[13] = new ItemStack(Items.WHITE_WOOL);        // MAX
    }

    public static void render(GuiGraphics graphics, int screenW, int screenH, float time) {
        if (tierItems == null) initItems();

        List<BlockInstance> blocks = new ArrayList<>(20);
        for (int orbit = 0; orbit < RenderConfig.ORBITS.length; orbit++) {
            float r = RenderConfig.ORBITS[orbit][0];
            float speed = RenderConfig.ORBITS[orbit][1];
            int count = (int) RenderConfig.ORBITS[orbit][2];
            for (int slot = 0; slot < count; slot++) {
                float phase = (float) slot / count * 6.28318f;
                BlockInstance b = computeBlock(orbit, r, speed, phase, time, screenW, screenH);
                if (b != null) blocks.add(b);
            }
        }
        blocks.sort(Comparator.comparingDouble((BlockInstance b) -> b.depth).reversed());
        for (BlockInstance b : blocks) {
            renderBlock(graphics, b, screenW, screenH);
        }
    }

    private static BlockInstance computeBlock(int tier, float baseR, float speed,
                                               float phase, float t, int screenW, int screenH) {
        // 吸入循环
        float cycleTime = RenderConfig.BLOCK_CYCLE_BASE + tier * RenderConfig.BLOCK_CYCLE_STEP;
        float cyc = frac(t / cycleTime + phase * 0.618f);
        float fall = (float) Math.pow(cyc, 2.8);
        float r = lerp(baseR, RenderConfig.RS * 1.05f, fall);
        float a = phase + t * speed * (1.0f + 3.0f * fall);

        // 盘局部坐标（倾斜平面）
        float diskX = r * (float) Math.cos(a);
        float diskY = r * (float) Math.sin(a);

        // 深度：sin(a)>0 为近侧
        float depth = -r * (float) Math.sin(a) * RenderConfig.SIN_TILT;

        // 屏幕 uv 坐标（与 shader 一致）
        float uvX = diskX;
        float uvY = diskY * RenderConfig.COS_TILT;

        // 被黑洞遮挡：在远侧且投影落入视界椭圆
        if (depth > 0.0f) {
            float projR = (float) Math.sqrt(uvX * uvX + uvY * uvY);
            if (projR < RenderConfig.RS * 0.95f) return null;
        }

        // 坠入视界
        if (r < RenderConfig.RS * 1.08f) return null;

        // 屏幕像素坐标
        float px = uvX * screenH + screenW / 2.0f;
        float py = -uvY * screenH + screenH / 2.0f;

        // 透视缩放
        float scale = RenderConfig.BLOCK_BASE_SCALE * (1.0f + depth * RenderConfig.BLOCK_DEPTH_SCALE);

        // 潮汐拉伸
        float stretch = 1.0f + RenderConfig.BLOCK_TIDAL_STRENGTH
                * smoothstep(baseR * 0.8f, RenderConfig.RS * 1.1f, r);

        // 消隐
        float fade = smoothstep(0.0f, RenderConfig.BLOCK_FADE_IN_END, cyc)
                   * smoothstep(1.0f, RenderConfig.BLOCK_FADE_OUT_START, cyc);

        return new BlockInstance(tier, px, py, scale, stretch, fade, depth, a);
    }

    private static void renderBlock(GuiGraphics graphics, BlockInstance b,
                                     int screenW, int screenH) {
        if (b.fade < 0.01f) return;
        if (b.px < -50 || b.px > screenW + 50 || b.py < -50 || b.py > screenH + 50) return;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(b.px, b.py, 0);
        pose.scale(b.scale * b.stretch, b.scale, b.scale);
        float spin = b.angle * RenderConfig.BLOCK_SPIN_SPEED + b.tier * 7.0f;
        pose.mulPose(new org.joml.Quaternionf().rotateY(spin));
        graphics.renderItem(tierItems[b.tier], -8, -8);
        pose.popPose();
    }

    private static float frac(float x) { return x - (float) Math.floor(x); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float smoothstep(float e0, float e1, float x) {
        float t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    private static final class BlockInstance {
        final int tier;
        final float px, py, scale, stretch, fade, depth, angle;

        BlockInstance(int tier, float px, float py, float scale, float stretch,
                      float fade, float depth, float angle) {
            this.tier = tier; this.px = px; this.py = py;
            this.scale = scale; this.stretch = stretch; this.fade = fade;
            this.depth = depth; this.angle = angle;
        }
    }
}
