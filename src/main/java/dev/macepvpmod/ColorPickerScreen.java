package dev.macepvpmod;
import java.awt.Color;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
final class ColorPickerScreen extends Screen {
    private final Screen parent; private final IntConsumer apply;
    private float hue,saturation,brightness; private int left,top,side; private boolean dragging,valid=true;
    private HueSlider hueSlider; private boolean syncing; private EditBox hex; private Button done;
    ColorPickerScreen(Screen parent,int color,IntConsumer apply) {
        super(Component.literal("Choose color"));this.parent=parent;this.apply=apply;
        float[] hsb=Color.RGBtoHSB(color>>16&255,color>>8&255,color&255,null);hue=hsb[0];saturation=hsb[1];brightness=hsb[2];
    }
    private int color(){return Color.HSBtoRGB(hue,saturation,brightness)&0xffffff;}
    protected void init(){
        side=Math.max(50,Math.min(150,height-140));left=width/2-side/2;top=30;
        hueSlider=new HueSlider();hueSlider.setPosition(left,top+side+5);addRenderableWidget(hueSlider);
        hex=new EditBox(font,left,top+side+30,side,20,Component.literal("Hex RGB"));hex.setMaxLength(6);hex.setValue(String.format("%06X",color()));
        hex.setResponder(v->{if(syncing)return;valid=v.matches("[0-9a-fA-F]{6}");if(valid){int c=Integer.parseInt(v,16);float[] a=Color.RGBtoHSB(c>>16&255,c>>8&255,c&255,null);hue=a[0];saturation=a[1];brightness=a[2];hueSlider.refresh();}hex.setTextColor(valid?0xffffffff:0xffff7777);done.active=valid;});addRenderableWidget(hex);
        int[] presets={0xffffff,0xff5555,0xffaa00,0xffff55,0x55ff55,0x55ffff,0x5555ff,0xff55ff};
        for(int i=0;i<presets.length;i++){int c=presets[i];addRenderableWidget(Button.builder(Component.literal("■").withColor(c),b->{float[] a=Color.RGBtoHSB(c>>16&255,c>>8&255,c&255,null);hue=a[0];saturation=a[1];brightness=a[2];sync();}).bounds(width/2-100+i*25,top+side+55,24,20).build());}
        done=addRenderableWidget(Button.builder(Component.literal("Use color"),b->{apply.accept(color());onClose();}).bounds(width/2-104,height-26,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(width/2+4,height-26,100,20).build());
    }
    private final class HueSlider extends AbstractSliderButton {
        HueSlider(){super(0,0,side,20,Component.empty(),hue);updateMessage();}
        void refresh(){value=hue;updateMessage();}
        protected void updateMessage(){setMessage(Component.literal("Hue: "+Math.round(value*360)));}
        protected void applyValue(){hue=(float)value;sync();}
    }
    private void sync(){syncing=true;hex.setValue(String.format("%06X",color()));syncing=false;hueSlider.refresh();valid=true;hex.setTextColor(0xffffffff);if(done!=null)done.active=true;}
    private void pick(double x,double y){saturation=(float)Math.clamp((x-left)/side,0,1);brightness=1-(float)Math.clamp((y-top)/side,0,1);sync();}
    public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick){if(e.button()==0&&e.x()>=left&&e.x()<=left+side&&e.y()>=top&&e.y()<=top+side){dragging=true;pick(e.x(),e.y());return true;}return super.mouseClicked(e,doubleClick);}
    public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){if(dragging){pick(e.x(),e.y());return true;}return super.mouseDragged(e,dx,dy);}
    public boolean mouseReleased(MouseButtonEvent e){dragging=false;return super.mouseReleased(e);}
    public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){super.extractRenderState(g,mx,my,dt);g.centeredText(font,title,width/2,10,0xffffffff);
        for(int x=0;x<side;x+=2)for(int y=0;y<side;y+=2)g.fill(left+x,top+y,left+Math.min(side,x+2),top+Math.min(side,y+2),Color.HSBtoRGB(hue,x/(float)side,1-y/(float)side));
        int x=left+Math.round(saturation*side),y=top+Math.round((1-brightness)*side);g.fill(x-2,y-2,x+3,y+3,0xff000000);g.fill(x-1,y-1,x+2,y+2,0xffffffff);
        g.fill(left+side+8,top,left+side+28,top+side,0xff000000|color());
    }
    public void onClose(){minecraft.gui.setScreen(parent);}
}
