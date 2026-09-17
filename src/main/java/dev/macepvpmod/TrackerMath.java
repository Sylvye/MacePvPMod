package dev.macepvpmod;

final class TrackerMath {
    record Point(double x,double y){}
    private TrackerMath(){}
    static Point onRing(double relativeBearingDegrees,double centerX,double centerY,double radius){
        double radians=Math.toRadians(relativeBearingDegrees-90);
        return new Point(centerX+Math.cos(radians)*radius,centerY+Math.sin(radians)*radius);
    }
    static boolean finiteDistance(double distanceSquared){return Double.isFinite(distanceSquared)&&distanceSquared>=0;}
    static double distance(double distanceSquared){return finiteDistance(distanceSquared)?Math.sqrt(distanceSquared):Double.NaN;}
    static double scale(double distance,double strength,double hideStartDistance){
        if(!Double.isFinite(distance))return 1;double progress=Math.clamp(distance/hideStartDistance,0,1);
        return 1-Math.clamp(strength,0,1)*.5*progress;
    }
    static double fade(double distance,boolean enabled,double hideStartDistance){
        if(!enabled||!Double.isFinite(distance)||distance<=hideStartDistance)return 1;
        return Math.clamp(1-(distance-hideStartDistance)/(hideStartDistance*.25),0,1);
    }
    static boolean hidden(double distance,boolean enabled,double hideStartDistance){return enabled&&Double.isFinite(distance)&&distance>=hideStartDistance*1.25;}
    static String distanceLabel(double distance){return Double.isFinite(distance)?Math.round(distance)+"m":"";}
    static int firstVisibleIndex(int count,int maximum){return maximum<=0?0:Math.max(0,count-maximum);}
}
