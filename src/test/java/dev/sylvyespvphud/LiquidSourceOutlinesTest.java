package dev.sylvyespvphud;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LiquidSourceOutlinesTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private static LiquidSourceConfig config(boolean water,boolean lava,int limit,boolean top,HardToReachMode hard,double area) {
        return new LiquidSourceConfig(true,water,lava,limit,OutlineRenderMode.WIREFRAME,0x66ccff,.22,5,top,hard,area);
    }

    private static final class World implements BlockGetter {
        final Map<BlockPos,BlockState> blocks=new HashMap<>();
        World put(int x,int y,int z,BlockState block) { blocks.put(new BlockPos(x,y,z),block);return this; }
        public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos,Blocks.AIR.defaultBlockState()); }
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        public int getHeight() { return 384; }
        public int getMinY() { return -64; }
        List<LiquidSourceOutlines.Source> select(Vec3 eye,double reach,LiquidSourceConfig config) {
            var candidates=new ArrayList<LiquidSourceOutlines.Candidate>();
            for(var entry:blocks.entrySet())if(!entry.getValue().getFluidState().isEmpty()) {
                var pos=entry.getKey();var shape=entry.getValue().getFluidState().getShape(this,pos);
                candidates.add(new LiquidSourceOutlines.Candidate(pos,shape,LiquidSourceOutlines.distanceSquared(eye,shape.bounds().move(pos))));
            }
            return LiquidSourceOutlines.selectSources(this,CollisionContext.empty(),eye,reach,config,candidates);
        }
    }

    @Test void onlySelectedStandaloneSourcesQualify() {
        var both=LiquidSourceConfig.defaults();
        assertTrue(LiquidSourceOutlines.eligibleSource(Blocks.WATER.defaultBlockState(),both));
        assertTrue(LiquidSourceOutlines.eligibleSource(Blocks.LAVA.defaultBlockState(),both));
        assertFalse(LiquidSourceOutlines.eligibleSource(Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,1),both));
        assertFalse(LiquidSourceOutlines.eligibleSource(Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL,8),both));
        assertFalse(LiquidSourceOutlines.eligibleSource(Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,true),both));
        assertFalse(LiquidSourceOutlines.eligibleSource(Blocks.STONE.defaultBlockState(),both));
        assertFalse(LiquidSourceOutlines.eligibleSource(Blocks.WATER.defaultBlockState(),config(false,true,8,false,HardToReachMode.RENDER_FULL,.5)));
        assertFalse(LiquidSourceOutlines.eligibleSource(Blocks.LAVA.defaultBlockState(),config(true,false,8,false,HardToReachMode.RENDER_FULL,.5)));
    }

    @Test void bucketRayPassesThroughFlowButStopsAtSolidOrNearerSource() {
        var world=new World().put(0,0,0,Blocks.WATER.defaultBlockState());
        Vec3 eye=new Vec3(.5,.4,-2);
        assertEquals(1,world.select(eye,4,LiquidSourceConfig.defaults()).size());
        world.put(0,0,-1,Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,4));
        assertEquals(List.of(BlockPos.ZERO),world.select(eye,4,LiquidSourceConfig.defaults()).stream().map(LiquidSourceOutlines.Source::pos).toList());
        world.put(0,0,-1,Blocks.STONE.defaultBlockState());
        assertTrue(world.select(eye,4,LiquidSourceConfig.defaults()).isEmpty());
        world.put(0,0,-1,Blocks.WATER.defaultBlockState());
        assertEquals(List.of(new BlockPos(0,0,-1)),world.select(eye,4,LiquidSourceConfig.defaults()).stream().map(LiquidSourceOutlines.Source::pos).toList());
    }

    @Test void capCountsOnlyRenderableSourcesAcrossBothLiquids() {
        var world=new World().put(0,0,0,Blocks.WATER.defaultBlockState()).put(0,1,0,Blocks.STONE.defaultBlockState())
                .put(2,0,0,Blocks.LAVA.defaultBlockState()).put(-2,0,0,Blocks.WATER.defaultBlockState());
        var eye=new Vec3(.5,3,.5);
        var c=config(true,true,1,true,HardToReachMode.RENDER_FULL,.5);
        var selected=world.select(eye,5,c);
        assertEquals(1,selected.size());assertEquals(new BlockPos(-2,0,0),selected.getFirst().pos());
        assertEquals(2,world.select(eye,5,config(true,true,8,true,HardToReachMode.RENDER_FULL,.5)).size());
        assertEquals(new BlockPos(2,0,0),world.select(eye,5,config(false,true,1,true,HardToReachMode.RENDER_FULL,.5)).getFirst().pos());
        assertTrue(world.select(eye,5,config(false,false,8,false,HardToReachMode.RENDER_FULL,.5)).isEmpty());
        var off=new LiquidSourceConfig(false,true,true,8,OutlineRenderMode.WIREFRAME,0,.5,5,false,HardToReachMode.RENDER_FULL,.5);
        assertTrue(world.select(eye,5,off).isEmpty());
    }

    @Test void nearestUsesFluidSurfaceDistanceAndCoordinateTies() {
        var world=new World().put(0,0,0,Blocks.WATER.defaultBlockState()).put(-2,0,0,Blocks.LAVA.defaultBlockState())
                .put(2,0,0,Blocks.WATER.defaultBlockState());
        var c=config(true,true,2,true,HardToReachMode.RENDER_FULL,.5);
        var order=world.select(new Vec3(.5,3,.5),5,c).stream().map(LiquidSourceOutlines.Source::pos).toList();
        assertEquals(List.of(BlockPos.ZERO,new BlockPos(-2,0,0)),order);
        assertEquals(order,world.select(new Vec3(.5,3,.5),5,c).stream().map(LiquidSourceOutlines.Source::pos).toList());
    }

    @Test void faceTargetsMustActuallyBeWithinReach() {
        var world=new World().put(0,0,0,Blocks.WATER.defaultBlockState());
        Vec3 eye=new Vec3(.5,.4,-2);
        assertTrue(world.select(eye,1.99,LiquidSourceConfig.defaults()).isEmpty());
        assertEquals(1,world.select(eye,2.05,LiquidSourceConfig.defaults()).size());
        assertTrue(world.select(eye,4,config(true,true,8,true,HardToReachMode.RENDER_FULL,.5)).isEmpty());
        assertEquals(1,world.select(new Vec3(.5,2,.5),4,config(true,true,8,true,HardToReachMode.RENDER_FULL,.5)).size());
    }

    @Test void sourceContainingTheEyesRemainsBucketTargetable() {
        var world=new World().put(0,0,0,Blocks.WATER.defaultBlockState()).put(1,0,0,Blocks.WATER.defaultBlockState());
        var sources=world.select(new Vec3(.5,.4,.5),4,LiquidSourceConfig.defaults());
        assertEquals(List.of(BlockPos.ZERO),sources.stream().map(LiquidSourceOutlines.Source::pos).toList());
        for(float opacity:sources.getFirst().opacity())assertEquals(1,opacity);
        var top=world.select(new Vec3(.5,.4,.5),4,config(true,true,8,true,HardToReachMode.RENDER_FULL,.5));
        assertEquals(1,top.getFirst().opacity()[3]);assertEquals(0,top.getFirst().opacity()[0]);
    }

    @Test void partialSurfaceReachHonorsIndependentFadeAndHideSettings() {
        var world=new World().put(0,0,0,Blocks.WATER.defaultBlockState());
        var shape=world.getFluidState(BlockPos.ZERO).getShape(world,BlockPos.ZERO);
        AABB box=shape.bounds();assertTrue(box.maxY<1);
        Vec3 eye=new Vec3(.5,2,.5);double reach=2-box.maxY+.03;
        double area=LiquidSourceOutlines.reachableArea(box,Direction.UP,eye,reach);
        assertTrue(area>0&&area<.8);
        var full=world.select(eye,reach,config(true,true,8,true,HardToReachMode.RENDER_FULL,.8));
        var faded=world.select(eye,reach,config(true,true,8,true,HardToReachMode.FADE_FACES,.8));
        assertEquals(1,full.size());assertEquals(1,full.getFirst().opacity()[3]);
        assertEquals(area/.8,faded.getFirst().opacity()[3],1e-6);
        assertTrue(world.select(eye,reach,config(true,true,8,true,HardToReachMode.DO_NOT_RENDER,.8)).isEmpty());
    }

    @Test void modesProduceOnlyEdgesOrTintAtActualFluidHeight() {
        var shape=Shapes.box(0,0,0,1,8.0/9,1);float[] top={0,0,0,.4f,0,0};
        var wire=ReachOutlines.geometry(BlockPos.ZERO,shape,shape.bounds(),top,OutlineRenderMode.WIREFRAME);
        assertEquals(4,wire.edges().size());assertTrue(wire.quads().isEmpty());
        for(var edge:wire.edges()) { assertEquals(8.0/9+.002,edge.y1(),1e-7);assertEquals(.4f,edge.opacity()); }
        var tint=ReachOutlines.geometry(new BlockPos(2,3,4),shape,shape.bounds(),top,OutlineRenderMode.TINT);
        assertTrue(tint.edges().isEmpty());assertEquals(1,tint.quads().size());
        var quad=tint.quads().getFirst();assertEquals(Direction.UP,quad.face());assertEquals(.4f,quad.opacity());
        assertEquals(3+8.0/9,quad.box().maxY,1e-7);
        assertEquals(new Vec3(2,3+8.0/9,5),LiquidSourceOutlines.facePoint(quad.box(),quad.face(),0,1));
    }

    @Test void regularTintKeepsExistingUnitBoundaryFaceRules() {
        float[] top={0,0,0,1,0,0};var unit=new AABB(0,0,0,1,1,1);
        var slab=Blocks.STONE_SLAB.defaultBlockState().getShape(null,null);
        assertTrue(ReachOutlines.geometry(BlockPos.ZERO,slab,unit,top,OutlineRenderMode.TINT).quads().isEmpty());
        var cube=ReachOutlines.geometry(BlockPos.ZERO,Shapes.block(),unit,top,OutlineRenderMode.TINT);
        assertEquals(1,cube.quads().size());assertEquals(Direction.UP,cube.quads().getFirst().face());
    }
}
