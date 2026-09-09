package com.maxieruff.dynasty.world;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;

/** Four adjacent chunks centred around (0, 0), with world-space block access. */
public class VoxelWorld {
    public static final int STONE_LAYERS = 30;
    public static final int DIRT_LAYERS = 4;
    public static final int SURFACE_Y = STONE_LAYERS + DIRT_LAYERS;
    public static final int BUILD_HEIGHT = 20;
    public static final int VOID_Y = -20;
    private final Array<VoxelChunk> chunks = new Array<>();
    private final Texture grassTexture = texture("textures/grass.png");
    private final Texture dirtTexture = texture("textures/dirt.png");
    private final Texture stoneTexture = texture("textures/stone.png");
    private final Material grassMaterial = new Material(TextureAttribute.createDiffuse(grassTexture));
    private final Material dirtMaterial = new Material(TextureAttribute.createDiffuse(dirtTexture));
    private final Material stoneMaterial = new Material(TextureAttribute.createDiffuse(stoneTexture));

    public VoxelWorld() {
        chunks.add(new VoxelChunk(-16, -16)); chunks.add(new VoxelChunk(0, -16));
        chunks.add(new VoxelChunk(-16, 0)); chunks.add(new VoxelChunk(0, 0));
        generateTerrain(); rebuildAll();
    }

    private void generateTerrain() {
        for (int x = -16; x < 16; x++) for (int z = -16; z < 16; z++) {
            for (int y = 0; y < STONE_LAYERS; y++) setBlockInternal(x, y, z, BlockType.STONE);
            for (int y = STONE_LAYERS; y < SURFACE_Y; y++) setBlockInternal(x, y, z, BlockType.DIRT);
            setBlockInternal(x, SURFACE_Y, z, BlockType.GRASS);
        }
    }

    public BlockType getBlock(int x, int y, int z) {
        VoxelChunk chunk = findChunk(x, z);
        return chunk == null || y < 0 || y >= VoxelChunk.HEIGHT ? BlockType.AIR : chunk.getLocal(x - chunk.getOriginX(), y, z - chunk.getOriginZ());
    }
    public boolean isSolid(int x, int y, int z) { return getBlock(x, y, z).isSolid(); }
    public boolean isInsideWorld(int x, int y, int z) { return x >= -16 && x < 16 && z >= -16 && z < 16 && y >= 0 && y < VoxelChunk.HEIGHT; }

    public void setBlock(int x, int y, int z, BlockType type) {
        if (!isInsideWorld(x, y, z)) return;
        setBlockInternal(x, y, z, type);
        rebuildAround(x, z);
    }
    private void setBlockInternal(int x, int y, int z, BlockType type) {
        VoxelChunk chunk = findChunk(x, z);
        if (chunk != null && y >= 0 && y < VoxelChunk.HEIGHT) chunk.setLocal(x - chunk.getOriginX(), y, z - chunk.getOriginZ(), type);
    }
    private void rebuildAround(int x, int z) {
        VoxelChunk changed = findChunk(x, z); if (changed != null) changed.rebuild(this);
        if (Math.floorMod(x, VoxelChunk.SIZE) == 0) rebuildChunk(x - 1, z);
        if (Math.floorMod(x + 1, VoxelChunk.SIZE) == 0) rebuildChunk(x + 1, z);
        if (Math.floorMod(z, VoxelChunk.SIZE) == 0) rebuildChunk(x, z - 1);
        if (Math.floorMod(z + 1, VoxelChunk.SIZE) == 0) rebuildChunk(x, z + 1);
    }
    private void rebuildChunk(int x, int z) { VoxelChunk chunk = findChunk(x, z); if (chunk != null) chunk.rebuild(this); }
    private void rebuildAll() { for (VoxelChunk chunk : chunks) chunk.rebuild(this); }

    public void renderableChunks(PerspectiveCamera camera, Array<ModelInstance> output) {
        output.clear();
        BoundingBox bounds = new BoundingBox();
        for (VoxelChunk chunk : chunks) {
            ModelInstance instance = chunk.getInstance();
            if (instance == null) continue;
            chunk.getBounds(bounds);
            if (camera.frustum.boundsInFrustum(bounds)) output.add(instance);
        }
    }
    public int getSurfaceY(int x, int z) { for (int y = VoxelChunk.HEIGHT - 1; y >= 0; y--) if (isSolid(x, y, z)) return y + 1; return 0; }
    public Material getGrassMaterial() { return grassMaterial; }
    public Material getDirtMaterial() { return dirtMaterial; }
    public Material getStoneMaterial() { return stoneMaterial; }
    public void dispose() { for (VoxelChunk chunk : chunks) chunk.dispose(); grassTexture.dispose(); dirtTexture.dispose(); stoneTexture.dispose(); }

    private static Texture texture(String path) {
        Texture texture = new Texture(path);
        texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        texture.setWrap(TextureWrap.Repeat, TextureWrap.Repeat);
        return texture;
    }

    private VoxelChunk findChunk(int x, int z) { for (VoxelChunk chunk : chunks) if (x >= chunk.getOriginX() && x < chunk.getOriginX() + VoxelChunk.SIZE && z >= chunk.getOriginZ() && z < chunk.getOriginZ() + VoxelChunk.SIZE) return chunk; return null; }
}
