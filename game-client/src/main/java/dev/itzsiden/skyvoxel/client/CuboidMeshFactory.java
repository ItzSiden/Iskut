package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.render.api.MeshData;

/** Creates backend-neutral cuboid geometry centred at the local origin. */
public final class CuboidMeshFactory {
    private CuboidMeshFactory() {}

    /**
     * Returns a reusable unit cuboid spanning -0.5..+0.5 on each axis.
     * Object dimensions and positions are supplied through each instance's model matrix.
     * Face colours are slightly shaded to give a readable 3D look without a lighting system.
     */
    public static MeshData createUnitCuboid() {
        float[] vertices = new float[6 * 4 * 6];
        int[] indices = new int[6 * 6];
        int[] vertexOffset = {0};
        int[] indexOffset = {0};
        int[] vertexBase = {0};

        // Top (+Y)
        addFace(vertices, indices, vertexOffset, indexOffset, vertexBase,
            -0.5f, 0.5f, -0.5f,
            -0.5f, 0.5f,  0.5f,
             0.5f, 0.5f,  0.5f,
             0.5f, 0.5f, -0.5f, 1.00f);
        // Front (+Z)
        addFace(vertices, indices, vertexOffset, indexOffset, vertexBase,
            -0.5f, -0.5f, 0.5f,
             0.5f, -0.5f, 0.5f,
             0.5f,  0.5f, 0.5f,
            -0.5f,  0.5f, 0.5f, 0.82f);
        // Right (+X)
        addFace(vertices, indices, vertexOffset, indexOffset, vertexBase,
             0.5f, -0.5f, -0.5f,
             0.5f,  0.5f, -0.5f,
             0.5f,  0.5f,  0.5f,
             0.5f, -0.5f,  0.5f, 0.64f);
        // Back (-Z)
        addFace(vertices, indices, vertexOffset, indexOffset, vertexBase,
             0.5f, -0.5f, -0.5f,
            -0.5f, -0.5f, -0.5f,
            -0.5f,  0.5f, -0.5f,
             0.5f,  0.5f, -0.5f, 0.58f);
        // Left (-X)
        addFace(vertices, indices, vertexOffset, indexOffset, vertexBase,
            -0.5f, -0.5f, -0.5f,
            -0.5f, -0.5f,  0.5f,
            -0.5f,  0.5f,  0.5f,
            -0.5f,  0.5f, -0.5f, 0.73f);
        // Bottom (-Y)
        addFace(vertices, indices, vertexOffset, indexOffset, vertexBase,
            -0.5f, -0.5f, -0.5f,
             0.5f, -0.5f, -0.5f,
             0.5f, -0.5f,  0.5f,
            -0.5f, -0.5f,  0.5f, 0.42f);

        return MeshData.fromPositionColor(vertices, indices);
    }

    private static void addFace(
        float[] vertices,
        int[] indices,
        int[] vertexOffset,
        int[] indexOffset,
        int[] vertexBase,
        float x0, float y0, float z0,
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        float x3, float y3, float z3,
        float shade
    ) {
        int offset = vertexOffset[0];
        offset = putVertex(vertices, offset, x0, y0, z0, shade);
        offset = putVertex(vertices, offset, x1, y1, z1, shade);
        offset = putVertex(vertices, offset, x2, y2, z2, shade);
        offset = putVertex(vertices, offset, x3, y3, z3, shade);
        vertexOffset[0] = offset;

        int base = vertexBase[0];
        int index = indexOffset[0];
        indices[index++] = base;
        indices[index++] = base + 1;
        indices[index++] = base + 2;
        indices[index++] = base;
        indices[index++] = base + 2;
        indices[index++] = base + 3;
        indexOffset[0] = index;
        vertexBase[0] = base + 4;
    }

    private static int putVertex(float[] output, int offset, float x, float y, float z, float shade) {
        output[offset++] = x;
        output[offset++] = y;
        output[offset++] = z;
        output[offset++] = shade;
        output[offset++] = shade;
        output[offset++] = shade;
        return offset;
    }
}
