package dev.sylvyespvphud;

import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;

/** Focused world rendering and draft/profile integration checks. */
public final class ReachOutlinesSmokeTest implements FabricClientGameTest {
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    private static Object field(Object object,Class<?> type,String name) {
        try {var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static ReachOutlines.Geometry geometry(String name) { return (ReachOutlines.Geometry)field(null,ReachOutlines.class,name); }
    private static SettingsSession draft(Screen screen) { return (SettingsSession)field(screen,SettingsScreen.class,"draft"); }
    private static java.util.stream.Stream<GuiEventListener> descendants(ContainerEventHandler parent) {
        return parent.children().stream().flatMap(child->java.util.stream.Stream.concat(java.util.stream.Stream.of(child),
                child instanceof ContainerEventHandler container?descendants(container):java.util.stream.Stream.empty()));
    }
    private static void click(Screen screen,String label) {
        descendants(screen).filter(w->w instanceof Button b&&b.getMessage().getString().equals(label))
                .map(Button.class::cast).findFirst().orElseThrow(()->new AssertionError("Missing button: "+label)).onPress(null);
    }
    private static AbstractWidget control(Screen screen,String label) {
        var text=descendants(screen).filter(w->w instanceof StringWidget t&&t.getMessage().getString().equals(label))
                .map(StringWidget.class::cast).findFirst().orElseThrow();
        return descendants(screen).filter(w->w instanceof AbstractWidget a&&!(w instanceof StringWidget)
                &&a.getX()>text.getX()&&Math.abs(a.getY()-text.getY())<=1).map(AbstractWidget.class::cast).findFirst().orElseThrow();
    }
    private static ReachOutlineConfig config(OutlineRenderMode regular,OutlineRenderMode liquid,int limit,boolean enabled) {
        var l=new LiquidSourceConfig(enabled,true,true,limit,liquid,0xff8822,.55,5,false,HardToReachMode.RENDER_FULL,.5);
        return new ReachOutlineConfig(1,true,0x33ff88,.35,4,false,HardToReachMode.RENDER_FULL,.5,regular,l);
    }
    private static void refresh(Minecraft mc) {
        try {var f=ReachOutlines.class.getDeclaredField("cachedTick");f.setAccessible(true);f.setLong(null,Long.MIN_VALUE);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
        ReachOutlines.tick(mc);
    }
    private static void fixture(Minecraft mc,BlockPos base) {
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=-1;y<=4;y++)
            mc.level.setBlock(base.offset(x,y,z),(y==-1?Blocks.STONE:Blocks.AIR).defaultBlockState(),3);
        for(int x=-1;x<=1;x++)for(int z=0;z<=2;z++)mc.level.setBlock(base.offset(x,0,z),Blocks.WATER.defaultBlockState(),3);
        mc.level.setBlock(base.offset(2,0,0),Blocks.LAVA.defaultBlockState(),3);
        mc.player.getAbilities().flying=true;
        mc.player.setPos(base.getX()+.5,base.getY()+1,base.getZ()-2.5);
        mc.player.setYRot(0);mc.player.setXRot(35);
        mc.player.setDeltaMovement(0,0,0);
    }

    @Override public void runTest(ClientGameTestContext context) {
        var original=new AtomicReference<ProfileSettings>();var base=new AtomicReference<BlockPos>();
        context.runOnClient(mc->original.set(ProfileSettings.current()));
        try(var world=context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.runOnClient(mc->{base.set(mc.player.blockPosition().above(8));fixture(mc,base.get());});
            for(OutlineRenderMode mode:OutlineRenderMode.values()) {
                context.runOnClient(mc->{
                    fixture(mc,base.get());SylvyesPvPHud.REACH_OUTLINE_CONFIG.activate(config(mode,mode,2,true));refresh(mc);
                    var regular=geometry("cachedRegular");var liquid=geometry("cachedLiquids");
                    check(!regular.equals(ReachOutlines.Geometry.EMPTY),"Regular outlines missing");
                    check(mode==OutlineRenderMode.WIREFRAME?!regular.edges().isEmpty()&&!liquid.edges().isEmpty():!regular.quads().isEmpty()&&!liquid.quads().isEmpty(),"Mode produced no geometry: "+mode);
                    check(mode==OutlineRenderMode.WIREFRAME?liquid.quads().isEmpty():liquid.edges().isEmpty(),"Modes overlapped");
                    var c=SylvyesPvPHud.REACH_OUTLINE_CONFIG.current();
                    var candidates=new java.util.ArrayList<LiquidSourceOutlines.Candidate>();
                    for(int x=-1;x<=2;x++)for(int z=0;z<=2;z++) {
                        var pos=base.get().offset(x,0,z);var state=mc.level.getBlockState(pos);
                        if(!LiquidSourceOutlines.eligibleSource(state,c.liquidSources()))continue;
                        var shape=state.getFluidState().getShape(mc.level,pos);
                        candidates.add(new LiquidSourceOutlines.Candidate(pos,shape,LiquidSourceOutlines.distanceSquared(mc.player.getEyePosition(),shape.bounds().move(pos))));
                    }
                    check(LiquidSourceOutlines.selectSources(mc.level,net.minecraft.world.phys.shapes.CollisionContext.of(mc.player),mc.player.getEyePosition(),mc.player.blockInteractionRange(),c.liquidSources(),candidates).size()==2,"Pool count cap failed");
                });
                context.waitTicks(3);context.takeScreenshot("reach-outlines-"+mode.name().toLowerCase());
            }
            context.runOnClient(mc->{
                var pos=base.get();var eye=new net.minecraft.world.phys.Vec3(pos.getX()+.5,pos.getY()+.4,pos.getZ()-2);
                var target=mc.level.getFluidState(pos).getShape(mc.level,pos).bounds();
                mc.level.setBlock(pos.north(),Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,4),3);
                check(LiquidSourceOutlines.reachableFaces(mc.level,net.minecraft.world.phys.shapes.CollisionContext.empty(),pos,target,eye,4,LiquidSourceConfig.defaults())!=null,"Flow blocked bucket ray");
                mc.level.setBlock(pos.north(),Blocks.STONE.defaultBlockState(),3);
                check(LiquidSourceOutlines.reachableFaces(mc.level,net.minecraft.world.phys.shapes.CollisionContext.empty(),pos,target,eye,4,LiquidSourceConfig.defaults())==null,"Solid obstacle failed to block source");
                mc.level.setBlock(pos.north(),Blocks.WATER.defaultBlockState(),3);
                check(LiquidSourceOutlines.reachableFaces(mc.level,net.minecraft.world.phys.shapes.CollisionContext.empty(),pos,target,eye,4,LiquidSourceConfig.defaults())==null,"Nearer source failed to block source");
                mc.player.setPos(pos.getX()+30,pos.getY()+1,pos.getZ());refresh(mc);
                check(geometry("cachedLiquids").equals(ReachOutlines.Geometry.EMPTY),"Out-of-reach sources remained cached");
                fixture(mc,pos);SylvyesPvPHud.REACH_OUTLINE_CONFIG.activate(config(OutlineRenderMode.WIREFRAME,OutlineRenderMode.TINT,8,false));refresh(mc);
                check(geometry("cachedLiquids").equals(ReachOutlines.Geometry.EMPTY),"Submodule switch retained liquid geometry");
                check(!geometry("cachedRegular").equals(ReachOutlines.Geometry.EMPTY),"Submodule switch disabled regular outlines");
                SylvyesPvPHud.REACH_OUTLINE_CONFIG.activate(config(OutlineRenderMode.TINT,OutlineRenderMode.WIREFRAME,8,true).withEnabled(false));refresh(mc);
                check(geometry("cachedLiquids").equals(ReachOutlines.Geometry.EMPTY)&&geometry("cachedRegular").equals(ReachOutlines.Geometry.EMPTY),"Master switch retained geometry");
                SylvyesPvPHud.REACH_OUTLINE_CONFIG.activate(ReachOutlineConfig.defaults());
            });
            for(int scale:new int[]{1,2,3}) {
                context.runOnClient(mc->{
                    mc.options.guiScale().set(scale);mc.resizeGui();mc.gui.setScreen(new ReachOutlineSettingsScreen(null));
                    check(mc.gui.screen() instanceof SettingsScreen,"Legacy editor did not open shared settings");
                    click(mc.gui.screen(),"Wireframe");
                    check(!control(mc.gui.screen(),"Thickness").active,"Tint thickness remained active");
                    click(mc.gui.screen(),"Liquid Sources");var d=draft(mc.gui.screen());
                    check(d.reach.renderMode()==OutlineRenderMode.TINT&&d.reach.liquidSources().renderMode()==OutlineRenderMode.WIREFRAME,"Render selectors are not independent");
                    check(control(mc.gui.screen(),"Thickness").active,"Wireframe thickness is inactive");
                    click(mc.gui.screen(),"Wireframe");check(!control(mc.gui.screen(),"Thickness").active,"Liquid tint thickness remained active");
                    ((Button)control(mc.gui.screen(),"Water")).onPress(null);d.liquidSources("maximumSources",3);
                    d.toggle(5);d.toggle(5);check(d.reach.liquidSources().maximumSources()==3&&!d.reach.liquidSources().water(),"Master toggle lost draft");
                    check(SylvyesPvPHud.REACH_OUTLINE_CONFIG.current().renderMode()==OutlineRenderMode.WIREFRAME,"Draft leaked before Apply");
                });
                context.waitTick();context.takeScreenshot("reach-liquid-settings-"+scale);
                context.runOnClient(mc->{
                    mc.gui.screen().mouseScrolled(mc.gui.screen().width-20,110,0,-100);
                });
                context.waitTick();context.takeScreenshot("reach-liquid-render-settings-"+scale);
                context.runOnClient(mc->{
                    click(mc.gui.screen(),"Cancel");click(mc.gui.screen(),"Discard");
                    check(SylvyesPvPHud.REACH_OUTLINE_CONFIG.current().liquidSources().water(),"Cancel applied edits");
                    mc.gui.setScreen(new SettingsScreen(null,5));click(mc.gui.screen(),"Liquid Sources");
                    var d=draft(mc.gui.screen());d.reach("renderMode",OutlineRenderMode.TINT);d.liquidSources("maximumSources",3);d.liquidSources("lava",false);
                    click(mc.gui.screen(),"Apply");
                    check(SylvyesPvPHud.REACH_OUTLINE_CONFIG.current().renderMode()==OutlineRenderMode.TINT&&SylvyesPvPHud.REACH_OUTLINE_CONFIG.current().liquidSources().maximumSources()==3,"Apply lost settings");
                    d=draft(mc.gui.screen());d.reset(5);check(d.reach.equals(ReachOutlineConfig.defaults()),"Reset failed");click(mc.gui.screen(),"Apply");
                    mc.gui.setScreen(null);
                });
            }
        } finally {
            context.runOnClient(mc->{
                try { SylvyesPvPHud.PROFILES.updateActive(original.get()); }
                catch(java.io.IOException e) { throw new AssertionError(e); }
                mc.options.guiScale().set(2);mc.resizeGui();ReachOutlines.tick(mc);
            });
        }
    }
}
