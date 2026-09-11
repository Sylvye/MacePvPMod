package dev.macepvpmod;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ColorScaleEditorScreen extends Screen {
    private final Screen parent; private final Consumer<ColorScale> apply; private final String label; private final boolean gearDomain;
    private ColorMode mode; private int flatColor; private double minimum,maximum; private final List<GradientKey> keys;
    private final Set<EditBox> invalid=new HashSet<>(); private Button done; private int previewX,previewY,previewW;
    ColorScaleEditorScreen(Screen parent,String label,ColorScale scale,Consumer<ColorScale> apply) {
        this(parent,label,scale,false,apply);
    }
    ColorScaleEditorScreen(Screen parent,String label,ColorScale scale,boolean gearDomain,Consumer<ColorScale> apply) {
        super(Component.literal(label+" colors"));this.parent=parent;this.label=label;this.apply=apply;
        this.gearDomain=gearDomain;mode=scale.mode();flatColor=scale.flatColor();minimum=scale.minimum();maximum=scale.maximum();keys=new ArrayList<>(scale.keys());
    }
    private ColorScale value(){return new ColorScale(mode,flatColor,minimum,maximum,List.copyOf(keys));}
    private boolean valid(){try{value().validated();return invalid.isEmpty();}catch(IllegalArgumentException e){return false;}}
    protected void init(){invalid.clear();int w=Math.min(420,width-32);var rows=LinearLayout.vertical().spacing(5);
        rows.addChild(Button.builder(Component.literal("Mode: "+(mode==ColorMode.FLAT?"Flat":"Gradient")),b->{mode=mode==ColorMode.FLAT?ColorMode.GRADIENT:ColorMode.FLAT;rebuildWidgets();}).bounds(0,0,w,20).build());
        rows.addChild(Button.builder(Component.literal("Flat color ■ #"+String.format("%06X",flatColor)).withColor(flatColor),b->minecraft.gui.setScreen(new ColorPickerScreen(this,flatColor,c->{flatColor=c;rebuildWidgets();}))).bounds(0,0,w,20).build());
        if(gearDomain)rows.addChild(new StringWidget(Component.literal("Enemy gear currently previews this gradient from 0–20 damage.").withColor(0xffaab6c2),font));
        else {number(rows,"Domain minimum",minimum,w,n->minimum=n);number(rows,"Domain maximum",maximum,w,n->maximum=n);}
        rows.addChild(new StringWidget(Component.literal("Gradient keys"),font));
        for(int i=0;i<keys.size();i++) keyRow(rows,i,w);
        rows.addChild(Button.builder(Component.literal("Add color key"),b->{addKey();rebuildWidgets();}).bounds(0,0,w,20).build());
        var scroll=new ScrollableLayout(minecraft,rows,Math.max(40,height-112));scroll.setMinWidth(w);scroll.arrangeElements();scroll.setX((width-scroll.getWidth())/2);scroll.setY(30);scroll.visitWidgets(this::addRenderableWidget);
        previewW=w;previewX=(width-w)/2;previewY=height-72;
        done=addRenderableWidget(Button.builder(Component.literal("Use colors"),b->{apply.accept(value().validated());onClose();}).bounds(width/2-104,height-26,100,20).build());done.active=valid();
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(width/2+4,height-26,100,20).build());
    }
    private void number(LinearLayout rows,String label,double initial,int w,java.util.function.DoubleConsumer set){var box=new EditBox(font,0,0,w,20,Component.literal(label));box.setValue(fmt(initial));box.setResponder(s->{try{double n=Double.parseDouble(s);if(!Double.isFinite(n))throw new IllegalArgumentException();set.accept(n);invalid.remove(box);box.setTextColor(0xffffffff);}catch(IllegalArgumentException e){invalid.add(box);box.setTextColor(0xffff7777);}if(done!=null)done.active=valid();});rows.addChild(box);}
    private void keyRow(LinearLayout rows,int index,int w){GradientKey key=keys.get(index);var row=LinearLayout.horizontal().spacing(4);boolean endpoint=index==0||index==keys.size()-1;
        double displayMinimum=gearDomain?0:minimum,displayMaximum=gearDomain?20:maximum;
        if(endpoint)row.addChild(new StringWidget((w-72)/2,20,Component.literal(fmt(displayMinimum+(displayMaximum-displayMinimum)*key.position())),font));
        else {var position=new EditBox(font,0,0,(w-72)/2,20,Component.literal("Damage value"));position.setValue(fmt(displayMinimum+(displayMaximum-displayMinimum)*key.position()));position.setResponder(s->{try{double n=Double.parseDouble(s),p=(n-displayMinimum)/(displayMaximum-displayMinimum);if(!Double.isFinite(p)||p<=keys.get(index-1).position()||p>=keys.get(index+1).position())throw new IllegalArgumentException();keys.set(index,new GradientKey(p,keys.get(index).color()));invalid.remove(position);position.setTextColor(0xffffffff);}catch(Exception e){invalid.add(position);position.setTextColor(0xffff7777);}if(done!=null)done.active=valid();});row.addChild(position);}
        row.addChild(Button.builder(Component.literal("■ #"+String.format("%06X",key.color())).withColor(key.color()),b->minecraft.gui.setScreen(new ColorPickerScreen(this,keys.get(index).color(),c->{keys.set(index,new GradientKey(keys.get(index).position(),c));rebuildWidgets();}))).bounds(0,0,(w-72)/2,20).build());
        row.addChild(Button.builder(Component.literal(endpoint?"—":"Remove"),b->{if(!endpoint){keys.remove(index);rebuildWidgets();}}).bounds(0,0,68,20).build()).active=!endpoint;rows.addChild(row);
    }
    private void addKey(){int gap=0;double size=-1;for(int i=0;i<keys.size()-1;i++){double s=keys.get(i+1).position()-keys.get(i).position();if(s>size){size=s;gap=i;}}double p=(keys.get(gap).position()+keys.get(gap+1).position())/2;keys.add(gap+1,new GradientKey(p,ColorScale.rgb(keys.get(gap).color(),keys.get(gap+1).color(),.5)));}
    private static String fmt(double n){return String.format(Locale.ROOT,"%.2f",n);}
    public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){super.extractRenderState(g,x,y,dt);g.centeredText(font,title,width/2,12,0xffffffff);
        double displayMinimum=gearDomain?0:minimum,displayMaximum=gearDomain?20:maximum;ColorScale scale;try{scale=value().validated().withDomain(displayMinimum,displayMaximum);}catch(Exception e){scale=null;}for(int px=0;px<previewW;px++){double v=displayMinimum+(displayMaximum-displayMinimum)*px/Math.max(1,previewW-1);int c=scale==null?flatColor:scale.color(v);g.fill(previewX+px,previewY,previewX+px+1,previewY+14,0xff000000|c);}g.centeredText(font,valid()?label+" preview":"Enter an ordered, valid domain and keys",width/2,previewY+17,valid()?0xffbbbbbb:0xffff7777);}
    public void onClose(){minecraft.gui.setScreen(parent);}
}
