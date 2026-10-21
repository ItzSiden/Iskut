package dev.itzsiden.skyvoxel.render.software;

import dev.itzsiden.skyvoxel.render.api.Renderer;
import dev.itzsiden.skyvoxel.render.api.RendererProvider;

public final class SoftwareRendererProvider implements RendererProvider {
    @Override
    public String id() {
        return "software";
    }

    @Override
    public Renderer createRenderer() {
        return new SoftwareRenderer();
    }
}
