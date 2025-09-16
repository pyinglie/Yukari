package club.nankai.mc.yukari.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

import java.util.Random;

/**
 * Random safe teleport utilities.
 * - randomSafeTeleport: old square-area method (fallback)
 * - randomSafeTeleportRing: ring-area random with uniform area distribution
 * <p>
 * Fixes:
 * 1. Remove calls to ServerChunkCache#getChunkNow(long) (that signature does not exist) and use level.getChunk(cx, cz) to force synchronous chunk loading/generation.
 * 2. Avoid using ChunkPos#toLong which causes long -> int incompatibility issues.
 */
public class TeleportUtil {
    private static final Random RAND = new Random();

    /**
     * Random within a square area (fallback).
     */
    public static boolean randomSafeTeleport(ServerPlayer player, int range, int maxTries) {
        ServerLevel level = player.serverLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        for (int i = 0; i < maxTries; i++) {
            int dx = spawn.getX() + RAND.nextInt(range * 2 + 1) - range;
            int dz = spawn.getZ() + RAND.nextInt(range * 2 + 1) - range;
            if (attemptTeleport(level, player, dx, dz)) return true;
        }
        return false;
    }

    /**
     * Random within a ring area: radius in [minDistance, maxDistance], uniform by area.
     */
    public static boolean randomSafeTeleportRing(ServerPlayer player, int minDistance, int maxDistance, int maxTries) {
        if (maxDistance <= 0) return false;
        if (minDistance < 0) minDistance = 0;
        if (minDistance > maxDistance) minDistance = maxDistance / 2;

        ServerLevel level = player.serverLevel();
        BlockPos origin = level.getSharedSpawnPos();

        double min2 = (double) minDistance * minDistance;
        double max2 = (double) maxDistance * maxDistance;

        for (int i = 0; i < maxTries; i++) {
            double r = Math.sqrt(min2 + RAND.nextDouble() * (max2 - min2));
            double theta = RAND.nextDouble() * Math.PI * 2.0;
            int dx = origin.getX() + (int) Math.round(r * Math.cos(theta));
            int dz = origin.getZ() + (int) Math.round(r * Math.sin(theta));
            if (attemptTeleport(level, player, dx, dz)) return true;
        }
        return false;
    }

    /**
     * Try to find a safe landing spot at (x,z) and teleport there.
     */
    private static boolean attemptTeleport(ServerLevel level, ServerPlayer player, int x, int z) {
        // Ensure the chunk is loaded/generated (synchronously)
        level.getChunk(x >> 4, z >> 4);

        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos base = new BlockPos(x, y, z);

        // direct height point
        if (isSafe(level, base)) {
            doTeleport(player, level, base);
            return true;
        }
        // scan downward
        for (int dy = 0; dy < 30; dy++) {
            int ny = y - dy;
            if (ny < level.getMinBuildHeight() + 5) break;
            BlockPos p = new BlockPos(x, ny, z);
            if (isSafe(level, p)) {
                doTeleport(player, level, p);
                return true;
            }
        }
        // scan upwards a little (extra safety)
        for (int dy = 1; dy <= 20; dy++) {
            int ny = y + dy;
            if (ny > level.getMaxBuildHeight() - 10) break;
            BlockPos p = new BlockPos(x, ny, z);
            if (isSafe(level, p)) {
                doTeleport(player, level, p);
                return true;
            }
        }
        return false;
    }

    private static void doTeleport(ServerPlayer player, ServerLevel level, BlockPos pos) {
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                player.getYRot(), player.getXRot());
    }

    /**
     * Determine whether the position is safe to land: foot and head are air, block below is not air or lava, no fluids, and has a collision shape.
     */
    private static boolean isSafe(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() + 5 || pos.getY() > level.getMaxBuildHeight() - 10) return false;

        var foot = level.getBlockState(pos);
        var head = level.getBlockState(pos.above());
        var below = level.getBlockState(pos.below());

        if (!foot.isAir()) return false;
        if (!head.isAir()) return false;
        if (below.isAir()) return false;
        if (below.is(Blocks.LAVA)) return false;

        FluidState fluidBelow = below.getFluidState();
        if (!fluidBelow.isEmpty()) return false;

        if (below.getCollisionShape(level, pos.below()).isEmpty()) return false;

        return true;
    }
}