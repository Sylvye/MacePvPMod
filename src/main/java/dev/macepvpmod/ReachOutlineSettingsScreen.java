package dev.macepvpmod;

import java.io.IOException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ReachOutlineSettingsScreen extends Screen {
    private final Screen parent;
    private boolean enabled; private int color, thickness; private double intensity; private String error = "";
    public ReachOutlineSettingsScreen(Screen parent) {
        super(Component.literal("MacePvPMod • Reach Outlines")); this.parent = parent;
        var c = MacePvPMod.REACH_OUTLINE_CONFIG.current(); enabled=c.enabled(); color=c.color(); intensity=c.intensity(); thickness=c.thickness();
    }
    @Override protected void init() {
        int w=Math.min(320,width-20), x=(width-w)/2;
        addRenderableWidget(Button.builder(Component.literal("Enabled: "+(enabled?"On":"Off")), new Button.OnPress(){
            public void onPress(Button b){enabled=!enabled;b.setMessage(Component.literal("Enabled: "+(enabled?"On":"Off")));}
        }).bounds(x,42,w,20).build());
        var alpha=SettingsControls.slider("Intensity",intensity,.02,1,.01,w,v->intensity=v); alpha.setPosition(x,68); addRenderableWidget(alpha);
        var thick=SettingsControls.slider("Thickness",thickness,1,5,1,w,v->thickness=(int)v); thick.setPosition(x,94); addRenderableWidget(thick);
        addRenderableWidget(Button.builder(Component.literal(String.format("Color: #%06X",color)),b->minecraft.gui.setScreen(new ColorPickerScreen(this,color,c->{color=c;rebuildWidgets();}))).bounds(x,120,w,20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset defaults"),b->{var c=ReachOutlineConfig.defaults();enabled=c.enabled();color=c.color();intensity=c.intensity();thickness=c.thickness();rebuildWidgets();}).bounds(x,146,w,20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"),b->{try{MacePvPMod.REACH_OUTLINE_CONFIG.save(new ReachOutlineConfig(1,enabled,color,intensity,thickness));onClose();}catch(IOException e){error="Couldn't save. Check config folder permissions.";}}).bounds(x,height-28,(w-8)/2,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(x+(w+8)/2,height-28,(w-8)/2,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){super.extractRenderState(g,mx,my,dt);g.centeredText(font,title,width/2,18,0xffffffff);if(!error.isEmpty())g.centeredText(font,error,width/2,height-42,0xffff8888);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
