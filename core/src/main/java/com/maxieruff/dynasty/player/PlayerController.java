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
    private static final float GRAVITY = -20f, JUMP_VELOCITY = 8f;
    private static final float WALK_SPEED = 4.6f, SPRINT_SPEED = 6.9f, EXHAUSTED_SPEED = 3.3f, CROUCH_SPEED = 2.2f;
    private static final float STAMINA_MAX = 100f, STAMINA_DRAIN_PER_SECOND = 2.2f, STAMINA_RECOVERY_PER_SECOND = 3.1f;
    private static final float EXHAUSTION_COOLDOWN = 8f, SPRINT_RECOVERY_THRESHOLD = 65f;
    private static final float HALF_WIDTH = .30f, STANDING_HEIGHT = 1.80f, CROUCH_HEIGHT = 1.15f, STEP = .10f;
    private static final float STANDING_EYE_HEIGHT = 1.62f, CROUCH_EYE_HEIGHT = 1.02f;
    private static final float JUMP_BUFFER_TIME = .12f, COYOTE_TIME = .10f, GROUND_PROBE_DEPTH = .06f;
    private final PerspectiveCamera camera;
    private final VoxelWorld world;
    private final Vector3 position = new Vector3(), velocity = new Vector3(), horizontalVelocity = new Vector3(), spawn = new Vector3();
    private final Vector3 forward = new Vector3(), right = new Vector3(), wish = new Vector3();
    private boolean grounded;
    private float yaw = -90, pitch = -18;
    private int selectedSlot = -1;
    private RaycastHit target;
    private boolean debugVisible = true;
    private boolean sprintToggled;
    private boolean crouching;
    private boolean exhausted;
    private float stamina = STAMINA_MAX;
    private float sprintCooldown;
    private float jumpBuffer;
    private float coyoteTime;

    public PlayerController(PerspectiveCamera camera, VoxelWorld world, Vector3 spawn) {
        this.camera = camera; this.world = world; this.spawn.set(spawn); position.set(spawn); updateCamera();
    }
    public void update(float delta) {
        updateGroundState(delta); look(); input(delta); tryJump(); moveHorizontal(delta); moveVertical(delta); updateCamera();
        if (position.y <= VoxelWorld.VOID_Y) { position.set(spawn); velocity.setZero(); horizontalVelocity.setZero(); }
    }
    private void look() {
        if (!Gdx.input.isCursorCatched()) return;
        yaw += Gdx.input.getDeltaX() * .15f; pitch = Math.max(-89, Math.min(89, pitch - Gdx.input.getDeltaY() * .15f));
    }
    private void input(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.input.setCursorCatched(!Gdx.input.isCursorCatched());
        if (Gdx.input.isKeyJustPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.SHIFT_RIGHT)) sprintToggled = !sprintToggled;
        if (Gdx.input.isKeyJustPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.CONTROL_RIGHT)) toggleCrouch();
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) debugVisible = !debugVisible;
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) jumpBuffer = JUMP_BUFFER_TIME;
    }
    private void moveHorizontal(float delta) {
        float radians = (float) Math.toRadians(yaw); forward.set((float)Math.cos(radians), 0, (float)Math.sin(radians)).nor(); right.set(forward).crs(Vector3.Y).nor();
        wish.setZero();
        if (Gdx.input.isKeyPressed(Input.Keys.W)) wish.add(forward);
        if (Gdx.input.isKeyPressed(Input.Keys.S)) wish.sub(forward);
        if (Gdx.input.isKeyPressed(Input.Keys.D)) wish.add(right);
        if (Gdx.input.isKeyPressed(Input.Keys.A)) wish.sub(right);
        boolean moving = wish.len2() > 0;
        boolean sprinting = sprintToggled && !crouching && !exhausted && moving;
        float speed = crouching ? CROUCH_SPEED : exhausted ? EXHAUSTED_SPEED : sprinting ? SPRINT_SPEED : WALK_SPEED;
        if (moving) wish.nor().scl(speed);
        float acceleration = moving ? (grounded ? 24f : 8f) : 30f;
        horizontalVelocity.x = approach(horizontalVelocity.x, wish.x, acceleration * delta);
        horizontalVelocity.z = approach(horizontalVelocity.z, wish.z, acceleration * delta);
        float oldX = position.x, oldZ = position.z;
        moveAxis(horizontalVelocity.x * delta, 0); moveAxis(horizontalVelocity.z * delta, 1);
        boolean actuallyMoved = (position.x - oldX) * (position.x - oldX) + (position.z - oldZ) * (position.z - oldZ) > .000001f;
        updateStamina(delta, sprinting && actuallyMoved);
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
        int minY = floor(position.y), maxY = floor(position.y + bodyHeight() - .001f);
        int minZ = floor(position.z - HALF_WIDTH), maxZ = floor(position.z + HALF_WIDTH);
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) if (world.isSolid(x, y, z)) return true;
        return false;
    }
    private void updateGroundState(float delta) {
        boolean standingOnGround = velocity.y <= 0f && isStandingOnGround();
        grounded = standingOnGround;
        coyoteTime = standingOnGround ? COYOTE_TIME : Math.max(0f, coyoteTime - delta);
        jumpBuffer = Math.max(0f, jumpBuffer - delta);
    }
    private boolean isStandingOnGround() {
        int minX = floor(position.x - HALF_WIDTH), maxX = floor(position.x + HALF_WIDTH);
        int y = floor(position.y - GROUND_PROBE_DEPTH);
        int minZ = floor(position.z - HALF_WIDTH), maxZ = floor(position.z + HALF_WIDTH);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) if (world.isSolid(x, y, z)) return true;
        return false;
    }
    private void tryJump() {
        if (jumpBuffer <= 0f || coyoteTime <= 0f) return;
        velocity.y = JUMP_VELOCITY;
        grounded = false;
        coyoteTime = 0f;
        jumpBuffer = 0f;
    }
    public boolean intersectsBlock(int x, int y, int z) {
        return position.x + HALF_WIDTH > x && position.x - HALF_WIDTH < x + 1 && position.y + bodyHeight() > y && position.y < y + 1 && position.z + HALF_WIDTH > z && position.z - HALF_WIDTH < z + 1;
    }
    private void updateStamina(float delta, boolean sprinting) {
        if (sprinting) stamina = Math.max(0f, stamina - STAMINA_DRAIN_PER_SECOND * delta);
        else stamina = Math.min(STAMINA_MAX, stamina + STAMINA_RECOVERY_PER_SECOND * delta);
        if (stamina == 0f) { exhausted = true; sprintToggled = false; sprintCooldown = EXHAUSTION_COOLDOWN; }
        if (exhausted) {
            sprintCooldown = Math.max(0f, sprintCooldown - delta);
            if (sprintCooldown == 0f && stamina >= SPRINT_RECOVERY_THRESHOLD) exhausted = false;
        }
    }
    private void toggleCrouch() {
        if (crouching && !canStand()) return;
        crouching = !crouching;
    }
    private boolean canStand() {
        int minX = floor(position.x - HALF_WIDTH), maxX = floor(position.x + HALF_WIDTH);
        int minY = floor(position.y), maxY = floor(position.y + STANDING_HEIGHT - .001f);
        int minZ = floor(position.z - HALF_WIDTH), maxZ = floor(position.z + HALF_WIDTH);
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) if (world.isSolid(x, y, z)) return false;
        return true;
    }
    private float bodyHeight() { return crouching ? CROUCH_HEIGHT : STANDING_HEIGHT; }
    private static float approach(float current, float target, float change) {
        if (current < target) return Math.min(current + change, target);
        return Math.max(current - change, target);
    }
    private void updateCamera() {
        float yawRad = (float)Math.toRadians(yaw), pitchRad = (float)Math.toRadians(pitch);
        camera.position.set(position.x, position.y + (crouching ? CROUCH_EYE_HEIGHT : STANDING_EYE_HEIGHT), position.z);
        camera.direction.set((float)(Math.cos(yawRad)*Math.cos(pitchRad)), (float)Math.sin(pitchRad), (float)(Math.sin(yawRad)*Math.cos(pitchRad))).nor();
        camera.up.set(Vector3.Y); camera.update();
    }
    private static int floor(float value) { return (int)Math.floor(value); }
    public Vector3 getPosition() { return position; }
    public int getSelectedSlot() { return selectedSlot; }
    public boolean isDebugVisible() { return debugVisible; }
    public float getStamina() { return stamina; }
    public boolean isSprinting() { return sprintToggled && !crouching && !exhausted; }
    public boolean isCrouching() { return crouching; }
    public void setTarget(RaycastHit target) { this.target = target; }
    @Override public boolean scrolled(float amountX, float amountY) { return true; }
    @Override public boolean touchDown(int x, int y, int pointer, int button) {
        if (!Gdx.input.isCursorCatched()) { Gdx.input.setCursorCatched(true); return true; }
        if (target == null) return false;
        if (button == Input.Buttons.LEFT) world.setBlock(target.x(), target.y(), target.z(), BlockType.AIR);
        if (button == Input.Buttons.RIGHT) {
            int px=target.x()+target.normalX(), py=target.y()+target.normalY(), pz=target.z()+target.normalZ();
            if (selectedSlot >= 0 && world.isInsideWorld(px, py, pz) && world.getBlock(px, py, pz) == BlockType.AIR && !intersectsBlock(px,py,pz)) {
                world.setBlock(px,py,pz,BlockType.fromHotbarSlot(selectedSlot));
            }
        }
        return true;
    }
}
