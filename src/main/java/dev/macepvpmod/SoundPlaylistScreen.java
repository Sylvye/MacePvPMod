package dev.macepvpmod;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
final class SoundPlaylistScreen extends Screen {
    private final Screen parent;private final List<SoundEntry> entries;private final Consumer<List<SoundEntry>> apply;
    private SimpleSoundInstance preview;private String error="";
    SoundPlaylistScreen(Screen parent,List<SoundEntry> entries,Consumer<List<SoundEntry>> apply){super(Component.literal("Low health sound playlist"));this.parent=parent;this.entries=new ArrayList<>(entries);this.apply=apply;}
    static String name(String id){String path=id.contains(":")?id.substring(id.indexOf(':')+1):id;return path.replace('.',' ').replace('_',' ');}
    protected void init(){int w=Math.min(520,width-32);var rows=LinearLayout.vertical().spacing(6);
        rows.addChild(new StringWidget(Component.literal("Each beat plays the next entry. Empty list mutes warnings."),font));
        for(int i=0;i<entries.size();i++){int index=i;var e=entries.get(i);var row=LinearLayout.horizontal().spacing(4);
            row.addChild(Button.builder(Component.literal((i+1)+". "+name(e.sound())),b->minecraft.gui.setScreen(new SoundSelectorScreen(this,e.sound(),id->{var old=entries.get(index);entries.set(index,new SoundEntry(id,old.volume(),old.pitch()));}))).bounds(0,0,w-180,20).tooltip(Tooltip.create(Component.literal(e.sound()))).build());
            row.addChild(Button.builder(Component.literal("Play"),b->play(entries.get(index))).bounds(0,0,42,20).build());
            var up=Button.builder(Component.literal("↑"),b->{Collections.swap(entries,index,index-1);rebuildWidgets();}).bounds(0,0,24,20).build();up.active=i>0;row.addChild(up);
            var down=Button.builder(Component.literal("↓"),b->{Collections.swap(entries,index,index+1);rebuildWidgets();}).bounds(0,0,24,20).build();down.active=i+1<entries.size();row.addChild(down);
            row.addChild(Button.builder(Component.literal("Remove"),b->{entries.remove(index);rebuildWidgets();}).bounds(0,0,74,20).build());rows.addChild(row);
            var levels=LinearLayout.horizontal().spacing(4);
            levels.addChild(SettingsControls.slider("Volume %",e.volume()*100,0,100,1,(w-4)/2,v->{var old=entries.get(index);entries.set(index,new SoundEntry(old.sound(),v/100,old.pitch()));}));
            levels.addChild(SettingsControls.slider("Pitch",e.pitch(),.5,2,.05,(w-4)/2,v->{var old=entries.get(index);entries.set(index,new SoundEntry(old.sound(),old.volume(),v));}));rows.addChild(levels);
        }
        rows.addChild(Button.builder(Component.literal("Add sound"),b->minecraft.gui.setScreen(new SoundSelectorScreen(this,"minecraft:block.note_block.harp",id->entries.add(new SoundEntry(id,.5,1))))).bounds(0,0,w,20).build());
        rows.addChild(Button.builder(Component.literal("Reset playlist"),b->{entries.clear();entries.addAll(SoundEntry.legacy(.5,.5,1,1));rebuildWidgets();}).bounds(0,0,w,20).build());
        var scroll=new ScrollableLayout(minecraft,rows,Math.max(40,height-80));scroll.setMinWidth(w);scroll.arrangeElements();scroll.setX((width-scroll.getWidth())/2);scroll.setY(30);scroll.visitWidgets(this::addRenderableWidget);
        addRenderableWidget(Button.builder(Component.literal("Done"),b->{apply.accept(List.copyOf(entries));onClose();}).bounds(width/2-104,height-26,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(width/2+4,height-26,100,20).build());
    }
    void play(SoundEntry e){stop();var id=Identifier.tryParse(e.sound());if(id==null||!BuiltInRegistries.SOUND_EVENT.containsKey(id)){error="Sound unavailable: "+e.sound();return;}error="";preview=SimpleSoundInstance.forUI(BuiltInRegistries.SOUND_EVENT.getValue(id),(float)e.pitch(),(float)e.volume());minecraft.getSoundManager().play(preview);}
    private void stop(){if(preview!=null){minecraft.getSoundManager().stop(preview);preview=null;}}
    public void removed(){stop();super.removed();}
    public void onClose(){minecraft.gui.setScreen(parent);}
    public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){super.extractRenderState(g,x,y,dt);g.centeredText(font,title,width/2,12,0xffffffff);g.centeredText(font,error,width/2,height-40,0xffff7777);}
}
