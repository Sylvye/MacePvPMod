package dev.macepvpmod;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ReachOutlineRulesTest {
    @BeforeAll static void bootstrapMinecraft() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void onlyOpaqueDryNonreplaceableBlocksAreSupports() {
        assertTrue(ReachOutlines.eligibleSupport(Blocks.STONE.defaultBlockState()));
        assertFalse(ReachOutlines.eligibleSupport(Blocks.AIR.defaultBlockState()));
        assertFalse(ReachOutlines.eligibleSupport(Blocks.WATER.defaultBlockState()));
        assertFalse(ReachOutlines.eligibleSupport(Blocks.LAVA.defaultBlockState()));
        assertFalse(ReachOutlines.eligibleSupport(Blocks.GLASS.defaultBlockState()));
        assertFalse(ReachOutlines.eligibleSupport(Blocks.OAK_LEAVES.defaultBlockState()));
    }

    @Test void destinationsMustBeReplaceableAndDry() {
        assertTrue(ReachOutlines.openDestination(Blocks.AIR.defaultBlockState()));
        assertTrue(ReachOutlines.openDestination(Blocks.SHORT_GRASS.defaultBlockState()));
        assertFalse(ReachOutlines.openDestination(Blocks.STONE.defaultBlockState()));
        assertFalse(ReachOutlines.openDestination(Blocks.WATER.defaultBlockState()));
    }

    @Test void distantFloorTargetDoesNotPassThroughNearerFloor() {
        var camera=new Vec3(.5,1.62,.5);
        var targetPos=new BlockPos(3,0,0);
        var target=ReachOutlines.faceTarget(targetPos,camera,Direction.UP);
        assertTrue(new AABB(0,0,0,3,1,1).clip(camera,target).isEmpty());
        assertEquals(Direction.UP,Shapes.block().clip(camera,target,targetPos).getDirection());
    }

    @Test void diagonalTargetsEnterEveryRequestedFace() {
        var pos=BlockPos.ZERO;
        assertTargetsFace(pos,new Vec3(-3,3,3),Direction.WEST);
        assertTargetsFace(pos,new Vec3(3,3,3),Direction.EAST);
        assertTargetsFace(pos,new Vec3(3,-3,3),Direction.DOWN);
        assertTargetsFace(pos,new Vec3(3,3,3),Direction.UP);
        assertTargetsFace(pos,new Vec3(3,3,-3),Direction.NORTH);
        assertTargetsFace(pos,new Vec3(3,3,3),Direction.SOUTH);
    }

    @Test void topOnlyModeFiltersEveryOtherDirection() {
        for(Direction face:Direction.values())assertEquals(face==Direction.UP,ReachOutlines.allowsFace(true,face));
        for(Direction face:Direction.values())assertTrue(ReachOutlines.allowsFace(false,face));
    }

    @Test void estimatesFullPartialBareAndUnreachableAreas() {
        var pos=BlockPos.ZERO;var eye=new Vec3(.5,2,.5);
        assertEquals(1,ReachOutlines.reachableArea(pos,Direction.UP,eye,2));
        double partial=ReachOutlines.reachableArea(pos,Direction.UP,eye,1.1);
        double barely=ReachOutlines.reachableArea(pos,Direction.UP,eye,1.01);
        assertTrue(partial>barely&&partial<1);assertTrue(barely>0);
        assertEquals(0,ReachOutlines.reachableArea(pos,Direction.UP,eye,.5));
    }

    @Test void appliesHardToReachModesBelowThreshold() {
        assertEquals(1,ReachOutlines.opacityFactor(HardToReachMode.RENDER_FULL,.25,.5));
        assertEquals(.5,ReachOutlines.opacityFactor(HardToReachMode.FADE_FACES,.25,.5));
        assertEquals(0,ReachOutlines.opacityFactor(HardToReachMode.DO_NOT_RENDER,.25,.5));
        for(HardToReachMode mode:HardToReachMode.values())assertEquals(1,ReachOutlines.opacityFactor(mode,.5,.5));
        for(HardToReachMode mode:HardToReachMode.values())assertEquals(0,ReachOutlines.opacityFactor(mode,0,.5));
    }

    @Test void sharedEdgesUseTheMoreVisibleFace() {
        float[] opacity={0,.25f,0,.75f,0,0};
        assertEquals(.75f,ReachOutlines.edgeOpacity(opacity,POS_X,POS_Y));
        assertEquals(0x4066ccff,ReachOutlines.scaledColor(0x8066ccff,.5f));
    }

    private static void assertTargetsFace(BlockPos pos,Vec3 camera,Direction face) {
        var hit=Shapes.block().clip(camera,ReachOutlines.faceTarget(pos,camera,face),pos);
        assertNotNull(hit);
        assertEquals(pos,hit.getBlockPos());
        assertEquals(face,hit.getDirection());
    }

    private static final int POS_X=1, POS_Y=3;
}
