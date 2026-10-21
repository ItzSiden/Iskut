package dev.itzsiden.skyvoxel.render.api;

/** Service-provider entry point. Backends register themselves using Java ServiceLoader. */
public interface RendererProvider {
    String id();

    Renderer createRenderer();
}
