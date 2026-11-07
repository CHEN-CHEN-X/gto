package com.gto.menu.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.concurrent.ThreadLocalRandom;
import org.joml.Quaternionf;

/** Uses the actual installed GTO/GTCEu item models, not copied textures. */
public final class VoltageBlockRenderer {
    private static final String[] TIERS={"ulv","lv","mv","hv","ev","iv","luv","zpm","uv","uhv","uev","uiv","uxv","opv"};
    private static final ItemStack[] ITEMS=new ItemStack[TIERS.length];
    private static boolean resolved;
    private static final long START=System.nanoTime();
    // Choose exactly three distinct tiers once per game launch; zero means no spin.
    private static final float[] SPIN_DEGREES_PER_SECOND=createSpinSpeeds();
    private VoltageBlockRenderer() {}

    private static float[] createSpinSpeeds() {
        float[] speeds=new float[TIERS.length];
        ThreadLocalRandom random=ThreadLocalRandom.current();
        int selected=0;
        while(selected<3) {
            int index=random.nextInt(TIERS.length);
            if(speeds[index]!=0f)continue;
            speeds[index]=5f+random.nextFloat()*15f;
            selected++;
        }
        return speeds;
    }

    private static void resolveItems() {
        for(int i=0;i<TIERS.length;i++) {
            String name=TIERS[i]+"_hermetic_casing";
            ResourceLocation core=new ResourceLocation("gtocore",name);
            ResourceLocation gt=new ResourceLocation("gtceu",name);
            Item item=BuiltInRegistries.ITEM.containsKey(core)?BuiltInRegistries.ITEM.get(core):
                    BuiltInRegistries.ITEM.containsKey(gt)?BuiltInRegistries.ITEM.get(gt):Items.AIR;
            ITEMS[i]=item==Items.AIR?ItemStack.EMPTY:new ItemStack(item);
        }
        resolved=true;
    }

    public static void render(GuiGraphics graphics,int screenW,int screenH) {
        if(!resolved)resolveItems();
        double seconds=(System.nanoTime()-START)/1.0e9;
        float aspect=(float)screenW/screenH, sourceAspect=1671f/941f;
        float fit=Math.min(1f,aspect/sourceAspect);
        for(int i=0;i<TIERS.length;i++) {
            if(ITEMS[i].isEmpty())continue;
            double turns=seconds/300.0+i/(double)TIERS.length;
            float[] p=ReferenceOrbitRenderer.orbitPoint(i%8,turns);
            if(p==null)continue;
            // Far-side items disappear behind the central event horizon.
            float hx=p[0],hy=p[1]-.015f;
            float ry=(float)(-Math.sin(.26)*p[0]+Math.cos(.26)*(p[1]+.015));
            if(hx*hx+hy*hy<.19f*.19f && ry>-.065f)continue;
            float x=screenW*.5f+p[0]*screenH*fit;
            float y=screenH*.5f-p[1]*screenH*fit;
            float scale=1.3f*Math.max(.24f,Math.min(.48f,screenH/1080f*.42f));
            PoseStack pose=graphics.pose();pose.pushPose();
            pose.translate(x,y,0);pose.scale(scale,scale,scale);
            if(SPIN_DEGREES_PER_SECOND[i]>0f) {
                float angle=(float)Math.toRadians((seconds*SPIN_DEGREES_PER_SECOND[i])%360.0);
                pose.mulPose(new Quaternionf().rotateY(angle));
            }
            graphics.renderItem(ITEMS[i],-8,-8);
            pose.popPose();
        }
        graphics.flush();
    }
}
