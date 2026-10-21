package dev.itzsiden.skyvoxel.render.api;

/** Opaque renderer-owned mesh resource. No OpenGL/Vulkan handles are exposed. */
public interface RenderMesh extends AutoCloseable {
    @Override
    void close();
}
