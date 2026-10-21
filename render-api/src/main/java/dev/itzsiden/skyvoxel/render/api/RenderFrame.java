package dev.itzsiden.skyvoxel.render.api;

/** Per-frame camera matrix, fog colour/range and backend-neutral skybox settings. */
public final class RenderFrame {
    private final float[] viewProjectionMatrix;
    private final float clearRed, clearGreen, clearBlue;
    private final float fogStart, fogEnd;
    private final SkyboxSettings skybox;

    public RenderFrame(float[] viewProjectionMatrix, float clearRed, float clearGreen, float clearBlue) {
        this(viewProjectionMatrix, clearRed, clearGreen, clearBlue, 0.0f, 0.0f,
            SkyboxSettings.fromHorizon(clearRed, clearGreen, clearBlue));
    }

    public RenderFrame(float[] viewProjectionMatrix, float clearRed, float clearGreen, float clearBlue,
                       float fogStart, float fogEnd) {
        this(viewProjectionMatrix, clearRed, clearGreen, clearBlue, fogStart, fogEnd,
            SkyboxSettings.fromHorizon(clearRed, clearGreen, clearBlue));
    }

    public RenderFrame(float[] viewProjectionMatrix, float clearRed, float clearGreen, float clearBlue,
                       float fogStart, float fogEnd, SkyboxSettings skybox) {
        if (viewProjectionMatrix == null || viewProjectionMatrix.length != 16) {
            throw new IllegalArgumentException("View-projection matrix must contain 16 floats.");
        }
        this.viewProjectionMatrix = viewProjectionMatrix.clone();
        this.clearRed = channel(clearRed); this.clearGreen = channel(clearGreen); this.clearBlue = channel(clearBlue);
        if (!Float.isFinite(fogStart) || !Float.isFinite(fogEnd) || fogStart < 0 || fogEnd < 0) {
            throw new IllegalArgumentException("Fog distances must be finite and non-negative.");
        }
        this.fogStart = fogStart; this.fogEnd = fogEnd;
        this.skybox = java.util.Objects.requireNonNull(skybox, "skybox");
    }

    public float[] viewProjectionMatrix() { return viewProjectionMatrix.clone(); }
    /** Compatibility clear colour; also used as a fallback by older renderer extensions. */
    public float clearRed() { return clearRed; }
    public float clearGreen() { return clearGreen; }
    public float clearBlue() { return clearBlue; }
    public float fogStart() { return fogStart; }
    public float fogEnd() { return fogEnd; }
    public boolean fogEnabled() { return fogEnd > fogStart && fogEnd > 0.0f; }
    public SkyboxSettings skybox() { return skybox; }

    private static float channel(float value) {
        if (!Float.isFinite(value) || value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException("Clear colours must be finite and within [0,1].");
        }
        return value;
    }
}
