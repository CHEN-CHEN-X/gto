package com.gto.menu.render;

import com.gto.menu.GTOMenuMod;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

/** One source texture, procedural flow, no frame cache or offscreen targets. */
public final class ArtworkFlowRenderer {
    private static boolean attempted, active;
    private static int program, vao, texture, width, height;
    private static int resolutionLoc, aspectLoc, angleLoc, strengthLoc;
    private static long start;

    private ArtworkFlowRenderer() {}
    public static boolean isActive() { return active; }

    public static boolean render() {
        if (attempted && !active) return false;
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean srgb = GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            if (!attempted) {
                attempted = true;
                init();
            }
            Minecraft mc = Minecraft.getInstance();
            int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
            if (w <= 0 || h <= 0) return true;
            // Bind only draw target; leave the caller's read framebuffer intact.
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mc.getMainRenderTarget().frameBufferId);
            GL11.glViewport(0, 0, w, h);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            GL11.glDepthMask(false);
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL20.glUniform2f(resolutionLoc, w, h);
            GL20.glUniform1f(aspectLoc, (float) width / height);
            // Reduce a completed revolution in double precision, never reset elapsed
            // time part way through a turn. sin/cos are continuous at 2*pi.
            double seconds = (System.nanoTime()-start)/1.0e9;
            double turns = seconds * RenderConfig.ARTWORK_FLOW_STRENGTH / 60.0;
            float angle = (float) ((turns - Math.floor(turns)) * Math.PI * 2.0);
            GL20.glUniform1f(angleLoc, angle);
            GL20.glUniform1f(strengthLoc, RenderConfig.ARTWORK_FLOW_STRENGTH);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            return true;
        } catch (Exception e) {
            active = false;
            release();
            GTOMenuMod.LOGGER.error("GTO artwork flow failed; reverting to procedural renderer", e);
            return false;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
            GL13.glActiveTexture(prevUnit);
            GL30.glBindVertexArray(prevVao);
            GL20.glUseProgram(prevProgram);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevFbo);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL11.glDepthMask(depthWrite);
            setEnabled(GL11.GL_DEPTH_TEST, depth);
            setEnabled(GL11.GL_BLEND, blend);
            setEnabled(GL11.GL_SCISSOR_TEST, scissor);
            setEnabled(GL11.GL_CULL_FACE, cull);
            setEnabled(GL30.GL_FRAMEBUFFER_SRGB, srgb);
        }
    }

    private static void init() throws Exception {
        int vs = 0, fs = 0;
        try {
            vs = compile(GL20.GL_VERTEX_SHADER, read("shaders/fsquad.vsh"));
            fs = compile(GL20.GL_FRAGMENT_SHADER, read("shaders/artwork_flow.fsh"));
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vs);
            GL20.glAttachShader(program, fs);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
                throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
        } finally {
            if (vs != 0) GL20.glDeleteShader(vs);
            if (fs != 0) GL20.glDeleteShader(fs);
        }
        byte[] encoded;
        try (InputStream in = resource("textures/reference.png")) { encoded = in.readAllBytes(); }
        ByteBuffer input = BufferUtils.createByteBuffer(encoded.length);
        input.put(encoded).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer x = stack.mallocInt(1), y = stack.mallocInt(1), channels = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(input, x, y, channels, 4);
            if (pixels == null) throw new IllegalStateException(STBImage.stbi_failure_reason());
            try {
                width = x.get(0); height = y.get(0);
                texture = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height,
                    0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            } finally { STBImage.stbi_image_free(pixels); }
        }
        vao = GL30.glGenVertexArrays();
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "uArtwork"), 0);
        resolutionLoc = GL20.glGetUniformLocation(program, "uResolution");
        aspectLoc = GL20.glGetUniformLocation(program, "uSourceAspect");
        angleLoc = GL20.glGetUniformLocation(program, "uOrbitAngle");
        strengthLoc = GL20.glGetUniformLocation(program, "uFlowStrength");
        start = System.nanoTime();
        active = true;
        GTOMenuMod.LOGGER.info("GTO artwork flow ready: {}x{}, {} texture bytes, no frame cache/FBOs", width, height, width*height*4);
    }

    private static void release() {
        if (program != 0) GL20.glDeleteProgram(program);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (texture != 0) GL11.glDeleteTextures(texture);
        program = vao = texture = 0;
    }
    private static void setEnabled(int cap, boolean enabled) {
        if (enabled) GL11.glEnable(cap); else GL11.glDisable(cap);
    }
    private static InputStream resource(String path) {
        InputStream in = ArtworkFlowRenderer.class.getResourceAsStream("/assets/gto_menu/"+path);
        if (in == null) throw new IllegalStateException("Missing artwork resource: "+path);
        return in;
    }
    private static String read(String path) throws Exception {
        try (InputStream in = resource(path)) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }
    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String error = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(error);
        }
        return shader;
    }
}
