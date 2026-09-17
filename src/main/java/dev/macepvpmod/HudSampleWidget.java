package dev.macepvpmod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Non-interactive centered appearance sample for the HUD Studio controls page. */
final class HudSampleWidget extends Button {
    private final SettingsSession draft;
    private final int element;
    HudSampleWidget(int width,int height,SettingsSession draft,int element) {
        super(0,0,width,height,Component.empty(),b->{},DEFAULT_NARRATION);
        this.draft=draft;this.element=element;active=false;
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float dt) {
        g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),0xff111922);
        g.fill(getX(),getY(),getX()+getWidth(),getY()+1,0xff334353);
        g.fill(getX(),getY()+getHeight()-1,getX()+getWidth(),getY()+getHeight(),0xff334353);
        g.pose().pushMatrix();g.pose().translate(getX(),getY());
        HudPreview.render(g,draft,element,HudPreview.centered(draft.hud.get(element)),getWidth(),getHeight(),false);
        g.pose().popMatrix();
    }
}
