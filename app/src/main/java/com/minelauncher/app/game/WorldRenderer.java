package com.minelauncher.app.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

/**
 * OpenGL ES 2.0 chunk renderer with a procedurally generated 4x4 texture atlas
 * (16 px tiles, nearest neighbour filtering) and per-face shading + distance fog.
 */
public class WorldRenderer {

    private static final String VERTEX_SHADER =
            "attribute vec3 aPos;\n" +
            "attribute vec2 aUV;\n" +
            "attribute float aShade;\n" +
            "uniform mat4 uMVP;\n" +
            "uniform vec3 uCam;\n" +
            "varying vec2 vUV;\n" +
            "varying float vShade;\n" +
            "varying float vDist;\n" +
            "void main() {\n" +
            "    vUV = aUV;\n" +
            "    vShade = aShade;\n" +
            "    vDist = length(aPos - uCam);\n" +
            "    gl_Position = uMVP * vec4(aPos, 1.0);\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "varying vec2 vUV;\n" +
            "varying float vShade;\n" +
            "varying float vDist;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec3 uFogColor;\n" +
            "uniform float uFogStart;\n" +
            "uniform float uFogEnd;\n" +
            "void main() {\n" +
            "    vec4 texel = texture2D(uTex, vUV);\n" +
            "    vec3 rgb = texel.rgb * vShade;\n" +
            "    float fog = clamp((vDist - uFogStart) / max(uFogEnd - uFogStart, 0.001), 0.0, 1.0);\n" +
            "    rgb = mix(rgb, uFogColor, fog);\n" +
            "    gl_FragColor = vec4(rgb, 1.0);\n" +
            "}\n";

    private static final int FLOATS_PER_VERTEX = 6;
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4;

    private static final float SHADE_TOP = 1.0f;
    private static final float SHADE_BOTTOM = 0.45f;
    private static final float SHADE_X = 0.68f;
    private static final float SHADE_Z = 0.86f;

    private static final float INSET = 0.0015f;

    private final int chunks = World.CX * World.CZ;
    private final int[] vboIds = new int[World.CX * World.CZ];
    private final int[] vertexCount = new int[World.CX * World.CZ];

    private int program;
    private int aPos;
    private int aUV;
    private int aShade;
    private int uMVP;
    private int uCam;
    private int uTex;
    private int uFogColor;
    private int uFogStart;
    private int uFogEnd;
    private int atlasTexture;

    private float[] verts = new float[1 << 15];
    private int vertLen;

    private ByteBuffer uploadBuffer;
    private FloatBuffer uploadFloats;

    public final float[] fogColor = {0.639f, 0.784f, 0.980f};
    public float fogStart = 60f;
    public float fogEnd = 145f;

    private final float[] tileU0 = new float[16];
    private final float[] tileU1 = new float[16];
    private final float[] tileV0 = new float[16];
    private final float[] tileV1 = new float[16];

    public void init() {
        program = buildProgram();
        aPos = GLES20.glGetAttribLocation(program, "aPos");
        aUV = GLES20.glGetAttribLocation(program, "aUV");
        aShade = GLES20.glGetAttribLocation(program, "aShade");
        uMVP = GLES20.glGetUniformLocation(program, "uMVP");
        uCam = GLES20.glGetUniformLocation(program, "uCam");
        uTex = GLES20.glGetUniformLocation(program, "uTex");
        uFogColor = GLES20.glGetUniformLocation(program, "uFogColor");
        uFogStart = GLES20.glGetUniformLocation(program, "uFogStart");
        uFogEnd = GLES20.glGetUniformLocation(program, "uFogEnd");

        atlasTexture = createAtlasTexture();

        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_DITHER);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);

        java.util.Arrays.fill(vboIds, 0);
        java.util.Arrays.fill(vertexCount, 0);
        vertLen = 0;
    }

    public void release() {
        for (int i = 0; i < chunks; i++) {
            if (vboIds[i] != 0) {
                GLES20.glDeleteBuffers(1, new int[]{vboIds[i]}, 0);
                vboIds[i] = 0;
            }
            vertexCount[i] = 0;
        }
        if (atlasTexture != 0) {
            GLES20.glDeleteTextures(1, new int[]{atlasTexture}, 0);
            atlasTexture = 0;
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
    }

    private static int buildProgram() {
        int vs = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        int prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, vs);
        GLES20.glAttachShader(prog, fs);
        GLES20.glLinkProgram(prog);
        int[] status = new int[1];
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            throw new RuntimeException("Program link failed: " + GLES20.glGetProgramInfoLog(prog));
        }
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        return prog;
    }

    private static int compile(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            throw new RuntimeException("Shader compile failed: " + GLES20.glGetShaderInfoLog(shader));
        }
        return shader;
    }

    // ------------------------------------------------------------- chunk mesh

    public void buildChunk(World world, int cx, int cz) {
        vertLen = 0;
        final int x0 = cx * World.CHUNK;
        final int z0 = cz * World.CHUNK;

        for (int x = x0; x < x0 + World.CHUNK; x++) {
            for (int z = z0; z < z0 + World.CHUNK; z++) {
                for (int y = 0; y < World.SY; y++) {
                    int id = world.getFast(x, y, z);
                    if (id == BlockTypes.AIR) continue;

                    int above = world.get(x, y + 1, z);
                    int below = world.get(x, y - 1, z);
                    int east = world.get(x + 1, y, z);
                    int west = world.get(x - 1, y, z);
                    int south = world.get(x, y, z + 1);
                    int north = world.get(x, y, z - 1);

                    float h = (id == BlockTypes.WATER) ? 0.875f : 1.0f;

                    if (visible(id, above)) {
                        quad(x, y + h, z, x, y + h, z + 1, x + 1, y + h, z + 1, x + 1, y + h, z,
                                BlockTypes.tileTop(id), SHADE_TOP);
                    }
                    if (visible(id, below)) {
                        quad(x, y, z + 1, x, y, z, x + 1, y, z, x + 1, y, z + 1,
                                BlockTypes.tileBottom(id), SHADE_BOTTOM);
                    }
                    if (visible(id, west)) {
                        quad(x, y, z, x, y, z + 1, x, y + h, z + 1, x, y + h, z,
                                BlockTypes.tileSide(id), SHADE_X);
                    }
                    if (visible(id, east)) {
                        quad(x + 1, y, z + 1, x + 1, y, z, x + 1, y + h, z, x + 1, y + h, z + 1,
                                BlockTypes.tileSide(id), SHADE_X);
                    }
                    if (visible(id, north)) {
                        quad(x + 1, y, z, x, y, z, x, y + h, z, x + 1, y + h, z,
                                BlockTypes.tileSide(id), SHADE_Z);
                    }
                    if (visible(id, south)) {
                        quad(x, y, z + 1, x + 1, y, z + 1, x + 1, y + h, z + 1, x, y + h, z + 1,
                                BlockTypes.tileSide(id), SHADE_Z);
                    }
                }
            }
        }
        uploadChunk(cz * World.CX + cx);
    }

    private static boolean visible(int self, int neighbour) {
        if (BlockTypes.isOpaque(neighbour)) return false;
        if (self == neighbour) return false;
        return true;
    }

    private void quad(float x0, float y0, float z0,
                      float x1, float y1, float z1,
                      float x2, float y2, float z2,
                      float x3, float y3, float z3,
                      int tile, float shade) {
        int tx = tile & 3;
        int ty = tile >> 2;
        float u0 = tileU0[tx];
        float u1 = tileU1[tx];
        float v0 = tileV0[ty];
        float v1 = tileV1[ty];

        ensureCapacity(36);
        put(x0, y0, z0, u0, v0, shade);
        put(x1, y1, z1, u1, v0, shade);
        put(x2, y2, z2, u1, v1, shade);
        put(x0, y0, z0, u0, v0, shade);
        put(x2, y2, z2, u1, v1, shade);
        put(x3, y3, z3, u0, v1, shade);
    }

    private void put(float x, float y, float z, float u, float v, float shade) {
        verts[vertLen++] = x;
        verts[vertLen++] = y;
        verts[vertLen++] = z;
        verts[vertLen++] = u;
        verts[vertLen++] = v;
        verts[vertLen++] = shade;
    }

    private void ensureCapacity(int extraFloats) {
        if (vertLen + extraFloats <= verts.length) return;
        int size = verts.length;
        while (size < vertLen + extraFloats) size *= 2;
        float[] bigger = new float[size];
        System.arraycopy(verts, 0, bigger, 0, vertLen);
        verts = bigger;
    }

    private void uploadChunk(int chunkIndex) {
        if (chunkIndex < 0 || chunkIndex >= chunks) return;
        if (vboIds[chunkIndex] == 0) {
            int[] tmp = new int[1];
            GLES20.glGenBuffers(1, tmp, 0);
            vboIds[chunkIndex] = tmp[0];
        }
        int bytes = vertLen * 4;
        ensureUploadBuffer(vertLen);
        uploadFloats.clear();
        uploadFloats.put(verts, 0, vertLen);
        uploadFloats.position(0);
        uploadFloats.limit(vertLen);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboIds[chunkIndex]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, bytes, uploadFloats, GLES20.GL_STATIC_DRAW);
        vertexCount[chunkIndex] = vertLen / FLOATS_PER_VERTEX;
    }

    private void ensureUploadBuffer(int floats) {
        if (uploadBuffer != null && uploadBuffer.capacity() >= floats * 4) return;
        int capacity = Math.max(floats * 4, 1 << 16);
        uploadBuffer = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
        uploadFloats = uploadBuffer.asFloatBuffer();
    }

    // ------------------------------------------------------------------ draw

    public void draw(float[] mvp, float[] cam) {
        if (program == 0 || atlasTexture == 0) return;

        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0);
        GLES20.glUniform3f(uCam, cam[0], cam[1], cam[2]);
        GLES20.glUniform3f(uFogColor, fogColor[0], fogColor[1], fogColor[2]);
        GLES20.glUniform1f(uFogStart, fogStart);
        GLES20.glUniform1f(uFogEnd, fogEnd);
        GLES20.glUniform1i(uTex, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTexture);

        for (int i = 0; i < chunks; i++) {
            int count = vertexCount[i];
            if (count == 0 || vboIds[i] == 0) continue;

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboIds[i]);
            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, BYTES_PER_VERTEX, 0);
            GLES20.glEnableVertexAttribArray(aUV);
            GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, BYTES_PER_VERTEX, 12);
            GLES20.glEnableVertexAttribArray(aShade);
            GLES20.glVertexAttribPointer(aShade, 1, GLES20.GL_FLOAT, false, BYTES_PER_VERTEX, 20);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count);
        }

        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aUV);
        GLES20.glDisableVertexAttribArray(aShade);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    // --------------------------------------------------------------- textures

    private int createAtlasTexture() {
        Bitmap atlas = buildAtlasBitmap();
        Matrix flip = new Matrix();
        flip.setScale(1f, -1f);
        Bitmap uploaded = Bitmap.createBitmap(atlas, 0, 0, atlas.getWidth(), atlas.getHeight(), flip, false);
        if (uploaded != atlas) atlas.recycle();

        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, uploaded, 0);
        uploaded.recycle();

        for (int i = 0; i < 16; i++) {
            int tx = i & 3;
            int ty = i >> 2;
            tileU0[i] = tx / 4f + INSET;
            tileU1[i] = (tx + 1) / 4f - INSET;
            tileV1[i] = 1f - ty / 4f - INSET;
            tileV0[i] = 1f - (ty + 1) / 4f + INSET;
        }
        return tex[0];
    }

    private static int shift(int color, int delta) {
        int r = Math.max(0, Math.min(255, ((color >> 16) & 0xFF) + delta));
        int g = Math.max(0, Math.min(255, ((color >> 8) & 0xFF) + delta));
        int b = Math.max(0, Math.min(255, (color & 0xFF) + delta));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void noiseTile(Canvas c, Paint p, Random rnd, int tile, int base, int amp) {
        int tx = (tile & 3) * 16;
        int ty = (tile >> 2) * 16;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                p.setColor(shift(base, rnd.nextInt(amp * 2 + 1) - amp));
                c.drawRect(tx + x, ty + y, tx + x + 1, ty + y + 1, p);
            }
        }
    }

    private static void px(Canvas c, Paint p, int tile, int x, int y, int color) {
        int tx = (tile & 3) * 16 + x;
        int ty = (tile >> 2) * 16 + y;
        p.setColor(color);
        c.drawRect(tx, ty, tx + 1, ty + 1, p);
    }

    private static Bitmap buildAtlasBitmap() {
        Bitmap bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint();
        p.setStyle(Paint.Style.FILL);
        p.setAntiAlias(false);
        c.drawColor(0xFF202020);
        Random rnd = new Random(20241125L);

        // 0: grass top
        noiseTile(c, p, rnd, 0, 0xFF6EBB43, 22);
        // 2: dirt
        noiseTile(c, p, rnd, 2, 0xFF8B5E34, 18);
        // 1: grass side (dirt + grass cap)
        noiseTile(c, p, rnd, 1, 0xFF8B5E34, 16);
        for (int x = 0; x < 16; x++) {
            int depth = 3 + rnd.nextInt(3);
            for (int y = 0; y < depth; y++) {
                px(c, p, 1, x, y, shift(0xFF6EBB43, rnd.nextInt(40) - 20));
            }
        }
        // 3: stone
        noiseTile(c, p, rnd, 3, 0xFF8F8F8F, 16);
        for (int i = 0; i < 8; i++) {
            px(c, p, 3, rnd.nextInt(16), rnd.nextInt(16), 0xFF6E6E6E);
        }
        // 4: cobblestone
        noiseTile(c, p, rnd, 4, 0xFF7E7E7E, 14);
        for (int i = 0; i < 6; i++) {
            int x = rnd.nextInt(13);
            int y = rnd.nextInt(13);
            int w = 2 + rnd.nextInt(2);
            int h = 2 + rnd.nextInt(2);
            for (int dx = 0; dx < w; dx++) {
                for (int dy = 0; dy < h; dy++) {
                    px(c, p, 4, (x + dx) & 15, (y + dy) & 15, 0xFF5A5A5A);
                }
            }
        }
        // 5: sand
        noiseTile(c, p, rnd, 5, 0xFFE2D6A2, 12);
        for (int i = 0; i < 10; i++) {
            px(c, p, 5, rnd.nextInt(16), rnd.nextInt(16), 0xFFCDBF87);
        }
        // 6: log side
        noiseTile(c, p, rnd, 6, 0xFF6B4A26, 12);
        for (int y = 0; y < 16; y++) {
            px(c, p, 6, 2, y, 0xFF4E351A);
            px(c, p, 6, 9, y, 0xFF563A1D);
            px(c, p, 6, 13, y, 0xFF4E351A);
        }
        // 7: log top
        noiseTile(c, p, rnd, 7, 0xFF8A6032, 10);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double d = Math.hypot(x - 7.5, y - 7.5);
                if (d > 6.4) px(c, p, 7, x, y, 0xFF4E351A);
                else if (d > 4.2 && d < 5.2) px(c, p, 7, x, y, 0xFF6B4A26);
                else if (d < 1.6) px(c, p, 7, x, y, 0xFF5A3C1F);
            }
        }
        // 8: leaves
        noiseTile(c, p, rnd, 8, 0xFF3E7A2A, 20);
        for (int i = 0; i < 26; i++) {
            px(c, p, 8, rnd.nextInt(16), rnd.nextInt(16), 0xFF1F4A16);
        }
        // 9: water
        noiseTile(c, p, rnd, 9, 0xFF2F63C4, 10);
        for (int y = 2; y < 16; y += 5) {
            for (int x = 0; x < 16; x++) {
                px(c, p, 9, x, y + (x / 6), 0xFF4A82E0);
            }
        }
        // 10: planks
        noiseTile(c, p, rnd, 10, 0xFFB08A54, 12);
        for (int y = 0; y < 16; y += 5) {
            for (int x = 0; x < 16; x++) px(c, p, 10, x, y, 0xFF8A6537);
        }
        for (int y = 0; y < 16; y++) {
            if (y % 5 != 0) px(c, p, 10, 6, y, 0xFF97703F);
        }
        // 11: glass
        noiseTile(c, p, rnd, 11, 0xFFCDE8F2, 8);
        for (int i = 0; i < 16; i++) {
            px(c, p, 11, i, 0, 0xFFF4FBFE);
            px(c, p, 11, i, 15, 0xFFAFC9D4);
            px(c, p, 11, 0, i, 0xFFF4FBFE);
            px(c, p, 11, 15, i, 0xFFAFC9D4);
        }
        // 12: bedrock
        noiseTile(c, p, rnd, 12, 0xFF4A4A4A, 22);
        for (int i = 0; i < 20; i++) {
            px(c, p, 12, rnd.nextInt(16), rnd.nextInt(16), 0xFF2B2B2B);
        }
        // 13: snow
        noiseTile(c, p, rnd, 13, 0xFFF2F7FA, 8);
        // 14: brick
        noiseTile(c, p, rnd, 14, 0xFFA64B3B, 10);
        for (int y = 0; y < 16; y += 4) {
            for (int x = 0; x < 16; x++) px(c, p, 14, x, y, 0xFFC9C1B4);
        }
        for (int row = 0; row < 4; row++) {
            int offset = (row % 2 == 0) ? 0 : 4;
            for (int x = offset; x < 16; x += 8) {
                for (int y = row * 4; y < row * 4 + 4; y++) px(c, p, 14, x, y, 0xFFC9C1B4);
            }
        }
        // 15: unused (magenta/black checker)
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                px(c, p, 15, x, y, ((x / 4 + y / 4) % 2 == 0) ? 0xFFFF00FF : 0xFF000000);
            }
        }
        return bmp;
    }
}
