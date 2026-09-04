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
final class SoundSelectorScreen extends Screen {
        private final Screen parent; private SimpleSoundInstance preview;private final Consumer<String> apply;private String query="",selected;private final List<AbstractWidget> results=new ArrayList<>();
        SoundSelectorScreen(Screen parent,String selected,Consumer<String> apply){super(Component.literal("Choose sound"));this.parent=parent;this.selected=selected;this.apply=apply;}
        protected void init(){results.clear();int w=Math.min(520,width-32);var search=new EditBox(font,(width-w)/2,30,w,20,Component.literal("Search sounds"));search.setHint(Component.literal("Search sound names or IDs"));search.setValue(query);search.setResponder(v->{query=v;refresh();});addRenderableWidget(search);
            addRenderableWidget(Button.builder(Component.literal("Use sound"),b->{apply.accept(selected);onClose();}).bounds(width/2-156,height-26,100,20).build());
            addRenderableWidget(Button.builder(Component.literal("Play"),b->play()).bounds(width/2-50,height-26,100,20).build());
            addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(width/2+56,height-26,100,20).build());refresh();}
        private void refresh(){for(var widget:results)removeWidget(widget);results.clear();int w=Math.min(520,width-32);var rows=LinearLayout.vertical().spacing(3);
            BuiltInRegistries.SOUND_EVENT.keySet().stream().map(Object::toString).sorted().filter(id->(id+" "+SoundPlaylistScreen.name(id)).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).forEach(id->rows.addChild(Button.builder(Component.literal((id.equals(selected)?"▶ ":"")+SoundPlaylistScreen.name(id)),b->{selected=id;refresh();}).bounds(0,0,w,20).tooltip(Tooltip.create(Component.literal(id))).build()));
            var scroll=new ScrollableLayout(minecraft,rows,Math.max(30,height-105));scroll.setMinWidth(w);scroll.arrangeElements();scroll.setX((width-scroll.getWidth())/2);scroll.setY(56);scroll.visitWidgets(widget->{results.add(widget);addRenderableWidget(widget);});}
        private void play(){stop();var id=Identifier.tryParse(selected);if(id!=null&&BuiltInRegistries.SOUND_EVENT.containsKey(id)){preview=SimpleSoundInstance.forUI(BuiltInRegistries.SOUND_EVENT.getValue(id),1,.5f);minecraft.getSoundManager().play(preview);}}
        private void stop(){if(preview!=null){minecraft.getSoundManager().stop(preview);preview=null;}}
        public void removed(){stop();super.removed();}
        public void onClose(){minecraft.gui.setScreen(parent);}
        public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){super.extractRenderState(g,x,y,dt);g.centeredText(font,title,width/2,12,0xffffffff);g.centeredText(font,selected,width/2,height-40,0xffbbbbbb);}
    }
