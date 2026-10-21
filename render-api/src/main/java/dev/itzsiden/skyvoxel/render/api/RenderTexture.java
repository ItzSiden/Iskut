package dev.itzsiden.skyvoxel.render.api;

/** Opaque renderer-owned texture resource. */
public interface RenderTexture extends AutoCloseable {
    @Override
    void close();
}
