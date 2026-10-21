package dev.itzsiden.skyvoxel.render.api;

/** Cursor position in UI viewport coordinates (same coordinate space as UiDrawList). */
public record MousePosition(double x, double y) {
    public static final MousePosition INVALID = new MousePosition(-1, -1);
}
