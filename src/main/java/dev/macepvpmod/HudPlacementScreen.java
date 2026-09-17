package dev.macepvpmod;

import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Scaled full-screen HUD canvas sharing the parent workspace's unsaved draft. */
final class HudPlacementScreen extends Screen {
    private static final String[] NAMES={"Pitch bar","Fall distance","Hit damage","Retotem","Healing","Attribute swap","Velocity"};
    private final SettingsScreen parent;private final SettingsSession draft;private final int selected;
    private int panelW,canvasX,canvasY,canvasW,canvasH;private double zoom,originX,originY;
    private HudRenderer.Bounds selectedBounds;private boolean dragging,resizing;
    private SyncSlider xSlider,ySlider,sizeSlider,thicknessSlider;

    HudPlacementScreen(SettingsScreen parent,SettingsSession draft,int selected){super(Component.literal("HUD position & size"));this.parent=parent;this.draft=draft;this.selected=selected;}
    private HudStyle style(){return draft.hud.get(selected);}
    HudStyle currentStyle(){return style();}
    HudRenderer.Bounds currentBounds(){return selectedBounds;}
    double screenX(double logical){return originX+logical*zoom;}
    double screenY(double logical){return originY+logical*zoom;}
    private void change(HudStyle style){draft.hud=draft.hud.with(selected,style.validated());syncControls();}

    @Override protected void init(){
        panelW=Math.min(250,Math.max(180,width/3));canvasX=panelW+18;canvasY=42;canvasW=Math.max(40,width-canvasX-14);canvasH=Math.max(40,height-canvasY-14);
        zoom=Math.min(canvasW/(double)Math.max(1,width),canvasH/(double)Math.max(1,height));originX=canvasX+(canvasW-width*zoom)/2;originY=canvasY+(canvasH-height*zoom)/2;
        int x=12,y=48,w=panelW-24;label("POSITION",x,y,w,0xff62d8ff);y+=17;
        xSlider=slider("Horizontal offset",x,y,w,style().x(),-4000,4000,1,v->{var s=style();change(s.edit(v,s.y(),s.scale(),s.color(),s.secondaryColor(),s.combinedColor(),s.width(),s.thickness(),s.opacity()));});y+=35;
        ySlider=slider("Vertical offset",x,y,w,style().y(),-4000,4000,1,v->{var s=style();change(s.edit(s.x(),v,s.scale(),s.color(),s.secondaryColor(),s.combinedColor(),s.width(),s.thickness(),s.opacity()));});y+=40;
        label("SIZE",x,y,w,0xff62d8ff);y+=17;
        if(selected==0){sizeSlider=slider("Width",x,y,w,style().width(),10,400,1,v->{var s=style();change(s.edit(s.x(),s.y(),s.scale(),s.color(),s.secondaryColor(),s.combinedColor(),(int)v,s.thickness(),s.opacity()));});y+=35;thicknessSlider=slider("Thickness",x,y,w,style().thickness(),1,8,1,v->{var s=style();change(s.edit(s.x(),s.y(),s.scale(),s.color(),s.secondaryColor(),s.combinedColor(),s.width(),(int)v,s.opacity()));});y+=39;}
        else{sizeSlider=slider("Scale",x,y,w,style().scale(),.5,4,.05,v->{var s=style();change(s.edit(s.x(),s.y(),v,s.color(),s.secondaryColor(),s.combinedColor(),s.width(),s.thickness(),s.opacity()));});y+=39;}
        addRenderableWidget(Button.builder(Component.literal("Reset element"),b->{draft.hud=draft.hud.with(selected,HudConfig.defaults().get(selected));syncControls();}).bounds(x,y,w,20).build());
        addRenderableWidget(Button.builder(Component.literal("Back to HUD Studio"),b->onClose()).bounds(x,height-32,w,20).build());
    }
    private void label(String text,int x,int y,int w,int color){var label=new StringWidget(w,10,Component.literal(text).withColor(color),font);label.setX(x);label.setY(y);addRenderableWidget(label);}
    private SyncSlider slider(String name,int x,int y,int w,double value,double min,double max,double step,DoubleConsumer setter){label(name,x,y,w,0xffdfe7ee);var slider=new SyncSlider(x,y+12,w,value,min,max,step,setter);addRenderableWidget(slider);return slider;}
    private void syncControls(){if(xSlider==null)return;var s=style();xSlider.sync(s.x());ySlider.sync(s.y());sizeSlider.sync(selected==0?s.width():s.scale());if(thicknessSlider!=null)thicknessSlider.sync(s.thickness());}

    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        g.fill(0,0,width,height,0xff0e141b);g.fill(0,0,panelW,height,0xff171f29);g.fill(panelW,0,panelW+1,height,0xff334353);
        g.text(font,"HUD POSITION & SIZE",12,16,0xffffffff);g.text(font,NAMES[selected],canvasX,16,0xff62d8ff);
        g.fill(canvasX,canvasY,canvasX+canvasW,canvasY+canvasH,0xff080d12);
        g.pose().pushMatrix();g.pose().translate((float)originX,(float)originY);g.pose().scale((float)zoom,(float)zoom);
        selectedBounds=null;for(int i=0;i<7;i++)if(i!=selected)HudPreview.render(g,draft,i,draft.hud.get(i),width,height,true);
        selectedBounds=HudPreview.render(g,draft,selected,draft.hud.get(selected),width,height,false);
        if(selectedBounds!=null){var b=selectedBounds;int x=(int)b.x()-2,y=(int)b.y()-2,r=(int)(b.x()+b.width())+2,bot=(int)(b.y()+b.height())+2;g.fill(x,y,r,y+1,0xff62d8ff);g.fill(x,bot,r,bot+1,0xff62d8ff);g.fill(x,y,x+1,bot,0xff62d8ff);g.fill(r,y,r+1,bot,0xff62d8ff);g.fill(r-4,bot-4,r+5,bot+5,0xffffffff);}
        g.pose().popMatrix();
        int left=(int)originX,top=(int)originY,right=(int)(originX+width*zoom),bottom=(int)(originY+height*zoom);
        g.fill(left,top,right,top+1,0xff526272);g.fill(left,bottom-1,right,bottom,0xff526272);g.fill(left,top,left+1,bottom,0xff526272);g.fill(right-1,top,right,bottom,0xff526272);
        super.extractRenderState(g,mx,my,dt);g.centeredText(font,"Drag to move • Drag the corner to resize • Arrow keys nudge",canvasX+canvasW/2,height-11,0xffaab6c2);
    }
    private double logicalX(double x){return (x-originX)/zoom;}private double logicalY(double y){return (y-originY)/zoom;}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){if(super.mouseClicked(e,twice))return true;if(e.button()!=0||selectedBounds==null)return false;double x=logicalX(e.x()),y=logicalY(e.y()),right=selectedBounds.x()+selectedBounds.width(),bottom=selectedBounds.y()+selectedBounds.height();if(Math.abs(x-right)<10/zoom&&Math.abs(y-bottom)<10/zoom){resizing=true;setFocused(null);return true;}if(selectedBounds.contains(x,y)){dragging=true;setFocused(null);return true;}return false;}
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){double lx=dx/zoom,ly=dy/zoom;if(dragging){move(lx,ly);return true;}if(resizing){var s=style();if(selected==0)change(s.edit(s.x(),s.y(),s.scale(),s.color(),s.secondaryColor(),s.combinedColor(),s.width()+(int)Math.round(lx),s.thickness()+(int)Math.round(ly),s.opacity()));else change(s.edit(s.x(),s.y(),s.scale()+(lx+ly)/160,s.color(),s.secondaryColor(),s.combinedColor(),s.width(),s.thickness(),s.opacity()));return true;}return super.mouseDragged(e,dx,dy);}
    private void move(double dx,double dy){var s=style();var b=selectedBounds;if(b==null)return;double x=Math.clamp(b.x()+dx,0,Math.max(0,width-b.width())),y=Math.clamp(b.y()+dy,0,Math.max(0,height-b.height()));double ox=x-(width*s.anchorX()-b.width()*s.alignX()),oy=y-(height*s.anchorY()-b.height()*s.alignY());change(s.edit(ox,oy,s.scale(),s.color(),s.secondaryColor(),s.combinedColor(),s.width(),s.thickness(),s.opacity()));}
    @Override public boolean mouseReleased(MouseButtonEvent e){dragging=resizing=false;return super.mouseReleased(e);}
    @Override public boolean keyPressed(KeyEvent e){if(e.key()>=262&&e.key()<=265){move(e.key()==262?1:e.key()==263?-1:0,e.key()==264?1:e.key()==265?-1:0);return true;}return super.keyPressed(e);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}

    private static final class SyncSlider extends AbstractSliderButton {
        private final double min,max,step;private final DoubleConsumer setter;
        SyncSlider(int x,int y,int width,double initial,double min,double max,double step,DoubleConsumer setter){super(x,y,width,20,Component.empty(),Math.clamp((initial-min)/(max-min),0,1));this.min=min;this.max=max;this.step=step;this.setter=setter;updateMessage();}
        private double actual(){return Math.clamp(Math.round((min+value*(max-min))/step)*step,min,max);}
        @Override protected void updateMessage(){setMessage(Component.literal(String.format(java.util.Locale.ROOT,step<1?"%.2f":"%.0f",actual())));}
        @Override protected void applyValue(){setter.accept(actual());}
        void sync(double n){value=Math.clamp((n-min)/(max-min),0,1);updateMessage();}
    }
}
