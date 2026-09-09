package com.maxieruff.dynasty;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.maxieruff.dynasty.player.PlayerController;
import com.maxieruff.dynasty.world.RaycastHit;
import com.maxieruff.dynasty.world.VoxelRaycaster;
import com.maxieruff.dynasty.world.VoxelWorld;

/** Alpha 0.1.1 voxel prototype: editable chunks, collision, selection, and debug HUD. */
public class DynastyGame extends ApplicationAdapter {
    public static final String VERSION = "Alpha 0.1.1";
    /** Reserved for the main menu once that screen is added. */
    public static final String MAIN_MENU_FONT = "ui/OldLondon.ttf";
    private PerspectiveCamera camera;
    private OrthographicCamera hudCamera;
    private ModelBatch modelBatch;
    private Environment environment;
    private ShapeRenderer shapes;
    private SpriteBatch spriteBatch;
    private BitmapFont font;
    private VoxelWorld world;
    private PlayerController controller;
    private final Array<ModelInstance> visibleChunks = new Array<>();
    private RaycastHit target;

    @Override public void create() {
        Gdx.graphics.setTitle("Dynasty - " + VERSION);
        camera = new PerspectiveCamera(70, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()); camera.near = .1f; camera.far = 200f;
        hudCamera = new OrthographicCamera(); resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        modelBatch = new ModelBatch(); shapes = new ShapeRenderer(); spriteBatch = new SpriteBatch(); font = new BitmapFont();
        environment = new Environment(); environment.set(new ColorAttribute(ColorAttribute.AmbientLight, .62f, .62f, .62f, 1)); environment.add(new DirectionalLight().set(.75f,.75f,.75f,-1,-.8f,-.35f));
        world = new VoxelWorld();
        controller = new PlayerController(camera, world, new Vector3(0, world.getSpawnY() + .01f, 0));
        Gdx.input.setInputProcessor(controller); Gdx.input.setCursorCatched(true);
    }
    @Override public void render() {
        world.updateStreaming(camera.position.x, camera.position.z);
        boolean worldReady = world.isInitialLoadComplete();
        if (worldReady) {
            controller.update(Math.min(Gdx.graphics.getDeltaTime(), .1f));
            target = VoxelRaycaster.cast(world, camera.position, camera.direction, 6f); controller.setTarget(target);
        } else { target = null; controller.setTarget(null); }
        Gdx.gl.glViewport(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight()); Gdx.gl.glClearColor(.45f,.70f,1f,1f); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        world.renderableChunks(camera, visibleChunks); modelBatch.begin(camera); for (ModelInstance chunk : visibleChunks) modelBatch.render(chunk, environment); modelBatch.end();
        if (worldReady) { drawTargetOutline(); drawHud(); } else drawLoadingScreen();
    }
    private void drawTargetOutline() {
        if (target == null || (target.normalX() == 0 && target.normalY() == 0 && target.normalZ() == 0)) return;
        // Keep selection lines behind intervening voxels. The tiny outward bias
        // prevents depth fighting with the selected face itself.
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);
        shapes.setProjectionMatrix(camera.combined); shapes.begin(ShapeRenderer.ShapeType.Line); shapes.setColor(Color.BLACK);
        float x = target.x(), y = target.y(), z = target.z(), e = .003f;
        if (target.normalX() != 0) {
            float faceX = x + (target.normalX() > 0 ? 1f + e : -e);
            lineLoop(faceX,y,z, faceX,y + 1,z, faceX,y + 1,z + 1, faceX,y,z + 1);
        } else if (target.normalY() != 0) {
            float faceY = y + (target.normalY() > 0 ? 1f + e : -e);
            lineLoop(x,faceY,z, x + 1,faceY,z, x + 1,faceY,z + 1, x,faceY,z + 1);
        } else {
            float faceZ = z + (target.normalZ() > 0 ? 1f + e : -e);
            lineLoop(x,y,faceZ, x,y + 1,faceZ, x + 1,y + 1,faceZ, x + 1,y,faceZ);
        }
        shapes.end();
        Gdx.gl.glDepthMask(true);
    }
    private void lineLoop(float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3,float x4,float y4,float z4) {
        shapes.line(x1,y1,z1,x2,y2,z2); shapes.line(x2,y2,z2,x3,y3,z3); shapes.line(x3,y3,z3,x4,y4,z4); shapes.line(x4,y4,z4,x1,y1,z1);
    }
    private void drawHud() {
        float width = Gdx.graphics.getWidth(), height = Gdx.graphics.getHeight(), cx = width / 2f, cy = height / 2f;
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        shapes.setProjectionMatrix(hudCamera.combined); shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0,0,0,.62f); shapes.rect(cx - 1, cy - 8, 2, 16); shapes.rect(cx - 8, cy - 1, 16, 2);
        shapes.end();
        float handWidth = 148f, handHeight = 36f, xpWidth = 230f, xpHeight = 16f, gap = 18f;
        float hudWidth = handWidth * 2 + xpWidth + gap * 2;
        float hudX = cx - hudWidth / 2f, hudY = 24f;
        drawHandBar(hudX, hudY, handWidth, handHeight);
        drawXpBar(hudX + handWidth + gap, hudY + (handHeight - xpHeight) / 2f, xpWidth, xpHeight);
        drawHandBar(hudX + handWidth + gap + xpWidth + gap, hudY, handWidth, handHeight);

        spriteBatch.setProjectionMatrix(hudCamera.combined);
        spriteBatch.begin(); font.setColor(Color.WHITE);
        if (controller.isDebugVisible()) { Vector3 p=controller.getPosition(); font.draw(spriteBatch, VERSION + "  |  " + Gdx.graphics.getFramesPerSecond() + " fps", 12, height - 12); font.draw(spriteBatch, String.format("XYZ: %.2f / %.2f / %.2f", p.x,p.y,p.z), 12, height - 32); font.draw(spriteBatch, String.format("Stamina: %.0f%%  |  %s%s  |  Shift: sprint toggle   Ctrl: crouch   LMB: break", controller.getStamina(), controller.isSprinting() ? "sprinting" : "walking", controller.isCrouching() ? ", crouched" : ""), 12, height - 52); }
        spriteBatch.end();
    }
    private void drawHandBar(float x, float y, float width, float height) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Color.BLACK); shapes.rect(x, y, width, height);
        shapes.setColor(.18f, .18f, .20f, .88f); shapes.rect(x + 5, y + 5, width - 10, height - 10);
        shapes.end();
    }
    private void drawXpBar(float x, float y, float width, float height) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Color.BLACK); shapes.rect(x, y, width, height);
        shapes.setColor(.10f, .10f, .12f, .92f); shapes.rect(x + 4, y + 4, width - 8, height - 8);
        shapes.end();
    }
    private void drawLoadingScreen() {
        float width = Gdx.graphics.getWidth(), height = Gdx.graphics.getHeight(), progress = world.getInitialLoadProgress();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST); shapes.setProjectionMatrix(hudCamera.combined); shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, .70f); shapes.rect(0, 0, width, height);
        float barWidth = Math.min(420f, width - 80f), barX = (width - barWidth) / 2f, barY = height / 2f - 12f;
        shapes.setColor(Color.BLACK); shapes.rect(barX, barY, barWidth, 24f);
        shapes.setColor(.67f, .53f, .22f, 1f); shapes.rect(barX + 4, barY + 4, (barWidth - 8) * progress, 16f);
        shapes.end(); spriteBatch.setProjectionMatrix(hudCamera.combined); spriteBatch.begin(); font.setColor(Color.WHITE);
        font.draw(spriteBatch, "Building world... " + Math.round(progress * 100f) + "%", barX, barY + 48f);
        font.draw(spriteBatch, "Seed: " + world.getSeed() + "  |  Render distance: " + VoxelWorld.DEFAULT_RENDER_DISTANCE + " chunks", barX, barY - 12f);
        spriteBatch.end();
    }
    @Override public void resize(int width, int height) { if (camera != null) { camera.viewportWidth=width; camera.viewportHeight=height; camera.update(); } if (hudCamera != null) { hudCamera.setToOrtho(false,width,height); hudCamera.update(); } }
    @Override public void dispose() { modelBatch.dispose(); shapes.dispose(); spriteBatch.dispose(); font.dispose(); world.dispose(); }
}
