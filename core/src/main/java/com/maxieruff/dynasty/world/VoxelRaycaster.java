package com.maxieruff.dynasty.world;

import com.badlogic.gdx.math.Vector3;

/** Amanatides-Woo grid traversal: visits only the voxel cells crossed by a ray. */
public final class VoxelRaycaster {
    private VoxelRaycaster() { }
    public static RaycastHit cast(VoxelWorld world, Vector3 origin, Vector3 direction, float reach) {
        if (reach < 0 || direction.len2() == 0) return null;
        int x = floor(origin.x), y = floor(origin.y), z = floor(origin.z);
        int stepX = direction.x >= 0 ? 1 : -1, stepY = direction.y >= 0 ? 1 : -1, stepZ = direction.z >= 0 ? 1 : -1;
        float tx = tToBoundary(origin.x, direction.x, x, stepX), ty = tToBoundary(origin.y, direction.y, y, stepY), tz = tToBoundary(origin.z, direction.z, z, stepZ);
        float dx = delta(direction.x), dy = delta(direction.y), dz = delta(direction.z); int nx = 0, ny = 0, nz = 0;
        while (true) {
            if (world.isSolid(x, y, z)) return new RaycastHit(x, y, z, nx, ny, nz);
            if (tx <= ty && tx <= tz) { if (tx > reach) return null; x += stepX; nx = -stepX; ny = nz = 0; tx += dx; }
            else if (ty <= tz) { if (ty > reach) return null; y += stepY; ny = -stepY; nx = nz = 0; ty += dy; }
            else { if (tz > reach) return null; z += stepZ; nz = -stepZ; nx = ny = 0; tz += dz; }
        }
    }
    private static int floor(float value) { return (int) Math.floor(value); }
    private static float delta(float value) { return value == 0 ? Float.POSITIVE_INFINITY : Math.abs(1f / value); }
    private static float tToBoundary(float position, float direction, int cell, int step) { if (direction == 0) return Float.POSITIVE_INFINITY; float boundary = step > 0 ? cell + 1 : cell; return (boundary - position) / direction; }
}
