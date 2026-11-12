package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.engine.scene.Mesh;
import dev.itzsiden.skyvoxel.engine.scene.RenderObject;
import dev.itzsiden.skyvoxel.engine.scene.Scene;
import dev.itzsiden.skyvoxel.engine.scene.Transform;
import dev.itzsiden.skyvoxel.render.api.MeshData;
import dev.itzsiden.skyvoxel.render.api.TextureData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Infinite, animated ocean. Replaces the old single 4096x4096 quad.
 *
 * Why: the software rasterizer drops any triangle whose clipped screen extent exceeds 1e7 px
 * (SoftwareRasterCore.setupTriangle). A giant quad clipped against the near plane breaks that limit,
 * so the sea vanished depending on camera angle. Here the sea is a grid of small triangles that
 * follows the camera, so every clipped triangle stays small. The texture and the baked shading
 * scroll together; the mesh only snaps in whole shading periods, so there is no visible pop.
 */
final class OceanSurface {
    static final float SEA_LEVEL = 1.0f;

    private static final int CELLS = 32;            // 32 x 32 cells
    private static final float CELL = 16.0f;        // world units per cell -> 512 span, +-256 reach
    private static final float TILE = 8.0f;         // world units per texture repeat (32px texture)
    private static final double SNAP = 64.0;        // multiple of TILE and of the shading period
    private static final double DRIFT_X = 0.55, DRIFT_Z = 0.32; // world units / second

    private final Scene scene = new Scene();
    private final Transform transform = new Transform();

    private OceanSurface(Material material) {
        scene.add(new RenderObject("ocean/surface", buildMesh(), material, transform)
            .setCollidable(false).setStaticBatchExcluded(true));
        transform.setPosition(0.0f, SEA_LEVEL, 0.0f);
    }

    static OceanSurface load(Path waterTexture) throws IOException {
        BufferedImage image = ImageIO.read(waterTexture.toFile());
        if (image == null) throw new IOException("Unsupported ocean texture: " + waterTexture);
        return new OceanSurface(Material.textured("ocean_water", TextureData.fromImage("world/ocean_water", image)));
    }

    Scene scene() { return scene; }

    /** Removes the legacy giant ocean plane (placed by main_hub.vmap) from the given scenes. */
    static void removeLegacyPlane(Scene... scenes) {
        for (Scene s : scenes) {
            List<String> ids = new ArrayList<>();
            for (RenderObject o : s.objects()) if (o.id().endsWith("/ocean_water")) ids.add(o.id());
            for (String id : ids) s.remove(id);
        }
    }

    /** Call once per frame. Keeps the grid centred on the camera and scrolls the water. */
    void update(double camX, double camZ, double timeSeconds) {
        double dx = timeSeconds * DRIFT_X, dz = timeSeconds * DRIFT_Z;
        double cx = Math.floor((camX - dx) / SNAP + 0.5) * SNAP + dx;
        double cz = Math.floor((camZ - dz) / SNAP + 0.5) * SNAP + dz;
        transform.setPosition((float) cx, SEA_LEVEL, (float) cz);
    }

    private static Mesh buildMesh() {
        MeshData.Builder b = MeshData.builder();
        int side = CELLS + 1;
        float half = CELLS * CELL * 0.5f;
        int[] index = new int[side * side];
        for (int iz = 0; iz < side; iz++) {
            for (int ix = 0; ix < side; ix++) {
                float lx = ix * CELL - half, lz = iz * CELL - half;
                float shade = shade(lx, lz);
                // Vertex colour multiplies the texture: soft deep/light patches break up the tiling.
                index[iz * side + ix] = b.vertex(lx, 0.0f, lz, lx / TILE, lz / TILE,
                    shade * 0.90f, shade * 0.97f, Math.min(1.0f, shade * 1.04f));
            }
        }
        for (int iz = 0; iz < CELLS; iz++) {
            for (int ix = 0; ix < CELLS; ix++) {
                int a = index[iz * side + ix];
                int bz = index[(iz + 1) * side + ix];
                int c = index[(iz + 1) * side + ix + 1];
                int d = index[iz * side + ix + 1];
                b.quad(a, bz, c, d);   // same winding as WorldPlaneMeshFactory (+Y facing)
            }
        }
        return new Mesh("ocean-grid", b.build());
    }

    /** Large-scale brightness variation with a 64-unit period (matches SNAP). */
    private static float shade(float x, float z) {
        double k = Math.PI * 2.0 / SNAP;
        double v = 0.55 * Math.sin(x * k * 1.0 + z * k * 2.0)
                 + 0.30 * Math.sin(x * k * 3.0 - z * k * 1.0 + 1.7)
                 + 0.15 * Math.sin(x * k * 2.0 + z * k * 3.0 + 0.6);
        return (float) (0.90 + 0.10 * v);   // ~0.80 .. 1.00
    }
}
