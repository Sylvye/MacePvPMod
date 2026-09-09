package dev.macepvpmod;

import java.io.IOException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ReachOutlineSettingsScreen extends Screen {
    private final Screen parent;
    private boolean enabled, topFacesOnly; private int color, thickness; private double intensity, minimumReachableArea;
    private HardToReachMode hardToReachMode; private String error = "";
    public ReachOutlineSettingsScreen(Screen parent) {
        super(Component.literal("MacePvPMod • Reach Outlines")); this.parent = parent;
        apply(MacePvPMod.REACH_OUTLINE_CONFIG.current());
    }
    @Override protected void init() {
        int w=Math.min(320,width-20), x=(width-w)/2;
        addRenderableWidget(Button.builder(Component.literal("Enabled: "+(enabled?"On":"Off")), new Button.OnPress(){
            public void onPress(Button b){enabled=!enabled;b.setMessage(Component.literal("Enabled: "+(enabled?"On":"Off")));}
        }).bounds(x,34,w,20).build());
        addRenderableWidget(Button.builder(Component.literal("Top faces only: "+(topFacesOnly?"On":"Off")),b->{topFacesOnly=!topFacesOnly;b.setMessage(Component.literal("Top faces only: "+(topFacesOnly?"On":"Off")));}).bounds(x,58,w,20).build());
        addRenderableWidget(Button.builder(modeLabel(),b->{hardToReachMode=hardToReachMode.next();b.setMessage(modeLabel());}).bounds(x,82,w,20).build());
        var area=SettingsControls.slider("Minimum reachable area %",minimumReachableArea*100,1,100,1,w,v->minimumReachableArea=v/100); area.setPosition(x,106); addRenderableWidget(area);
        var alpha=SettingsControls.slider("Intensity",intensity,.02,1,.01,w,v->intensity=v); alpha.setPosition(x,130); addRenderableWidget(alpha);
        var thick=SettingsControls.slider("Thickness",thickness,1,ReachOutlineConfig.MAX_THICKNESS,1,w,v->thickness=(int)v); thick.setPosition(x,154); addRenderableWidget(thick);
        int half=(w-4)/2;
        addRenderableWidget(Button.builder(Component.literal(String.format("Color: #%06X",color)),b->minecraft.gui.setScreen(new ColorPickerScreen(this,color,c->{color=c;rebuildWidgets();}))).bounds(x,178,half,20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset defaults"),b->{apply(ReachOutlineConfig.defaults());rebuildWidgets();}).bounds(x+half+4,178,half,20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"),b->{try{MacePvPMod.REACH_OUTLINE_CONFIG.save(new ReachOutlineConfig(1,enabled,color,intensity,thickness,topFacesOnly,hardToReachMode,minimumReachableArea));onClose();}catch(IOException e){error="Couldn't save. Check config folder permissions.";}}).bounds(x,height-28,(w-8)/2,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(x+(w+8)/2,height-28,(w-8)/2,20).build());
    }
    private void apply(ReachOutlineConfig c){enabled=c.enabled();color=c.color();intensity=c.intensity();thickness=c.thickness();topFacesOnly=c.topFacesOnly();hardToReachMode=c.hardToReachMode();minimumReachableArea=c.minimumReachableArea();}
    private Component modeLabel(){return Component.literal("Hard-to-reach faces: "+hardToReachMode.label());}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){super.extractRenderState(g,mx,my,dt);g.centeredText(font,title,width/2,18,0xffffffff);if(!error.isEmpty())g.centeredText(font,error,width/2,height-42,0xffff8888);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
}
