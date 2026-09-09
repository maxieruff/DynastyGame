package com.maxieruff.dynasty.world;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** A seeded streaming voxel world; generation is background work and meshing is time-sliced on the render thread. */
public class VoxelWorld {
    public static final int DEFAULT_RENDER_DISTANCE = 6;
    public static final int WORLD_CHUNK_LIMIT = 1_000_000;
    public static final int WORLD_BLOCK_LIMIT = WORLD_CHUNK_LIMIT * VoxelChunk.SIZE;
    public static final int DIRT_LAYERS = 4;
    public static final int SURFACE_Y = 64;
    public static final int BUILD_HEIGHT = VoxelChunk.HEIGHT;
    public static final int VOID_Y = -20;
    private static final int STREAM_PADDING = 1;
    private final Map<Long, VoxelChunk> chunks = new HashMap<>();
    private final Map<Long, Future<VoxelChunk>> generating = new HashMap<>();
    private final Set<Long> initialChunks = new HashSet<>(), queuedForRebuild = new HashSet<>();
    private final ArrayDeque<VoxelChunk> rebuildQueue = new ArrayDeque<>();
    private final ConcurrentHashMap<Long, BlockType> edits = new ConcurrentHashMap<>();
    private final ExecutorService generator = Executors.newSingleThreadExecutor(runnable -> { Thread thread = new Thread(runnable, "dynasty-world-generator"); thread.setDaemon(true); return thread; });
    private final long seed;
    private final Texture grassTexture = texture("textures/grass.png"), dirtTexture = texture("textures/dirt.png"), stoneTexture = texture("textures/stone.png");
    private final Material grassMaterial = new Material(TextureAttribute.createDiffuse(grassTexture));
    private final Material dirtMaterial = new Material(TextureAttribute.createDiffuse(dirtTexture));
    private final Material stoneMaterial = new Material(TextureAttribute.createDiffuse(stoneTexture));
    private boolean initialLoadComplete;

    public VoxelWorld() { seed = new java.util.Random().nextLong(); requestInitialArea(0, 0); }

    /** Polls completed background generation and builds at most one mesh each frame. */
    public void updateStreaming(float playerX, float playerZ) {
        int chunkX = Math.floorDiv(MathUtils.floor(playerX), VoxelChunk.SIZE), chunkZ = Math.floorDiv(MathUtils.floor(playerZ), VoxelChunk.SIZE);
        requestArea(chunkX, chunkZ); collectGeneratedChunks(); rebuildOneChunk();
        if (!initialLoadComplete && allInitialChunksMeshed()) initialLoadComplete = true;
        if (initialLoadComplete) unloadDistantChunks(chunkX, chunkZ);
    }
    private void requestInitialArea(int chunkX, int chunkZ) {
        for (int x = chunkX - DEFAULT_RENDER_DISTANCE; x <= chunkX + DEFAULT_RENDER_DISTANCE; x++) for (int z = chunkZ - DEFAULT_RENDER_DISTANCE; z <= chunkZ + DEFAULT_RENDER_DISTANCE; z++) {
            if (isChunkInsideWorld(x, z)) initialChunks.add(chunkKey(x, z));
        }
        requestArea(chunkX, chunkZ);
    }
    private void requestArea(int chunkX, int chunkZ) {
        // Square rings submit nearby work first, so the world appears from the player outward.
        for (int radius = 0; radius <= DEFAULT_RENDER_DISTANCE; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
            int x = chunkX + dx, z = chunkZ + dz;
            if (isChunkInsideWorld(x, z)) requestChunk(x, z);
        }
    }
    private void requestChunk(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        if (chunks.containsKey(key) || generating.containsKey(key)) return;
        generating.put(key, generator.submit(() -> generateChunk(chunkX * VoxelChunk.SIZE, chunkZ * VoxelChunk.SIZE)));
    }
    private void collectGeneratedChunks() {
        ArrayList<Long> complete = new ArrayList<>();
        for (Map.Entry<Long, Future<VoxelChunk>> entry : generating.entrySet()) if (entry.getValue().isDone()) complete.add(entry.getKey());
        for (long key : complete) try {
            VoxelChunk chunk = generating.remove(key).get(); chunks.put(key, chunk); queueRebuildAround(chunk.getOriginX(), chunk.getOriginZ());
        } catch (Exception exception) { throw new RuntimeException("World generation failed", exception); }
    }
    private void rebuildOneChunk() {
        VoxelChunk chunk = rebuildQueue.poll(); if (chunk == null) return;
        queuedForRebuild.remove(chunkKeyFromBlock(chunk.getOriginX(), chunk.getOriginZ()));
        if (findChunk(chunk.getOriginX(), chunk.getOriginZ()) == chunk) chunk.rebuild(this);
    }
    private boolean allInitialChunksMeshed() {
        if (!generating.isEmpty() || !rebuildQueue.isEmpty()) return false;
        for (long key : initialChunks) if (!chunks.containsKey(key)) return false;
        return true;
    }
    private void unloadDistantChunks(int centerX, int centerZ) {
        ArrayList<Long> discarded = new ArrayList<>();
        for (Map.Entry<Long, VoxelChunk> entry : chunks.entrySet()) if (Math.abs(unpackChunkX(entry.getKey()) - centerX) > DEFAULT_RENDER_DISTANCE + STREAM_PADDING || Math.abs(unpackChunkZ(entry.getKey()) - centerZ) > DEFAULT_RENDER_DISTANCE + STREAM_PADDING) { entry.getValue().dispose(); discarded.add(entry.getKey()); }
        for (long key : discarded) chunks.remove(key);
    }

    private VoxelChunk generateChunk(int originX, int originZ) {
        VoxelChunk chunk = new VoxelChunk(originX, originZ);
        for (int localX = 0; localX < VoxelChunk.SIZE; localX++) for (int localZ = 0; localZ < VoxelChunk.SIZE; localZ++) {
            int x = originX + localX, z = originZ + localZ, surface = terrainHeight(x, z);
            for (int y = 0; y <= surface; y++) { BlockType type = terrainBlock(x, y, z, surface); if (type != BlockType.AIR) chunk.setLocal(localX, y, localZ, type); }
            for (int y = 0; y < VoxelChunk.HEIGHT; y++) { BlockType edit = edits.get(blockKey(x, y, z)); if (edit != null) chunk.setLocal(localX, y, localZ, edit); }
        }
        return chunk;
    }
    private BlockType terrainBlock(int x, int y, int z, int surface) {
        if (isCave(x, y, z, surface)) return BlockType.AIR;
        if (y == surface) return isRockySurface(x, z, surface) ? BlockType.STONE : BlockType.GRASS;
        return y >= surface - DIRT_LAYERS ? BlockType.DIRT : BlockType.STONE;
    }
    private int terrainHeight(int x, int z) {
        float continental = valueNoise2D(x * .006f, z * .006f, 0) * 2f - 1f, hills = valueNoise2D(x * .035f, z * .035f, 1) * 2f - 1f, detail = valueNoise2D(x * .14f, z * .14f, 2) * 2f - 1f;
        float mountainMask = smoothRange(valueNoise2D(x * .012f, z * .012f, 3), .58f, .79f), ridges = 1f - Math.abs(valueNoise2D(x * .028f, z * .028f, 4) * 2f - 1f);
        float surrounding = (rawHeight(x - 16, z) + rawHeight(x + 16, z) + rawHeight(x, z - 16) + rawHeight(x, z + 16)) * .25f;
        float local = SURFACE_Y + continental * 18f + hills * 11f + detail * 3f + mountainMask * (30f + ridges * 28f);
        return MathUtils.clamp(Math.round(local * .72f + surrounding * .28f), 12, VoxelChunk.HEIGHT - 12);
    }
    private float rawHeight(int x, int z) { return SURFACE_Y + (valueNoise2D(x * .006f, z * .006f, 0) * 2f - 1f) * 18f + (valueNoise2D(x * .035f, z * .035f, 1) * 2f - 1f) * 11f; }
    private boolean isRockySurface(int x, int z, int height) { return height >= 96 || Math.abs(terrainHeight(x + 1, z) - terrainHeight(x - 1, z)) + Math.abs(terrainHeight(x, z + 1) - terrainHeight(x, z - 1)) >= 7; }
    private boolean isCave(int x, int y, int z, int surface) {
        boolean entrance = valueNoise2D(x * .065f, z * .065f, 5) > .93f;
        if (entrance && y >= surface - 13 && y <= surface && valueNoise2D(x * .25f, z * .25f, 6) > .43f) return true;
        if (y < 8 || y > surface - DIRT_LAYERS - 2) return false;
        return valueNoise3D(x * .075f, y * .075f, z * .075f, 7) * .68f + valueNoise3D(x * .17f, y * .17f, z * .17f, 8) * .32f > .72f;
    }

    public BlockType getBlock(int x, int y, int z) {
        if (y < 0 || y >= VoxelChunk.HEIGHT || !isBlockInsideWorld(x, z)) return BlockType.AIR;
        VoxelChunk chunk = findChunk(x, z); if (chunk != null) return chunk.getLocal(Math.floorMod(x, VoxelChunk.SIZE), y, Math.floorMod(z, VoxelChunk.SIZE));
        BlockType edit = edits.get(blockKey(x, y, z)); return edit == null ? BlockType.AIR : edit;
    }
    public boolean isSolid(int x, int y, int z) { return getBlock(x, y, z).isSolid(); }
    public boolean isInsideWorld(int x, int y, int z) { return isBlockInsideWorld(x, z) && y >= 0 && y < VoxelChunk.HEIGHT; }
    public void setBlock(int x, int y, int z, BlockType type) {
        if (!isInsideWorld(x, y, z)) return; edits.put(blockKey(x, y, z), type);
        VoxelChunk chunk = findChunk(x, z); if (chunk != null) { chunk.setLocal(Math.floorMod(x, VoxelChunk.SIZE), y, Math.floorMod(z, VoxelChunk.SIZE), type); queueRebuildAround(x, z); }
    }
    private void queueRebuildAround(int x, int z) {
        queueRebuild(findChunk(x, z));
        if (Math.floorMod(x, VoxelChunk.SIZE) == 0) queueRebuild(findChunk(x - 1, z)); if (Math.floorMod(x + 1, VoxelChunk.SIZE) == 0) queueRebuild(findChunk(x + 1, z));
        if (Math.floorMod(z, VoxelChunk.SIZE) == 0) queueRebuild(findChunk(x, z - 1)); if (Math.floorMod(z + 1, VoxelChunk.SIZE) == 0) queueRebuild(findChunk(x, z + 1));
    }
    private void queueRebuild(VoxelChunk chunk) { if (chunk != null && queuedForRebuild.add(chunkKeyFromBlock(chunk.getOriginX(), chunk.getOriginZ()))) rebuildQueue.add(chunk); }
    public boolean isInitialLoadComplete() { return initialLoadComplete; }
    public float getInitialLoadProgress() { int ready = 0; for (long key : initialChunks) if (chunks.containsKey(key) && !queuedForRebuild.contains(key)) ready++; return initialChunks.isEmpty() ? 1f : ready / (float) initialChunks.size(); }
    public long getSeed() { return seed; }
    public int getSpawnY() { return terrainHeight(0, 0) + 1; }
    public int getSurfaceY(int x, int z) { for (int y = VoxelChunk.HEIGHT - 1; y >= 0; y--) if (isSolid(x, y, z)) return y + 1; return 0; }
    public void renderableChunks(PerspectiveCamera camera, Array<ModelInstance> output) { output.clear(); BoundingBox bounds = new BoundingBox(); for (VoxelChunk chunk : chunks.values()) { ModelInstance instance = chunk.getInstance(); if (instance != null) { chunk.getBounds(bounds); if (camera.frustum.boundsInFrustum(bounds)) output.add(instance); } } }
    public Material getGrassMaterial() { return grassMaterial; } public Material getDirtMaterial() { return dirtMaterial; } public Material getStoneMaterial() { return stoneMaterial; }
    public void dispose() { generator.shutdownNow(); for (VoxelChunk chunk : chunks.values()) chunk.dispose(); grassTexture.dispose(); dirtTexture.dispose(); stoneTexture.dispose(); }
    private VoxelChunk findChunk(int x, int z) { return chunks.get(chunkKey(Math.floorDiv(x, VoxelChunk.SIZE), Math.floorDiv(z, VoxelChunk.SIZE))); }
    private static boolean isChunkInsideWorld(int x, int z) { return x >= -WORLD_CHUNK_LIMIT && x < WORLD_CHUNK_LIMIT && z >= -WORLD_CHUNK_LIMIT && z < WORLD_CHUNK_LIMIT; }
    private static boolean isBlockInsideWorld(int x, int z) { return x >= -WORLD_BLOCK_LIMIT && x < WORLD_BLOCK_LIMIT && z >= -WORLD_BLOCK_LIMIT && z < WORLD_BLOCK_LIMIT; }
    private static long chunkKey(int x, int z) { return ((long)x << 32) ^ (z & 0xffffffffL); } private static long chunkKeyFromBlock(int x, int z) { return chunkKey(Math.floorDiv(x, VoxelChunk.SIZE), Math.floorDiv(z, VoxelChunk.SIZE)); }
    private static int unpackChunkX(long key) { return (int)(key >> 32); } private static int unpackChunkZ(long key) { return (int)key; }
    private static long blockKey(int x, int y, int z) { return ((long)(x + WORLD_BLOCK_LIMIT) << 34) | ((long)(z + WORLD_BLOCK_LIMIT) << 9) | y; }
    private float valueNoise2D(float x, float z, int salt) { int x0 = fastFloor(x), z0 = fastFloor(z); float tx = smooth(x - x0), tz = smooth(z - z0); return lerp(lerp(random(x0,0,z0,salt), random(x0+1,0,z0,salt), tx), lerp(random(x0,0,z0+1,salt), random(x0+1,0,z0+1,salt), tx), tz); }
    private float valueNoise3D(float x, float y, float z, int salt) { int x0=fastFloor(x), y0=fastFloor(y), z0=fastFloor(z); float tx=smooth(x-x0), ty=smooth(y-y0), tz=smooth(z-z0); float a=lerp(random(x0,y0,z0,salt),random(x0+1,y0,z0,salt),tx), b=lerp(random(x0,y0+1,z0,salt),random(x0+1,y0+1,z0,salt),tx), c=lerp(random(x0,y0,z0+1,salt),random(x0+1,y0,z0+1,salt),tx), d=lerp(random(x0,y0+1,z0+1,salt),random(x0+1,y0+1,z0+1,salt),tx); return lerp(lerp(a,b,ty),lerp(c,d,ty),tz); }
    private float random(int x, int y, int z, int salt) { long hash=((long)x*73428767L)^((long)y*912931L)^((long)z*19349663L)^seed^((long)salt*83492791L); hash=(hash^(hash>>>33))*0xff51afd7ed558ccdL; hash^=hash>>>33; return (hash&Long.MAX_VALUE)/(float)Long.MAX_VALUE; }
    private static int fastFloor(float value) { int floor=(int)value; return value<floor?floor-1:floor; } private static float smooth(float value) { return value*value*(3f-2f*value); } private static float smoothRange(float value,float min,float max) { return smooth(MathUtils.clamp((value-min)/(max-min),0f,1f)); } private static float lerp(float a,float b,float amount) { return a+(b-a)*amount; }
    private static Texture texture(String path) { Texture texture=new Texture(path); texture.setFilter(TextureFilter.Nearest,TextureFilter.Nearest); texture.setWrap(TextureWrap.Repeat,TextureWrap.Repeat); return texture; }
}
