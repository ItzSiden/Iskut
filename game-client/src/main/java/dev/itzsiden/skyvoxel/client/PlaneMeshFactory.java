package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.render.api.MeshData;

/** Builds a coloured debug floor. Larger checker tiles reduce CPU rasterizer work substantially. */
final class PlaneMeshFactory {
    private static final int SOFTWARE_FRIENDLY_TILE_SIZE = 4;

    private PlaneMeshFactory() {}

    static MeshData createDebugPlane(int halfSize) {
        if (halfSize < 1 || halfSize > 256) {
            throw new IllegalArgumentException("halfSize must be between 1 and 256.");
        }

        // Keep the plane's checkerboard appearance, but avoid thousands of one-unit tiles.
        // For small or odd-sized test planes, fall back to one-unit tiles to preserve exact bounds.
        int tileSize = halfSize >= 8 && halfSize % 2 == 0 ? SOFTWARE_FRIENDLY_TILE_SIZE : 1;
        int side = halfSize * 2;
        int tilesPerSide = side / tileSize;
        int tileCount = tilesPerSide * tilesPerSide;
        float[] vertices = new float[tileCount * 4 * 6];
        int[] indices = new int[tileCount * 6];
        int vertexOffset = 0;
        int indexOffset = 0;
        int vertexBase = 0;

        for (int z = -halfSize; z < halfSize; z += tileSize) {
            for (int x = -halfSize; x < halfSize; x += tileSize) {
                boolean alternate = (((x + z) / tileSize) & 1) == 0;
                float red = alternate ? 0.17f : 0.20f;
                float green = alternate ? 0.22f : 0.25f;
                float blue = alternate ? 0.18f : 0.21f;

                vertexOffset = putVertex(vertices, vertexOffset, x, 0.0f, z, red, green, blue);
                vertexOffset = putVertex(vertices, vertexOffset, x, 0.0f, z + tileSize, red, green, blue);
                vertexOffset = putVertex(vertices, vertexOffset, x + tileSize, 0.0f, z + tileSize, red, green, blue);
                vertexOffset = putVertex(vertices, vertexOffset, x + tileSize, 0.0f, z, red, green, blue);

                indices[indexOffset++] = vertexBase;
                indices[indexOffset++] = vertexBase + 1;
                indices[indexOffset++] = vertexBase + 2;
                indices[indexOffset++] = vertexBase;
                indices[indexOffset++] = vertexBase + 2;
                indices[indexOffset++] = vertexBase + 3;
                vertexBase += 4;
            }
        }
        return MeshData.fromPositionColor(vertices, indices);
    }

    private static int putVertex(
        float[] output,
        int offset,
        float x,
        float y,
        float z,
        float red,
        float green,
        float blue
    ) {
        output[offset++] = x;
        output[offset++] = y;
        output[offset++] = z;
        output[offset++] = red;
        output[offset++] = green;
        output[offset++] = blue;
        return offset;
    }
}
