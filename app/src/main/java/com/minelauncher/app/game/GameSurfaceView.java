package com.minelauncher.app.game;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.view.MotionEvent;

import java.util.concurrent.ConcurrentLinkedQueue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * GLSurfaceView + renderer + input handling for the in-game session.
 */
public class GameSurfaceView extends GLSurfaceView implements GLSurfaceView.Renderer {

    public interface Listener {
        void onWorldReady();

        void onFps(int fps);
    }

    public static final int MODE_SURVIVAL = 0;
    public static final int MODE_CREATIVE = 1;
    public static final int MODE_ADVENTURE = 2;
    public static final int MODE_HARDCORE = 3;

    private static final float REACH = 6.0f;

    private final WorldRenderer renderer = new WorldRenderer();
    public final Player player = new Player();
    private volatile World world;
    private Listener listener;
    private int gamemode = MODE_SURVIVAL;

    private final float[] proj = new float[16];
    private final float[] view = new float[16];
    private final float[] vp = new float[16];
    private final float[] cam = new float[3];

    private long lastFrameNanos;
    private long fpsWindowStart;
    private int fpsFrames;

    private int movePointer = -1;
    private int lookPointer = -1;
    private float moveAnchorX, moveAnchorY, moveKnobX, moveKnobY;
    private float lookLastX, lookLastY;
    private volatile boolean jumpHeld;
    private volatile boolean downHeld;
    public float sensitivity = 0.12f;

    private final float[] moveInput = {0f, 0f};
    private volatile boolean moveActive;
    private float joyRadius = 120f;

    private final ConcurrentLinkedQueue<int[]> rebuildQueue = new ConcurrentLinkedQueue<int[]>();
    private volatile boolean meshing;
    private int buildIndex;
    private boolean readyNotified;

    public GameSurfaceView(Context context) {
        super(context);
        setup();
    }

    public GameSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup();
    }

    private void setup() {
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        setFocusable(true);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    private void notifyReady() {
        if (readyNotified) return;
        readyNotified = true;
        post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) listener.onWorldReady();
            }
        });
    }

    public World getWorld() {
        return world;
    }

    public int getGamemode() {
        return gamemode;
    }

    // ------------------------------------------------------------ world setup

    public void createWorld(final long seed, final int mode) {
        this.gamemode = mode;
        player.creative = (mode == MODE_CREATIVE);
        player.flying = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                World w = new World(seed);
                w.generate();
                int[] spawn = w.findSpawn();
                player.setPosition(spawn[0] + 0.5, spawn[1] + 1.2, spawn[2] + 0.5);
                player.yaw = 0f;
                player.pitch = 0f;
                world = w;
            }
        }, "world-generation").start();
    }

    // ---------------------------------------------------------------- actions

    public void setJumpHeld(boolean held) {
        jumpHeld = held;
    }

    public void setDownHeld(boolean held) {
        downHeld = held;
    }

    /** Returns false when flight is not available in the current game mode. */
    public boolean toggleFly() {
        if (!player.creative) return false;
        player.flying = !player.flying;
        if (player.flying) player.vy = 0;
        return true;
    }

    public boolean isFlying() {
        return player.flying;
    }

    public void breakBlock() {
        World w = world;
        if (w == null) return;
        int[] hit = w.raycast(player.eyeX(), player.eyeY(), player.eyeZ(),
                player.dirX(), player.dirY(), player.dirZ(), REACH);
        if (hit == null) return;
        int id = w.get(hit[0], hit[1], hit[2]);
        if (id == BlockTypes.BEDROCK || id == BlockTypes.AIR) return;
        w.set(hit[0], hit[1], hit[2], BlockTypes.AIR);
        queueRebuild(hit[0], hit[2]);
    }

    public void placeBlock(int blockId) {
        World w = world;
        if (w == null) return;
        int[] hit = w.raycast(player.eyeX(), player.eyeY(), player.eyeZ(),
                player.dirX(), player.dirY(), player.dirZ(), REACH);
        if (hit == null) return;
        int px = hit[0] + hit[3];
        int py = hit[1] + hit[4];
        int pz = hit[2] + hit[5];
        int current = w.get(px, py, pz);
        if (current != BlockTypes.AIR && current != BlockTypes.WATER) return;
        if (player.intersectsBlock(px, py, pz)) return;
        w.set(px, py, pz, blockId);
        queueRebuild(px, pz);
    }

    private void queueRebuild(int blockX, int blockZ) {
        int cx = blockX >> 4;
        int cz = blockZ >> 4;
        addChunk(cx, cz);
        int lx = blockX & 15;
        int lz = blockZ & 15;
        if (lx == 0) addChunk(cx - 1, cz);
        if (lx == 15) addChunk(cx + 1, cz);
        if (lz == 0) addChunk(cx, cz - 1);
        if (lz == 15) addChunk(cx, cz + 1);
    }

    private void addChunk(int cx, int cz) {
        if (cx < 0 || cx >= World.CX || cz < 0 || cz >= World.CZ) return;
        rebuildQueue.add(new int[]{cx, cz});
    }

    // ------------------------------------------------------------- joystick UI

    public boolean isMoveActive() {
        return moveActive;
    }

    public float getMoveAnchorX() {
        return moveAnchorX;
    }

    public float getMoveAnchorY() {
        return moveAnchorY;
    }

    public float getMoveKnobX() {
        return moveKnobX;
    }

    public float getMoveKnobY() {
        return moveKnobY;
    }

    public float getJoyRadius() {
        return joyRadius;
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        final int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                float x = e.getX();
                float y = e.getY();
                joyRadius = Math.max(80f, getWidth() * 0.11f);
                if (x < getWidth() * 0.45f) {
                    startMove(e.getPointerId(0), x, y);
                } else {
                    lookPointer = e.getPointerId(0);
                    lookLastX = x;
                    lookLastY = y;
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = e.getActionIndex();
                float x = e.getX(index);
                float y = e.getY(index);
                int id = e.getPointerId(index);
                if (movePointer == -1 && x < getWidth() * 0.45f) {
                    startMove(id, x, y);
                } else if (lookPointer == -1) {
                    lookPointer = id;
                    lookLastX = x;
                    lookLastY = y;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int id = e.getPointerId(i);
                    if (id == movePointer) {
                        updateMove(e.getX(i), e.getY(i));
                    } else if (id == lookPointer) {
                        float x = e.getX(i);
                        float y = e.getY(i);
                        player.yaw -= (x - lookLastX) * sensitivity;
                        player.pitch -= (y - lookLastY) * sensitivity;
                        if (player.pitch > 89f) player.pitch = 89f;
                        if (player.pitch < -89f) player.pitch = -89f;
                        if (player.yaw > 360f) player.yaw -= 360f;
                        if (player.yaw < -360f) player.yaw += 360f;
                        lookLastX = x;
                        lookLastY = y;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                int index = e.getActionIndex();
                int id = e.getPointerId(index);
                if (id == movePointer) stopMove();
                if (id == lookPointer) lookPointer = -1;
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                stopMove();
                lookPointer = -1;
                break;
            }
            default:
                break;
        }
        return true;
    }

    private void startMove(int id, float x, float y) {
        movePointer = id;
        moveAnchorX = x;
        moveAnchorY = y;
        moveKnobX = x;
        moveKnobY = y;
        moveActive = true;
        updateMove(x, y);
    }

    private void updateMove(float x, float y) {
        float dx = x - moveAnchorX;
        float dy = y - moveAnchorY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > joyRadius) {
            dx = dx / len * joyRadius;
            dy = dy / len * joyRadius;
        }
        moveKnobX = moveAnchorX + dx;
        moveKnobY = moveAnchorY + dy;
        moveInput[1] = dx / joyRadius;
        moveInput[0] = -dy / joyRadius;
    }

    private void stopMove() {
        movePointer = -1;
        moveActive = false;
        moveInput[0] = 0f;
        moveInput[1] = 0f;
    }

    // --------------------------------------------------------------- renderer

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.639f, 0.784f, 0.980f, 1f);
        renderer.init();
        buildIndex = 0;
        meshing = true;
        readyNotified = false;
        lastFrameNanos = 0;
        fpsWindowStart = System.nanoTime();
        fpsFrames = 0;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float aspect = height == 0 ? 1f : (float) width / (float) height;
        Matrix.perspectiveM(proj, 0, 68f, aspect, 0.1f, 220f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0.016f : (now - lastFrameNanos) / 1e9f;
        lastFrameNanos = now;
        if (dt > 0.05f) dt = 0.05f;

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        World w = world;
        if (w == null) return;

        if (meshing) {
            long start = System.nanoTime();
            int total = World.CX * World.CZ;
            while (buildIndex < total && System.nanoTime() - start < 7000000L) {
                renderer.buildChunk(w, buildIndex % World.CX, buildIndex / World.CX);
                buildIndex++;
            }
            if (buildIndex >= total) {
                meshing = false;
                notifyReady();
            }
        }

        for (int i = 0; i < 4; i++) {
            int[] chunk = rebuildQueue.poll();
            if (chunk == null) break;
            renderer.buildChunk(w, chunk[0], chunk[1]);
        }

        player.update(w, moveInput[0], moveInput[1], jumpHeld, downHeld, dt);

        double eyeX = player.eyeX();
        double eyeY = player.eyeY();
        double eyeZ = player.eyeZ();
        float dx = (float) player.dirX();
        float dy = (float) player.dirY();
        float dz = (float) player.dirZ();

        Matrix.setLookAtM(view, 0,
                (float) eyeX, (float) eyeY, (float) eyeZ,
                (float) (eyeX + dx), (float) (eyeY + dy), (float) (eyeZ + dz),
                0f, 1f, 0f);
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0);

        cam[0] = (float) eyeX;
        cam[1] = (float) eyeY;
        cam[2] = (float) eyeZ;

        renderer.draw(vp, cam);

        fpsFrames++;
        long elapsed = now - fpsWindowStart;
        if (elapsed > 500000000L) {
            final int fps = (int) (fpsFrames * 1e9 / elapsed);
            fpsFrames = 0;
            fpsWindowStart = now;
            if (listener != null) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (listener != null) listener.onFps(fps);
                    }
                });
            }
        }
    }
}
