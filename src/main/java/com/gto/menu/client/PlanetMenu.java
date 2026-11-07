package com.gto.menu.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.gto.menu.GTOMenuMod;
import com.gto.menu.MenuConfig;
import net.minecraft.Util;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import java.net.URI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Functional planet controls layered over the existing title-screen effect. */
public final class PlanetMenu {
    private static final String[] KEYS={"menu.singleplayer","menu.multiplayer","fml.menu.mods","menu.options","menu.quit"};
    private static final String[] FILES={"single","multi","mods","options","quit"};
    // Five-ring arrangement rotated along the disk: options / mods / single above, quit / multi below.
    private static final float[] X={.93f,.91f,.805f,.645f,.78f};
    private static final float[] Y={.60f,.78f,.72f,.84f,.92f};
    private static final ResourceLocation TITLE=new ResourceLocation("gtocore","textures/gui/title/title.png");
    private static final ResourceLocation LOGO=new ResourceLocation("gtocore","textures/gui/title/logo.png");
    private static final ResourceLocation[] ICONS=new ResourceLocation[FILES.length];
    private static int selected=-1;
    private static boolean texturesLoaded;
    static {for(int i=0;i<FILES.length;i++)ICONS[i]=new ResourceLocation("gto_menu","textures/menu/"+FILES[i]+".png");}
    private PlanetMenu() {}

    private static void loadTextures() {
        if(texturesLoaded)return;
        texturesLoaded=true;
        for(int i=0;i<FILES.length;i++) {
            String path="/assets/gto_menu/textures/menu/"+FILES[i]+".png";
            try(var stream=PlanetMenu.class.getResourceAsStream(path)) {
                if(stream==null)throw new java.io.FileNotFoundException(path);
                NativeImage image=NativeImage.read(stream);
                Minecraft.getInstance().getTextureManager().register(ICONS[i],new DynamicTexture(image));
            } catch(Exception e) {
                GTOMenuMod.LOGGER.error("Unable to load menu icon {}",FILES[i],e);
            }
        }
    }
    private static Button button(Screen screen,int index) {
        for(var child:screen.children())if(child instanceof Button button && button.getMessage().getContents() instanceof TranslatableContents text) {
            String key=text.getKey();
            if(key.equals(KEYS[index]) || (index==0 && key.equals("menu.playdemo")) || (index==1 && key.equals("menu.resetdemo")))return button;
        }
        return null;
    }
    private static int size(Screen s){return Math.max(18,Math.min(52,(int)(s.height*.155f)));}
    private static int cx(Screen s,int i){return Math.max(size(s)/2,Math.min(s.width-size(s)/2,(int)(s.width*X[i])));}
    private static int cy(Screen s,int i){return Math.max(size(s)/2,Math.min(s.height-size(s)/2-12,(int)(s.height*Y[i])));}
    private static boolean hit(Screen s,int i,double mx,double my) {
        int n=size(s);return Math.abs(mx-cx(s,i))<=n*.54 && my>=cy(s,i)-n*.54 && my<=cy(s,i)+n*.54+10;
    }
    private static int logoSize(Screen s){return Math.max(18,Math.min((int)(s.height*.20f),s.width/8));}
    private static int logoX(Screen s){return s.width/2;}
    private static int logoY(Screen s){return (int)(s.height*.44f);}
    private static boolean logoHit(Screen s,double x,double y){
        int n=logoSize(s);return Math.abs(x-logoX(s))<=n*.5 && Math.abs(y-logoY(s))<=n*.5;
    }
    private static void focusCorners(GuiGraphics g,int x,int y,int n){
        int left=x-n/2-2,right=x+n/2+2,top=y-n/2-2,bottom=y+n/2+2;
        int length=Math.max(3,n/6),col=0xD080ECFF;
        g.fill(left,top,left+length,top+1,col);g.fill(left,top,left+1,top+length,col);
        g.fill(right-length,top,right,top+1,col);g.fill(right-1,top,right,top+length,col);
        g.fill(left,bottom-1,left+length,bottom,col);g.fill(left,bottom-length,left+1,bottom,col);
        g.fill(right-length,bottom-1,right,bottom,col);g.fill(right-1,bottom-length,right,bottom,col);
    }
    private static void openWiki(){
        try {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK,1f));
            Util.getPlatform().openUri(URI.create(MenuConfig.WIKI_URL.get()));
        } catch(Exception e) {GTOMenuMod.LOGGER.warn("Unable to open configured wiki URL",e);}
    }
    public static void render(Screen screen,GuiGraphics graphics,int mouseX,int mouseY) {
        loadTextures();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        int titleWidth=Math.min((int)(screen.width*.31f),screen.width-8);
        int titleHeight=Math.max(1,titleWidth*1488/4042);
        graphics.blit(TITLE,(int)(screen.width*.025f),(int)(screen.height*.025f),titleWidth,titleHeight,0,0,4042,1488,4042,1488);
        int hovered=-1;
        for(int i=0;i<FILES.length;i++)if(hit(screen,i,mouseX,mouseY)){hovered=i;break;}
        if(hovered<0 && logoHit(screen,mouseX,mouseY))hovered=FILES.length;
        boolean logoFocus=hovered==FILES.length || (hovered<0 && selected==FILES.length);
        int logoSize=logoSize(screen),logoShown=logoSize+(logoFocus?Math.max(3,logoSize/14):0);
        if(logoFocus)focusCorners(graphics,logoX(screen),logoY(screen),logoSize);
        graphics.blit(LOGO,logoX(screen)-logoShown/2,logoY(screen)-logoShown/2,logoShown,logoShown,0,0,882,882,882,882);
        var font=Minecraft.getInstance().font;
        if(logoFocus)graphics.drawCenteredString(font,Component.translatableWithFallback("gto_menu.wiki","百科"),logoX(screen),logoY(screen)+logoSize/2+2,0xC9ECFF);
        for(int i=0;i<FILES.length;i++) {
            Button original=button(screen,i);
            boolean active=original!=null && original.active;
            boolean hover=hovered==i;
            boolean focus=active && (hover || (hovered<0 && selected==i));
            int n=size(screen),x=cx(screen,i),y=cy(screen,i);
            int shown=n+(focus?Math.max(3,n/7):0);
            if(focus)focusCorners(graphics,x,y,n);
            float tint=active?1f:.38f;
            graphics.setColor(tint,tint,tint,1f);
            graphics.blit(ICONS[i],x-shown/2,y-shown/2,shown,shown,0,0,256,256,256,256);
            graphics.setColor(1,1,1,1);
            Component label=original!=null?original.getMessage():Component.translatable(KEYS[i]);
            graphics.drawCenteredString(font,label,x,Math.min(screen.height-font.lineHeight-2,y+n/2),active?(focus?0xFFFFFF:0xC9ECFF):0x777777);
        }
        graphics.flush();
    }
    public static void click(Screen screen,double x,double y,int mouseButton) {
        if(mouseButton!=0)return;
        if(logoHit(screen,x,y)){selected=FILES.length;openWiki();return;}
        for(int i=0;i<FILES.length;i++)if(hit(screen,i,x,y)){
            Button original=button(screen,i);selected=i;
            if(original!=null && original.active){original.playDownSound(Minecraft.getInstance().getSoundManager());original.onPress();}
            return;
        }
    }
    public static void key(Screen screen,int key) {
        if(key==GLFW.GLFW_KEY_TAB || key==GLFW.GLFW_KEY_RIGHT || key==GLFW.GLFW_KEY_DOWN)selected=(selected+1)%(FILES.length+1);
        else if(key==GLFW.GLFW_KEY_LEFT || key==GLFW.GLFW_KEY_UP)selected=(selected+FILES.length)%(FILES.length+1);
        else if((key==GLFW.GLFW_KEY_ENTER || key==GLFW.GLFW_KEY_KP_ENTER || key==GLFW.GLFW_KEY_SPACE) && selected>=0){
            if(selected==FILES.length)openWiki();
            else {Button original=button(screen,selected);if(original!=null && original.active){original.playDownSound(Minecraft.getInstance().getSoundManager());original.onPress();}}
        }
    }
}
