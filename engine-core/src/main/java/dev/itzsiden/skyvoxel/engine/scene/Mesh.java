package dev.itzsiden.skyvoxel.engine.scene;

import dev.itzsiden.skyvoxel.render.api.MeshData;

import java.util.Objects;

/** Immutable, renderer-independent geometry plus a conservative local-space bounding sphere. */
public final class Mesh {
    private final String name;
    private final MeshData data;
    private final float centerX, centerY, centerZ, radius;
    private final float minX, minY, minZ, maxX, maxY, maxZ;

    public Mesh(String name, MeshData data) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("Mesh name cannot be blank.");
        this.data = Objects.requireNonNull(data, "data");
        float[] vertices = data.vertices();
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < vertices.length; i += MeshData.FLOATS_PER_VERTEX) {
            float x = vertices[i], y = vertices[i + 1], z = vertices[i + 2];
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                throw new IllegalArgumentException("Mesh position values must be finite.");
            }
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        centerX = (minX + maxX) * 0.5f;
        centerY = (minY + maxY) * 0.5f;
        centerZ = (minZ + maxZ) * 0.5f;
        float radiusSquared = 0.0f;
        for (int i = 0; i < vertices.length; i += MeshData.FLOATS_PER_VERTEX) {
            float dx = vertices[i] - centerX, dy = vertices[i + 1] - centerY, dz = vertices[i + 2] - centerZ;
            radiusSquared = Math.max(radiusSquared, dx * dx + dy * dy + dz * dz);
        }
        radius = (float) Math.sqrt(radiusSquared);
    }

    public String name() { return name; }
    public MeshData data() { return data; }
    public int triangleCount() { return data.indexCount() / 3; }
    public float minX() { return minX; }
    public float minY() { return minY; }
    public float minZ() { return minZ; }
    public float maxX() { return maxX; }
    public float maxY() { return maxY; }
    public float maxZ() { return maxZ; }
    public float centerX() { return centerX; }
    public float centerY() { return centerY; }
    public float centerZ() { return centerZ; }
    public float boundingRadius() { return radius; }
}
