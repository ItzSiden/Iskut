package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.render.api.TextureData;

/**
 * Procedural 64x64 block atlas (4x4 tiles of 16x16, Minecraft-style) so the renderer can be
 * exercised without shipping any art. Replace with a PNG loaded via TextureData.fromImage later.
 */
final class BlockAtlas {
    static final int TILE = 16;
    static final int TILES_PER_ROW = 4;
    static final int SIZE = TILE * TILES_PER_ROW;

    static final int GRASS_TOP = 0, GRASS_SIDE = 1, DIRT = 2, STONE = 3, SAND = 4, LEAVES = 5, PLANKS = 6;

    private BlockAtlas() {}

    static TextureData create() {
        int[] px = new int[SIZE * SIZE];
        for (int ty = 0; ty < TILE; ty++) {
            for (int tx = 0; tx < TILE; tx++) {
                put(px, GRASS_TOP, tx, ty, noisy(0x5BA23A, tx, ty, GRASS_TOP, 22));
                put(px, DIRT, tx, ty, noisy(0x866043, tx, ty, DIRT, 20));
                put(px, STONE, tx, ty, noisy(0x7D7D7D, tx, ty, STONE, 26));
                put(px, SAND, tx, ty, noisy(0xDBCF9B, tx, ty, SAND, 12));
                int grassSide = ty < 4 ? noisy(0x5BA23A, tx, ty, GRASS_TOP, 22) : noisy(0x866043, tx, ty, DIRT, 20);
                put(px, GRASS_SIDE, tx, ty, grassSide);
                // Leaves: cut-out holes exercise the alpha-tested path.
                boolean hole = hash(tx, ty, LEAVES) % 5 == 0;
                put(px, LEAVES, tx, ty, hole ? 0x00000000 : noisy(0x2F7A2A, tx, ty, LEAVES, 30));
                boolean plankLine = ty % 4 == 3;
                put(px, PLANKS, tx, ty, plankLine ? 0xFF6E5430 : noisy(0xA8864F, tx, ty, PLANKS, 10));
            }
        }
        return new TextureData("block-atlas", SIZE, SIZE, px);
    }

    /** Atlas UV bounds for a tile, inset slightly so nearest sampling never bleeds into a neighbour. */
    static float u0(int tile) { return ((tile % TILES_PER_ROW) * TILE + 0.02f) / SIZE; }
    static float u1(int tile) { return ((tile % TILES_PER_ROW) * TILE + TILE - 0.02f) / SIZE; }
    static float v0(int tile) { return ((tile / TILES_PER_ROW) * TILE + 0.02f) / SIZE; }
    static float v1(int tile) { return ((tile / TILES_PER_ROW) * TILE + TILE - 0.02f) / SIZE; }

    private static void put(int[] px, int tile, int tx, int ty, int argb) {
        int x = (tile % TILES_PER_ROW) * TILE + tx;
        int y = (tile / TILES_PER_ROW) * TILE + ty;
        px[y * SIZE + x] = argb;
    }

    private static int noisy(int rgb, int x, int y, int salt, int amplitude) {
        int n = (hash(x, y, salt) & 0xFF) * amplitude / 255 - amplitude / 2;
        int r = clamp(((rgb >> 16) & 255) + n), g = clamp(((rgb >> 8) & 255) + n), b = clamp((rgb & 255) + n);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int hash(int x, int y, int salt) {
        int h = x * 374761393 + y * 668265263 + salt * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7FFFFFFF;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
