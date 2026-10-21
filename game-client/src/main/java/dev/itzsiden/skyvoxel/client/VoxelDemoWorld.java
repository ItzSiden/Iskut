package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.engine.scene.Mesh;
import dev.itzsiden.skyvoxel.engine.scene.RenderObject;
import dev.itzsiden.skyvoxel.engine.scene.Scene;
import dev.itzsiden.skyvoxel.engine.scene.Transform;
import dev.itzsiden.skyvoxel.render.api.MeshData;

/**
 * Small heightmap terrain used to exercise and benchmark the renderer with a realistic voxel
 * workload: 16x16 chunks, only exposed faces are meshed (like a real chunk mesher), face shading
 * is baked into vertex colour, one shared atlas texture. Rendering demo only: the terrain surface
 * is kept at or below y = 0 so the existing flat-floor player controller stays above it.
 */
final class VoxelDemoWorld {
    static final int CHUNK = 16;
    private static final int MIN_Y = -12;
    // Minecraft-style fixed face brightness.
    private static final float SHADE_TOP = 1.00f, SHADE_Z = 0.80f, SHADE_X = 0.60f;

    private VoxelDemoWorld() {}

    /** Top block index of the column (block occupies y .. y+1). Always between -9 and -1. */
    static int height(int x, int z) {
        double h = Math.sin(x * 0.11) * 2.2 + Math.cos(z * 0.09) * 2.0 + Math.sin((x + z) * 0.05) * 1.6
            + Math.sin(x * 0.31 + z * 0.17) * 0.6;
        return (int) Math.max(-9, Math.min(-1, Math.round(h) - 5));
    }

    static Scene create(int chunkRadius) {
        Scene scene = new Scene();
        Material material = Material.textured("terrain", BlockAtlas.create());
        for (int cz = -chunkRadius; cz < chunkRadius; cz++) {
            for (int cx = -chunkRadius; cx < chunkRadius; cx++) {
                MeshData data = meshChunk(cx, cz);
                if (data == null) continue;
                Mesh mesh = new Mesh("chunk-" + cx + "_" + cz, data);
                Transform transform = new Transform().setPosition(cx * CHUNK, 0.0f, cz * CHUNK);
                scene.add(new RenderObject("chunk-" + cx + "_" + cz, mesh, material, transform));
            }
        }
        return scene;
    }

    static MeshData meshChunk(int cx, int cz) {
        MeshData.Builder mb = MeshData.builder();
        for (int lz = 0; lz < CHUNK; lz++) {
            for (int lx = 0; lx < CHUNK; lx++) {
                int wx = cx * CHUNK + lx, wz = cz * CHUNK + lz;
                int top = height(wx, wz);
                // Top face.
                int tile = top <= -8 ? BlockAtlas.SAND : BlockAtlas.GRASS_TOP;
                quad(mb, tile, SHADE_TOP, lx, top + 1, lz + 1, lx + 1, top + 1, lz + 1, lx + 1, top + 1, lz, lx, top + 1, lz);
                // Side faces: only the part of the column that is exposed to a lower neighbour.
                side(mb, wx, wz, top, height(wx, wz + 1), lx, lz, 0);   // +Z
                side(mb, wx, wz, top, height(wx + 1, wz), lx, lz, 1);   // +X
                side(mb, wx, wz, top, height(wx, wz - 1), lx, lz, 2);   // -Z
                side(mb, wx, wz, top, height(wx - 1, wz), lx, lz, 3);   // -X
            }
        }
        return mb.isEmpty() ? null : mb.build();
    }

    private static void side(MeshData.Builder mb, int wx, int wz, int top, int neighborTop, int lx, int lz, int dir) {
        for (int y = Math.max(neighborTop + 1, MIN_Y); y <= top; y++) {
            int depth = top - y;
            int tile = depth == 0 ? (top <= -8 ? BlockAtlas.SAND : BlockAtlas.GRASS_SIDE)
                : depth <= 2 ? (top <= -8 ? BlockAtlas.SAND : BlockAtlas.DIRT) : BlockAtlas.STONE;
            switch (dir) {
                case 0 -> quad(mb, tile, SHADE_Z, lx, y, lz + 1, lx + 1, y, lz + 1, lx + 1, y + 1, lz + 1, lx, y + 1, lz + 1);
                case 1 -> quad(mb, tile, SHADE_X, lx + 1, y, lz + 1, lx + 1, y, lz, lx + 1, y + 1, lz, lx + 1, y + 1, lz + 1);
                case 2 -> quad(mb, tile, SHADE_Z, lx + 1, y, lz, lx, y, lz, lx, y + 1, lz, lx + 1, y + 1, lz);
                default -> quad(mb, tile, SHADE_X, lx, y, lz, lx, y, lz + 1, lx, y + 1, lz + 1, lx, y + 1, lz);
            }
        }
    }

    /** Counter-clockwise quad (seen from outside); uv runs along the first edge, then up the second. */
    private static void quad(MeshData.Builder mb, int tile, float shade,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        float u0 = BlockAtlas.u0(tile), u1 = BlockAtlas.u1(tile), v0 = BlockAtlas.v0(tile), v1 = BlockAtlas.v1(tile);
        int a = mb.vertex(x0, y0, z0, u0, v1, shade, shade, shade);
        int b = mb.vertex(x1, y1, z1, u1, v1, shade, shade, shade);
        int c = mb.vertex(x2, y2, z2, u1, v0, shade, shade, shade);
        int d = mb.vertex(x3, y3, z3, u0, v0, shade, shade, shade);
        mb.quad(a, b, c, d);
    }
}
