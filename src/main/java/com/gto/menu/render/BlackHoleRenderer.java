package com.gto.menu.render;

import com.gto.menu.GTOMenuMod;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 黑洞渲染器（多 pass HDR 管线）。
 *
 *   Scene（光线追踪，RGBA16F × 0.75 分辨率）
 *     → Trail（帧反馈：max(本帧, 上帧×衰减)，数字能量尾迹）
 *     → Bloom（亮部软提取 + 4 次可分离高斯模糊）
 *     → Composite（原生分辨率合成进 MC 主 FBO：ACES/sRGB/暗角/标题区压暗）
 *
 * 相机为固定轨道相机，参数全部来自 RenderConfig。
 */
public final class BlackHoleRenderer {

    private static int vao;

    // 4 个着色器程序
    private static int sceneProgram;
    private static int trailProgram;
    private static int bloomProgram;
    private static int compositeProgram;

    // HDR 浮点 FBO
    private static Fbo sceneFbo;
    private static Fbo[] trailFbo = new Fbo[2];
    private static Fbo[] bloomFbo = new Fbo[2];
    private static int trailIndex = 0;

    private static int fboW, fboH;
    private static long startNanos;
    private static boolean initialized, active;

    // scene uniforms
    private static int sResolution, sTime, sCamPos, sCamMat, sTanFov, sStep;
    private static int sDiskInner, sDiskOuter, sDiskBright, sBeaming, sOrbitDir, sTimeScale;
    private static int sDigitA, sDigitR, sSpiral, sStarInt, sStarDen;
    private static int sRingStr, sGasStr, sRibbonGain;

    // pass uniforms
    private static int tCur, tPrev, tDecay;
    private static int bTex, bTexel, bDir, bThreshold;
    private static int cTrail, cBloom, cRes, cTime, cExposure, cBloomStr, cVig,
                       cBottomStart, cBottomEnd, cBottomDark;

    private static long trailFrameNanos;
    private static long lastFrameNanos;
    private static float emaFrameMs = 16.7f;
    private static int frameCount;

    private BlackHoleRenderer() {}

    public static boolean isActive() { return active || ReferenceOrbitRenderer.isActive(); }

    public static void render() {
        if (RenderConfig.ARTWORK_FLOW && ReferenceOrbitRenderer.render()) return;
        if (!initialized) { initialized = true; init(); }
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getWidth();
        int screenH = mc.getWindow().getHeight();
        float time = (float) ((System.nanoTime() - startNanos) / 1_000_000_000.0);

        int w = Math.max(1, Math.round(screenW * RenderConfig.RENDER_SCALE));
        int h = Math.max(1, Math.round(screenH * RenderConfig.RENDER_SCALE));

        // 保存 GL 状态
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        int texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        ensureTargets(w, h);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_BLEND);
        GL30.glBindVertexArray(vao);

        int prev = trailIndex;
        int next = 1 - trailIndex;

        // ============ Pass 1：Scene 光线追踪 ============
        bindTarget(sceneFbo, w, h);
        GL20.glUseProgram(sceneProgram);
        setSceneUniforms(w, h, time);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        // ============ Pass 2：Trail 帧反馈 ============
        bindTarget(trailFbo[next], w, h);
        GL20.glUseProgram(trailProgram);
        bindTexture(0, sceneFbo.tex);
        bindTexture(1, trailFbo[prev].tex);
        GL20.glUniform1i(tCur, 0);
        GL20.glUniform1i(tPrev, 1);
        long trailNow = System.nanoTime();
        double dt = trailFrameNanos == 0 ? 0.0 : (trailNow - trailFrameNanos) / 1_000_000_000.0;
        float decay = dt <= 0.0 || dt > 0.5 ? 0f
            : (float) Math.pow(RenderConfig.TRAIL_DECAY, dt * 60.0);
        trailFrameNanos = trailNow;
        GL20.glUniform1f(tDecay, decay);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        // ============ Pass 3：Bloom（亮部提取 + H） ============
        GL20.glUseProgram(bloomProgram);
        runBloom(bloomFbo[0], trailFbo[next].tex, 1f, 0f, w, h, RenderConfig.BLOOM_THRESHOLD);
        // V
        runBloom(bloomFbo[1], bloomFbo[0].tex, 0f, 1f, bloomFbo[0].width, bloomFbo[0].height, 0f);
        // H（更宽）
        runBloom(bloomFbo[0], bloomFbo[1].tex, 1f, 0f, bloomFbo[1].width, bloomFbo[1].height, 0f);
        // V
        runBloom(bloomFbo[1], bloomFbo[0].tex, 0f, 1f, bloomFbo[0].width, bloomFbo[0].height, 0f);

        // ============ Pass 4：Composite → MC 主 FBO（原生分辨率） ============
        mc.getMainRenderTarget().bindWrite(false);
        GL11.glViewport(0, 0, screenW, screenH);
        GL20.glUseProgram(compositeProgram);
        bindTexture(0, trailFbo[next].tex);
        bindTexture(1, bloomFbo[1].tex);
        GL20.glUniform1i(cTrail, 0);
        GL20.glUniform1i(cBloom, 1);
        GL20.glUniform2f(cRes, screenW, screenH);
        GL20.glUniform1f(cTime, time);
        GL20.glUniform1f(cExposure, RenderConfig.EXPOSURE);
        GL20.glUniform1f(cBloomStr, RenderConfig.BLOOM_STRENGTH);
        GL20.glUniform1f(cVig, RenderConfig.VIGNETTE);
        GL20.glUniform1f(cBottomStart, RenderConfig.BOTTOM_FADE_START);
        GL20.glUniform1f(cBottomEnd, RenderConfig.BOTTOM_FADE_END);
        GL20.glUniform1f(cBottomDark, RenderConfig.BOTTOM_FADE_DARK);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        trailIndex = next;

        // 解绑纹理并把活动纹理单元复位到 0，避免污染后续 MC GUI 绘制
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture1);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture0);
        GL13.glActiveTexture(activeTexture);

        // 恢复 GL 状态
        GL30.glBindVertexArray(prevVao);
        GL20.glUseProgram(prevProgram);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        GL11.glDepthMask(depthWrite);
        if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        if (depthTest) GL11.glEnable(GL11.GL_DEPTH_TEST);
        if (blend) GL11.glEnable(GL11.GL_BLEND);

        reportFps(w, h);
    }

    private static void runBloom(Fbo dst, int srcTex, float dx, float dy,
                                 int w, int h, float threshold) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, dst.id);
        GL11.glViewport(0, 0, dst.width, dst.height);
        bindTexture(0, srcTex);
        GL20.glUniform1i(bTex, 0);
        GL20.glUniform2f(bTexel, 1f / w, 1f / h);
        GL20.glUniform2f(bDir, dx, dy);
        GL20.glUniform1f(bThreshold, threshold);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    private static void bindTarget(Fbo fbo, int w, int h) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo.id);
        GL11.glViewport(0, 0, w, h);
    }

    private static void bindTexture(int unit, int tex) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
    }

    private static void setSceneUniforms(int w, int h, float time) {
        GL20.glUniform2f(sResolution, w, h);
        GL20.glUniform1f(sTime, time);

        // 轨道相机
        float az = RenderConfig.CAM_AZIMUTH + time * RenderConfig.CAM_AUTO_SPIN;
        float pol = RenderConfig.CAM_POLAR;
        float dist = RenderConfig.CAM_DISTANCE;
        float sinP = (float) Math.sin(pol), cosP = (float) Math.cos(pol);
        float px = dist * sinP * (float) Math.cos(az);
        float py = dist * cosP;
        float pz = dist * sinP * (float) Math.sin(az);
        float fx = -px / dist, fy = -py / dist, fz = -pz / dist;
        float rx = -fz, rz = fx;
        float rLen = (float) Math.sqrt(rx * rx + rz * rz);
        rx /= rLen; rz /= rLen;
        float ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;

        float cr = (float) Math.cos(RenderConfig.CAM_ROLL);
        float sr = (float) Math.sin(RenderConfig.CAM_ROLL);
        float rrx = cr * rx + sr * ux, rry = sr * uy, rrz = cr * rz + sr * uz;
        float uux = cr * ux - sr * rx, uuy = cr * uy, uuz = cr * uz - sr * rz;
        GL20.glUniform3f(sCamPos, px, py, pz);
        GL20.glUniformMatrix3fv(sCamMat, false, new float[] {
            rrx, rry, rrz,
            uux, uuy, uuz,
            fx, fy, fz
        });
        GL20.glUniform1f(sTanFov,
            (float) Math.tan(Math.toRadians(RenderConfig.CAM_FOV_DEG) * 0.5));
        GL20.glUniform1f(sStep, RenderConfig.RAY_STEP_SCALE);

        GL20.glUniform1f(sDiskInner, RenderConfig.DISK_INNER);
        GL20.glUniform1f(sDiskOuter, RenderConfig.DISK_OUTER);
        GL20.glUniform1f(sDiskBright, RenderConfig.DISK_BRIGHTNESS);
        GL20.glUniform1f(sBeaming, RenderConfig.BEAMING);
        GL20.glUniform1f(sOrbitDir, RenderConfig.ORBIT_DIR);
        GL20.glUniform1f(sTimeScale, RenderConfig.TIME_SCALE);

        GL20.glUniform1f(sDigitA, RenderConfig.DIGIT_DENSITY);
        GL20.glUniform1f(sDigitR, RenderConfig.DIGIT_DENSITY_R);
        GL20.glUniform1f(sSpiral, RenderConfig.SPIRAL_TURNS);
        GL20.glUniform1f(sRingStr, RenderConfig.RING_STRENGTH);
        GL20.glUniform1f(sGasStr, RenderConfig.GAS_STRENGTH);
        GL20.glUniform1f(sRibbonGain, RenderConfig.RIBBON_GAIN);

        GL20.glUniform1f(sStarInt, RenderConfig.STAR_INTENSITY);
        GL20.glUniform1f(sStarDen, RenderConfig.STAR_DENSITY);
    }

    // ==================== FBO 管理 ====================

    private static void ensureTargets(int w, int h) {
        if (sceneFbo != null && w == fboW && h == fboH) return;
        deleteTargets();
        fboW = w; fboH = h;
        sceneFbo = Fbo.create(w, h);
        trailFbo[0] = Fbo.create(w, h);
        trailFbo[1] = Fbo.create(w, h);
        bloomFbo[0] = Fbo.create(Math.max(1, w / 2), Math.max(1, h / 2));
        bloomFbo[1] = Fbo.create(Math.max(1, w / 2), Math.max(1, h / 2));
        trailIndex = 0;
        trailFrameNanos = 0;
    }

    private static void deleteTargets() {
        if (sceneFbo != null) { sceneFbo.delete(); sceneFbo = null; }
        for (int i = 0; i < 2; i++) {
            if (trailFbo[i] != null) { trailFbo[i].delete(); trailFbo[i] = null; }
            if (bloomFbo[i] != null) { bloomFbo[i].delete(); bloomFbo[i] = null; }
        }
    }

    /** RGBA16F 浮点帧缓冲（HDR 累积需要） */
    private static final class Fbo {
        final int id, tex, width, height;
        Fbo(int id, int tex, int width, int height) {
            this.id = id; this.tex = tex; this.width = width; this.height = height;
        }

        static Fbo create(int w, int h) {
            int id = GL30.glGenFramebuffers();
            int tex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, w, h, 0,
                GL11.GL_RGBA, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, id);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, tex, 0);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                GTOMenuMod.LOGGER.error("GTO Menu: HDR FBO 不完整 status=0x{}", Integer.toHexString(status));
            }
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            return new Fbo(id, tex, w, h);
        }

        void delete() {
            GL30.glDeleteFramebuffers(id);
            GL11.glDeleteTextures(tex);
        }
    }

    // ==================== 初始化 ====================

    private static void reportFps(int w, int h) {
        long now = System.nanoTime();
        if (lastFrameNanos != 0) {
            float ms = (now - lastFrameNanos) / 1_000_000.0f;
            emaFrameMs += (ms - emaFrameMs) * 0.05f;
        }
        lastFrameNanos = now;
        if (++frameCount % 120 == 0) {
            GTOMenuMod.LOGGER.info("GTO Menu: 管线 {} ms/帧 (约 {} FPS), 内部 {}x{}",
                String.format("%.1f", emaFrameMs),
                Math.round(1000.0f / Math.max(emaFrameMs, 0.01f)), w, h);
        }
    }

    private static void init() {
        startNanos = System.nanoTime();
        try {
            String vsh = readResource("/assets/gto_menu/shaders/fsquad.vsh");

            sceneProgram     = link(vsh, readResource("/assets/gto_menu/shaders/blackhole.fsh"), "scene");
            trailProgram     = link(vsh, readResource("/assets/gto_menu/shaders/trail.fsh"),     "trail");
            bloomProgram     = link(vsh, readResource("/assets/gto_menu/shaders/bloom.fsh"),     "bloom");
            compositeProgram = link(vsh, readResource("/assets/gto_menu/shaders/composite.fsh"), "composite");
            if (sceneProgram == 0 || trailProgram == 0 || bloomProgram == 0 || compositeProgram == 0) {
                return;
            }

            sResolution = loc(sceneProgram, "uResolution");
            sTime       = loc(sceneProgram, "uTime");
            sCamPos     = loc(sceneProgram, "uCamPos");
            sCamMat     = loc(sceneProgram, "uCamMat");
            sTanFov     = loc(sceneProgram, "uTanHalfFov");
            sStep       = loc(sceneProgram, "uStepScale");
            sDiskInner  = loc(sceneProgram, "uDiskInner");
            sDiskOuter  = loc(sceneProgram, "uDiskOuter");
            sDiskBright = loc(sceneProgram, "uDiskBrightness");
            sBeaming    = loc(sceneProgram, "uBeaming");
            sOrbitDir   = loc(sceneProgram, "uOrbitDir");
            sTimeScale  = loc(sceneProgram, "uTimeScale");
            sDigitA     = loc(sceneProgram, "uDigitDensity");
            sDigitR     = loc(sceneProgram, "uDigitDensityR");
            sSpiral     = loc(sceneProgram, "uSpiralTurns");
            sRingStr    = loc(sceneProgram, "uRingStrength");
            sGasStr     = loc(sceneProgram, "uGasStrength");
            sRibbonGain = loc(sceneProgram, "uRibbonGain");
            sStarInt    = loc(sceneProgram, "uStarIntensity");
            sStarDen    = loc(sceneProgram, "uStarDensity");

            tCur   = loc(trailProgram, "uCur");
            tPrev  = loc(trailProgram, "uPrev");
            tDecay = loc(trailProgram, "uDecay");

            bTex       = loc(bloomProgram, "uTex");
            bTexel     = loc(bloomProgram, "uTexel");
            bDir       = loc(bloomProgram, "uDir");
            bThreshold = loc(bloomProgram, "uThreshold");

            cTrail       = loc(compositeProgram, "uTrail");
            cBloom       = loc(compositeProgram, "uBloom");
            cRes         = loc(compositeProgram, "uResolution");
            cTime        = loc(compositeProgram, "uTime");
            cExposure    = loc(compositeProgram, "uExposure");
            cBloomStr    = loc(compositeProgram, "uBloomStrength");
            cVig         = loc(compositeProgram, "uVignette");
            cBottomStart = loc(compositeProgram, "uBottomStart");
            cBottomEnd   = loc(compositeProgram, "uBottomEnd");
            cBottomDark  = loc(compositeProgram, "uBottomDark");

            vao = GL30.glGenVertexArrays();
            active = true;
            GTOMenuMod.LOGGER.info("GTO Menu: 黑洞多 pass HDR 管线初始化成功");
        } catch (Exception e) {
            GTOMenuMod.LOGGER.error("GTO Menu: pipeline init failed", e);
            active = false;
        }
    }

    private static int link(String vshSrc, String fshSrc, String name) {
        int vs = compile(GL20.GL_VERTEX_SHADER, vshSrc, name + ".vsh");
        int fs = compile(GL20.GL_FRAGMENT_SHADER, fshSrc, name + ".fsh");
        if (vs == 0 || fs == 0) return 0;
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL20.glLinkProgram(p);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            GTOMenuMod.LOGGER.error("GTO Menu: {} link failed: {}", name, GL20.glGetProgramInfoLog(p));
            GL20.glDeleteProgram(p);
            return 0;
        }
        return p;
    }

    private static int loc(int program, String name) {
        int l = GL20.glGetUniformLocation(program, name);
        if (l < 0) GTOMenuMod.LOGGER.warn("GTO Menu: uniform '{}' not found", name);
        return l;
    }

    private static String readResource(String path) throws Exception {
        try (InputStream in = BlackHoleRenderer.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Resource not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int compile(int type, String src, String name) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, src);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            GTOMenuMod.LOGGER.error("GTO Menu: {} compile failed: {}", name, GL20.glGetShaderInfoLog(id));
            GL20.glDeleteShader(id);
            return 0;
        }
        return id;
    }
}
