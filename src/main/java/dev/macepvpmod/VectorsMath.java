package dev.macepvpmod;

import net.minecraft.world.phys.Vec3;

final class VectorsMath {
    record Point(double x, double y) {}
    private VectorsMath() {}
    static Vec3 effectiveVelocity(Vec3 velocity,boolean grounded) {
        return grounded ? new Vec3(velocity.x,0,velocity.z) : velocity;
    }
    static double magnitude(Vec3 velocity) { return velocity.length() * 20; }
    static boolean stationary(Vec3 velocity, double threshold) { return magnitude(velocity) < threshold; }
    static Point project(Vec3 velocity,float viewYawDegrees,float viewPitchDegrees,double verticalFovDegrees,
                         int width,int height,int margin) {
        double yaw=Math.toRadians(viewYawDegrees),pitch=Math.toRadians(viewPitchDegrees);
        double sinYaw=Math.sin(yaw),cosYaw=Math.cos(yaw),sinPitch=Math.sin(pitch),cosPitch=Math.cos(pitch);
        double right=-velocity.x*cosYaw-velocity.z*sinYaw;
        double up=-velocity.x*sinYaw*sinPitch+velocity.y*cosPitch+velocity.z*cosYaw*sinPitch;
        double forward=-velocity.x*sinYaw*cosPitch-velocity.y*sinPitch+velocity.z*cosYaw*cosPitch;
        double cx=width/2.0,cy=height/2.0,dx,dy;
        if(forward>1e-9) {
            double focal=(height/2.0)/Math.tan(Math.toRadians(Math.clamp(verticalFovDegrees,1,179))/2);
            dx=right/forward*focal;dy=-up/forward*focal;
        } else {
            double horizontal=Math.hypot(velocity.x,velocity.z);
            double targetYaw=Math.toDegrees(Math.atan2(-velocity.x,velocity.z));
            double targetPitch=Math.toDegrees(Math.atan2(-velocity.y,horizontal));
            double verticalFov=Math.toRadians(Math.clamp(verticalFovDegrees,1,179));
            double horizontalFov=2*Math.atan(Math.tan(verticalFov/2)*width/(double)Math.max(1,height));
            dx=wrapDegrees(targetYaw-viewYawDegrees)/Math.toDegrees(horizontalFov/2)*width/2.0;
            dy=(targetPitch-viewPitchDegrees)/Math.toDegrees(verticalFov/2)*height/2.0;
            if(Math.hypot(dx,dy)<1e-9)dx=1;
        }
        return clampRay(cx,cy,dx,dy,Math.max(0,margin),width,height);
    }
    private static Point clampRay(double cx,double cy,double dx,double dy,int margin,int width,int height) {
        double left=margin,right=Math.max(left,width-margin),top=margin,bottom=Math.max(top,height-margin),scale=1;
        if(dx<0)scale=Math.min(scale,(left-cx)/dx);else if(dx>0)scale=Math.min(scale,(right-cx)/dx);
        if(dy<0)scale=Math.min(scale,(top-cy)/dy);else if(dy>0)scale=Math.min(scale,(bottom-cy)/dy);
        scale=Math.clamp(scale,0,1);
        return new Point(Math.clamp(cx+dx*scale,left,right),Math.clamp(cy+dy*scale,top,bottom));
    }
    private static double wrapDegrees(double degrees) {
        double wrapped=degrees%360;if(wrapped>=180)wrapped-=360;if(wrapped< -180)wrapped+=360;return wrapped;
    }
}
