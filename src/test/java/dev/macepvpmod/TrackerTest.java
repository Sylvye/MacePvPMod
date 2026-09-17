package dev.macepvpmod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.Waypoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TrackerTest {
    @TempDir Path directory;

    @Test void defaultsClampAndPersist() throws Exception {
        var defaults=TrackerConfig.defaults();assertTrue(defaults.enabled());assertEquals(48,defaults.radius());
        assertEquals(.9,defaults.opacity());assertEquals(12,defaults.iconSize());assertTrue(defaults.onlyWhenPlayerListHeld());
        assertEquals(TrackerDisplayMode.HEADS,defaults.displayMode());assertFalse(defaults.hideLocatorBar());
        assertEquals(.25,defaults.distanceScalingStrength());assertFalse(defaults.showDistance());assertFalse(defaults.hideDistantPlayers());assertEquals(1000,defaults.hideStartDistance());assertEquals(0,defaults.maxVisiblePlayers());
        var clamped=new TrackerConfig(3,true,-1,2,99,false,null,true,2,true,false,20000,999).validated();
        assertEquals(16,clamped.radius());assertEquals(1,clamped.opacity());assertEquals(24,clamped.iconSize());assertEquals(TrackerDisplayMode.HEADS,clamped.displayMode());
        assertEquals(1,clamped.distanceScalingStrength());assertEquals(10000,clamped.hideStartDistance());assertEquals(100,clamped.maxVisiblePlayers());
        Path path=directory.resolve("tracker.json");var store=new TrackerConfigStore(path);store.save(clamped);
        var loaded=new TrackerConfigStore(path);loaded.load();assertEquals(clamped,loaded.current());
    }

    @Test void schemaOneMigratesDistanceDefaultsAndPreservesExistingValues() throws Exception {
        Path path=directory.resolve("old-tracker.json");Files.writeString(path,"{\"schemaVersion\":1,\"enabled\":false,\"radius\":77,\"opacity\":0.6,\"iconSize\":18,\"onlyWhenPlayerListHeld\":false,\"displayMode\":\"COLORS\",\"hideLocatorBar\":true}");
        var store=new TrackerConfigStore(path);store.load();var migrated=store.current();
        assertEquals(3,migrated.schemaVersion());assertFalse(migrated.enabled());assertEquals(77,migrated.radius());assertEquals(.6,migrated.opacity());assertEquals(18,migrated.iconSize());
        assertEquals(TrackerDisplayMode.COLORS,migrated.displayMode());assertTrue(migrated.hideLocatorBar());assertEquals(.25,migrated.distanceScalingStrength());assertFalse(migrated.showDistance());assertFalse(migrated.hideDistantPlayers());assertEquals(1000,migrated.hideStartDistance());assertEquals(0,migrated.maxVisiblePlayers());
    }

    @Test void schemaTwoPreservesDistanceChoicesAndGainsUnlimitedMaximum() throws Exception {
        Path path=directory.resolve("v2-tracker.json");Files.writeString(path,"{\"schemaVersion\":2,\"enabled\":true,\"radius\":48,\"opacity\":0.9,\"iconSize\":12,\"onlyWhenPlayerListHeld\":true,\"displayMode\":\"HEADS\",\"hideLocatorBar\":false,\"distanceScalingStrength\":0.75,\"showDistance\":true,\"hideDistantPlayers\":true,\"hideStartDistance\":400}");
        var store=new TrackerConfigStore(path);store.load();assertEquals(3,store.current().schemaVersion());assertEquals(.75,store.current().distanceScalingStrength());assertTrue(store.current().showDistance());assertTrue(store.current().hideDistantPlayers());assertEquals(400,store.current().hideStartDistance());assertEquals(0,store.current().maxVisiblePlayers());
    }

    @Test void invalidConfigIsBackedUp() throws Exception {
        Path path=directory.resolve("tracker.json");Files.writeString(path,"{\"schemaVersion\":99}");
        var store=new TrackerConfigStore(path);store.load();assertEquals(TrackerConfig.defaults(),store.current());
        try(var files=Files.list(directory)){assertTrue(files.anyMatch(p->p.getFileName().toString().startsWith("macepvpmod-invalid-")));}
    }

    @Test void ringProjectionUsesCameraRelativeCardinalsAndWraps() {
        assertPoint(100,52,TrackerMath.onRing(0,100,100,48));
        assertPoint(148,100,TrackerMath.onRing(90,100,100,48));
        assertPoint(100,148,TrackerMath.onRing(180,100,100,48));
        assertPoint(52,100,TrackerMath.onRing(-90,100,100,48));
        assertPoint(100,52,TrackerMath.onRing(360,100,100,48));
        double diagonal=48/Math.sqrt(2);assertPoint(100+diagonal,100-diagonal,TrackerMath.onRing(45,100,100,48));
    }

    @Test void distanceScalingIsLinearAndClampedAtThreshold() {
        assertEquals(1,TrackerMath.scale(0,.25,1000),1e-9);assertEquals(.9375,TrackerMath.scale(500,.25,1000),1e-9);assertEquals(.875,TrackerMath.scale(1000,.25,1000),1e-9);assertEquals(.875,TrackerMath.scale(5000,.25,1000),1e-9);
        assertEquals(1,TrackerMath.scale(1000,0,1000),1e-9);assertEquals(.5,TrackerMath.scale(1000,1,1000),1e-9);
        assertEquals(1,TrackerMath.scale(Double.NaN,1,1000),1e-9);
    }

    @Test void distantTargetsFadeThenHideUnlessDisabledOrUnknown() {
        assertEquals(1,TrackerMath.fade(999,true,1000),1e-9);assertEquals(1,TrackerMath.fade(1000,true,1000),1e-9);assertEquals(.5,TrackerMath.fade(1125,true,1000),1e-9);assertEquals(0,TrackerMath.fade(1250,true,1000),1e-9);
        assertFalse(TrackerMath.hidden(1249.9,true,1000));assertTrue(TrackerMath.hidden(1250,true,1000));
        assertEquals(1,TrackerMath.fade(5000,false,1000),1e-9);assertFalse(TrackerMath.hidden(5000,false,1000));
        assertEquals(1,TrackerMath.fade(Double.NaN,true,1000),1e-9);assertFalse(TrackerMath.hidden(Double.NaN,true,1000));
    }

    @Test void distanceExtractionAndLabelsHandleUnavailableRanges() {
        assertTrue(TrackerMath.finiteDistance(144));assertEquals(12,TrackerMath.distance(144));assertEquals("12m",TrackerMath.distanceLabel(12.4));assertEquals("13m",TrackerMath.distanceLabel(12.5));
        assertFalse(TrackerMath.finiteDistance(Double.POSITIVE_INFINITY));assertTrue(Double.isNaN(TrackerMath.distance(Double.POSITIVE_INFINITY)));assertEquals("",TrackerMath.distanceLabel(Double.NaN));
    }

    @Test void maximumKeepsNearestTailAndZeroMeansUnlimited() {
        assertEquals(0,TrackerMath.firstVisibleIndex(12,0));assertEquals(0,TrackerMath.firstVisibleIndex(3,5));assertEquals(7,TrackerMath.firstVisibleIndex(12,5));
    }

    @Test void lookDirectionUsesRequiredPitchInsteadOfRawHeight() {
        var camera=new net.minecraft.world.phys.Vec3(0,64,0);
        assertEquals(TrackedWaypoint.PitchDirection.UP,PlayerTrackerHud.lookDirection(camera,0,new net.minecraft.world.phys.Vec3(0,84,20),TrackedWaypoint.PitchDirection.DOWN));
        assertEquals(TrackedWaypoint.PitchDirection.DOWN,PlayerTrackerHud.lookDirection(camera,0,new net.minecraft.world.phys.Vec3(0,44,20),TrackedWaypoint.PitchDirection.UP));
        assertEquals(TrackedWaypoint.PitchDirection.DOWN,PlayerTrackerHud.lookDirection(camera,-45,new net.minecraft.world.phys.Vec3(0,70,100),TrackedWaypoint.PitchDirection.UP));
        assertEquals(TrackedWaypoint.PitchDirection.UP,PlayerTrackerHud.lookDirection(camera,45,new net.minecraft.world.phys.Vec3(0,58,100),TrackedWaypoint.PitchDirection.DOWN));
        assertEquals(TrackedWaypoint.PitchDirection.NONE,PlayerTrackerHud.lookDirection(camera,0,new net.minecraft.world.phys.Vec3(0,65,100),TrackedWaypoint.PitchDirection.DOWN));
    }

    @Test void waypointFilterAcceptsOnlyKnownOtherPlayerUuids() {
        UUID local=UUID.randomUUID(),other=UUID.randomUUID(),missing=UUID.randomUUID();
        TrackedWaypoint player=TrackedWaypoint.setPosition(other,Waypoint.Icon.NULL,BlockPos.ZERO);
        TrackedWaypoint self=TrackedWaypoint.setPosition(local,Waypoint.Icon.NULL,BlockPos.ZERO);
        assertTrue(PlayerTrackerHud.isPlayerWaypoint(player,local,other::equals));
        assertFalse(PlayerTrackerHud.isPlayerWaypoint(self,local,id->true));
        assertFalse(PlayerTrackerHud.isPlayerWaypoint(TrackedWaypoint.setPosition(missing,Waypoint.Icon.NULL,BlockPos.ZERO),local,other::equals));
    }

    private static void assertPoint(double x,double y,TrackerMath.Point point){assertEquals(x,point.x(),1e-8);assertEquals(y,point.y(),1e-8);}
}
