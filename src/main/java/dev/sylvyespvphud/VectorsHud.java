package dev.sylvyespvphud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class VectorsHud {
    private static final VelocitySmoother SMOOTHER=new VelocitySmoother();
    private VectorsHud() {}
    static boolean shouldRender(Minecraft mc, VectorsConfig c) {
        var p=mc.player;
        return c.enabled() && p!=null && mc.level!=null && mc.gui.screen()==null && !mc.gui.hud.isHidden()
                && p.isAlive() && !p.isSpectator();
    }
    static boolean activityAllowed(VectorsConfig c,boolean gliding,boolean chargingSpear) {
        if(!c.elytraOnly()&&!c.spearOnly())return true;
        return c.elytraOnly()&&gliding || c.spearOnly()&&chargingSpear;
    }
    static boolean activityAllowed(Player player,VectorsConfig c) {
        return activityAllowed(c,player.isFallFlying(),player.isUsingItem()&&player.getUseItem().is(ItemTags.SPEARS));
    }
    private static Entity movementSource(Player player) {
        return player.getVehicle()==null ? player : player.getVehicle();
    }
    static void tick(Minecraft mc) {
        var c=SylvyesPvPHud.VECTORS_CONFIG.current();
        if(!shouldRender(mc,c)||!c.reticleEnabled()||!activityAllowed(mc.player,c)) { SMOOTHER.reset();return; }
        var source=movementSource(mc.player);
        var velocity=VectorsMath.effectiveVelocity(source.getDeltaMovement(),source.onGround());
        if(VectorsMath.stationary(velocity,c.stationaryThreshold())) { SMOOTHER.reset();return; }
        SMOOTHER.sample(velocity,source.onGround(),source,mc.level);
    }
    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        var mc=Minecraft.getInstance();var c=SylvyesPvPHud.VECTORS_CONFIG.current();if(!shouldRender(mc,c)){SMOOTHER.reset();return;}
        var source=movementSource(mc.player);
        var velocity=VectorsMath.effectiveVelocity(source.getDeltaMovement(),source.onGround());
        double magnitude=VectorsMath.magnitude(velocity);
        boolean activity=activityAllowed(mc.player,c);
        boolean reticleActive=c.reticleEnabled()&&activity&&!VectorsMath.stationary(velocity,c.stationaryThreshold());
        if(reticleActive) {
            float partial=delta.getGameTimeDeltaPartialTick(false);
            var point=VectorsMath.project(SMOOTHER.value(partial,velocity),mc.player.getYRot(partial),mc.player.getXRot(partial),
                    mc.options.fov().get(),g.guiWidth(),g.guiHeight(),c.size());
            drawIcon(g,(int)Math.round(point.x()),(int)Math.round(point.y()),c);
        } else SMOOTHER.reset();
        if(c.velocityEnabled()&&magnitude>c.velocityThreshold()) HudRenderer.textColor(g,VectorsText.format(c.velocityTemplate(),magnitude),
                SylvyesPvPHud.HUD_CONFIG.current().velocity(),c.velocityColors().color(magnitude));
    }
    private static void drawIcon(GuiGraphicsExtractor g,int cx,int cy,VectorsConfig c) {
        int r=c.size()/2,argb=((int)Math.round(c.opacity()*255)<<24)|c.color();
        switch(c.icon()) {
            case CROSSHAIR -> { g.fill(cx-r,cy,cx+r+1,cy+1,argb);g.fill(cx,cy-r,cx+1,cy+r+1,argb); }
            case STAR -> { g.fill(cx-r,cy,cx+r+1,cy+1,argb);g.fill(cx,cy-r,cx+1,cy+r+1,argb);for(int i=-r;i<=r;i++){g.fill(cx+i,cy+i,cx+i+1,cy+i+1,argb);g.fill(cx+i,cy-i,cx+i+1,cy-i+1,argb);} }
            case CIRCLE -> { double outer=r+.5,inner=Math.max(0,r-1);for(int y=-r;y<=r;y++)for(int x=-r;x<=r;x++){double distance=Math.hypot(x,y);if(distance<=outer&&distance>=inner)g.fill(cx+x,cy+y,cx+x+1,cy+y+1,argb);} }
        }
    }
}
