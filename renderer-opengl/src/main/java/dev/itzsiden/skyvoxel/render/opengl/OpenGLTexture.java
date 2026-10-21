package dev.itzsiden.skyvoxel.render.opengl;

import dev.itzsiden.skyvoxel.render.api.RenderTexture;

final class OpenGLTexture implements RenderTexture {
    private final OpenGLRenderer owner;
    private final int id;
    private boolean closed;

    OpenGLTexture(OpenGLRenderer owner, int id) {
        this.owner = owner;
        this.id = id;
    }

    OpenGLRenderer owner() { return owner; }
    int id() { return id; }
    boolean isClosed() { return closed; }

    @Override
    public void close() {
        if (!closed) owner.destroyTexture(this);
    }

    void disposeFromOwner() {
        if (!closed) {
            closed = true;
            org.lwjgl.opengl.GL11.glDeleteTextures(id);
        }
    }
}
