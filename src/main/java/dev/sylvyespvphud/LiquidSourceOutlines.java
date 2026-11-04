package dev.sylvyespvphud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Bucket targeting and face geometry for standalone source blocks. */
final class LiquidSourceOutlines {
    record Candidate(BlockPos pos, VoxelShape shape, double distanceSquared) {}
    record Source(BlockPos pos, VoxelShape shape, float[] opacity) {}
    private static final int SAMPLES = 8;
    private static final double INSET = .002;
    // ReachOutlines uses axis pairs, rather than Direction's enum order.
    private static final Direction[] FACES = {Direction.WEST, Direction.EAST, Direction.DOWN,
            Direction.UP, Direction.NORTH, Direction.SOUTH};
    private static final Comparator<Candidate> NEAREST = Comparator.comparingDouble(Candidate::distanceSquared)
            .thenComparingInt(c -> c.pos().getX()).thenComparingInt(c -> c.pos().getY()).thenComparingInt(c -> c.pos().getZ());

    private LiquidSourceOutlines() {}

    static boolean eligibleSource(BlockState state, LiquidSourceConfig config) {
        return config.enabled() && state.getFluidState().isSource()
                && ((config.water() && state.is(Blocks.WATER)) || (config.lava() && state.is(Blocks.LAVA)));
    }

    static List<Source> selectSources(BlockGetter level, CollisionContext collision, Vec3 eye, double reach,
                                       LiquidSourceConfig config, List<Candidate> candidates) {
        if (!config.enabled() || (!config.water() && !config.lava())) return List.of();
        var ordered = new ArrayList<>(candidates);
        ordered.sort(NEAREST);
        var result = new ArrayList<Source>();
        for (Candidate candidate : ordered) {
            if (candidate.distanceSquared() > reach * reach || !eligibleSource(level.getBlockState(candidate.pos()), config)) continue;
            float[] opacity = reachableFaces(level, collision, candidate.pos(), candidate.shape().bounds(), eye, reach, config);
            if (opacity == null) continue;
            result.add(new Source(candidate.pos(), candidate.shape(), opacity));
            if (result.size() == config.maximumSources()) break;
        }
        return List.copyOf(result);
    }

    static float[] reachableFaces(BlockGetter level, CollisionContext collision, BlockPos pos, AABB localBounds,
                                   Vec3 eye, double reach, LiquidSourceConfig config) {
        AABB box = localBounds.move(pos);
        boolean inside = box.contains(eye);
        float[] opacity = new float[6];
        boolean any = false;
        for (int i = 0; i < FACES.length; i++) {
            Direction face = FACES[i];
            if (!ReachOutlines.allowsFace(config.topFacesOnly(), face) || (!inside && !facesEye(box, eye, face))) continue;
            double area = reachableArea(box, face, eye, reach);
            float factor = (float)ReachOutlines.opacityFactor(config.hardToReachMode(), area, config.minimumReachableArea());
            if (factor <= 0 || !canTargetFace(level, collision, pos, box, face, eye, reach)) continue;
            opacity[i] = factor;
            any = true;
        }
        return any ? opacity : null;
    }

    static double reachableArea(AABB box, Direction face, Vec3 eye, double reach) {
        Vec3 nearest = closestFacePoint(box, face, eye);
        if (nearest.distanceToSqr(eye) > reach * reach) return 0;
        boolean all = true;
        for (int u = 0; u <= 1; u++) for (int v = 0; v <= 1; v++)
            all &= facePoint(box, face, u, v).distanceToSqr(eye) <= reach * reach;
        if (all) return 1;
        int count = 0;
        for (int u = 0; u < SAMPLES; u++) for (int v = 0; v < SAMPLES; v++)
            if (facePoint(box, face, (u + .5) / SAMPLES, (v + .5) / SAMPLES).distanceToSqr(eye) <= reach * reach) count++;
        return count / (double)(SAMPLES * SAMPLES);
    }

    private static boolean canTargetFace(BlockGetter level, CollisionContext collision, BlockPos pos,
                                          AABB box, Direction face, Vec3 eye, double reach) {
        if (hitsSource(level, collision, pos, face, eye, closestFacePoint(box, face, eye), reach)) return true;
        // A closer obstruction may cover only part of a face. Try reachable interior points too.
        for (int u = 0; u < SAMPLES; u++) for (int v = 0; v < SAMPLES; v++)
            if (hitsSource(level, collision, pos, face, eye,
                    facePoint(box, face, (u + .5) / SAMPLES, (v + .5) / SAMPLES), reach)) return true;
        return false;
    }

    private static boolean hitsSource(BlockGetter level, CollisionContext collision, BlockPos pos, Direction face,
                                       Vec3 eye, Vec3 surfacePoint, double reach) {
        if (surfacePoint.distanceToSqr(eye) > reach * reach) return false;
        Vec3 end = surfacePoint.add(-face.getStepX() * INSET, -face.getStepY() * INSET, -face.getStepZ() * INSET);
        var hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, collision));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) && (hit.getDirection() == face || hit.isInside())
                && hit.getLocation().distanceToSqr(eye) <= reach * reach;
    }

    static double distanceSquared(Vec3 point, AABB box) {
        double x = Math.clamp(point.x, box.minX, box.maxX) - point.x;
        double y = Math.clamp(point.y, box.minY, box.maxY) - point.y;
        double z = Math.clamp(point.z, box.minZ, box.maxZ) - point.z;
        return x*x + y*y + z*z;
    }

    private static boolean facesEye(AABB box, Vec3 eye, Direction face) {
        return switch (face) {
            case WEST -> eye.x < box.minX; case EAST -> eye.x > box.maxX;
            case DOWN -> eye.y < box.minY; case UP -> eye.y > box.maxY;
            case NORTH -> eye.z < box.minZ; case SOUTH -> eye.z > box.maxZ;
        };
    }

    private static Vec3 closestFacePoint(AABB box, Direction face, Vec3 eye) {
        double x = Math.clamp(eye.x, box.minX + INSET, box.maxX - INSET);
        double y = Math.clamp(eye.y, box.minY + INSET, box.maxY - INSET);
        double z = Math.clamp(eye.z, box.minZ + INSET, box.maxZ - INSET);
        return switch (face) {
            case WEST -> new Vec3(box.minX,y,z); case EAST -> new Vec3(box.maxX,y,z);
            case DOWN -> new Vec3(x,box.minY,z); case UP -> new Vec3(x,box.maxY,z);
            case NORTH -> new Vec3(x,y,box.minZ); case SOUTH -> new Vec3(x,y,box.maxZ);
        };
    }

    static Vec3 facePoint(AABB box, Direction face, double u, double v) {
        double x = box.minX + u*(box.maxX-box.minX), y = box.minY + u*(box.maxY-box.minY);
        double z = box.minZ + v*(box.maxZ-box.minZ);
        return switch (face) {
            case WEST -> new Vec3(box.minX, y, z); case EAST -> new Vec3(box.maxX, y, z);
            case DOWN -> new Vec3(x, box.minY, z); case UP -> new Vec3(x, box.maxY, z);
            case NORTH -> new Vec3(x, box.minY + v*(box.maxY-box.minY), box.minZ);
            case SOUTH -> new Vec3(x, box.minY + v*(box.maxY-box.minY), box.maxZ);
        };
    }
}
