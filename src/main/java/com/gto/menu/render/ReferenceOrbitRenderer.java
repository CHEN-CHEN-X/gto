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
    private static int plateProgram, glyphProgram, starsProgram, plateVao, glyphVao, vbo;
    private static final int[] textures = new int[5];
    private static int width, height, glyphCount;
    private static int plateBrightRegions, plateDarkRegions, plateRegionStrength;
    private static final FloatBuffer brightRegions=BufferUtils.createFloatBuffer(136);
    private static final FloatBuffer darkRegions=BufferUtils.createFloatBuffer(136);
    private static float[] lightTracks;
    private static int plateResolution, plateAspect, glyphResolution, glyphAspect, glyphAngle, glyphOpacity;
    private static long start;
    private static int plateMouse, glyphMouse, plateParticles, starsTime, starsStrength, starsResolution, starsAspect, starsMouse;
    private static final int PARTICLE_COUNT=12;
    private static final float[] particleX=new float[PARTICLE_COUNT], particleY=new float[PARTICLE_COUNT];
    private static final double[] particleTime=new double[PARTICLE_COUNT];
    private static final FloatBuffer particleData=BufferUtils.createFloatBuffer(PARTICLE_COUNT*4);
    private static int particleNext;
    private static double lastParticleTime=-10;
    private static float lastMouseX=Float.NaN,lastMouseY;

    private ReferenceOrbitRenderer() {}
    public static boolean isActive() { return active; }

    public static boolean render() {
        if (attempted && !active) return false;
        int prevProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int prevFbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean hasSamplers=GL.getCapabilities().OpenGL33 || GL.getCapabilities().GL_ARB_sampler_objects;
        int[] oldTextures=new int[5], oldSamplers=new int[5], viewport=new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        for(int i=0;i<textures.length;i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0+i);
            oldTextures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            if(hasSamplers)oldSamplers[i]=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        }
        ByteBuffer colorMask=BufferUtils.createByteBuffer(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,colorMask);
        boolean stencil=GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        boolean rasterDiscard=GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD);
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
            enabled(GL11.GL_STENCIL_TEST,false);enabled(GL30.GL_RASTERIZER_DISCARD,false);
            GL11.glColorMask(true,true,true,true);
            enabled(GL11.GL_SCISSOR_TEST,false);enabled(GL11.GL_CULL_FACE,false);
            enabled(GL30.GL_FRAMEBUFFER_SRGB,false);GL11.glDepthMask(false);
            for(int i=0;i<textures.length;i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+i);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,textures[i]);
                if(hasSamplers)GL33.glBindSampler(i,0);
            }
            double seconds=(System.nanoTime()-start)/1.0e9;
            // MouseHandler coordinates use window dimensions, not GUI-scaled units.
            double mx=mc.mouseHandler.xpos(), my=mc.mouseHandler.ypos();
            double ww=mc.getWindow().getScreenWidth(), wh=mc.getWindow().getScreenHeight();
            float fit=Math.min(1f,((float)w/h)/((float)width/height));
            float mouseX=(float)((mx/ww-.5)*((float)w/h)/fit);
            float mouseY=(float)((.5-my/wh)/fit);
            boolean mouseInside=mc.isWindowActive() && mx>=0 && my>=0 && mx<ww && my<wh;
            GL20.glUseProgram(plateProgram);GL30.glBindVertexArray(plateVao);
            double lightTurns=seconds/RenderConfig.ARTWORK_SPOT_ORBIT_SECONDS;
            updateLightRegions(lightTurns);
            GL20.glUniform3f(plateMouse,mouseX,mouseY,mouseInside?1f:0f);
            updateMouseParticles(mouseX,mouseY,mouseInside,seconds);
            boolean lightEnabled=RenderConfig.ARTWORK_FLOW_STRENGTH>0;
            GL20.glUniform2f(plateRegionStrength,lightEnabled ? RenderConfig.ARTWORK_SPOT_BRIGHT_GAIN : 0f,
                lightEnabled ? RenderConfig.ARTWORK_SPOT_DARK_DROP : 0f);
            GL20.glUniform2f(plateResolution,w,h);GL20.glUniform1f(plateAspect,(float)width/height);
            GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            // Separate additive pass: stars only write their own dim emission.
            GL20.glUseProgram(starsProgram);
            GL20.glUniform2f(starsResolution,w,h);
            GL20.glUniform1f(starsAspect,(float)width/height);
            GL20.glUniform1f(starsTime,(float)seconds);
            GL20.glUniform1f(starsStrength,RenderConfig.BACKGROUND_STAR_STRENGTH);
            GL20.glUniform3f(starsMouse,mouseX,mouseY,mouseInside?1f:0f);
            enabled(GL11.GL_BLEND,true);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD,GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE);
            GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            GL20.glUseProgram(glyphProgram);GL30.glBindVertexArray(glyphVao);
            GL20.glUniform3f(glyphMouse,mouseX,mouseY,mouseInside?1f:0f);
            GL20.glUniform2f(glyphResolution,w,h);GL20.glUniform1f(glyphAspect,(float)width/height);
            enabled(GL11.GL_BLEND,true);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD,GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_ONE,GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE);
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
            for(int i=0;i<textures.length;i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0+i);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,oldTextures[i]);
                if(hasSamplers)GL33.glBindSampler(i,oldSamplers[i]);
            }
            GL13.glActiveTexture(prevUnit);GL30.glBindVertexArray(prevVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,prevBuffer);GL20.glUseProgram(prevProgram);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,prevFbo);
            GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);GL11.glDepthMask(depthWrite);
            GL14.glBlendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
            GL20.glBlendEquationSeparate(eqRgb,eqAlpha);
            GL11.glColorMask(colorMask.get(0)!=0,colorMask.get(1)!=0,colorMask.get(2)!=0,colorMask.get(3)!=0);
            enabled(GL11.GL_STENCIL_TEST,stencil);enabled(GL30.GL_RASTERIZER_DISCARD,rasterDiscard);
            enabled(GL11.GL_DEPTH_TEST,depth);enabled(GL11.GL_BLEND,blend);
            enabled(GL11.GL_SCISSOR_TEST,scissor);enabled(GL11.GL_CULL_FACE,cull);
            enabled(GL30.GL_FRAMEBUFFER_SRGB,srgb);
        }
    }

    private static void updateMouseParticles(float x,float y,boolean inside,double seconds) {
        double co=Math.cos(.26),si=Math.sin(.26);
        double lx=co*x+si*(y+.015),ly=-si*x+co*(y+.015);
        double radius=Math.hypot(lx,ly/.30);
        boolean disk=inside && radius>.30 && radius<1.08;
        if(disk && seconds-lastParticleTime>.12 && (Float.isNaN(lastMouseX)||Math.hypot(x-lastMouseX,y-lastMouseY)>.012)) {
            particleX[particleNext]=x;particleY[particleNext]=y;
            particleTime[particleNext]=seconds;
            particleNext=(particleNext+1)%PARTICLE_COUNT;
            lastMouseX=x;lastMouseY=y;lastParticleTime=seconds;
        }
        if(!disk)lastMouseX=Float.NaN;
        particleData.clear();
        for(int i=0;i<PARTICLE_COUNT;i++) {
            float age=(float)(seconds-particleTime[i]);
            boolean active=particleTime[i]>0 && age>=0 && age<3.5f;
            particleData.put(particleX[i]).put(particleY[i]).put(age).put(active?(i%2==0?1f:2f):0f);
        }
        particleData.flip();GL20.glUniform4fv(plateParticles,particleData);
    }

    private static void loadLightTracks() throws Exception {
        try(DataInputStream in=new DataInputStream(new GZIPInputStream(resource("textures/light_tracks.bin.gz")))) {
            if(in.readInt()!=0x47544C31||in.readInt()!=8||in.readInt()!=512)
                throw new IllegalStateException("Invalid source-guided light paths");
            lightTracks=new float[8*512*2];
            for(int i=0;i<lightTracks.length;i++) {
                lightTracks[i]=in.readFloat();
                if(!Float.isFinite(lightTracks[i]))throw new IllegalStateException("Invalid light path coordinate");
            }
            if(in.read()!=-1)throw new IllegalStateException("Unexpected light path data");
        }
    }

    private static float trackValue(int track,int sample,int axis) {
        return lightTracks[(track*512+(sample&511))*2+axis];
    }

    public static float[] orbitPoint(int track,double phase) {
        if(lightTracks==null || track<0 || track>=8)return null;
        double index=(phase-Math.floor(phase))*512.0;
        int i=(int)index;float f=(float)(index-i);
        float[] result=new float[2];
        for(int axis=0;axis<2;axis++) {
            float p0=trackValue(track,i-1,axis),p1=trackValue(track,i,axis);
            float p2=trackValue(track,i+1,axis),p3=trackValue(track,i+2,axis);
            float a=-p0+p2,b=2*p0-5*p1+4*p2-p3,c=-p0+3*p1-3*p2+p3;
            result[axis]=.5f*(2*p1+a*f+b*f*f+c*f*f*f);
        }
        return result;
    }

    private static void putRegion(FloatBuffer out,int track,double phase) {
        double index=(phase-Math.floor(phase))*512.0;
        int i=(int)Math.floor(index);float f=(float)(index-i);
        float x=0,y=0,dx=0,dy=0;
        for(int axis=0;axis<2;axis++) {
            float p0=trackValue(track,i-1,axis),p1=trackValue(track,i,axis);
            float p2=trackValue(track,i+1,axis),p3=trackValue(track,i+2,axis);
            float a=-p0+p2,b=2*p0-5*p1+4*p2-p3,c=-p0+3*p1-3*p2+p3;
            float pos=.5f*(2*p1+a*f+b*f*f+c*f*f*f);
            float tangent=.5f*(a+2*b*f+3*c*f*f);
            if(axis==0){x=pos;dx=tangent;}else{y=pos;dy=tangent;}
        }
        float length=(float)Math.sqrt(dx*dx+dy*dy);
        out.put(x).put(y).put(dx/Math.max(length,1e-7f)).put(dy/Math.max(length,1e-7f));
    }

    private static void updateLightRegions(double turns) {
        brightRegions.clear();darkRegions.clear();
        for(int i=0;i<8;i++) {
            int patches=i<2 ? 5 : 4;
            for(int patch=0;patch<patches;patch++) {
                double phase=turns+i*.38196601125+patch/(double)patches;
                putRegion(brightRegions,i,phase);
                // Alternate independent bright/dark patches around the closed track.
                putRegion(darkRegions,i,phase+0.5/patches);
            }
        }
        brightRegions.flip();darkRegions.flip();
        GL20.glUniform4fv(plateBrightRegions,brightRegions);
        GL20.glUniform4fv(plateDarkRegions,darkRegions);
    }

    private static void drawGlyphs(double seconds,float opacity) {
        double turns=seconds*RenderConfig.ARTWORK_FLOW_STRENGTH/60.0;
        GL20.glUniform1f(glyphAngle,(float)((turns-Math.floor(turns))*Math.PI*2.0));
        GL20.glUniform1f(glyphOpacity,opacity);
        GL11.glDrawArrays(GL11.GL_TRIANGLES,0,glyphCount*6);
    }

    private static void init() throws Exception {
        loadLightTracks();
        plateProgram=link("fsquad.vsh","orbit_plate.fsh",false);
        glyphProgram=link("glyph_orbit.vsh","glyph_orbit.fsh",true);
        starsProgram=link("fsquad.vsh","binary_stars.fsh",false);
        textures[0]=loadTexture("orbit_plate.png",true);
        textures[1]=loadTexture("orbit_glyphs.png",false);
        textures[2]=loadTexture("orbit_lut.png",false);
        textures[3]=loadTexture("plate_bright_digits.png",false);
        textures[4]=loadTexture("plate_dark_lines.png",false);
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
        GL20.glUniform1i(GL20.glGetUniformLocation(plateProgram,"uBrightMaterial"),3);
        GL20.glUniform1i(GL20.glGetUniformLocation(plateProgram,"uDarkMaterial"),4);
        plateMouse=GL20.glGetUniformLocation(plateProgram,"uMouse");
        plateParticles=GL20.glGetUniformLocation(plateProgram,"uParticles[0]");
        plateResolution=GL20.glGetUniformLocation(plateProgram,"uResolution");
        plateAspect=GL20.glGetUniformLocation(plateProgram,"uSourceAspect");
        plateBrightRegions=GL20.glGetUniformLocation(plateProgram,"uBrightRegions[0]");
        plateDarkRegions=GL20.glGetUniformLocation(plateProgram,"uDarkRegions[0]");
        plateRegionStrength=GL20.glGetUniformLocation(plateProgram,"uRegionStrength");
        GL20.glUseProgram(starsProgram);
        GL20.glUniform1i(GL20.glGetUniformLocation(starsProgram,"uArtwork"),0);
        starsResolution=GL20.glGetUniformLocation(starsProgram,"uResolution");
        starsAspect=GL20.glGetUniformLocation(starsProgram,"uSourceAspect");
        starsMouse=GL20.glGetUniformLocation(starsProgram,"uMouse");
        starsTime=GL20.glGetUniformLocation(starsProgram,"uStarsTime");
        starsStrength=GL20.glGetUniformLocation(starsProgram,"uStarsStrength");
        GL20.glUseProgram(glyphProgram);
        GL20.glUniform1i(GL20.glGetUniformLocation(glyphProgram,"uPlate"),0);
        GL20.glUniform1i(GL20.glGetUniformLocation(glyphProgram,"uGlyphs"),1);
        GL20.glUniform1i(GL20.glGetUniformLocation(glyphProgram,"uOrbitLut"),2);
        glyphMouse=GL20.glGetUniformLocation(glyphProgram,"uMouse");
        glyphResolution=GL20.glGetUniformLocation(glyphProgram,"uResolution");
        glyphAspect=GL20.glGetUniformLocation(glyphProgram,"uSourceAspect");
        glyphAngle=GL20.glGetUniformLocation(glyphProgram,"uOrbitAngle");
        glyphOpacity=GL20.glGetUniformLocation(glyphProgram,"uOpacity");
        start=System.nanoTime();active=true;
    }

    private static int loadTexture(String name,boolean scene) throws Exception {
        byte[] encoded;
        try(InputStream in=resource("textures/"+name)) { encoded=in.readAllBytes(); }
        ByteBuffer input=BufferUtils.createByteBuffer(encoded.length);input.put(encoded).flip();
        int unpack=GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int rowLength=GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int skipRows=GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        int skipPixels=GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int imageHeight=GL11.glGetInteger(GL12.GL_UNPACK_IMAGE_HEIGHT);
        int skipImages=GL11.glGetInteger(GL12.GL_UNPACK_SKIP_IMAGES);
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
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,0);
                GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT,0);
                GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES,0);
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
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,unpack);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,rowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,skipRows);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,skipPixels);
            GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT,imageHeight);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES,skipImages);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,pbo);
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
        if(plateProgram!=0)GL20.glDeleteProgram(plateProgram);if(glyphProgram!=0)GL20.glDeleteProgram(glyphProgram);if(starsProgram!=0)GL20.glDeleteProgram(starsProgram);
        if(plateVao!=0)GL30.glDeleteVertexArrays(plateVao);if(glyphVao!=0)GL30.glDeleteVertexArrays(glyphVao);
        if(vbo!=0)GL15.glDeleteBuffers(vbo);
        for(int i=0;i<textures.length;i++) { if(textures[i]!=0)GL11.glDeleteTextures(textures[i]);textures[i]=0; }
        plateProgram=glyphProgram=starsProgram=plateVao=glyphVao=vbo=0;
    }
}
