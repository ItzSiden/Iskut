package dev.itzsiden.skyvoxel.render.opengl;

import dev.itzsiden.skyvoxel.render.api.Renderer;
import dev.itzsiden.skyvoxel.render.api.RendererProvider;

public final class OpenGLRendererProvider implements RendererProvider {
    @Override
    public String id() {
        return "opengl";
    }

    @Override
    public Renderer createRenderer() {
        return new OpenGLRenderer();
    }
}
