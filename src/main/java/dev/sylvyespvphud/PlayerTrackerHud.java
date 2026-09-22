package dev.sylvyespvphud;

import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.util.ARGB;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.phys.Vec3;

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
        Minecraft mc=Minecraft.getInstance();TrackerConfig config=SylvyesPvPHud.TRACKER_CONFIG.current();if(!shouldRender(mc,config))return;
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
            var mainCamera=mc.gameRenderer.mainCamera();
            var direction=trackedPlayer==null?fallback:lookDirection(mainCamera.position(),mainCamera.xRot(),trackedPlayer.getEyePosition(partial.apply(trackedPlayer)),fallback);
            drawVerticalBadge(graphics,x,y,iconSize,direction,alpha);
            if(config.showDistance()&&Double.isFinite(distance))drawDistance(graphics,x,y,iconSize,point.y()<cy,direction,TrackerMath.distanceLabel(distance),alpha);
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
        if(direction==TrackedWaypoint.PitchDirection.NONE)return;int color=ARGB.color(alpha,0xffffff),width=arrowWidth(size),height=arrowHeight(size),half=width/2,cx=x+size/2;
        int top=direction==TrackedWaypoint.PitchDirection.UP?y-height-2:y+size+2;
        for(int row=0;row<height;row++){int extent=direction==TrackedWaypoint.PitchDirection.UP?(int)Math.round(half*row/(double)(height-1)):(int)Math.round(half*(height-1-row)/(double)(height-1));graphics.fill(cx-extent,top+row,cx+extent+1,top+row+1,color);}
    }
    private static int arrowWidth(int iconSize){return Math.max(7,(iconSize*3/4)|1);}
    private static int arrowHeight(int iconSize){return arrowWidth(iconSize)/2+1;}
    static TrackedWaypoint.PitchDirection lookDirection(Vec3 cameraPosition,float cameraPitch,Vec3 targetPosition,TrackedWaypoint.PitchDirection fallback){
        Vec3 offset=targetPosition.subtract(cameraPosition);double horizontal=Math.hypot(offset.x,offset.z);if(horizontal<1e-6&&Math.abs(offset.y)<1e-6)return fallback;
        double targetPitch=Math.toDegrees(Math.atan2(-offset.y,horizontal)),difference=targetPitch-cameraPitch;
        if(difference>2)return TrackedWaypoint.PitchDirection.DOWN;if(difference< -2)return TrackedWaypoint.PitchDirection.UP;return TrackedWaypoint.PitchDirection.NONE;
    }
    private static void drawDistance(GuiGraphicsExtractor graphics,int markerX,int markerY,int markerSize,boolean aboveCursor,TrackedWaypoint.PitchDirection direction,String label,int alpha){
        var font=Minecraft.getInstance().font;int x=markerX+(markerSize-font.width(label))/2;
        int arrowSpace=(aboveCursor&&direction==TrackedWaypoint.PitchDirection.UP)||(!aboveCursor&&direction==TrackedWaypoint.PitchDirection.DOWN)?arrowHeight(markerSize)+4:0;
        int y=aboveCursor?markerY-font.lineHeight-3-arrowSpace:markerY+markerSize+3+arrowSpace;
        graphics.text(font,label,x,y,ARGB.color(alpha,0xffffff),true);
    }
}
