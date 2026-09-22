package dev.sylvyespvphud;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class VectorsTest {
    @TempDir Path directory;

    @Test void magnitudeDirectionAndStationaryRules() {
        var idle=VectorsMath.effectiveVelocity(new Vec3(0,-.0784,0),true);
        assertEquals(0,VectorsMath.magnitude(idle),1e-9);
        assertEquals(4,VectorsMath.magnitude(VectorsMath.effectiveVelocity(new Vec3(.2,-.0784,0),true)),1e-9);
        assertEquals(1.568,VectorsMath.magnitude(VectorsMath.effectiveVelocity(new Vec3(0,-.0784,0),false)),1e-9);
        assertEquals(5, VectorsMath.magnitude(new Vec3(.15,.2,0)), 1e-9);
        assertTrue(VectorsMath.stationary(new Vec3(.001,0,0), .05));
        assertFalse(VectorsMath.stationary(new Vec3(.01,0,0), .05));
        var forward=VectorsMath.project(new Vec3(0,0,1),0,0,90,200,100,5);
        assertEquals(100,forward.x(),1e-9);assertEquals(50,forward.y(),1e-9);
        var right=VectorsMath.project(new Vec3(-1,0,0),0,0,90,200,100,5);
        assertEquals(195,right.x(),1e-9);assertEquals(50,right.y(),1e-9);
        var up=VectorsMath.project(new Vec3(0,1,0),0,0,90,200,100,5);
        assertEquals(100,up.x(),1e-9);assertEquals(5,up.y(),1e-9);
        var diagonal=VectorsMath.project(new Vec3(-1,1,2),0,0,90,200,100,5);
        assertEquals(125,diagonal.x(),1e-9);assertEquals(25,diagonal.y(),1e-9);
        var behind=VectorsMath.project(new Vec3(0,0,-1),0,0,90,200,100,5);
        assertEquals(5,behind.x(),1e-9);
        var wide=VectorsMath.project(new Vec3(-1,0,2),0,0,90,400,100,5);
        assertEquals(225,wide.x(),1e-9);
        var zoomed=VectorsMath.project(new Vec3(-1,0,2),0,0,60,200,100,5);
        assertTrue(zoomed.x()>125);
        for(float yaw:new float[]{-90,0,90,180}) {
            double radians=Math.toRadians(yaw);
            var aligned=new Vec3(-Math.sin(radians),0,Math.cos(radians));
            var center=VectorsMath.project(aligned,yaw,0,90,200,100,5);
            assertEquals(100,center.x(),1e-8);assertEquals(50,center.y(),1e-8);
            var screenRight=new Vec3(-Math.cos(radians),0,-Math.sin(radians));
            var edge=VectorsMath.project(screenRight,yaw,0,90,200,100,5);
            assertEquals(195,edge.x(),1e-8);
        }
        var fixedVelocity=new Vec3(0,0,1);
        var far=VectorsMath.project(fixedVelocity,45,0,90,200,100,5);
        var near=VectorsMath.project(fixedVelocity,20,0,90,200,100,5);
        var aligned=VectorsMath.project(fixedVelocity,0,0,90,200,100,5);
        assertTrue(distance(near,100,50)<distance(far,100,50));
        assertTrue(distance(aligned,100,50)<distance(near,100,50));
    }

    private static double distance(VectorsMath.Point point,double x,double y){return Math.hypot(point.x()-x,point.y()-y);}

    @Test void reticleSmoothsSmallChangesForAtMostOneTick() {
        var smoother=new VelocitySmoother();var player=new Object();var world=new Object();
        var first=new Vec3(0,0,1);var second=new Vec3(-.2,0,1);
        smoother.sample(first,false,player,world);smoother.sample(second,false,player,world);
        assertDirection(first,smoother.value(0,Vec3.ZERO));
        var halfway=smoother.value(.5f,Vec3.ZERO);
        assertTrue(halfway.x()<0&&halfway.x()>second.normalize().x);
        assertDirection(second,smoother.value(1,Vec3.ZERO));
        assertDirection(second,smoother.value(2,Vec3.ZERO));
    }

    @Test void reticleSnapsOnSharpMovementAndStateChanges() {
        var smoother=new VelocitySmoother();var player=new Object();var world=new Object();
        smoother.sample(new Vec3(0,0,1),false,player,world);
        var sharp=new Vec3(-1,0,0);smoother.sample(sharp,false,player,world);
        assertDirection(sharp,smoother.value(0,Vec3.ZERO));
        var grounded=new Vec3(-.9,0,.1);smoother.sample(grounded,true,player,world);
        assertDirection(grounded,smoother.value(0,Vec3.ZERO));
        smoother.sample(Vec3.ZERO,true,player,world);
        assertEquals(Vec3.ZERO,smoother.value(0,sharp));
        smoother.sample(grounded,true,new Object(),world);
        assertDirection(grounded,smoother.value(0,Vec3.ZERO));
        var dimensionChange=new Vec3(-.8,0,.2);smoother.sample(dimensionChange,true,player,new Object());
        assertDirection(dimensionChange,smoother.value(0,Vec3.ZERO));
        smoother.reset();assertEquals(sharp,smoother.value(.5f,sharp));
    }

    private static void assertDirection(Vec3 expected,Vec3 actual) {
        assertEquals(expected.normalize().x,actual.normalize().x,1e-9);
        assertEquals(expected.normalize().y,actual.normalize().y,1e-9);
        assertEquals(expected.normalize().z,actual.normalize().z,1e-9);
    }

    @Test void templateRequiresOnlyMagnitude() {
        assertEquals("Speed: 12.5 blocks/s",VectorsText.format("Speed: {magnitude} blocks/s",12.45));
        assertEquals("",VectorsText.error("{magnitude} bps"));
        for(String invalid:new String[]{"", "speed", "{unknown}", "{{magnitude}}", "{magnitude"})
            assertFalse(VectorsText.error(invalid).isEmpty());
    }

    @Test void defaultsClampAndPersist() throws Exception {
        var defaults=VectorsConfig.defaults().validated();
        assertFalse(defaults.enabled());assertTrue(defaults.reticleEnabled()&&defaults.velocityEnabled());
        assertEquals(0,defaults.velocityColors().minimum());assertEquals(40,defaults.velocityColors().maximum());
        assertEquals(0,defaults.velocityThreshold());
        var clamped=new VectorsConfig(2,true,true,true,true,true,null,-4,-1,2,-5,-2,
                "{magnitude}",defaults.velocityColors()).validated();
        assertEquals(VectorIcon.CIRCLE,clamped.icon());assertEquals(3,clamped.size());
        assertEquals(0xffffff,clamped.color());assertEquals(1,clamped.opacity());assertEquals(0,clamped.stationaryThreshold());assertEquals(0,clamped.velocityThreshold());
        Path path=directory.resolve("vectors.json");var store=new VectorsConfigStore(path);store.save(clamped);
        var loaded=new VectorsConfigStore(path);loaded.load();assertEquals(clamped,loaded.current());
    }

    @Test void activityFiltersUseOrLogic() {
        var both=VectorsConfig.defaults();
        assertTrue(VectorsHud.activityAllowed(both,true,false));
        assertTrue(VectorsHud.activityAllowed(both,false,true));
        assertFalse(VectorsHud.activityAllowed(both,false,false));
        var elytra=new VectorsConfig(2,true,true,true,true,false,both.icon(),both.size(),both.color(),both.opacity(),both.stationaryThreshold(),both.velocityThreshold(),both.velocityTemplate(),both.velocityColors());
        assertTrue(VectorsHud.activityAllowed(elytra,true,false));assertFalse(VectorsHud.activityAllowed(elytra,false,true));
        var unrestricted=new VectorsConfig(2,true,true,true,false,false,both.icon(),both.size(),both.color(),both.opacity(),both.stationaryThreshold(),both.velocityThreshold(),both.velocityTemplate(),both.velocityColors());
        assertTrue(VectorsHud.activityAllowed(unrestricted,false,false));
    }

    @Test void schemaOneMigratesAndPreservesValues() throws Exception {
        Path path=directory.resolve("old-vectors.json");
        Files.writeString(path,"{\"schemaVersion\":1,\"enabled\":false,\"reticleEnabled\":true,\"velocityEnabled\":false,\"radius\":99,\"size\":11,\"color\":1193046,\"opacity\":0.7,\"stationaryThreshold\":0.2,\"velocityTemplate\":\"Speed {magnitude}\"}");
        var store=new VectorsConfigStore(path);store.load();var migrated=store.current();
        assertEquals(2,migrated.schemaVersion());assertFalse(migrated.enabled());assertFalse(migrated.velocityEnabled());
        assertTrue(migrated.elytraOnly());assertTrue(migrated.spearOnly());assertEquals(11,migrated.size());assertEquals(0x123456,migrated.color());
    }

    @Test void invalidFileFallsBackAndIsBackedUp() throws Exception {
        Path path=directory.resolve("vectors.json");Files.writeString(path,"{\"schemaVersion\":99}");
        var store=new VectorsConfigStore(path);store.load();assertEquals(VectorsConfig.defaults(),store.current());
        try(var files=Files.list(directory)){assertTrue(files.anyMatch(p->p.getFileName().toString().startsWith("sylvyespvphud-invalid-")));}
    }
}
