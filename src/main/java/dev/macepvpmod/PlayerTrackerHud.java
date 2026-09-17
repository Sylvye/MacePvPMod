package dev.macepvpmod;

import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.util.ARGB;
import net.minecraft.world.waypoints.TrackedWaypoint;

public final class PlayerTrackerHud {
    private record Target(TrackedWaypoint waypoint,UUID id,PlayerInfo info,double distance){}
    private PlayerTrackerHud(){}
    static boolean shouldRender(Minecraft mc,TrackerConfig config){
        var player=mc.player;
        return config.enabled()&&player!=null&&mc.level!=null&&mc.gui.screen()==null&&!mc.gui.hud.isHidden()
                &&player.isAlive()&&!player.isSpectator()&&(!config.onlyWhenPlayerListHeld()||mc.options.keyPlayerList.isDown());
    }
    static boolean isPlayerWaypoint(TrackedWaypoint waypoint,UUID localPlayer,java.util.function.Predicate<UUID> knownPlayer){
        UUID id=waypoint.id().left().orElse(null);return id!=null&&!id.equals(localPlayer)&&knownPlayer.test(id);
    }
    public static void extract(GuiGraphicsExtractor graphics,DeltaTracker delta){
        Minecraft mc=Minecraft.getInstance();TrackerConfig config=MacePvPMod.TRACKER_CONFIG.current();if(!shouldRender(mc,config))return;
        var camera=mc.getCameraEntity();if(camera==null)return;var level=camera.level();var rates=level.tickRateManager();
        net.minecraft.world.waypoints.PartialTickSupplier partial=entity->delta.getGameTimeDeltaPartialTick(!rates.isEntityFrozen(entity));
        double cx=graphics.guiWidth()/2.0,cy=graphics.guiHeight()/2.0;
        var targets=new ArrayList<Target>();
        mc.player.connection.getWaypointManager().forEachWaypoint(camera,waypoint->{
            UUID id=waypoint.id().left().orElse(null);if(id==null||id.equals(mc.player.getUUID()))return;
            PlayerInfo info=mc.player.connection.getPlayerInfo(id);if(info==null)return;
            double distance=TrackerMath.distance(waypoint.distanceSquared(camera));
            if(TrackerMath.hidden(distance,config.hideDistantPlayers(),config.hideStartDistance()))return;
            targets.add(new Target(waypoint,id,info,distance));
        });
        int first=TrackerMath.firstVisibleIndex(targets.size(),config.maxVisiblePlayers());
        for(int i=first;i<targets.size();i++){
            Target target=targets.get(i);TrackedWaypoint waypoint=target.waypoint();UUID id=target.id();PlayerInfo info=target.info();double distance=target.distance();
            int alpha=(int)Math.round(config.opacity()*TrackerMath.fade(distance,config.hideDistantPlayers(),config.hideStartDistance())*255);
            int iconSize=Math.max(1,(int)Math.round(config.iconSize()*TrackerMath.scale(distance,config.distanceScalingStrength(),config.hideStartDistance())));
            double bearing=waypoint.yawAngleToCamera(level,mc.gameRenderer.mainCamera(),partial);
            var point=TrackerMath.onRing(bearing,cx,cy,config.radius());int x=(int)Math.round(point.x()-iconSize/2.0),y=(int)Math.round(point.y()-iconSize/2.0);
            if(config.displayMode()==TrackerDisplayMode.HEADS)drawHead(graphics,info,x,y,iconSize,alpha);
            else drawCircle(graphics,x+iconSize/2,y+iconSize/2,iconSize,ARGB.color(alpha,waypointColor(waypoint,id)));
            var trackedPlayer=level.getPlayerByUUID(id);var fallback=waypoint.pitchDirectionToCamera(level,mc.gameRenderer,partial);
            var direction=trackedPlayer==null?fallback:verticalDirection(trackedPlayer.getEyeY()-camera.getEyeY(),fallback);
            drawVerticalBadge(graphics,x,y,iconSize,direction,alpha);
            if(config.showDistance()&&Double.isFinite(distance))drawDistance(graphics,x,y,iconSize,point.y()<cy,TrackerMath.distanceLabel(distance),alpha);
        }
    }
    private static void drawHead(GuiGraphicsExtractor graphics,PlayerInfo info,int x,int y,int size,int alpha){
        var skin=info.getSkin().body().texturePath();PlayerFaceExtractor.extractRenderState(graphics,skin,x,y,size,info.showHat(),false,ARGB.color(alpha,0xffffff));
    }
    private static int waypointColor(TrackedWaypoint waypoint,UUID id){
        return waypoint.icon().color.orElseGet(()->ARGB.setBrightness(ARGB.color(255,id.hashCode()),.9f))&0xffffff;
    }
    private static void drawCircle(GuiGraphicsExtractor graphics,int cx,int cy,int size,int color){
        double radius=size/2.0;int r=(int)Math.ceil(radius);for(int y=-r;y<=r;y++)for(int x=-r;x<=r;x++)if(x*x+y*y<=radius*radius)graphics.fill(cx+x,cy+y,cx+x+1,cy+y+1,color);
    }
    private static void drawVerticalBadge(GuiGraphicsExtractor graphics,int x,int y,int size,TrackedWaypoint.PitchDirection direction,int alpha){
        if(direction==TrackedWaypoint.PitchDirection.NONE)return;int color=ARGB.color(alpha,0xffffff),cx=x+size-1,top=direction==TrackedWaypoint.PitchDirection.UP?y-4:y+size+1;
        if(direction==TrackedWaypoint.PitchDirection.UP){for(int row=0;row<3;row++)graphics.fill(cx-row,top+row,cx+row+1,top+row+1,color);}
        else{for(int row=0;row<3;row++)graphics.fill(cx-(2-row),top+row,cx+(2-row)+1,top+row+1,color);}
    }
    static TrackedWaypoint.PitchDirection verticalDirection(double yDifference,TrackedWaypoint.PitchDirection fallback){
        if(yDifference>2)return TrackedWaypoint.PitchDirection.UP;if(yDifference< -2)return TrackedWaypoint.PitchDirection.DOWN;return fallback;
    }
    private static void drawDistance(GuiGraphicsExtractor graphics,int markerX,int markerY,int markerSize,boolean aboveCursor,String label,int alpha){
        var font=Minecraft.getInstance().font;int x=markerX+(markerSize-font.width(label))/2;
        int y=aboveCursor?markerY-font.lineHeight-3:markerY+markerSize+3;
        graphics.text(font,label,x,y,ARGB.color(alpha,0xffffff),true);
    }
}
