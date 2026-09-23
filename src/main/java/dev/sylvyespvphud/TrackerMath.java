package dev.sylvyespvphud;

import net.minecraft.world.phys.Vec3;

final class TrackerMath {
    record Point(double x,double y){}
    record IconPosition(int x,int y){}
    private TrackerMath(){}
    static Point onRing(double relativeBearingDegrees,double centerX,double centerY,double radius){
        double radians=Math.toRadians(relativeBearingDegrees-90);
        return new Point(centerX+Math.cos(radians)*radius,centerY+Math.sin(radians)*radius);
    }
    static boolean finiteDistance(double distanceSquared){return Double.isFinite(distanceSquared)&&distanceSquared>=0;}
    static double distance(double distanceSquared){return finiteDistance(distanceSquared)?Math.sqrt(distanceSquared):Double.POSITIVE_INFINITY;}
    static double scale(double distance,double strength,double hideStartDistance){
        if(!Double.isFinite(distance))return 1;double progress=Math.clamp(distance/hideStartDistance,0,1);
        return 1-Math.clamp(strength,0,1)*.5*progress;
    }
    static double fade(double distance,boolean enabled,double hideStartDistance){
        if(!enabled||!Double.isFinite(distance)||distance<=hideStartDistance)return 1;
        return Math.clamp(1-(distance-hideStartDistance)/(hideStartDistance*.25),0,1);
    }
    static boolean hidden(double distance,boolean enabled,double hideStartDistance){return enabled&&(!Double.isFinite(distance)||distance>=hideStartDistance*1.25);}
    static double nearFade(double distance,double fadeDistance){return Math.clamp(distance/fadeDistance,0,1);}
    static Vec3 horizonPosition(Vec3 cameraPosition,double cameraYaw,double relativeBearing){
        double yaw=Math.toRadians(cameraYaw),bearing=Math.toRadians(relativeBearing);
        double forwardX=-Math.sin(yaw),forwardZ=Math.cos(yaw);
        double x=forwardX*Math.cos(bearing)-forwardZ*Math.sin(bearing);
        double z=forwardZ*Math.cos(bearing)+forwardX*Math.sin(bearing);
        return cameraPosition.add(x*256,0,z*256);
    }
    static Point screenPoint(Vec3 normalized,double forwardDistance,int width,int height){
        if(forwardDistance<=0||!Double.isFinite(normalized.x)||!Double.isFinite(normalized.y)||!Double.isFinite(normalized.z)
                ||Math.abs(normalized.x)>1||Math.abs(normalized.y)>1)return null;
        return new Point((normalized.x+1)*width/2.0,(1-normalized.y)*height/2.0);
    }
    static IconPosition aboveHeadIcon(Point headTop,int iconSize,int width,int height){
        int x=(int)Math.round(headTop.x-iconSize/2.0),y=(int)Math.round(headTop.y-iconSize-4);
        return x<0||y<0||x+iconSize>width||y+iconSize>height?null:new IconPosition(x,y);
    }
    static int distanceLabelYAbove(int iconY,int lineHeight){return iconY-lineHeight-3;}
    static String distanceLabel(double distance){return Double.isFinite(distance)?Math.round(distance)+"m":"";}
    static int firstVisibleIndex(int count,int maximum){return maximum<=0?0:Math.max(0,count-maximum);}
}
