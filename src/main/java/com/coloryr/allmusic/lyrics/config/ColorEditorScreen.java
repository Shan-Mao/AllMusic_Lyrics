package com.coloryr.allmusic.lyrics.config;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ColorEditorScreen extends Screen {

    private static final int P=100, HB=12, SW=130, SH=10, PREV=34;
    private final Screen parent; private final String key, title;
    private int a,r,g,b; private float hue,sat,val;
    private EditBox hex; private boolean dp,dh; private int ds=-1;
    private int px,py,hx,sx,ssy; private boolean codeUpdate;

    private DynamicTexture tex; private Identifier texId; private float lastHue=-1;

    public ColorEditorScreen(Screen parent, String key, String title) {
        super(Component.literal(title+" 颜色编辑器"));
        this.parent=parent; this.key=key; this.title=title;
        LyricsConfig cfg=LyricsConfig.get();
        int c=key.equals("textColor")?cfg.textColor:cfg.titleColor;
        a=(c>>24)&0xFF;r=(c>>16)&0xFF;g=(c>>8)&0xFF;b=c&0xFF; rgbToHsv();
    }

    @Override protected void init() {
        px=width/2-135; py=30; hx=px+P+6; sx=width/2+15; ssy=30;
        rebuild();
        int by=ssy+7*24+10;
        hex=new EditBox(font,px,by+PREV+10,90,16,Component.literal("HEX"));
        hex.setValue(hx()); hex.setResponder(this::onHex);
        addRenderableWidget(hex);
        addRenderableWidget(Button.builder(Component.literal("§a✓ 完成"),b->{save();onClose();})
                .pos(width/2-30,by+PREV+36).size(60,20).build());
    }

    private void rebuild() {
        if (lastHue==hue) return; lastHue=hue;
        NativeImage img=new NativeImage(P,P,false);
        for (int x=0;x<P;x++) {
            for (int y=0;y<P;y++) {
                float s=x/99f, v=1f-y/99f;
                int argb=hsbToRgb(hue/360f,s,v)|0xFF000000;
                int abgr=(argb&0xFF00FF00)|((argb&0x00FF0000)>>16)|((argb&0x000000FF)<<16);
                img.setPixelABGR(x,y,abgr);
            }
        }
        if (tex!=null) tex.close();
        tex=new DynamicTexture(()->"cp",img);
        if (texId!=null) minecraft.getTextureManager().release(texId);
        texId=Identifier.fromNamespaceAndPath("allmusic_lyrics","cp_"+System.nanoTime());
        minecraft.getTextureManager().register(texId,tex);
        tex.upload();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float d) {
        super.extractRenderState(ctx,mx,my,d);
        rebuild();
        ctx.centeredText(font,Component.literal(title+" 颜色编辑器"),width/2,8,0xFFFFFFFF);

        ctx.blit(RenderPipelines.GUI_TEXTURED,texId,px,py,0,0,P,P,P,P);
        box(ctx,px,py,P,P);
        int cx=px+(int)(sat/100*P), cy=py+(int)((100-val)/100*P);
        ctx.fill(cx-5,cy,cx+6,cy+1,0xFF000000); ctx.fill(cx-4,cy+1,cx+5,cy+2,0xFFFFFFFF);
        ctx.fill(cx,cy-5,cx+1,cy+6,0xFF000000); ctx.fill(cx+1,cy-4,cx+2,cy+5,0xFFFFFFFF);

        for (int y=0;y<P;y++) ctx.fill(hx,py+y,hx+HB,py+y+1,hsbToRgb(1f-(float)y/P,1,1)|0xFF000000);
        box(ctx,hx,py,HB,P);
        int hy=py+(int)((1f-hue/360)*P);
        ctx.fill(hx-4,hy,hx+HB+5,hy+1,0xFF000000); ctx.fill(hx-3,hy+1,hx+HB+4,hy+2,0xFFFFFFFF);

        String[] lb={"H","S","V","R","G","B","A"};
        int[] vi={(int)hue,(int)sat,(int)val,r,g,b,a}, mxV={360,100,100,255,255,255,255};
        for (int i=0;i<7;i++) {
            int sy=ssy+i*24; String u=i<=2?(i==0?"°":"%"):"";
            ctx.text(font,lb[i],sx-18,sy+9,0xFFFFFFFF,false);
            ctx.text(font,vi[i]+u,sx+SW+6,sy+9,0xFFFFFFFF,false);
            // 白色底
            ctx.fill(sx,sy+9,sx+SW,sy+9+SH,0xFFFFFFFF);
            // 渐变填充
            for (int x=0;x<SW;x++) {
                float t=(float)x/SW;
                ctx.fill(sx+x,sy+9,sx+x+1,sy+9+SH, sliderColor(i,t));
            }
            // 边框
            ctx.fill(sx-1,sy+8,sx+SW+1,sy+9+SH,0x80FFFFFF);
            // 三角箭头手柄
            float ratio=(float)vi[i]/mxV[i]; int hh=sx+(int)(ratio*SW);
            ctx.fill(hh-2,sy+6,hh+3,sy+7,0xFFFFFFFF);
            ctx.fill(hh-1,sy+7,hh+2,sy+8,0xFFFFFFFF);
            ctx.fill(hh,sy+8,hh+1,sy+15,0xFFFFFFFF);
        }

        int py2=ssy+7*24+10;
        ctx.fill(px,py2,px+PREV,py2+PREV,(a<<24)|(r<<16)|(g<<8)|b);
        box(ctx,px,py2,PREV,PREV);
    }

    private int sliderColor(int i, float t) {
        return switch (i) {
            case 0 -> hsbToRgb(t,1,1) | 0xFF000000;
            case 1 -> hsbToRgb(hue/360f,t,val/100f) | 0xFF000000;
            case 2 -> hsbToRgb(hue/360f,sat/100f,t) | 0xFF000000;
            case 3 -> 0xFF000000 | ((int)(t*255)<<16);
            case 4 -> 0xFF000000 | ((int)(t*255)<<8);
            case 5 -> 0xFF000000 | (int)(t*255);
            case 6 -> ((int)(t*255)<<24) | (r<<16) | (g<<8) | b;
            default -> 0xFFFFFFFF;
        };
    }

    private void box(GuiGraphicsExtractor ctx,int x,int y,int w,int h){
        ctx.fill(x-1,y-1,x+w+1,y,0xFFFFFFFF);ctx.fill(x-1,y+h,x+w+1,y+h+1,0xFFFFFFFF);
        ctx.fill(x-1,y,x,y+h,0xFFFFFFFF);ctx.fill(x+w,y,x+w+1,y+h,0xFFFFFFFF);
    }

    @Override public boolean mouseClicked(MouseButtonEvent e,boolean b2){
        double mx=e.x(),my=e.y();
        if(in(mx,my,px,py,P,P)){dp=true;pck(mx,my);return true;}
        if(in(mx,my,hx,py,HB,P)){dh=true;hck(my);return true;}
        return super.mouseClicked(e,b2);
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){
        double mx=e.x(),my=e.y();
        if(dp){pck(mx,my);return true;}if(dh){hck(my);return true;}
        if(ds>=0){sld(mx);return true;}
        for(int i=0;i<7;i++){int sy=ssy+i*24+9;if(my>=sy-3&&my<=sy+SH+3&&mx>=sx-4&&mx<=sx+SW+4){ds=i;sld(mx);return true;}}
        return super.mouseDragged(e,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent e){dp=dh=false;ds=-1;return super.mouseReleased(e);}
    private boolean in(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;}
    private void pck(double mx,double my){sat=clamp((float)(mx-px)/P*100,0,100);val=clamp((1f-(float)(my-py)/P)*100,0,100);applyHsv();}
    private void hck(double my){hue=clamp((1f-(float)(my-py)/P)*360,0,360);applyHsv();}
    private void sld(double mx){
        float t=clamp((float)(mx-sx)/SW,0,1);
        switch(ds){case 0->{hue=t*360;applyHsv();}case 1->{sat=t*100;applyHsv();}case 2->{val=t*100;applyHsv();}case 3->{r=(int)(t*255);rgbToHsv();}case 4->{g=(int)(t*255);rgbToHsv();}case 5->{b=(int)(t*255);rgbToHsv();}case 6->a=(int)(t*255);}
        if(ds>=3){codeUpdate=true;hex.setValue(hx());codeUpdate=false;}
    }

    private void rgbToHsv(){float[]f=rgbToHsb(r,g,b);hue=f[0]*360;sat=f[1]*100;val=f[2]*100;}

    // ================================================================
    //  HSB 色彩换算（自实现，避免依赖 java.desktop 的 java.awt.Color）
    // ================================================================

    /** HSB → RGB，h/s/v 取值均为 0..1，返回 0xRRGGBB */
    private static int hsbToRgb(float h, float s, float v) {
        if (s <= 0f) { int g = clamp255(v); return (g << 16) | (g << 8) | g; }
        float hh = (h - (float) Math.floor(h)) * 6f;
        int i = (int) hh;
        float f = hh - i;
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));
        float r1, g1, b1;
        switch (i) {
            case 0 -> { r1 = v; g1 = t; b1 = p; }
            case 1 -> { r1 = q; g1 = v; b1 = p; }
            case 2 -> { r1 = p; g1 = v; b1 = t; }
            case 3 -> { r1 = p; g1 = q; b1 = v; }
            case 4 -> { r1 = t; g1 = p; b1 = v; }
            default -> { r1 = v; g1 = p; b1 = q; }
        }
        return (clamp255(r1) << 16) | (clamp255(g1) << 8) | clamp255(b1);
    }

    /** RGB → HSB，h/s/v 取值均为 0..1 */
    private static float[] rgbToHsb(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;
        float h = 0f;
        if (delta > 1e-6f) {
            if (max == rf)      h = ((gf - bf) / delta) % 6f;
            else if (max == gf) h = (bf - rf) / delta + 2f;
            else                h = (rf - gf) / delta + 4f;
            h /= 6f;
            if (h < 0f) h += 1f;
        }
        float s = max <= 1e-6f ? 0f : delta / max;
        return new float[]{h, s, max};
    }

    private static int clamp255(float v) {
        int i = Math.round(v * 255f);
        return i < 0 ? 0 : Math.min(i, 255);
    }
    private void applyHsv(){int c=hsbToRgb(hue/360f,sat/100f,val/100f);r=(c>>16)&0xFF;g=(c>>8)&0xFF;b=c&0xFF;codeUpdate=true;hex.setValue(hx());codeUpdate=false;}
    static int lerp(int a,int b,float t){return((int)(((a>>16)&0xFF)+(((b>>16)&0xFF)-((a>>16)&0xFF))*t)<<16)|((int)(((a>>8)&0xFF)+(((b>>8)&0xFF)-((a>>8)&0xFF))*t)<<8)|(int)((a&0xFF)+((b&0xFF)-(a&0xFF))*t);}
    private void onHex(String s){if(codeUpdate)return;try{s=s.replace("#","").replace("0x","");if(s.length()==6)s="FF"+s;int x=(int)Long.parseLong(s,16);a=(x>>24)&0xFF;r=(x>>16)&0xFF;g=(x>>8)&0xFF;b=x&0xFF;rgbToHsv();}catch(Exception ignored){}}
    private String hx(){return String.format("%02X%02X%02X%02X",a,r,g,b);}
    static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
    private void save(){LyricsConfig cfg=LyricsConfig.get();int c=(a<<24)|(r<<16)|(g<<8)|b;if(key.equals("textColor"))cfg.textColor=c;else cfg.titleColor=c;LyricsConfig.save();}
    @Override public void onClose(){LyricsConfigScreen.open(parent);}
}
