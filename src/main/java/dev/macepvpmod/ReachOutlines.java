package dev.macepvpmod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class ReachOutlines {
    private record Edge(float x1, float y1, float z1, float x2, float y2, float z2, int faceA, int faceB) {}

    private static final int NEG_X = 1 << 0;
    private static final int POS_X = 1 << 1;
    private static final int NEG_Y = 1 << 2;
    private static final int POS_Y = 1 << 3;
    private static final int NEG_Z = 1 << 4;
    private static final int POS_Z = 1 << 5;
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
            if(!player.isWithinBlockInteractionRange(pos,0)||level.getBlockState(pos).isAir())continue;
            int visibleFaces=visibleFaces(pos,camera);
            if(visibleFaces==0)continue;
            PoseStack pose=new PoseStack();
            pose.translate(pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z);
            context.submitNodeCollector().submitCustomGeometry(pose,RenderTypes.linesTranslucent(),(blockPose,vertices)->drawEdges(blockPose,vertices,visibleFaces,config.argb(),config.thickness()));
        }
    }

    private static int visibleFaces(BlockPos pos,Vec3 camera) {
        double minX=pos.getX(), minY=pos.getY(), minZ=pos.getZ();
        double maxX=minX+1, maxY=minY+1, maxZ=minZ+1;
        int faces=0;
        if(camera.x<minX)faces|=NEG_X; else if(camera.x>maxX)faces|=POS_X;
        if(camera.y<minY)faces|=NEG_Y; else if(camera.y>maxY)faces|=POS_Y;
        if(camera.z<minZ)faces|=NEG_Z; else if(camera.z>maxZ)faces|=POS_Z;
        return faces;
    }

    private static void drawEdges(PoseStack.Pose pose,VertexConsumer vertices,int visibleFaces,int color,float width) {
        for(Edge edge:EDGES)if((visibleFaces&edge.faceA())!=0||(visibleFaces&edge.faceB())!=0)drawLine(pose,vertices,edge,color,width);
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
