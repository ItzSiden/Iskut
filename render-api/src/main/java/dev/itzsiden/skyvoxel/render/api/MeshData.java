package dev.itzsiden.skyvoxel.render.api;

import java.util.Arrays;

/**
 * Backend-neutral indexed triangle mesh.
 *
 * <p>Vertex layout (8 floats): position xyz, texture coordinate uv (0..1 across the texture),
 * and a per-vertex colour rgb. The colour is where baked face shading / ambient occlusion /
 * light levels go; it is multiplied with the texture and the material tint. Meshes without a
 * texture simply use u = v = 0.
 *
 * <p>Winding: outward-facing triangles are counter-clockwise (back faces are culled).
 */
public final class MeshData {
    public static final int FLOATS_PER_VERTEX = 8;
    public static final int OFFSET_POSITION = 0;
    public static final int OFFSET_UV = 3;
    public static final int OFFSET_COLOR = 5;

    private final float[] vertices;
    private final int[] indices;

    public MeshData(float[] vertices, int[] indices) {
        if (vertices == null || indices == null) {
            throw new IllegalArgumentException("Mesh arrays cannot be null.");
        }
        if (vertices.length == 0 || vertices.length % FLOATS_PER_VERTEX != 0) {
            throw new IllegalArgumentException(
                "Vertex data must contain position xyz, uv and RGB (8 floats) for every vertex.");
        }
        if (indices.length == 0 || indices.length % 3 != 0) {
            throw new IllegalArgumentException("Indices must describe indexed triangles.");
        }
        int vertexCount = vertices.length / FLOATS_PER_VERTEX;
        for (int index : indices) {
            if (index < 0 || index >= vertexCount) {
                throw new IllegalArgumentException("Mesh index outside vertex array: " + index);
            }
        }
        this.vertices = vertices.clone();
        this.indices = indices.clone();
    }

    /** Converts the legacy 6-float layout (xyz + rgb) into the current layout with uv = 0. */
    public static MeshData fromPositionColor(float[] positionColor, int[] indices) {
        if (positionColor == null || positionColor.length == 0 || positionColor.length % 6 != 0) {
            throw new IllegalArgumentException("Legacy vertex data must contain 6 floats per vertex.");
        }
        int count = positionColor.length / 6;
        float[] out = new float[count * FLOATS_PER_VERTEX];
        for (int v = 0; v < count; v++) {
            int s = v * 6, d = v * FLOATS_PER_VERTEX;
            out[d] = positionColor[s];
            out[d + 1] = positionColor[s + 1];
            out[d + 2] = positionColor[s + 2];
            out[d + 5] = positionColor[s + 3];
            out[d + 6] = positionColor[s + 4];
            out[d + 7] = positionColor[s + 5];
        }
        return new MeshData(out, indices);
    }

    public static Builder builder() { return new Builder(); }

    public float[] vertices() { return vertices.clone(); }
    public int[] indices() { return indices.clone(); }
    public int vertexCount() { return vertices.length / FLOATS_PER_VERTEX; }
    public int indexCount() { return indices.length; }

    /** Growable mesh builder, handy for chunk meshers. */
    public static final class Builder {
        private float[] vertices = new float[FLOATS_PER_VERTEX * 256];
        private int[] indices = new int[1024];
        private int vertexCount;
        private int indexCount;

        public int vertex(float x, float y, float z, float u, float v, float r, float g, float b) {
            int o = vertexCount * FLOATS_PER_VERTEX;
            if (o + FLOATS_PER_VERTEX > vertices.length) vertices = Arrays.copyOf(vertices, vertices.length * 2);
            vertices[o] = x; vertices[o + 1] = y; vertices[o + 2] = z;
            vertices[o + 3] = u; vertices[o + 4] = v;
            vertices[o + 5] = r; vertices[o + 6] = g; vertices[o + 7] = b;
            return vertexCount++;
        }

        public Builder triangle(int a, int b, int c) {
            if (indexCount + 3 > indices.length) indices = Arrays.copyOf(indices, indices.length * 2);
            indices[indexCount++] = a; indices[indexCount++] = b; indices[indexCount++] = c;
            return this;
        }

        /** Two triangles (a,b,c) and (a,c,d); a,b,c,d must be counter-clockwise. */
        public Builder quad(int a, int b, int c, int d) { return triangle(a, b, c).triangle(a, c, d); }

        public int vertexCount() { return vertexCount; }
        public int triangleCount() { return indexCount / 3; }
        public boolean isEmpty() { return indexCount == 0; }

        public MeshData build() {
            return new MeshData(Arrays.copyOf(vertices, vertexCount * FLOATS_PER_VERTEX),
                Arrays.copyOf(indices, indexCount));
        }
    }
}
