package com.maxieruff.dynasty.player;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import com.maxieruff.dynasty.world.BlockType;
import com.maxieruff.dynasty.world.RaycastHit;
import com.maxieruff.dynasty.world.VoxelWorld;

/** First-person movement with a capsule-like AABB body and solid voxel collision on every axis. */
public class PlayerController extends InputAdapter {
    private static final float GRAVITY = -20f, JUMP_VELOCITY = 8f, MOVE_SPEED = 5f, EYE_HEIGHT = 1.62f;
    private static final float HALF_WIDTH = .30f, HEIGHT = 1.80f, STEP = .10f;
    private final PerspectiveCamera camera;
    private final VoxelWorld world;
    private final Vector3 position = new Vector3(), velocity = new Vector3(), spawn = new Vector3();
    private final Vector3 forward = new Vector3(), right = new Vector3();
    private boolean grounded;
    private float yaw = -90, pitch = -18;
    private int selectedSlot;
    private RaycastHit target;
    private boolean debugVisible = true;

    public PlayerController(PerspectiveCamera camera, VoxelWorld world, Vector3 spawn) {
        this.camera = camera; this.world = world; this.spawn.set(spawn); position.set(spawn); updateCamera();
    }
    public void update(float delta) {
        look(); input(delta); moveHorizontal(delta); moveVertical(delta); updateCamera();
        if (position.y <= VoxelWorld.VOID_Y) { position.set(spawn); velocity.setZero(); }
    }
    private void look() {
        if (!Gdx.input.isCursorCatched()) return;
        yaw += Gdx.input.getDeltaX() * .15f; pitch = Math.max(-89, Math.min(89, pitch - Gdx.input.getDeltaY() * .15f));
    }
    private void input(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.input.setCursorCatched(!Gdx.input.isCursorCatched());
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) selectedSlot = 0;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) selectedSlot = 1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) selectedSlot = 2;
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) debugVisible = !debugVisible;
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) && grounded) { velocity.y = JUMP_VELOCITY; grounded = false; }
    }
    private void moveHorizontal(float delta) {
        float radians = (float) Math.toRadians(yaw); forward.set((float)Math.cos(radians), 0, (float)Math.sin(radians)).nor(); right.set(forward).crs(Vector3.Y).nor();
        Vector3 wish = new Vector3();
        if (Gdx.input.isKeyPressed(Input.Keys.W)) wish.add(forward);
        if (Gdx.input.isKeyPressed(Input.Keys.S)) wish.sub(forward);
        if (Gdx.input.isKeyPressed(Input.Keys.D)) wish.add(right);
        if (Gdx.input.isKeyPressed(Input.Keys.A)) wish.sub(right);
        if (wish.len2() > 0) { wish.nor().scl(MOVE_SPEED * delta); moveAxis(wish.x, 0); moveAxis(wish.z, 1); }
    }
    private void moveVertical(float delta) { velocity.y += GRAVITY * delta; grounded = false; moveAxis(velocity.y * delta, 2); }
    private void moveAxis(float amount, int axis) {
        int count = Math.max(1, (int)Math.ceil(Math.abs(amount) / STEP)); float step = amount / count;
        for (int i = 0; i < count; i++) {
            if (axis == 0) position.x += step; else if (axis == 1) position.z += step; else position.y += step;
            if (collides()) {
                if (axis == 0) position.x -= step; else if (axis == 1) position.z -= step; else { position.y -= step; if (step < 0) grounded = true; velocity.y = 0; }
                break;
            }
        }
    }
    private boolean collides() {
        int minX = floor(position.x - HALF_WIDTH), maxX = floor(position.x + HALF_WIDTH);
        int minY = floor(position.y), maxY = floor(position.y + HEIGHT - .001f);
        int minZ = floor(position.z - HALF_WIDTH), maxZ = floor(position.z + HALF_WIDTH);
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) if (world.isSolid(x, y, z)) return true;
        return false;
    }
    public boolean intersectsBlock(int x, int y, int z) {
        return position.x + HALF_WIDTH > x && position.x - HALF_WIDTH < x + 1 && position.y + HEIGHT > y && position.y < y + 1 && position.z + HALF_WIDTH > z && position.z - HALF_WIDTH < z + 1;
    }
    private void updateCamera() {
        float yawRad = (float)Math.toRadians(yaw), pitchRad = (float)Math.toRadians(pitch);
        camera.position.set(position.x, position.y + EYE_HEIGHT, position.z);
        camera.direction.set((float)(Math.cos(yawRad)*Math.cos(pitchRad)), (float)Math.sin(pitchRad), (float)(Math.sin(yawRad)*Math.cos(pitchRad))).nor();
        camera.up.set(Vector3.Y); camera.update();
    }
    private static int floor(float value) { return (int)Math.floor(value); }
    public Vector3 getPosition() { return position; }
    public int getSelectedSlot() { return selectedSlot; }
    public boolean isDebugVisible() { return debugVisible; }
    public void setTarget(RaycastHit target) { this.target = target; }
    @Override public boolean scrolled(float amountX, float amountY) { selectedSlot = Math.floorMod(selectedSlot + (amountY > 0 ? 1 : -1), 3); return true; }
    @Override public boolean touchDown(int x, int y, int pointer, int button) {
        if (!Gdx.input.isCursorCatched()) { Gdx.input.setCursorCatched(true); return true; }
        if (target == null) return false;
        if (button == Input.Buttons.LEFT) world.setBlock(target.x(), target.y(), target.z(), BlockType.AIR);
        if (button == Input.Buttons.RIGHT) {
            int px=target.x()+target.normalX(), py=target.y()+target.normalY(), pz=target.z()+target.normalZ();
            if (world.isInsideWorld(px, py, pz) && world.getBlock(px, py, pz) == BlockType.AIR && !intersectsBlock(px,py,pz)) {
                world.setBlock(px,py,pz,BlockType.fromHotbarSlot(selectedSlot));
            }
        }
        return true;
    }
}
