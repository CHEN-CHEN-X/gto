package com.gto.menu.render;

import com.gto.menu.GTOMenuMod;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/** Original glyph sprites on closed orbits; stationary lens/glow plate. */
public final class ReferenceOrbitRenderer {
    private static boolean attempted, active;
    private static int plateProgram, glyphProgram, plateVao, glyphVao, vbo;
    private static final int[] textures = new int[3];
    private static int width, height, glyphCount;
    private static int plateResolution, plateAspect, glyphResolution, glyphAspect, glyphAngle, glyphOpacity;
    private static long start;

    private ReferenceOrbitRenderer() {}
    public static boolean isActive() { return active; }

    public static boolean render() {
        if (attempted && !active) return false;
        int prevProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int prevFbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] oldTextures=new int[3], viewport=new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        for(int i=0;i<3;i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0+i);
            oldTextures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend=GL11.glIsEnabled(GL11.GL_BLEND);
        boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean srgb=GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB), depthWrite=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int eqRgb=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), eqAlpha=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        try {
            if(!attempted) { attempted=true; init(); }
            Minecraft mc=Minecraft.getInstance();
            int w=mc.getWindow().getWidth(),h=mc.getWindow().getHeight();
            if(w<=0||h<=0)return true;
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,mc.getMainRenderTarget().frameBufferId);
            GL11.glViewport(0,0,w,h);
            enabled(GL11.GL_DEPTH_TEST,false);enabled(GL11.GL_BLEND,false);
            enabled(GL11.GL_SCISSOR_TEST,false);enabled(GL11.GL_CULL_FACE,false);
            enabled(GL30.GL_FRAMEBUFFER_SRGB,false);GL11.glDepthMask(false);
            for(int i=0;i<3;i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+i);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[i]);
            }
            GL20.glUseProgram(plateProgram);GL30.glBindVertexArray(plateVao);
            GL20.glUniform2f(plateResolution,w,h);GL20.glUniform1f(plateAspect,(float)width/height);
            GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            GL20.glUseProgram(glyphProgram);GL30.glBindVertexArray(glyphVao);
            GL20.glUniform2f(glyphResolution,w,h);GL20.glUniform1f(glyphAspect,(float)width/height);
            enabled(GL11.GL_BLEND,true);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD,GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE);
            double seconds=(System.nanoTime()-start)/1.0e9;
            float trail=RenderConfig.ARTWORK_TRAIL_STRENGTH;
            if(trail>0) {
                drawGlyphs(seconds-.16,trail*.4f);
                drawGlyphs(seconds-.08,trail*.6f);
            }
            drawGlyphs(seconds,1.0f);
            return true;
        } catch(Exception e) {
            active=false;release();
            GTOMenuMod.LOGGER.error("GTO reference orbits failed; using procedural fallback",e);
            return false;
        } finally {
            for(int i=0;i<3;i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+i);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,oldTextures[i]);
            }
            GL13.glActiveTexture(prevUnit);GL30.glBindVertexArray(prevVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,prevBuffer);GL20.glUseProgram(prevProgram);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,prevFbo);
            GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);GL11.glDepthMask(depthWrite);
            GL14.glBlendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
            GL20.glBlendEquationSeparate(eqRgb,eqAlpha);
            enabled(GL11.GL_DEPTH_TEST,depth);enabled(GL11.GL_BLEND,blend);
            enabled(GL11.GL_SCISSOR_TEST,scissor);enabled(GL11.GL_CULL_FACE,cull);
            enabled(GL30.GL_FRAMEBUFFER_SRGB,srgb);
        }
    }

    private static void drawGlyphs(double seconds,float opacity) {
        double turns=seconds*RenderConfig.ARTWORK_FLOW_STRENGTH/60.0;
        GL20.glUniform1f(glyphAngle,(float)((turns-Math.floor(turns))*Math.PI*2.0));
        GL20.glUniform1f(glyphOpacity,opacity);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,glyphCount*6);
    }

    private static void init() throws Exception {
        plateProgram=link("fsquad.vsh","orbit_plate.fsh",false);
        glyphProgram=link("glyph_orbit.vsh","glyph_orbit.fsh",true);
        textures[0]=loadTexture("orbit_plate.png",true);
        textures[1]=loadTexture("orbit_glyphs.png",false);
        textures[2]=loadTexture("orbit_lut.png",false);
        plateVao=GL30.glGenVertexArrays();glyphVao=GL30.glGenVertexArrays();
        FloatBuffer vertices;
        try(DataInputStream in=new DataInputStream(new GZIPInputStream(resource("textures/orbit_instances.bin.gz")))) {
            if(in.readInt()!=0x47544F31)throw new IllegalStateException("Unsupported reference-orbit data");
            glyphCount=in.readInt();
            if(glyphCount<1||glyphCount>10000)throw new IllegalStateException("Invalid glyph count: "+glyphCount);
            vertices=BufferUtils.createFloatBuffer(glyphCount*6*16);
            float[] instance=new float[16];
            for(int i=0;i<glyphCount;i++) {
                for(int j=0;j<16;j++) {
                    instance[j]=in.readFloat();
                    if(!Float.isFinite(instance[j]))throw new IllegalStateException("Non-finite orbit parameter");
                }
                for(int corner=0;corner<6;corner++)vertices.put(instance);
            }
            if(in.read()!=-1)throw new IllegalStateException("Unexpected trailing orbit data");
            vertices.flip();
        }
        vbo=GL15.glGenBuffers();GL30.glBindVertexArray(glyphVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,vbo);GL15.glBufferData(GL15.GL_ARRAY_BUFFER,vertices,GL15.GL_STATIC_DRAW);
        for(int i=0;i<4;i++) {
            GL20.glEnableVertexAttribArray(i);
            GL20.glVertexAttribPointer(i,4,GL11.GL_FLOAT,false,64,i*16L);
        }
        GL20.glUseProgram(plateProgram);GL20.glUniform1i(GL20.glGetUniformLocation(plateProgram,"uArtwork"),0);
        plateResolution=GL20.glGetUniformLocation(plateProgram,"uResolution");
        plateAspect=GL20.glGetUniformLocation(plateProgram,"uSourceAspect");
        GL20.glUseProgram(glyphProgram);
        GL20.glUniform1i(GL20.glGetUniformLocation(glyphProgram,"uPlate"),0);
        GL20.glUniform1i(GL20.glGetUniformLocation(glyphProgram,"uGlyphs"),1);
        GL20.glUniform1i(GL20.glGetUniformLocation(glyphProgram,"uOrbitLut"),2);
        glyphResolution=GL20.glGetUniformLocation(glyphProgram,"uResolution");
        glyphAspect=GL20.glGetUniformLocation(glyphProgram,"uSourceAspect");
        glyphAngle=GL20.glGetUniformLocation(glyphProgram,"uOrbitAngle");
        glyphOpacity=GL20.glGetUniformLocation(glyphProgram,"uOpacity");
        start=System.nanoTime();active=true;
        GTOMenuMod.LOGGER.info("GTO reference orbits ready: {} glyphs, {}x{} plate; no video/frame cache/FBO",glyphCount,width,height);
    }

    private static int loadTexture(String name,boolean scene) throws Exception {
        byte[] encoded;
        try(InputStream in=resource("textures/"+name)) { encoded=in.readAllBytes(); }
        ByteBuffer input=BufferUtils.createByteBuffer(encoded.length);input.put(encoded).flip();
        int unpack=GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int pbo=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int tex=0;
        try(MemoryStack stack=MemoryStack.stackPush()) {
            IntBuffer x=stack.mallocInt(1),y=stack.mallocInt(1),c=stack.mallocInt(1);
            ByteBuffer pixels=STBImage.stbi_load_from_memory(input,x,y,c,4);
            if(pixels==null)throw new IllegalStateException(name+": "+STBImage.stbi_failure_reason());
            try {
                if(scene) { width=x.get(0);height=y.get(0); }
                tex=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,tex);
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,x.get(0),y.get(0),0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
                return tex;
            } finally { STBImage.stbi_image_free(pixels); }
        } catch(Exception e) {
            if(tex!=0)GL11.glDeleteTextures(tex);throw e;
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,unpack);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,pbo);
        }
    }

    private static int link(String vertex,String fragment,boolean attributes) throws Exception {
        int vs=0,fs=0,p=0;
        try {
            vs=compile(GL20.GL_VERTEX_SHADER,read("shaders/"+vertex));
            fs=compile(GL20.GL_FRAGMENT_SHADER,read("shaders/"+fragment));
            p=GL20.glCreateProgram();GL20.glAttachShader(p,vs);GL20.glAttachShader(p,fs);
            if(attributes) {
                String[] names={"aOrbit","aMotion","aAtlas","aSize"};
                for(int i=0;i<names.length;i++)GL20.glBindAttribLocation(p,i,names[i]);
            }
            GL20.glLinkProgram(p);
            if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==GL11.GL_FALSE)throw new IllegalStateException(GL20.glGetProgramInfoLog(p));
            return p;
        } catch(Exception e) { if(p!=0)GL20.glDeleteProgram(p);throw e; }
        finally { if(vs!=0)GL20.glDeleteShader(vs);if(fs!=0)GL20.glDeleteShader(fs); }
    }
    private static int compile(int type,String source) {
        int shader=GL20.glCreateShader(type);GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);
        if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==GL11.GL_FALSE) {
            String error=GL20.glGetShaderInfoLog(shader);GL20.glDeleteShader(shader);throw new IllegalStateException(error);
        }
        return shader;
    }
    private static InputStream resource(String path) {
        InputStream in=ReferenceOrbitRenderer.class.getResourceAsStream("/assets/gto_menu/"+path);
        if(in==null)throw new IllegalStateException("Missing reference-orbit resource: "+path);return in;
    }
    private static String read(String path) throws Exception {
        try(InputStream in=resource(path)) { return new String(in.readAllBytes(),StandardCharsets.UTF_8); }
    }
    private static void enabled(int cap,boolean value) { if(value)GL11.glEnable(cap);else GL11.glDisable(cap); }
    private static void release() {
        if(plateProgram!=0)GL20.glDeleteProgram(plateProgram);if(glyphProgram!=0)GL20.glDeleteProgram(glyphProgram);
        if(plateVao!=0)GL30.glDeleteVertexArrays(plateVao);if(glyphVao!=0)GL30.glDeleteVertexArrays(glyphVao);
        if(vbo!=0)GL15.glDeleteBuffers(vbo);
        for(int i=0;i<textures.length;i++) { if(textures[i]!=0)GL11.glDeleteTextures(textures[i]);textures[i]=0; }
        plateProgram=glyphProgram=plateVao=glyphVao=vbo=0;
    }
}
