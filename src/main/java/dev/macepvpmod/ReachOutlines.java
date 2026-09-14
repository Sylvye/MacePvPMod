package dev.macepvpmod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ReachOutlines {
    private record Face(int index, Direction direction) {}
    record Edge(double x1,double y1,double z1,double x2,double y2,double z2,float opacity) {}
    private record EdgeKey(long x1,long y1,long z1,long x2,long y2,long z2) {
        static EdgeKey of(double x1,double y1,double z1,double x2,double y2,double z2) {
            if(compare(x1,y1,z1,x2,y2,z2)>0)return of(x2,y2,z2,x1,y1,z1);
            return new EdgeKey(bits(x1),bits(y1),bits(z1),bits(x2),bits(y2),bits(z2));
        }
        private static int compare(double x1,double y1,double z1,double x2,double y2,double z2) {int c=Double.compare(x1,x2);if(c==0)c=Double.compare(y1,y2);if(c==0)c=Double.compare(z1,z2);return c;}
        private static long bits(double value){return Double.doubleToLongBits(Math.rint(value*4096)/4096);}
    }
    private record CachedEdge(double x1,double y1,double z1,double x2,double y2,double z2,float opacity) {}

    private static final int NEG_X=0,POS_X=1,NEG_Y=2,POS_Y=3,NEG_Z=4,POS_Z=5,FACE_COUNT=6;
    private static final int REACH_SAMPLES=8;
    private static final double FACE_INSET=.002,EPSILON=1e-7;
    private static final Face[] FACES={new Face(NEG_X,Direction.WEST),new Face(POS_X,Direction.EAST),new Face(NEG_Y,Direction.DOWN),new Face(POS_Y,Direction.UP),new Face(NEG_Z,Direction.NORTH),new Face(POS_Z,Direction.SOUTH)};
    private static List<CachedEdge> cachedEdges=List.of();
    private static ClientLevel cachedLevel;
    private static long cachedTick=Long.MIN_VALUE;
    private static ReachOutlineConfig cachedConfig;
    private static double cachedReach=Double.NaN;
    private static BlockPos cachedPlayerBlock=BlockPos.ZERO;

    private ReachOutlines() {}
    public static void register(){ClientTickEvents.END_CLIENT_TICK.register(ReachOutlines::tick);LevelRenderEvents.COLLECT_SUBMITS.register(ReachOutlines::render);}

    private static void tick(Minecraft mc){
        Player player=mc.player;ClientLevel level=mc.level;ReachOutlineConfig config=MacePvPMod.REACH_OUTLINE_CONFIG.current();
        if(!config.enabled()||player==null||level==null){cachedEdges=List.of();cachedLevel=level;return;}
        double reach=player.blockInteractionRange();BlockPos playerBlock=player.blockPosition();long tick=level.getGameTime();
        if(level==cachedLevel&&tick==cachedTick&&config.equals(cachedConfig)&&reach==cachedReach&&playerBlock.equals(cachedPlayerBlock))return;
        cachedLevel=level;cachedTick=tick;cachedConfig=config;cachedReach=reach;cachedPlayerBlock=playerBlock.immutable();cachedEdges=buildGeometry(level,player,reach,config);
    }

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null||mc.level!=cachedLevel||cachedEdges.isEmpty())return;
        Vec3 camera=context.levelState().cameraRenderState.pos;PoseStack pose=new PoseStack();pose.translate(-camera.x,-camera.y,-camera.z);List<CachedEdge> edges=cachedEdges;ReachOutlineConfig config=cachedConfig;
        context.submitNodeCollector().submitCustomGeometry(pose,RenderTypes.linesTranslucent(),(blockPose,vertices)->drawEdges(blockPose,vertices,edges,config.argb(),config.thickness()));
    }

    private static List<CachedEdge> buildGeometry(ClientLevel level,Player player,double reach,ReachOutlineConfig config){
        ArrayList<CachedEdge> result=new ArrayList<>();Vec3 eye=player.getEyePosition();int radius=(int)Math.ceil(reach)+1;BlockPos center=BlockPos.containing(eye);BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();double reachSquared=reach*reach;
        for(int x=-radius;x<=radius;x++)for(int y=-radius;y<=radius;y++)for(int z=-radius;z<=radius;z++){
            pos.setWithOffset(center,x,y,z);if(distanceSquaredToBox(eye,pos)>reachSquared)continue;
            BlockState state=level.getBlockState(pos);if(!eligibleSupport(state))continue;
            VoxelShape shape=state.getShape(level,pos,CollisionContext.of(player)).optimize();if(shape.isEmpty())continue;
            float[] opacity=placeableFaces(level,player,pos,shape,eye,reach,config);if(opacity==null)continue;
            for(Edge edge:shapeEdges(shape,opacity))result.add(new CachedEdge(pos.getX()+edge.x1,pos.getY()+edge.y1,pos.getZ()+edge.z1,pos.getX()+edge.x2,pos.getY()+edge.y2,pos.getZ()+edge.z2,edge.opacity));
        }
        return List.copyOf(result);
    }

    static boolean eligibleSupport(BlockState state){return !state.isAir()&&state.getFluidState().isEmpty()&&!state.canBeReplaced();}
    static boolean openDestination(BlockState state){return state.canBeReplaced()&&state.getFluidState().isEmpty();}
    static boolean allowsFace(boolean topFacesOnly,Direction face){return !topFacesOnly||face==Direction.UP;}
    static boolean supportsRailOrFire(ClientLevel level,BlockPos support,Direction face){
        BlockPos destination=support.relative(face);if(!level.isInWorldBounds(destination))return false;BlockState existing=level.getBlockState(destination);if(!openDestination(existing))return false;
        boolean rail=face==Direction.UP&&Blocks.RAIL.defaultBlockState().canSurvive(level,destination);boolean fire=BaseFireBlock.getState(level,destination).canSurvive(level,destination);return rail||fire;
    }

    private static float[] placeableFaces(ClientLevel level,Player player,BlockPos pos,VoxelShape shape,Vec3 eye,double reach,ReachOutlineConfig config){
        float[] opacity=new float[FACE_COUNT];boolean any=false;
        for(Face face:FACES){
            if(!allowsFace(config.topFacesOnly(),face.direction())||!facesEye(pos,eye,face.direction())||!hasBoundary(shape,face.direction()))continue;
            if(!supportsRailOrFire(level,pos,face.direction()))continue;
            double reachable=reachableArea(pos,face.direction(),shape,eye,reach);float factor=(float)opacityFactor(config.hardToReachMode(),reachable,config.minimumReachableArea());
            if(factor<=0||!canInteractWithFace(level,player,pos,shape,eye,face.direction()))continue;opacity[face.index()]=factor;any=true;
        }
        return any?opacity:null;
    }

    static double opacityFactor(HardToReachMode mode,double reachable,double threshold){if(reachable<=0)return 0;if(reachable>=threshold)return 1;return switch(mode){case RENDER_FULL->1;case FADE_FACES->reachable/threshold;case DO_NOT_RENDER->0;};}
    static double reachableArea(BlockPos pos,Direction face,Vec3 eye,double reach){return reachableArea(pos,face,Shapes.block(),eye,reach);}
    static double reachableArea(BlockPos pos,Direction face,VoxelShape shape,Vec3 eye,double reach){
        double reachSquared=reach*reach,minDistance=Double.POSITIVE_INFINITY,maxDistance=0;Vec3 localEye=eye.subtract(pos.getX(),pos.getY(),pos.getZ());
        for(AABB box:shape.toAabbs())if(touchesBoundary(box,face)){
            minDistance=Math.min(minDistance,distanceToFaceSquared(box,face,localEye,false));
            maxDistance=Math.max(maxDistance,distanceToFaceSquared(box,face,localEye,true));
        }
        if(minDistance>reachSquared)return 0;if(maxDistance<=reachSquared)return 1;
        int surface=0,reachable=0;
        for(int u=0;u<REACH_SAMPLES;u++)for(int v=0;v<REACH_SAMPLES;v++){double a=(u+.5)/REACH_SAMPLES,b=(v+.5)/REACH_SAMPLES;Vec3 local=facePoint(face,a,b,0);if(!containsBoundaryPoint(shape,face,local))continue;surface++;double dx=pos.getX()+local.x-eye.x,dy=pos.getY()+local.y-eye.y,dz=pos.getZ()+local.z-eye.z;if(dx*dx+dy*dy+dz*dz<=reachSquared)reachable++;}
        return surface==0?0:reachable/(double)surface;
    }

    private static double distanceToFaceSquared(AABB box,Direction face,Vec3 point,boolean farthest){
        double x=axisDistance(point.x,box.minX,box.maxX,farthest),y=axisDistance(point.y,box.minY,box.maxY,farthest),z=axisDistance(point.z,box.minZ,box.maxZ,farthest);
        switch(face){case WEST->x=-point.x;case EAST->x=1-point.x;case DOWN->y=-point.y;case UP->y=1-point.y;case NORTH->z=-point.z;case SOUTH->z=1-point.z;}
        return x*x+y*y+z*z;
    }
    private static double axisDistance(double value,double min,double max,boolean farthest){if(farthest)return Math.max(Math.abs(value-min),Math.abs(value-max));if(value<min)return min-value;if(value>max)return value-max;return 0;}

    static List<Edge> shapeEdges(VoxelShape shape,float[] faceOpacity){
        Map<EdgeKey,Edge> edges=new HashMap<>();shape.forAllEdges((x1,y1,z1,x2,y2,z2)->{
            int mask=boundaryMask(x1,y1,z1,x2,y2,z2);float opacity=0;double ox=0,oy=0,oz=0;
            for(Face face:FACES)if((mask&(1<<face.index()))!=0&&faceOpacity[face.index()]>0){opacity=Math.max(opacity,faceOpacity[face.index()]);Direction d=face.direction();ox+=d.getStepX();oy+=d.getStepY();oz+=d.getStepZ();}
            if(opacity<=0)return;Edge edge=new Edge(x1+ox*FACE_INSET,y1+oy*FACE_INSET,z1+oz*FACE_INSET,x2+ox*FACE_INSET,y2+oy*FACE_INSET,z2+oz*FACE_INSET,opacity);edges.merge(EdgeKey.of(x1,y1,z1,x2,y2,z2),edge,(a,b)->a.opacity>=b.opacity?a:b);
        });return List.copyOf(edges.values());
    }
    private static int boundaryMask(double x1,double y1,double z1,double x2,double y2,double z2){int mask=0;if(zero(x1)&&zero(x2))mask|=1<<NEG_X;if(one(x1)&&one(x2))mask|=1<<POS_X;if(zero(y1)&&zero(y2))mask|=1<<NEG_Y;if(one(y1)&&one(y2))mask|=1<<POS_Y;if(zero(z1)&&zero(z2))mask|=1<<NEG_Z;if(one(z1)&&one(z2))mask|=1<<POS_Z;return mask;}
    private static boolean hasBoundary(VoxelShape shape,Direction face){for(AABB box:shape.toAabbs())if(touchesBoundary(box,face))return true;return false;}
    private static boolean containsBoundaryPoint(VoxelShape shape,Direction face,Vec3 point){for(AABB box:shape.toAabbs())if(touchesBoundary(box,face)&&containsOnFace(box,face,point))return true;return false;}
    private static boolean touchesBoundary(AABB b,Direction face){return switch(face){case WEST->zero(b.minX);case EAST->one(b.maxX);case DOWN->zero(b.minY);case UP->one(b.maxY);case NORTH->zero(b.minZ);case SOUTH->one(b.maxZ);};}
    private static boolean containsOnFace(AABB b,Direction face,Vec3 p){return switch(face){case WEST,EAST->within(p.y,b.minY,b.maxY)&&within(p.z,b.minZ,b.maxZ);case DOWN,UP->within(p.x,b.minX,b.maxX)&&within(p.z,b.minZ,b.maxZ);case NORTH,SOUTH->within(p.x,b.minX,b.maxX)&&within(p.y,b.minY,b.maxY);};}

    private static boolean canInteractWithFace(ClientLevel level,Player player,BlockPos pos,VoxelShape shape,Vec3 eye,Direction face){Vec3 target=faceTarget(pos,shape,eye,face);if(target==null)return false;BlockHitResult hit=level.clip(new ClipContext(eye,target,ClipContext.Block.OUTLINE,ClipContext.Fluid.ANY,player));return hit.getBlockPos().equals(pos)&&hit.getDirection()==face;}
    static Vec3 faceTarget(BlockPos pos,Vec3 eye,Direction face){return faceTarget(pos,Shapes.block(),eye,face);}
    static Vec3 faceTarget(BlockPos pos,VoxelShape shape,Vec3 eye,Direction face){
        Vec3 localEye=eye.subtract(pos.getX(),pos.getY(),pos.getZ()),bestPoint=null;double bestDistance=Double.POSITIVE_INFINITY;
        for(AABB box:shape.toAabbs())if(touchesBoundary(box,face)){Vec3 point=faceInteriorPoint(box,face,localEye);double distance=point.distanceToSqr(localEye);if(distance<bestDistance){bestDistance=distance;bestPoint=point;}}
        if(bestPoint==null)return null;Direction d=face;return bestPoint.add(pos.getX()-d.getStepX()*FACE_INSET,pos.getY()-d.getStepY()*FACE_INSET,pos.getZ()-d.getStepZ()*FACE_INSET);
    }
    private static Vec3 faceInteriorPoint(AABB b,Direction face,Vec3 eye){double x=farInterior(eye.x,b.minX,b.maxX),y=farInterior(eye.y,b.minY,b.maxY),z=farInterior(eye.z,b.minZ,b.maxZ);return switch(face){case WEST->new Vec3(0,y,z);case EAST->new Vec3(1,y,z);case DOWN->new Vec3(x,0,z);case UP->new Vec3(x,1,z);case NORTH->new Vec3(x,y,0);case SOUTH->new Vec3(x,y,1);};}
    private static double farInterior(double value,double min,double max){if(max-min<=FACE_INSET*2)return (min+max)/2;if(value<min)return max-FACE_INSET;if(value>max)return min+FACE_INSET;return Math.clamp(value,min+FACE_INSET,max-FACE_INSET);}
    private static Vec3 facePoint(Direction face,double a,double b,double inset){return switch(face){case WEST->new Vec3(inset,a,b);case EAST->new Vec3(1-inset,a,b);case DOWN->new Vec3(a,inset,b);case UP->new Vec3(a,1-inset,b);case NORTH->new Vec3(a,b,inset);case SOUTH->new Vec3(a,b,1-inset);};}
    private static boolean facesEye(BlockPos pos,Vec3 eye,Direction face){return switch(face){case WEST->eye.x<pos.getX();case EAST->eye.x>pos.getX()+1;case DOWN->eye.y<pos.getY();case UP->eye.y>pos.getY()+1;case NORTH->eye.z<pos.getZ();case SOUTH->eye.z>pos.getZ()+1;};}
    static double distanceSquaredToBox(Vec3 point,BlockPos pos){double dx=Math.max(Math.max(pos.getX()-point.x,0),point.x-pos.getX()-1),dy=Math.max(Math.max(pos.getY()-point.y,0),point.y-pos.getY()-1),dz=Math.max(Math.max(pos.getZ()-point.z,0),point.z-pos.getZ()-1);return dx*dx+dy*dy+dz*dz;}
    private static boolean within(double v,double min,double max){return v>=min-EPSILON&&v<=max+EPSILON;}private static boolean zero(double v){return Math.abs(v)<EPSILON;}private static boolean one(double v){return Math.abs(v-1)<EPSILON;}
    static float edgeOpacity(float[] faceOpacity,int faceA,int faceB){return Math.max(faceOpacity[faceA],faceOpacity[faceB]);}
    static int scaledColor(int color,float opacity){int alpha=Math.round(((color>>>24)&0xff)*Math.clamp(opacity,0,1));return alpha<<24|color&0xffffff;}
    private static void drawEdges(PoseStack.Pose pose,VertexConsumer vertices,List<CachedEdge> edges,int color,float width){for(CachedEdge edge:edges)drawLine(pose,vertices,edge,scaledColor(color,edge.opacity),width);}
    private static void drawLine(PoseStack.Pose pose,VertexConsumer vertices,CachedEdge edge,int color,float width){float dx=(float)(edge.x2-edge.x1),dy=(float)(edge.y2-edge.y1),dz=(float)(edge.z2-edge.z1),length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);dx/=length;dy/=length;dz/=length;lineVertex(pose,vertices,(float)edge.x1,(float)edge.y1,(float)edge.z1,dx,dy,dz,color,width);lineVertex(pose,vertices,(float)edge.x2,(float)edge.y2,(float)edge.z2,dx,dy,dz,color,width);}
    private static void lineVertex(PoseStack.Pose pose,VertexConsumer vertices,float x,float y,float z,float nx,float ny,float nz,int color,float width){vertices.addVertex(pose,x,y,z).setColor(color).setNormal(pose,nx,ny,nz).setLineWidth(width);}
}
