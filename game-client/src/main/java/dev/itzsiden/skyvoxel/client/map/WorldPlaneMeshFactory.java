package dev.itzsiden.skyvoxel.client.map;

import dev.itzsiden.skyvoxel.render.api.MeshData;

/** Top-facing, zero-thickness textured plane for world-scale surfaces such as the ocean. */
final class WorldPlaneMeshFactory {
    private WorldPlaneMeshFactory() { }

    static MeshData create(float width, float depth, float tileWorldSize) {
        if (!Float.isFinite(width) || !Float.isFinite(depth) || width <= 0 || depth <= 0)
            throw new IllegalArgumentException("Plane dimensions must be positive and finite.");
        if (!Float.isFinite(tileWorldSize) || tileWorldSize <= 0)
            throw new IllegalArgumentException("Plane texture tile size must be positive and finite.");
        float x0 = -0.5f, x1 = 0.5f, z0 = -0.5f, z1 = 0.5f;
        float u = width / tileWorldSize, v = depth / tileWorldSize;
        MeshData.Builder builder = MeshData.builder();
        int first = builder.vertexCount();
        // Vertex winding matches the +Y faces emitted by WorldCuboidMeshFactory.
        builder.vertex(x0, 0, z0, 0, 0, 1, 1, 1);
        builder.vertex(x0, 0, z1, 0, v, 1, 1, 1);
        builder.vertex(x1, 0, z1, u, v, 1, 1, 1);
        builder.vertex(x1, 0, z0, u, 0, 1, 1, 1);
        builder.quad(first, first + 1, first + 2, first + 3);
        return builder.build();
    }
}
