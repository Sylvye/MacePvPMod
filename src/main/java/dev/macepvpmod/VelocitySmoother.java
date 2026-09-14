package dev.macepvpmod;

import net.minecraft.world.phys.Vec3;

final class VelocitySmoother {
    private static final double MOVING_EPSILON_SQUARED=1e-12;
    private static final double SNAP_ANGLE_DEGREES=30;
    private Vec3 previous;
    private Vec3 current;
    private boolean grounded;
    private Object subject;
    private Object world;

    void sample(Vec3 velocity,boolean onGround,Object sampleSubject,Object sampleWorld) {
        if(current==null||subject!=sampleSubject||world!=sampleWorld||grounded!=onGround||moving(current)!=moving(velocity)
                ||angleDegrees(current,velocity)>=SNAP_ANGLE_DEGREES) {
            previous=current=velocity;
        } else {
            previous=current;
            current=velocity;
        }
        grounded=onGround;
        subject=sampleSubject;
        world=sampleWorld;
    }

    Vec3 value(float partialTick,Vec3 fallback) {
        if(current==null)return fallback;
        if(previous==current)return current;
        double amount=Math.clamp(partialTick,0,1);
        Vec3 direction=previous.normalize().lerp(current.normalize(),amount).normalize();
        return direction.scale(current.length());
    }

    void reset() { previous=current=null;subject=world=null; }

    private static boolean moving(Vec3 velocity) { return velocity.lengthSqr()>MOVING_EPSILON_SQUARED; }

    private static double angleDegrees(Vec3 first,Vec3 second) {
        if(!moving(first)||!moving(second))return 180;
        double cosine=Math.clamp(first.dot(second)/Math.sqrt(first.lengthSqr()*second.lengthSqr()),-1,1);
        return Math.toDegrees(Math.acos(cosine));
    }
}
