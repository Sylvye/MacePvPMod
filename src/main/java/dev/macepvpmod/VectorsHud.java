package dev.macepvpmod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

public final class VectorsHud {
    private VectorsHud() {}
    static boolean shouldRender(Minecraft mc, VectorsConfig c) {
        var p=mc.player;
        return c.enabled() && p!=null && mc.level!=null && mc.gui.screen()==null && !mc.gui.hud.isHidden()
                && p.isAlive() && !p.isSpectator();
    }
    static boolean equipmentAllowed(VectorsConfig c,boolean wearingElytra,boolean mainSpear,boolean offhandSpear) {
        if(!c.elytraOnly()&&!c.spearOnly())return true;
        return c.elytraOnly()&&wearingElytra || c.spearOnly()&&(mainSpear||offhandSpear);
    }
    static boolean equipmentAllowed(Player player,VectorsConfig c) {
        return equipmentAllowed(c,player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA),
                player.getMainHandItem().is(ItemTags.SPEARS),player.getOffhandItem().is(ItemTags.SPEARS));
    }
    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        var mc=Minecraft.getInstance();var c=MacePvPMod.VECTORS_CONFIG.current();if(!shouldRender(mc,c))return;
        var velocity=VectorsMath.effectiveVelocity(mc.player.getDeltaMovement(),mc.player.onGround());
        double magnitude=VectorsMath.magnitude(velocity);
        boolean equipment=equipmentAllowed(mc.player,c);
        if(c.reticleEnabled()&&equipment&&!VectorsMath.stationary(velocity,c.stationaryThreshold())) {
            float partial=delta.getGameTimeDeltaPartialTick(false);
            var point=VectorsMath.project(velocity,mc.player.getYRot(partial),mc.player.getXRot(partial),
                    mc.options.fov().get(),g.guiWidth(),g.guiHeight(),c.size());
            drawIcon(g,(int)Math.round(point.x()),(int)Math.round(point.y()),c);
        }
        if(c.velocityEnabled()) HudRenderer.textColor(g,VectorsText.format(c.velocityTemplate(),magnitude),
                MacePvPMod.HUD_CONFIG.current().velocity(),c.velocityColors().color(magnitude));
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
