package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.engine.scene.Mesh;
import dev.itzsiden.skyvoxel.engine.scene.RenderObject;
import dev.itzsiden.skyvoxel.engine.scene.Scene;
import dev.itzsiden.skyvoxel.engine.scene.Transform;

/** Builds repeatable demo scenes using engine-core objects, not backend resources. */
final class SceneFactory {
    private static final Material[] PALETTE = {
        new Material("moss", 0.28f, 0.72f, 0.34f),
        new Material("stone", 0.62f, 0.60f, 0.56f),
        new Material("crystal", 0.34f, 0.67f, 0.88f),
        new Material("copper", 0.78f, 0.39f, 0.22f),
        new Material("sand", 0.84f, 0.72f, 0.42f),
        new Material("violet", 0.60f, 0.40f, 0.78f)
    };

    private SceneFactory() {}

    /** Textured voxel-terrain scene used for rendering and benchmarking (see VoxelDemoWorld). */
    static Scene createVoxelDemo() {
        return VoxelDemoWorld.create(8);
    }

    static Scene createDemo(int benchmarkCuboids) {
        Scene scene = new Scene();
        Mesh ground = new Mesh("debug-ground", PlaneMeshFactory.createDebugPlane(32));
        scene.add(new RenderObject("ground", ground, Material.WHITE));
        Mesh unitCuboid = new Mesh("unit-cuboid", CuboidMeshFactory.createUnitCuboid());

        if (benchmarkCuboids > 0) {
            addBenchmark(scene, unitCuboid, benchmarkCuboids);
        } else {
            addCuboid(scene, unitCuboid, "cube-01", -3.0f, 1.35f, 0.1f, 1.10f, 2.70f, 1.00f, 0);
            addCuboid(scene, unitCuboid, "cube-02", -1.45f, 0.42f, -1.3f, 1.90f, 0.84f, 1.30f, 1);
            addCuboid(scene, unitCuboid, "cube-03", 0.00f, 1.15f, -2.7f, 0.85f, 2.30f, 0.85f, 2);
            addCuboid(scene, unitCuboid, "cube-04", 1.75f, 0.55f, -0.5f, 2.60f, 1.10f, 1.35f, 3);
            addCuboid(scene, unitCuboid, "cube-05", 3.25f, 1.70f, -2.4f, 0.95f, 3.40f, 0.95f, 4);
            addCuboid(scene, unitCuboid, "cube-06", -2.80f, 0.70f, -4.0f, 1.50f, 1.40f, 1.50f, 5);
            addCuboid(scene, unitCuboid, "cube-07", 0.00f, 0.30f, -6.1f, 4.20f, 0.60f, 2.40f, 0);
            addCuboid(scene, unitCuboid, "cube-08", 2.50f, 0.50f, -5.5f, 1.45f, 1.00f, 1.45f, 1);
            addCuboid(scene, unitCuboid, "cube-09", 2.50f, 1.35f, -5.5f, 0.78f, 0.70f, 0.78f, 4);
        }
        return scene;
    }

    private static void addBenchmark(Scene scene, Mesh unitCuboid, int count) {
        // Deterministic grid. Many objects are intentionally outside the camera frustum so the
        // console stats demonstrate how many submissions frustum culling avoids.
        final int columns = 31;
        for (int i = 0; i < count; i++) {
            int column = i % columns;
            int row = i / columns;
            float x = (column - (columns - 1) * 0.5f) * 2.65f;
            float z = -5.5f - row * 2.8f;
            float width = 0.65f + (i % 5) * 0.12f;
            float height = 0.65f + (i % 7) * 0.24f;
            float length = 0.65f + (i % 3) * 0.18f;
            addCuboid(scene, unitCuboid, "bench-cube-" + i, x, height * 0.5f, z,
                width, height, length, i % PALETTE.length);
        }
    }

    private static void addCuboid(
        Scene scene, Mesh mesh, String id,
        float x, float y, float z, float width, float height, float length, int paletteIndex
    ) {
        Transform transform = new Transform()
            .setPosition(x, y, z)
            .setScale(width, height, length);
        scene.add(new RenderObject(id, mesh, PALETTE[paletteIndex], transform).setCollidable(true));
    }
}
