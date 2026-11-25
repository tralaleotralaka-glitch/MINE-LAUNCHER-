package com.minelauncher.app.game;

import java.util.Random;

/**
 * Flat byte-array voxel world with procedural terrain (noise based heightmap,
 * beaches, oceans, hills and trees).
 */
public class World {

    public static final int CHUNK = 16;
    public static final int CX = 12;
    public static final int CZ = 12;
    public static final int SX = CX * CHUNK;
    public static final int SZ = CZ * CHUNK;
    public static final int SY = 72;
    public static final int SEA = 26;

    public final long seed;
    private final byte[] blocks = new byte[SX * SY * SZ];

    public World(long seed) {
        this.seed = seed;
    }

    public int get(int x, int y, int z) {
        if (x < 0 || x >= SX || z < 0 || z >= SZ) return BlockTypes.AIR;
        if (y < 0) return BlockTypes.BEDROCK;
        if (y >= SY) return BlockTypes.AIR;
        return blocks[(y * SZ + z) * SX + x];
    }

    public int getFast(int x, int y, int z) {
        return blocks[(y * SZ + z) * SX + x];
    }

    public void set(int x, int y, int z, int id) {
        if (x < 0 || x >= SX || z < 0 || z >= SZ || y < 0 || y >= SY) return;
        blocks[(y * SZ + z) * SX + x] = (byte) id;
    }

    public int surfaceHeight(int x, int z) {
        for (int y = SY - 1; y >= 0; y--) {
            int id = get(x, y, z);
            if (id != BlockTypes.AIR && id != BlockTypes.WATER) return y;
        }
        return 0;
    }

    /** Finds a comfortable dry spawn point near the centre of the world. */
    public int[] findSpawn() {
        int cx = SX / 2;
        int cz = SZ / 2;
        for (int r = 0; r < 48; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = cx + dx;
                    int z = cz + dz;
                    if (x < 4 || x >= SX - 4 || z < 4 || z >= SZ - 4) continue;
                    int top = surfaceHeight(x, z);
                    if (top > SEA + 1 && get(x, top, z) == BlockTypes.GRASS) {
                        return new int[]{x, top, z};
                    }
                }
            }
        }
        int top = surfaceHeight(cx, cz);
        return new int[]{cx, top, cz};
    }

    // ---------------------------------------------------------------- terrain

    public void generate() {
        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                double rolling = fbm(x * 0.017, z * 0.017, seed, 4);
                double mountain = fbm(x * 0.0055 + 40.0, z * 0.0055 + 40.0, seed + 977L, 3);
                double ridge = mountain * mountain * mountain;
                int top = (int) Math.round(20.0 + rolling * 17.0 + ridge * 34.0);
                if (top < 5) top = 5;
                if (top > SY - 12) top = SY - 12;

                set(x, 0, z, BlockTypes.BEDROCK);
                for (int y = 1; y < top - 3; y++) set(x, y, z, BlockTypes.STONE);
                for (int y = Math.max(1, top - 3); y < top; y++) set(x, y, z, BlockTypes.DIRT);
                int surface = (top <= SEA + 1) ? BlockTypes.SAND : BlockTypes.GRASS;
                set(x, top, z, surface);
                for (int y = top + 1; y <= SEA; y++) set(x, y, z, BlockTypes.WATER);
            }
        }
        plantTrees();
    }

    private void plantTrees() {
        Random rnd = new Random(seed * 31L + 7L);
        for (int x = 4; x < SX - 4; x++) {
            for (int z = 4; z < SZ - 4; z++) {
                if (rnd.nextFloat() > 0.016f) continue;
                int top = surfaceHeight(x, z);
                if (top <= SEA + 2) continue;
                if (get(x, top, z) != BlockTypes.GRASS) continue;
                if (top > SY - 14) continue;
                buildTree(x, top + 1, z, rnd);
            }
        }
    }

    private void buildTree(int x, int y, int z, Random rnd) {
        int trunk = 4 + rnd.nextInt(3);
        for (int i = 0; i < trunk; i++) set(x, y + i, z, BlockTypes.LOG);
        int topY = y + trunk;
        for (int ly = topY - 2; ly <= topY + 1; ly++) {
            int r = (ly >= topY) ? 1 : 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) == r && Math.abs(dz) == r) continue;
                    if (get(x + dx, ly, z + dz) == BlockTypes.AIR) {
                        set(x + dx, ly, z + dz, BlockTypes.LEAVES);
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------- noise

    private static double hash2(int x, int z, long s) {
        long h = x * 374761393L + z * 668265263L + s * 1442695040888963407L;
        h = (h ^ (h >> 13)) * 1274126177L;
        h = h ^ (h >> 16);
        return (double) (h & 0xFFFFFFL) / (double) 0xFFFFFFL;
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double valueNoise(double x, double z, long s) {
        int xi = (int) Math.floor(x);
        int zi = (int) Math.floor(z);
        double u = smooth(x - xi);
        double v = smooth(z - zi);
        double a = hash2(xi, zi, s);
        double b = hash2(xi + 1, zi, s);
        double c = hash2(xi, zi + 1, s);
        double d = hash2(xi + 1, zi + 1, s);
        return lerp(lerp(a, b, u), lerp(c, d, u), v);
    }

    private static double fbm(double x, double z, long s, int octaves) {
        double sum = 0;
        double amp = 1;
        double total = 0;
        double freq = 1;
        for (int i = 0; i < octaves; i++) {
            sum += valueNoise(x * freq, z * freq, s + i * 7919L) * amp;
            total += amp;
            amp *= 0.5;
            freq *= 2.0;
        }
        return total == 0 ? 0 : sum / total;
    }

    // --------------------------------------------------------------- ray cast

    /**
     * Voxel ray cast (DDA). Returns {bx,by,bz,nx,ny,nz} or null.
     */
    public int[] raycast(double ox, double oy, double oz,
                         double dx, double dy, double dz, double maxDist) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0) return null;
        dx /= len;
        dy /= len;
        dz /= len;

        int x = (int) Math.floor(ox);
        int y = (int) Math.floor(oy);
        int z = (int) Math.floor(oz);

        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;

        double tDeltaX = dx == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        double tDeltaY = dy == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
        double tDeltaZ = dz == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);

        double tMaxX = dx == 0 ? Double.MAX_VALUE : (dx > 0 ? (x + 1 - ox) : (ox - x)) * tDeltaX;
        double tMaxY = dy == 0 ? Double.MAX_VALUE : (dy > 0 ? (y + 1 - oy) : (oy - y)) * tDeltaY;
        double tMaxZ = dz == 0 ? Double.MAX_VALUE : (dz > 0 ? (z + 1 - oz) : (oz - z)) * tDeltaZ;

        int nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < 256; i++) {
            double t = Math.min(Math.min(tMaxX, tMaxY), tMaxZ);
            if (t > maxDist) return null;
            int id = get(x, y, z);
            if (id != BlockTypes.AIR && id != BlockTypes.WATER) {
                return new int[]{x, y, z, nx, ny, nz};
            }
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
                nx = -stepX;
                ny = 0;
                nz = 0;
            } else if (tMaxY <= tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
                nx = 0;
                ny = -stepY;
                nz = 0;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
                nx = 0;
                ny = 0;
                nz = -stepZ;
            }
        }
        return null;
    }
}
