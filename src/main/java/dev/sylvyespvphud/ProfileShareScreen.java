package dev.sylvyespvphud;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ProfileShareScreen extends Screen {
    private final Screen parent;private final String profileId;private String status="";
    ProfileShareScreen(Screen parent,String profileId){super(Component.literal("Share HUD Profile"));this.parent=parent;this.profileId=profileId;}
    protected void init(){int x=width/2-110,y=height/2-42;String unicode=SylvyesPvPHud.PROFILES.exportProfile(profileId),ascii=SylvyesPvPHud.PROFILES.exportProfileAscii(profileId);List<String> chunks=SylvyesPvPHud.PROFILES.exportProfileDiscordChunks(profileId);
        addRenderableWidget(Button.builder(Component.literal("Copy shortest ("+unicode.length()+")"),b->{minecraft.keyboardHandler.setClipboard(unicode);status="Shortest profile copied.";}).bounds(x,y,220,20).build());y+=24;
        addRenderableWidget(Button.builder(Component.literal("Copy ASCII ("+ascii.length()+")"),b->{minecraft.keyboardHandler.setClipboard(ascii);status="ASCII profile copied.";}).bounds(x,y,220,20).build());y+=24;
        if(chunks.size()>1)addRenderableWidget(Button.builder(Component.literal("Copy Discord chunks ("+chunks.size()+")"),b->{minecraft.keyboardHandler.setClipboard(String.join("\n",chunks));status="Discord chunks copied; send each line separately.";}).bounds(x,y,220,20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(width/2-40,height-28,80,20).build());}
    public void onClose(){minecraft.gui.setScreen(parent);}
    public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){g.fill(0,0,width,height,0xff0e141b);super.extractRenderState(g,mx,my,dt);String unicode=SylvyesPvPHud.PROFILES.exportProfile(profileId),ascii=SylvyesPvPHud.PROFILES.exportProfileAscii(profileId);g.text(font,"SHARE PROFILE",width/2-110,height/2-72,0xff62d8ff);g.text(font,unicode.length()<=ProfileClipboard.DISCORD_LIMIT?"Shortest fits a standard Discord message.":"Shortest requires Discord chunks.",width/2-110,height/2-57,unicode.length()<=2000?0xff77dd99:0xffffc766);g.text(font,"ASCII: "+ascii.length()+" characters",width/2-110,height/2+40,0xff9ba8b5);if(!status.isEmpty())g.text(font,status,width/2-110,height-48,0xff77dd99);}
}
