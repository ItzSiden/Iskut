package dev.itzsiden.skyvoxel.render.api;

/** Shading quality hint. Backends that are always "smooth" (GPU) may ignore it. */
public enum RenderQuality {
    /** Flat per-triangle light level, nearest texture sampling. Fastest; the classic "fast graphics" look. */
    FAST,
    /** Per-pixel interpolated vertex colour (smooth lighting / ambient occlusion). */
    SMOOTH;

    public static RenderQuality parse(String value, RenderQuality fallback) {
        if (value == null) return fallback;
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "fast", "low" -> FAST;
            case "smooth", "fancy", "high" -> SMOOTH;
            default -> fallback;
        };
    }
}
