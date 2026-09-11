package dev.macepvpmod;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Compact, non-interactive preview that cycles through every selected item. */
final class RotatingItemPreview extends Button {
    private final List<SurvivalItemRule> items;
    RotatingItemPreview(List<SurvivalItemRule> items){super(0,0,40,24,Component.empty(),b->{},DEFAULT_NARRATION);this.items=List.copyOf(items);active=false;}
    @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float dt){
        g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),0xff202a35);
        if(items.isEmpty()){g.centeredText(net.minecraft.client.Minecraft.getInstance().font,"—",getX()+getWidth()/2,getY()+8,0xff77818c);return;}
        int index=(int)((System.currentTimeMillis()/900)%items.size());
        g.item(SurvivalItems.icon(items.get(index)),getX()+4,getY()+4);
        g.text(net.minecraft.client.Minecraft.getInstance().font,(index+1)+"/"+items.size(),getX()+21,getY()+8,0xffaab6c2);
    }
}
