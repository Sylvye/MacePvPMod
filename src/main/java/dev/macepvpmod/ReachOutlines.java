package dev.macepvpmod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

public final class ReachOutlines {
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
            for(int layer=0;layer<config.thickness();layer++){
                double e=layer*.0025;
                PoseStack pose=new PoseStack(); pose.translate(pos.getX()-camera.x-e,pos.getY()-camera.y-e,pos.getZ()-camera.z-e);
                context.submitNodeCollector().submitShapeOutline(pose,Shapes.box(0,0,0,1+e*2,1+e*2,1+e*2),RenderTypes.linesTranslucent(),config.argb(),1,false);
            }
        }
    }
}
