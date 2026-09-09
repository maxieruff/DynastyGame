package com.maxieruff.dynasty.world;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;

/** A 16x16 vertical slice of the voxel world. Only exposed block faces are meshed. */
public class VoxelChunk {
    public static final int SIZE = 16;
    /** The world supports terrain and structures up to y=399. */
    public static final int HEIGHT = 400;
    private static final int ATTRS = Usage.Position | Usage.Normal | Usage.TextureCoordinates;
    private final int originX, originZ;
    private final BlockType[][][] blocks = new BlockType[SIZE][HEIGHT][SIZE];
    private Model model;
    private ModelInstance instance;

    public VoxelChunk(int originX, int originZ) {
        this.originX = originX; this.originZ = originZ;
    }
    public int getOriginX() { return originX; }
    public int getOriginZ() { return originZ; }
    public BlockType getLocal(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return BlockType.AIR;
        BlockType block = blocks[x][y][z];
        return block == null ? BlockType.AIR : block;
    }
    public void setLocal(int x, int y, int z, BlockType type) { if (x >= 0 && x < SIZE && y >= 0 && y < HEIGHT && z >= 0 && z < SIZE) blocks[x][y][z] = type; }
    public ModelInstance getInstance() { return instance; }
    public void getBounds(BoundingBox out) {
        out.set(new Vector3(originX, 0, originZ), new Vector3(originX + SIZE, HEIGHT, originZ + SIZE));
    }

    public void rebuild(VoxelWorld world) {
        if (model != null) {
            model.dispose();
            model = null;
        }
        instance = null;
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        boolean any = addGrassTops(builder, world) | addDirtFaces(builder, world) | addStoneFaces(builder, world);
        if (!any) {
            builder.end().dispose();
            return;
        }
        model = builder.end();
        instance = new ModelInstance(model);
    }

    private boolean addGrassTops(ModelBuilder builder, VoxelWorld world) {
        MeshPartBuilder part = null;
        for (int x = 0; x < SIZE; x++) for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < SIZE; z++) {
            if (blocks[x][y][z] != BlockType.GRASS) continue;
            int wx = originX + x, wz = originZ + z;
            if (world.isSolid(wx, y + 1, wz)) continue;
            if (part == null) part = builder.part("grass-top", GL20.GL_TRIANGLES, ATTRS, world.getGrassMaterial());
            top(part, wx, y, wz);
        }
        return part != null;
    }

    private boolean addDirtFaces(ModelBuilder builder, VoxelWorld world) {
        MeshPartBuilder part = null;
        for (int x = 0; x < SIZE; x++) for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < SIZE; z++) {
            BlockType type = blocks[x][y][z];
            if (type != BlockType.DIRT && type != BlockType.GRASS) continue;
            int wx = originX + x, wz = originZ + z;
            boolean grass = type == BlockType.GRASS;
            if (!world.isSolid(wx + 1, y, wz)) { if (part == null) part = dirtPart(builder, world); posX(part, wx, y, wz); }
            if (!world.isSolid(wx - 1, y, wz)) { if (part == null) part = dirtPart(builder, world); negX(part, wx, y, wz); }
            if (!grass && !world.isSolid(wx, y + 1, wz)) { if (part == null) part = dirtPart(builder, world); top(part, wx, y, wz); }
            if (!world.isSolid(wx, y - 1, wz)) { if (part == null) part = dirtPart(builder, world); bottom(part, wx, y, wz); }
            if (!world.isSolid(wx, y, wz + 1)) { if (part == null) part = dirtPart(builder, world); posZ(part, wx, y, wz); }
            if (!world.isSolid(wx, y, wz - 1)) { if (part == null) part = dirtPart(builder, world); negZ(part, wx, y, wz); }
        }
        return part != null;
    }

    private boolean addStoneFaces(ModelBuilder builder, VoxelWorld world) {
        MeshPartBuilder part = null;
        for (int x = 0; x < SIZE; x++) for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < SIZE; z++) {
            if (blocks[x][y][z] != BlockType.STONE) continue;
            int wx = originX + x, wz = originZ + z;
            if (!world.isSolid(wx + 1, y, wz)) { if (part == null) part = builder.part("stone", GL20.GL_TRIANGLES, ATTRS, world.getStoneMaterial()); posX(part, wx, y, wz); }
            if (!world.isSolid(wx - 1, y, wz)) { if (part == null) part = builder.part("stone", GL20.GL_TRIANGLES, ATTRS, world.getStoneMaterial()); negX(part, wx, y, wz); }
            if (!world.isSolid(wx, y + 1, wz)) { if (part == null) part = builder.part("stone", GL20.GL_TRIANGLES, ATTRS, world.getStoneMaterial()); top(part, wx, y, wz); }
            if (!world.isSolid(wx, y - 1, wz)) { if (part == null) part = builder.part("stone", GL20.GL_TRIANGLES, ATTRS, world.getStoneMaterial()); bottom(part, wx, y, wz); }
            if (!world.isSolid(wx, y, wz + 1)) { if (part == null) part = builder.part("stone", GL20.GL_TRIANGLES, ATTRS, world.getStoneMaterial()); posZ(part, wx, y, wz); }
            if (!world.isSolid(wx, y, wz - 1)) { if (part == null) part = builder.part("stone", GL20.GL_TRIANGLES, ATTRS, world.getStoneMaterial()); negZ(part, wx, y, wz); }
        }
        return part != null;
    }

    private static MeshPartBuilder dirtPart(ModelBuilder builder, VoxelWorld world) {
        return builder.part("dirt", GL20.GL_TRIANGLES, ATTRS, world.getDirtMaterial());
    }

    public void dispose() { if (model != null) model.dispose(); }
    private static void posX(MeshPartBuilder p,float x,float y,float z){r(p,x+1,y,z,x+1,y+1,z,x+1,y+1,z+1,x+1,y,z+1,1,0,0);}
    private static void negX(MeshPartBuilder p,float x,float y,float z){r(p,x,y,z+1,x,y+1,z+1,x,y+1,z,x,y,z,-1,0,0);}
    private static void top(MeshPartBuilder p,float x,float y,float z){r(p,x,y+1,z+1,x+1,y+1,z+1,x+1,y+1,z,x,y+1,z,0,1,0);}
    private static void bottom(MeshPartBuilder p,float x,float y,float z){r(p,x,y,z,x+1,y,z,x+1,y,z+1,x,y,z+1,0,-1,0);}
    private static void posZ(MeshPartBuilder p,float x,float y,float z){r(p,x+1,y,z+1,x+1,y+1,z+1,x,y+1,z+1,x,y,z+1,0,0,1);}
    private static void negZ(MeshPartBuilder p,float x,float y,float z){r(p,x,y,z,x,y+1,z,x+1,y+1,z,x+1,y,z,0,0,-1);}
    private static void r(MeshPartBuilder p,float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3,float x4,float y4,float z4,float nx,float ny,float nz){
        Vector3 normal = new Vector3(nx,ny,nz);
        p.rect(vertex(x1,y1,z1,normal,0,1), vertex(x2,y2,z2,normal,0,0), vertex(x3,y3,z3,normal,1,0), vertex(x4,y4,z4,normal,1,1));
    }
    private static MeshPartBuilder.VertexInfo vertex(float x,float y,float z,Vector3 normal,float u,float v) { return new MeshPartBuilder.VertexInfo().setPos(x,y,z).setNor(normal).setUV(u,v); }
}
