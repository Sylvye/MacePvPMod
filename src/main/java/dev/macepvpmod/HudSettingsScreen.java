package dev.macepvpmod;
import java.io.IOException;
import java.util.*;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
final class HudSettingsScreen extends Screen {
    private final Screen parent; private HudConfig draft; private int selected,state=1; private boolean all=true,canvas,dragging,resizing;
    private String error="";private final Set<EditBox> invalid=new HashSet<>();private Button save;
    private final HudRenderer.Bounds[] bounds=new HudRenderer.Bounds[6];
    private static final String[] NAMES={"Pitch bar","Fall distance","Hit damage","Retotem warning","Healing warning","Attribute swap"};
    HudSettingsScreen(Screen parent,int selected){super(Component.literal("HUD"));this.parent=parent;this.selected=selected;draft=MacePvPMod.HUD_CONFIG.current();}
    private HudStyle style(){return draft.get(selected);}
    private void change(double x,double y,double scale,int color,int c2,int c3,int w,int t,double opacity){draft=draft.with(selected,style().edit(x,y,scale,color,c2,c3,w,t,opacity));}
    private void move(double dx,double dy){var s=style();
        if(canvas && bounds[selected]!=null){var b=bounds[selected];double x=Math.clamp(b.x()+dx,0,Math.max(0,width-b.width()));double y=Math.clamp(b.y()+dy,0,Math.max(0,height-b.height()));
            dx=x-(width*s.anchorX()-b.width()*s.alignX()+s.x());dy=y-(height*s.anchorY()-b.height()*s.alignY()+s.y());}
        change(s.x()+dx,s.y()+dy,s.scale(),s.color(),s.secondaryColor(),s.combinedColor(),s.width(),s.thickness(),s.opacity());}
    protected void init(){invalid.clear();
        if(canvas){addRenderableWidget(Button.builder(Component.literal("Back to controls"),b->{canvas=false;rebuildWidgets();}).bounds(width/2-80,height-24,160,20).build());return;}
        int w=Math.min(360,width-32);var rows=LinearLayout.vertical().spacing(6);
        rows.addChild(Button.builder(Component.literal("Drag / resize preview"),b->{canvas=true;rebuildWidgets();}).bounds(0,0,w,20).build());
        rows.addChild(Button.builder(Component.literal(all?"Preview: all elements":"Preview: selected only"),b->{all=!all;rebuildWidgets();}).bounds(0,0,w,20).build());
        LinearLayout elementRow=null;
        for(int i=0;i<NAMES.length;i++){
            if(i%2==0){elementRow=LinearLayout.horizontal().spacing(4);rows.addChild(elementRow);}
            int index=i;elementRow.addChild(Button.builder(Component.literal((selected==i?"▶ ":"")+NAMES[i]),b->{selected=index;rebuildWidgets();}).bounds(0,0,(w-4)/2,20).build());
        }
        var s=style();
        number(rows,"Horizontal offset",s.x(),-4000,4000,w,v->move(v-style().x(),0));
        number(rows,"Vertical offset",s.y(),-4000,4000,w,v->move(0,v-style().y()));
        number(rows,selected==0?"Width":"Scale",selected==0?s.width():s.scale(),selected==0?10:.5,selected==0?400:4,w,v->{var a=style();change(a.x(),a.y(),selected==0?a.scale():v,a.color(),a.secondaryColor(),a.combinedColor(),selected==0?(int)v:a.width(),a.thickness(),a.opacity());});
        if(selected==0){number(rows,"Thickness",s.thickness(),1,8,w,v->{var a=style();change(a.x(),a.y(),a.scale(),a.color(),a.secondaryColor(),a.combinedColor(),a.width(),(int)v,a.opacity());});rows.addChild(SettingsControls.slider("Opacity %",s.opacity()*100,5,100,1,w,v->{var a=style();change(a.x(),a.y(),a.scale(),a.color(),a.secondaryColor(),a.combinedColor(),a.width(),a.thickness(),v/100);}));}
        if(selected==4)rows.addChild(Button.builder(Component.literal("State: "+new String[]{"","Low health","Low saturation","Combined"}[state]),b->{state=state%3+1;rebuildWidgets();}).bounds(0,0,w,20).build());
        int c=selected==4&&state==2?s.secondaryColor():selected==4&&state==3?s.combinedColor():s.color();
        rows.addChild(Button.builder(Component.literal("Color ■ #"+String.format("%06X",c)).withColor(c),b->minecraft.gui.setScreen(new ColorPickerScreen(this,c,value->{var a=style();change(a.x(),a.y(),a.scale(),selected!=4||state==1?value:a.color(),selected==4&&state==2?value:a.secondaryColor(),selected==4&&state==3?value:a.combinedColor(),a.width(),a.thickness(),a.opacity());}))).bounds(0,0,w,20).build());
        rows.addChild(Button.builder(Component.literal("Reset selected element"),b->{draft=draft.with(selected,HudConfig.defaults().get(selected));rebuildWidgets();}).bounds(0,0,w,20).build());
        var scroll=new ScrollableLayout(minecraft,rows,Math.max(40,height-78));scroll.setMinWidth(w);scroll.arrangeElements();scroll.setX((width-scroll.getWidth())/2);scroll.setY(30);scroll.visitWidgets(this::addRenderableWidget);
        save=addRenderableWidget(Button.builder(Component.literal("Save"),b->{try{MacePvPMod.HUD_CONFIG.save(draft);onClose();}catch(IOException e){error="Could not save HUD settings.";}}).bounds(width/2-104,height-26,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(width/2+4,height-26,100,20).build());
    }
    private void number(LinearLayout rows,String label,double value,double min,double max,int w,DoubleConsumer setter){rows.addChild(new StringWidget(Component.literal(label+" ("+min+" to "+max+")"),font));var box=new EditBox(font,0,0,w,20,Component.literal(label));box.setValue(String.format(Locale.ROOT,"%.2f",value));box.setResponder(v->{try{double n=Double.parseDouble(v);if(!Double.isFinite(n)||n<min||n>max)throw new IllegalArgumentException();setter.accept(n);invalid.remove(box);box.setTextColor(0xffffffff);}catch(IllegalArgumentException e){invalid.add(box);box.setTextColor(0xffff7777);}if(save!=null)save.active=invalid.isEmpty();});rows.addChild(box);}
    private String sample(int i){var d=MacePvPMod.DAMAGE_CONFIG.current();var s=MacePvPMod.SURVIVAL_CONFIG.current();return switch(i){case 1->DamageText.format(d.fallTemplate(),12.5,18);case 2->DamageText.format(d.hitTemplate(),12.5,18);case 3->s.retotemText();case 5->AttributeSwaps.HUD_TEXT;default->state==1?s.healthText():state==2?s.saturationText():s.combinedText();};}
    public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){super.extractRenderState(g,mx,my,dt);
        if(!canvas){g.centeredText(font,title,width/2,12,0xffffffff);g.centeredText(font,invalid.isEmpty()?error:"Enter a number within the allowed range.",width/2,height-40,0xffff7777);return;}
        for(int i=0;i<NAMES.length;i++){bounds[i]=null;if(!all&&i!=selected)continue;bounds[i]=i==0?HudRenderer.pitch(g,draft.get(i),MacePvPMod.CONFIG.current(),MacePvPMod.CONFIG.current().targetPitch()):HudRenderer.text(g,sample(i),draft.get(i),i==4?state:0);}
        var b=bounds[selected];if(b!=null){int x=(int)b.x()-2,y=(int)b.y()-2,r=(int)(b.x()+b.width())+2,bot=(int)(b.y()+b.height())+2;g.fill(x,y,r,y+1,0xff55ffff);g.fill(x,bot,r,bot+1,0xff55ffff);g.fill(x,y,x+1,bot,0xff55ffff);g.fill(r,y,r+1,bot,0xff55ffff);g.fill(r-3,bot-3,r+4,bot+4,0xffffffff);}
        g.centeredText(font,NAMES[selected]+" • Drag to move • Corner to resize • Arrow keys to nudge",width/2,10,0xffffffff);
    }
    public boolean mouseClicked(MouseButtonEvent e,boolean twice){if(super.mouseClicked(e,twice))return true;if(!canvas||e.button()!=0)return false;var b=bounds[selected];if(b!=null&&Math.abs(e.x()-b.x()-b.width())<9&&Math.abs(e.y()-b.y()-b.height())<9){resizing=true;setFocused(null);return true;}for(int i=NAMES.length-1;i>=0;i--)if(bounds[i]!=null&&bounds[i].contains(e.x(),e.y())){selected=i;dragging=true;setFocused(null);return true;}return false;}
    public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){if(dragging){move(dx,dy);return true;}if(resizing){var s=style();change(s.x(),s.y(),selected==0?s.scale():s.scale()+dx/80,s.color(),s.secondaryColor(),s.combinedColor(),selected==0?s.width()+(int)Math.round(dx):s.width(),selected==0?s.thickness()+(int)Math.round(dy):s.thickness(),s.opacity());return true;}return super.mouseDragged(e,dx,dy);}
    public boolean mouseReleased(MouseButtonEvent e){dragging=resizing=false;return super.mouseReleased(e);}
    public boolean keyPressed(KeyEvent e){if(canvas&&e.key()>=262&&e.key()<=265){move(e.key()==262?1:e.key()==263?-1:0,e.key()==264?1:e.key()==265?-1:0);return true;}return super.keyPressed(e);}
    public void onClose(){if(canvas){canvas=false;rebuildWidgets();}else minecraft.gui.setScreen(parent);}
}
