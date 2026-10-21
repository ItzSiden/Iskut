package dev.itzsiden.skyvoxel.render.software;

import dev.itzsiden.skyvoxel.render.api.MeshData;
import dev.itzsiden.skyvoxel.render.api.RenderMesh;

final class SoftwareMesh implements RenderMesh {
    private final SoftwareRenderer owner;
    /** Vertex data in MeshData layout (8 floats per vertex). */
    final float[] vertices;
    final int[] indices;
    final int vertexCount;
    private boolean closed;

    SoftwareMesh(SoftwareRenderer owner, float[] vertices, int[] indices) {
        this.owner = owner;
        this.vertices = vertices;
        this.indices = indices;
        this.vertexCount = vertices.length / MeshData.FLOATS_PER_VERTEX;
    }

    boolean belongsTo(SoftwareRenderer renderer) { return owner == renderer; }
    boolean isClosed() { return closed; }

    @Override
    public void close() { closed = true; }
}
