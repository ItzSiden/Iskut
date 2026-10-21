package dev.itzsiden.skyvoxel.render.api;

import java.util.Objects;

public record WindowConfig(int width, int height, String title) {
    public WindowConfig {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Window dimensions must be positive.");
        }
        title = Objects.requireNonNull(title, "title");
    }
}
