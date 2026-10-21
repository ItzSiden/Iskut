package dev.itzsiden.skyvoxel.ui;

import java.util.Objects;

/** Anchored placement measured in 1280x720 design units. */
public record UiPlacement(UiWidget widget, UiAnchor anchor, float offsetX, float offsetY,
                          float width, float height) {
    public UiPlacement {
        Objects.requireNonNull(widget, "widget");
        Objects.requireNonNull(anchor, "anchor");
        if (width < 0 || height < 0) throw new IllegalArgumentException("UI dimensions cannot be negative.");
    }
}
