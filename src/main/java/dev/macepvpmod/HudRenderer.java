package dev.macepvpmod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
final class HudRenderer {
    record Bounds(double x,double y,double width,double height) {
        boolean contains(double px,double py) { return px>=x && py>=y && px<=x+width && py<=y+height; }
    }
    static Bounds bounds(HudStyle s,double w,double h,int viewportW,int viewportH) {
        return new Bounds(Math.clamp(viewportW*s.anchorX()-w*s.alignX()+s.x(),0,Math.max(0,viewportW-w)),
            Math.clamp(viewportH*s.anchorY()-h*s.alignY()+s.y(),0,Math.max(0,viewportH-h)),w,h);
    }
    static Bounds textBounds(String text,HudStyle s,int w,int h) {
        var font=Minecraft.getInstance().font;
        double scale=Math.min(s.scale(),Math.min(w/(double)Math.max(1,font.width(text)),h/(double)font.lineHeight));
        return bounds(s,font.width(text)*scale,font.lineHeight*scale,w,h);
    }
    static Bounds text(GuiGraphicsExtractor g,String text,HudStyle s,int state) {
        var b=textBounds(text,s,g.guiWidth(),g.guiHeight());
        float scale=(float)(b.height()/Minecraft.getInstance().font.lineHeight);
        int color=state==2?s.secondaryColor():state==3?s.combinedColor():s.color();
        g.pose().pushMatrix();g.pose().translate((float)b.x(),(float)b.y());g.pose().scale(scale,scale);
        g.text(Minecraft.getInstance().font,text,0,0,0xff000000|color);g.pose().popMatrix();return b;
    }
    static Bounds pitch(GuiGraphicsExtractor g,HudStyle s,PitchConfig c,double pitch) {
        var b=bounds(s,Math.min(s.width(),g.guiWidth()),s.thickness(),g.guiWidth(),g.guiHeight());
        int top=(int)Math.clamp(b.y()+PitchMath.offset(pitch,c),0,Math.max(0,g.guiHeight()-b.height()));
        g.fill((int)b.x(),top,(int)(b.x()+b.width()),top+(int)b.height(),((int)Math.round(s.opacity()*255)<<24)|s.color());
        return new Bounds(b.x(),top,b.width(),b.height());
    }
}
