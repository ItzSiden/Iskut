package dev.itzsiden.skyvoxel.render.opengl;

import dev.itzsiden.skyvoxel.render.api.RenderMesh;

final class OpenGLMesh implements RenderMesh {
    private final OpenGLRenderer owner;
    private final int vertexBuffer;
    private final int indexBuffer;
    private final int indexCount;
    private boolean closed;

    OpenGLMesh(OpenGLRenderer owner, int vertexBuffer, int indexBuffer, int indexCount) {
        this.owner = owner;
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.indexCount = indexCount;
    }

    OpenGLRenderer owner() {
        return owner;
    }

    int vertexBuffer() {
        return vertexBuffer;
    }

    int indexBuffer() {
        return indexBuffer;
    }

    int indexCount() {
        return indexCount;
    }

    boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            owner.destroyMesh(this);
        }
    }

    void disposeFromOwner() {
        if (!closed) {
            closed = true;
            org.lwjgl.opengl.GL15.glDeleteBuffers(vertexBuffer);
            org.lwjgl.opengl.GL15.glDeleteBuffers(indexBuffer);
        }
    }
}
