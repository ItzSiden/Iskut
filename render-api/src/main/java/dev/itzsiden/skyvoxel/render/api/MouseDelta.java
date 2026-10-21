package dev.itzsiden.skyvoxel.render.api;

/** Relative mouse movement accumulated since the previous read. */
public record MouseDelta(double x, double y) {
    public static final MouseDelta ZERO = new MouseDelta(0.0, 0.0);
}
