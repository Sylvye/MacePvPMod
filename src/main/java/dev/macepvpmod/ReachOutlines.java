package dev.macepvpmod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class ReachOutlines {
    private record Face(int index, Direction direction) {}
    private record Edge(float x1, float y1, float z1, float x2, float y2, float z2, int faceA, int faceB) {}

    private static final int NEG_X = 0;
    private static final int POS_X = 1;
    private static final int NEG_Y = 2;
    private static final int POS_Y = 3;
    private static final int NEG_Z = 4;
    private static final int POS_Z = 5;
    private static final int FACE_COUNT = 6;
    private static final int REACH_SAMPLES = 8;
    private static final double FACE_INSET = .01;
    private static final double EDGE_INSET = .001;
    private static final Face[] FACES = {
        new Face(NEG_X,Direction.WEST), new Face(POS_X,Direction.EAST),
        new Face(NEG_Y,Direction.DOWN), new Face(POS_Y,Direction.UP),
        new Face(NEG_Z,Direction.NORTH), new Face(POS_Z,Direction.SOUTH)
    };
    private static final Edge[] EDGES = {
        new Edge(0, 0, 0, 0, 0, 1, NEG_X, NEG_Y),
        new Edge(0, 1, 0, 0, 1, 1, NEG_X, POS_Y),
        new Edge(1, 0, 0, 1, 0, 1, POS_X, NEG_Y),
        new Edge(1, 1, 0, 1, 1, 1, POS_X, POS_Y),
        new Edge(0, 0, 0, 0, 1, 0, NEG_X, NEG_Z),
        new Edge(0, 0, 1, 0, 1, 1, NEG_X, POS_Z),
        new Edge(1, 0, 0, 1, 1, 0, POS_X, NEG_Z),
        new Edge(1, 0, 1, 1, 1, 1, POS_X, POS_Z),
        new Edge(0, 0, 0, 1, 0, 0, NEG_Y, NEG_Z),
        new Edge(0, 0, 1, 1, 0, 1, NEG_Y, POS_Z),
        new Edge(0, 1, 0, 1, 1, 0, POS_Y, NEG_Z),
        new Edge(0, 1, 1, 1, 1, 1, POS_Y, POS_Z)
    };

    private ReachOutlines() {}
    public static void register() { LevelRenderEvents.COLLECT_SUBMITS.register(ReachOutlines::render); }
    private static void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
        Minecraft mc=Minecraft.getInstance(); var player=mc.player; var level=mc.level; var config=MacePvPMod.REACH_OUTLINE_CONFIG.current();
        if(!config.enabled()||player==null||level==null)return;
        double reach=player.blockInteractionRange(); int radius=(int)Math.ceil(reach); BlockPos center=player.blockPosition(); Vec3 camera=context.levelState().cameraRenderState.pos;
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        for(int x=-radius;x<=radius;x++)for(int y=-radius;y<=radius;y++)for(int z=-radius;z<=radius;z++){
            pos.setWithOffset(center,x,y,z);
            BlockState state=level.getBlockState(pos);
            if(!eligibleSupport(state)||!player.isWithinBlockInteractionRange(pos,0))continue;
            float[] faceOpacity=placeableFaces(level,player,pos,camera,reach,config);
            if(faceOpacity==null)continue;
            PoseStack pose=new PoseStack();
            pose.translate(pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z);
            context.submitNodeCollector().submitCustomGeometry(pose,RenderTypes.linesTranslucent(),(blockPose,vertices)->drawEdges(blockPose,vertices,faceOpacity,config.argb(),config.thickness()));
        }
    }

    static boolean eligibleSupport(BlockState state) {
        return !state.isAir()&&state.getFluidState().isEmpty()&&!state.canBeReplaced()&&state.canOcclude();
    }

    static boolean openDestination(BlockState state) {
        return state.canBeReplaced()&&state.getFluidState().isEmpty();
    }

    private static float[] placeableFaces(ClientLevel level,Player player,BlockPos pos,Vec3 camera,double reach,ReachOutlineConfig config) {
        float[] opacity=new float[FACE_COUNT];
        Vec3 eye=player.getEyePosition();
        boolean any=false;
        for(Face face:FACES){
            if(!allowsFace(config.topFacesOnly(),face.direction()))continue;
            if(!facesCamera(pos,camera,face.direction()))continue;
            BlockPos destination=pos.relative(face.direction());
            if(!level.isInWorldBounds(destination)||!openDestination(level.getBlockState(destination)))continue;
            double reachable=reachableArea(pos,face.direction(),eye,reach);
            float factor=(float)opacityFactor(config.hardToReachMode(),reachable,config.minimumReachableArea());
            if(factor<=0||!canInteractWithFace(level,player,pos,camera,face.direction()))continue;
            opacity[face.index()]=factor;
            any=true;
        }
        return any?opacity:null;
    }

    static boolean allowsFace(boolean topFacesOnly,Direction face) { return !topFacesOnly||face==Direction.UP; }

    static double opacityFactor(HardToReachMode mode,double reachable,double threshold) {
        if(reachable<=0)return 0;
        if(reachable>=threshold)return 1;
        return switch(mode){
            case RENDER_FULL -> 1;
            case FADE_FACES -> reachable/threshold;
            case DO_NOT_RENDER -> 0;
        };
    }

    static double reachableArea(BlockPos pos,Direction face,Vec3 eye,double reach) {
        int reachable=0;
        double reachSquared=reach*reach;
        for(int u=0;u<REACH_SAMPLES;u++)for(int v=0;v<REACH_SAMPLES;v++){
            double a=(u+.5)/REACH_SAMPLES, b=(v+.5)/REACH_SAMPLES;
            double x=pos.getX()+.5, y=pos.getY()+.5, z=pos.getZ()+.5;
            switch(face){
                case WEST -> {x=pos.getX();y=pos.getY()+a;z=pos.getZ()+b;}
                case EAST -> {x=pos.getX()+1;y=pos.getY()+a;z=pos.getZ()+b;}
                case DOWN -> {x=pos.getX()+a;y=pos.getY();z=pos.getZ()+b;}
                case UP -> {x=pos.getX()+a;y=pos.getY()+1;z=pos.getZ()+b;}
                case NORTH -> {x=pos.getX()+a;y=pos.getY()+b;z=pos.getZ();}
                case SOUTH -> {x=pos.getX()+a;y=pos.getY()+b;z=pos.getZ()+1;}
            }
            double dx=x-eye.x, dy=y-eye.y, dz=z-eye.z;
            if(dx*dx+dy*dy+dz*dz<=reachSquared)reachable++;
        }
        return reachable/(double)(REACH_SAMPLES*REACH_SAMPLES);
    }

    private static boolean facesCamera(BlockPos pos,Vec3 camera,Direction face) {
        return switch(face){
            case WEST -> camera.x<pos.getX();
            case EAST -> camera.x>pos.getX()+1;
            case DOWN -> camera.y<pos.getY();
            case UP -> camera.y>pos.getY()+1;
            case NORTH -> camera.z<pos.getZ();
            case SOUTH -> camera.z>pos.getZ()+1;
        };
    }

    private static boolean canInteractWithFace(ClientLevel level,Player player,BlockPos pos,Vec3 camera,Direction face) {
        Vec3 target=faceTarget(pos,camera,face);
        BlockHitResult hit=level.clip(new ClipContext(camera,target,ClipContext.Block.OUTLINE,ClipContext.Fluid.ANY,player));
        return hit.getBlockPos().equals(pos)&&hit.getDirection()==face;
    }

    static Vec3 faceTarget(BlockPos pos,Vec3 camera,Direction face) {
        double x=farInterior(camera.x,pos.getX());
        double y=farInterior(camera.y,pos.getY());
        double z=farInterior(camera.z,pos.getZ());
        switch(face){
            case WEST -> x=pos.getX()+FACE_INSET;
            case EAST -> x=pos.getX()+1-FACE_INSET;
            case DOWN -> y=pos.getY()+FACE_INSET;
            case UP -> y=pos.getY()+1-FACE_INSET;
            case NORTH -> z=pos.getZ()+FACE_INSET;
            case SOUTH -> z=pos.getZ()+1-FACE_INSET;
        }
        return new Vec3(x,y,z);
    }

    private static double farInterior(double camera,double min) {
        double max=min+1;
        if(camera<min)return max-EDGE_INSET;
        if(camera>max)return min+EDGE_INSET;
        return Math.clamp(camera,min+EDGE_INSET,max-EDGE_INSET);
    }

    private static void drawEdges(PoseStack.Pose pose,VertexConsumer vertices,float[] faceOpacity,int color,float width) {
        for(Edge edge:EDGES){
            float opacity=edgeOpacity(faceOpacity,edge.faceA(),edge.faceB());
            if(opacity>0)drawLine(pose,vertices,edge,scaledColor(color,opacity),width);
        }
    }

    static float edgeOpacity(float[] faceOpacity,int faceA,int faceB) { return Math.max(faceOpacity[faceA],faceOpacity[faceB]); }

    static int scaledColor(int color,float opacity) {
        int alpha=Math.round(((color>>>24)&0xff)*Math.clamp(opacity,0,1));
        return alpha<<24|color&0xffffff;
    }

    private static void drawLine(PoseStack.Pose pose,VertexConsumer vertices,Edge edge,int color,float width) {
        float dx=edge.x2()-edge.x1(), dy=edge.y2()-edge.y1(), dz=edge.z2()-edge.z1();
        float length=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        dx/=length; dy/=length; dz/=length;
        lineVertex(pose,vertices,edge.x1(),edge.y1(),edge.z1(),dx,dy,dz,color,width);
        lineVertex(pose,vertices,edge.x2(),edge.y2(),edge.z2(),dx,dy,dz,color,width);
    }

    private static void lineVertex(PoseStack.Pose pose,VertexConsumer vertices,float x,float y,float z,float nx,float ny,float nz,int color,float width) {
        vertices.addVertex(pose,x,y,z).setColor(color).setNormal(pose,nx,ny,nz).setLineWidth(width);
    }
}
