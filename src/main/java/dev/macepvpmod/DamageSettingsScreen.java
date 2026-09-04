package dev.macepvpmod;
import java.io.IOException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class DamageSettingsScreen extends Screen {
    private final Screen parent;private final DamageConfig original;
    private boolean fall,hit,calculated;private double fallThreshold;private int seconds;private String fallText,hitText,error="";private Button save;
    public DamageSettingsScreen(Screen parent){super(Component.literal("Damage Counter"));this.parent=parent;original=MacePvPMod.DAMAGE_CONFIG.current();read(original);}
    private void read(DamageConfig c){fall=c.fallEnabled();hit=c.hitEnabled();calculated=c.calculatedDamage();fallThreshold=c.fallThreshold();seconds=c.hitSeconds();fallText=c.fallTemplate();hitText=c.hitTemplate();}
    private String validation(){String e=DamageText.error(fallText,false);return e.isEmpty()?DamageText.error(hitText,true):e;}
    protected void init(){int w=Math.min(420,width-32);var rows=LinearLayout.vertical().spacing(6);
        rows.addChild(Button.builder(Component.literal("Fall distance: "+(fall?"On":"Off")),b->{fall=!fall;rebuildWidgets();}).bounds(0,0,w,20).build());
        rows.addChild(SettingsControls.slider("Fall threshold (blocks)",fallThreshold,0,20,.1,w,v->fallThreshold=v));
        template(rows,false,w);
        rows.addChild(Button.builder(Component.literal("Hit damage: "+(hit?"On":"Off")),b->{hit=!hit;rebuildWidgets();}).bounds(0,0,w,20).build());
        template(rows,true,w);
        rows.addChild(SettingsControls.slider("Hit seconds",seconds,1,10,1,w,v->seconds=(int)v));
        rows.addChild(Button.builder(Component.literal("Damage: "+(calculated?"Calculated":"Reported")),b->{calculated=!calculated;rebuildWidgets();}).bounds(0,0,w,20).tooltip(Tooltip.create(Component.literal("Reported: server health lost. Calculated: raw damage before defenses."))).build());
        rows.addChild(Button.builder(Component.literal("Edit appearance in HUD"),b->minecraft.gui.setScreen(new HudSettingsScreen(this,1))).bounds(0,0,w,20).build());
        rows.addChild(Button.builder(Component.literal("Reset defaults"),b->{read(DamageConfig.defaults());rebuildWidgets();}).bounds(0,0,w,20).build());
        var scroll=new ScrollableLayout(minecraft,rows,Math.max(40,height-78));scroll.setMinWidth(w);scroll.arrangeElements();scroll.setX((width-scroll.getWidth())/2);scroll.setY(30);scroll.visitWidgets(this::addRenderableWidget);
        save=addRenderableWidget(Button.builder(Component.literal("Save"),b->{try{MacePvPMod.DAMAGE_CONFIG.save(new DamageConfig(1,fall,original.fallColor(),original.fallSize(),original.fallX(),original.fallY(),hit,original.hitColor(),original.hitSize(),original.hitX(),original.hitY(),seconds,calculated,fallText,hitText,fallThreshold));onClose();}catch(IOException e){error="Could not save settings.";}}).bounds(width/2-104,height-26,100,20).build());save.active=validation().isEmpty();
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(width/2+4,height-26,100,20).build());
    }
    private void template(LinearLayout rows,boolean isHit,int w){
        rows.addChild(new StringWidget(Component.literal(isHit?"{damage}: hit damage • {blocks}: fall at attack":"{blocks}: current fall distance"),font));
        var box=new EditBox(font,0,0,w,20,Component.literal(isHit?"Hit message":"Fall message"));box.setMaxLength(160);box.setValue(isHit?hitText:fallText);
        var preview=new StringWidget(w,20,Component.literal(DamageText.format(box.getValue(),12.5,18)),font);
        box.setResponder(v->{if(isHit)hitText=v;else fallText=v;preview.setMessage(Component.literal(DamageText.format(v,12.5,18)));if(save!=null)save.active=validation().isEmpty();});rows.addChild(box);
        var buttons=LinearLayout.horizontal().spacing(4);
        buttons.addChild(Button.builder(Component.literal("Insert {blocks}"),b->box.insertText("{blocks}")).bounds(0,0,(w-4)/2,20).build());
        if(isHit)buttons.addChild(Button.builder(Component.literal("Insert {damage}"),b->box.insertText("{damage}")).bounds(0,0,(w-4)/2,20).build());rows.addChild(buttons);rows.addChild(preview);
    }
    public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){super.extractRenderState(g,x,y,dt);g.centeredText(font,title,width/2,12,0xffffffff);g.centeredText(font,validation().isEmpty()?error:validation(),width/2,height-40,0xffff7777);}
    public void onClose(){minecraft.gui.setScreen(parent);}
}
