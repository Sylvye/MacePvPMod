package dev.macepvpmod;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
public final class ClientSmokeTest implements FabricClientGameTest {
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
    private static java.util.stream.Stream<GuiEventListener> descendants(ContainerEventHandler parent){return parent.children().stream().flatMap(child->java.util.stream.Stream.concat(java.util.stream.Stream.of(child),child instanceof ContainerEventHandler container?descendants(container):java.util.stream.Stream.empty()));}
    private static Button button(net.minecraft.client.Minecraft mc,String label){return descendants(mc.gui.screen()).filter(w->w instanceof Button b&&b.getMessage().getString().equals(label)).map(Button.class::cast).findFirst().orElseThrow(()->new AssertionError("Missing button: "+label));}
    private static void click(ClientGameTestContext context,String label){context.runOnClient(mc->button(mc,label).onPress(null));}
    public void runTest(ClientGameTestContext context){
        context.runOnClient(mc->{try{MacePvPMod.CONFIG.save(PitchConfig.defaults());MacePvPMod.DAMAGE_CONFIG.save(DamageConfig.defaults());MacePvPMod.HUD_CONFIG.save(HudConfig.defaults());}catch(Exception e){throw new RuntimeException(e);}});
        context.setScreen(()->new PitchSettingsScreen(null));click(context,"Enabled: On");context.runOnClient(mc->check(MacePvPMod.CONFIG.current().enabled(),"Draft leaked"));click(context,"Cancel");
        context.setScreen(()->new PitchSettingsScreen(null));click(context,"Advanced settings");click(context,"Third person: Off");click(context,"Save");context.runOnClient(mc->check(MacePvPMod.CONFIG.current().thirdPerson(),"Behavior save failed"));
        context.setScreen(()->new DamageSettingsScreen(null));
        context.runOnClient(mc->{var box=descendants(mc.gui.screen()).filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();box.setValue("{unknown}");check(!button(mc,"Save").active,"Invalid template accepted");box.setValue("Fall {blocks}");check(button(mc,"Save").active,"Valid template rejected");});
        click(context,"Damage: Reported");click(context,"Save");context.runOnClient(mc->{check(MacePvPMod.DAMAGE_CONFIG.current().calculatedDamage(),"Mode not saved");check(MacePvPMod.DAMAGE_CONFIG.current().fallTemplate().equals("Fall {blocks}"),"Template not saved");});
        context.setScreen(()->new HudSettingsScreen(null,0));
        context.runOnClient(mc->{var box=descendants(mc.gui.screen()).filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();box.setValue("oops");check(!button(mc,"Save").active,"Invalid position accepted");box.setValue("25");check(MacePvPMod.HUD_CONFIG.current().pitch().x()==0,"HUD draft leaked");});click(context,"Cancel");
        context.setScreen(()->new ColorPickerScreen(null,0xff0000,c->{}));context.runOnClient(mc->{var box=descendants(mc.gui.screen()).filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();box.setValue("oops");check(!button(mc,"Use color").active,"Invalid color accepted");box.setValue("ABCDEF");check(button(mc,"Use color").active,"Valid color rejected");});context.waitTick();context.takeScreenshot("color-picker");click(context,"Cancel");
        context.setScreen(()->new HudSettingsScreen(null,1));click(context,"Drag / resize preview");context.waitTick();
        context.runOnClient(mc->{
            var screen=mc.gui.screen();var original=MacePvPMod.HUD_CONFIG.current().fall();
            var bounds=HudRenderer.textBounds("Fall 12.5",original,screen.width,screen.height);
            var info=new net.minecraft.client.input.MouseButtonInfo(0,0);
            var press=new net.minecraft.client.input.MouseButtonEvent(bounds.x()+2,bounds.y()+2,info);
            check(screen.mouseClicked(press,false),"HUD drag target missed");
            var drag=new net.minecraft.client.input.MouseButtonEvent(press.x()+30,press.y()+20,info);
            check(screen.mouseDragged(drag,30,20),"HUD did not drag");screen.mouseReleased(drag);
            check(MacePvPMod.HUD_CONFIG.current().fall().equals(original),"Dragging leaked draft");
        });click(context,"Back to controls");click(context,"Save");
        context.runOnClient(mc->{check(MacePvPMod.HUD_CONFIG.current().fall().x()==30,"Drag x not saved");check(MacePvPMod.HUD_CONFIG.current().fall().y()==34,"Drag y not saved");});
        context.setScreen(()->new HudSettingsScreen(null,1));click(context,"Drag / resize preview");context.waitTick();
        context.runOnClient(mc->{
            var screen=mc.gui.screen();var bounds=HudRenderer.textBounds("Fall 12.5",MacePvPMod.HUD_CONFIG.current().fall(),screen.width,screen.height);
            var info=new net.minecraft.client.input.MouseButtonInfo(0,0);var press=new net.minecraft.client.input.MouseButtonEvent(bounds.x()+bounds.width(),bounds.y()+bounds.height(),info);
            check(screen.mouseClicked(press,false),"Resize handle missed");var drag=new net.minecraft.client.input.MouseButtonEvent(press.x()+40,press.y(),info);check(screen.mouseDragged(drag,40,0),"HUD did not resize");screen.mouseReleased(drag);
        });click(context,"Back to controls");click(context,"Save");context.runOnClient(mc->check(MacePvPMod.HUD_CONFIG.current().fall().scale()==1.5,"Resize not saved"));
        for(int scale:new int[]{1,2,3}){
            context.runOnClient(mc->{mc.options.guiScale().set(scale);mc.resizeGui();});
            context.setScreen(()->new HudSettingsScreen(null,1));context.waitTick();context.takeScreenshot("hud-controls-"+scale);click(context,"Drag / resize preview");context.waitTick();context.takeScreenshot("hud-global-"+scale);
            context.runOnClient(mc->{var screen=mc.gui.screen();screen.keyPressed(new net.minecraft.client.input.KeyEvent(262,0,0));});click(context,"Back to controls");click(context,"Save");context.runOnClient(mc->check(MacePvPMod.HUD_CONFIG.current().fall().x()>0,"Nudge not saved"));
            context.setScreen(()->new SoundPlaylistScreen(null,SoundEntry.legacy(.5,.5,1,1),list->{}));context.waitTick();context.takeScreenshot("sound-playlist-"+scale);
            click(context,"Add sound");context.waitTick();context.takeScreenshot("sound-selector-"+scale);click(context,"Cancel");click(context,"Cancel");
            context.setScreen(()->new DamageSettingsScreen(null));context.waitTick();context.takeScreenshot("damage-templates-"+scale);
        }
        context.runOnClient(mc->{try{MacePvPMod.CONFIG.save(PitchConfig.defaults());MacePvPMod.DAMAGE_CONFIG.save(DamageConfig.defaults());MacePvPMod.HUD_CONFIG.save(HudConfig.defaults());}catch(Exception e){throw new RuntimeException(e);}});
        context.setScreen(()->new AttributeSwapSettingsScreen(null));click(context,"Edit in HUD");
        context.runOnClient(mc->check(button(mc,"▶ Attribute swap") != null,"Attribute swap shortcut selected wrong element"));
        click(context,"Drag / resize preview");context.waitTick();context.takeScreenshot("attribute-swap-hud-preview");
        context.setScreen(()->null);
    }
}
