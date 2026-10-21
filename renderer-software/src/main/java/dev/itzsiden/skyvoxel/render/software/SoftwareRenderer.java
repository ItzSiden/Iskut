package dev.itzsiden.skyvoxel.render.software;

import dev.itzsiden.skyvoxel.render.api.MeshData;
import dev.itzsiden.skyvoxel.render.api.RenderFrame;
import dev.itzsiden.skyvoxel.render.api.RenderMesh;
import dev.itzsiden.skyvoxel.render.api.RenderQuality;
import dev.itzsiden.skyvoxel.render.api.RenderTexture;
import dev.itzsiden.skyvoxel.render.api.Renderer;
import dev.itzsiden.skyvoxel.render.api.RenderWindow;
import dev.itzsiden.skyvoxel.render.api.TextureData;
import dev.itzsiden.skyvoxel.render.api.UiDrawList;
import dev.itzsiden.skyvoxel.render.api.WindowConfig;
import dev.itzsiden.skyvoxel.render.api.UiRasterizer;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure-Java CPU renderer for low-end PCs. All heavy work lives in {@link SoftwareRasterCore};
 * this class adapts it to the backend-neutral {@link Renderer} API and owns the window.
 *
 * <p>Tuning (system property, or environment variable in brackets):
 * <ul>
 *   <li>{@code skyvoxel.renderScale} [SKYVOXEL_RENDER_SCALE]: 0.25..1.0, default 0.5</li>
 *   <li>{@code skyvoxel.threads} [SKYVOXEL_THREADS]: raster threads, default min(4, cores)</li>
 *   <li>{@code skyvoxel.quality} [SKYVOXEL_QUALITY]: smooth (default) | fast</li>
 * </ul>
 */
public final class SoftwareRenderer implements Renderer {
    private final Set<SoftwareMesh> meshes = new HashSet<>();
    private final List<SoftwareTexture> textures = new ArrayList<>();

    private SoftwareWindow window;
    private BufferedImage framebuffer;
    private SoftwareRasterCore core;
    private float renderScale = 0.50f;
    private UiDrawList uiDrawList = UiDrawList.EMPTY;
    private boolean offscreen;
    private boolean closed;

    /**
     * Window-less renderer that draws into an in-memory image: for screenshots, automated tests,
     * benchmarks and dedicated-server tooling. Use {@link #framebuffer()} after endFrame().
     */
    public static SoftwareRenderer offscreen(int width, int height, int threads, RenderQuality quality) {
        SoftwareRenderer renderer = new SoftwareRenderer();
        renderer.offscreen = true;
        renderer.framebuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) renderer.framebuffer.getRaster().getDataBuffer()).getData();
        renderer.core = new SoftwareRasterCore(pixels, width, height, Math.max(1, threads));
        renderer.core.setQuality(quality);
        return renderer;
    }

    /** The low-resolution frame that was last rendered (valid after endFrame). */
    public BufferedImage framebuffer() { return framebuffer; }

    @Override
    public RenderWindow createWindow(WindowConfig config) {
        ensureOpen();
        if (offscreen) throw new IllegalStateException("An offscreen renderer has no window.");
        if (window != null) throw new IllegalStateException("This renderer already owns a window.");
        renderScale = configuredRenderScale();
        int width = Math.max(192, Math.min(config.width(), Math.round(config.width() * renderScale)));
        int height = Math.max(108, Math.min(config.height(), Math.round(config.height() * renderScale)));
        window = new SoftwareWindow(config, width, height);
        framebuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] pixels = ((DataBufferInt) framebuffer.getRaster().getDataBuffer()).getData();
        core = new SoftwareRasterCore(pixels, width, height, configuredThreads());
        core.setQuality(RenderQuality.parse(configured("skyvoxel.quality", "SKYVOXEL_QUALITY"), RenderQuality.SMOOTH));
        return window;
    }

    @Override
    public RenderMesh createMesh(MeshData meshData) {
        ensureOpen();
        SoftwareMesh mesh = new SoftwareMesh(this, meshData.vertices(), meshData.indices());
        meshes.add(mesh);
        return mesh;
    }

    @Override
    public RenderTexture createTexture(TextureData textureData) {
        ensureOpen();
        SoftwareTexture texture = new SoftwareTexture(this, textures.size(), textureData);
        textures.add(texture);
        return texture;
    }

    void releaseTexture(SoftwareTexture texture) {
        // Slots stay reserved (they index the frame table); the pixel data is simply dropped with the object.
    }

    @Override
    public void beginFrame(RenderFrame frame) {
        ensureReady();
        core.begin(frame.viewProjectionMatrix(), frame.skybox().horizonRed(), frame.skybox().horizonGreen(), frame.skybox().horizonBlue(),
            frame.fogStart(), frame.fogEnd(), frame.skybox());
    }

    @Override
    public void draw(RenderMesh renderMesh, RenderTexture renderTexture, float[] modelMatrix,
                     float tintRed, float tintGreen, float tintBlue) {
        ensureReady();
        if (!(renderMesh instanceof SoftwareMesh mesh) || !mesh.belongsTo(this) || mesh.isClosed()) {
            throw new IllegalArgumentException("Mesh belongs to another renderer or is already closed.");
        }
        SoftwareTexture texture = null;
        if (renderTexture != null) {
            if (!(renderTexture instanceof SoftwareTexture t) || !t.belongsTo(this) || t.isClosed()) {
                throw new IllegalArgumentException("Texture belongs to another renderer or is already closed.");
            }
            texture = t;
        }
        if (modelMatrix == null || modelMatrix.length != 16) {
            throw new IllegalArgumentException("Model matrix must contain 16 floats.");
        }
        core.draw(mesh, texture, modelMatrix, tintRed, tintGreen, tintBlue);
    }

    @Override
    public void drawUi(UiDrawList drawList) {
        ensureReady();
        uiDrawList = drawList == null ? UiDrawList.EMPTY : drawList;
    }

    @Override
    public void endFrame() {
        ensureReady();
        core.flush();
        if (window != null) window.present(framebuffer, uiDrawList);
        uiDrawList = UiDrawList.EMPTY;
    }

    @Override
    public BufferedImage captureScreenshot() {
        ensureReady();
        core.flush();
        int width = window == null ? framebuffer.getWidth() : window.uiWidth();
        int height = window == null ? framebuffer.getHeight() : window.uiHeight();
        BufferedImage screenshot = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = screenshot.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(framebuffer, 0, 0, width, height, null);
            if (!uiDrawList.isEmpty()) {
                graphics.drawImage(UiRasterizer.rasterize(uiDrawList, width, height), 0, 0, null);
            }
        } finally {
            graphics.dispose();
        }
        return screenshot;
    }

    @Override
    public RenderQuality quality() { return core == null ? RenderQuality.FAST : core.quality(); }

    @Override
    public void setQuality(RenderQuality quality) { if (core != null) core.setQuality(quality); }

    /** Triangles that survived culling and were rasterized last frame (for HUD/benchmarks). */
    public int lastFrameTrianglesRasterized() { return core == null ? 0 : core.trianglesQueued(); }

    @Override
    public String description() {
        return String.format(java.util.Locale.ROOT,
            "Software rasterizer (%d thread%s, %s shading, %.0f%% scale, sky gradient)",
            core == null ? 1 : core.threadCount(), core == null || core.threadCount() == 1 ? "" : "s",
            quality() == RenderQuality.FAST ? "fast" : "smooth", renderScale * 100.0f);
    }

    private static String configured(String property, String env) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) value = System.getenv(env);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static float configuredRenderScale() {
        String configured = configured("skyvoxel.renderScale", "SKYVOXEL_RENDER_SCALE");
        if (configured == null) return 0.50f;
        try {
            float scale = Float.parseFloat(configured);
            if (!Float.isFinite(scale)) return 0.50f;
            return Math.max(0.25f, Math.min(1.0f, scale));
        } catch (NumberFormatException ignored) {
            System.err.println("[Iskut] Invalid render scale '" + configured + "'; using 0.50.");
            return 0.50f;
        }
    }

    private static int configuredThreads() {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        String configured = configured("skyvoxel.threads", "SKYVOXEL_THREADS");
        if (configured != null) {
            try { return Math.max(1, Math.min(16, Integer.parseInt(configured))); }
            catch (NumberFormatException ignored) { System.err.println("[Iskut] Invalid thread count; using auto."); }
        }
        return Math.min(4, cores);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (SoftwareMesh mesh : meshes) mesh.close();
        meshes.clear();
        for (SoftwareTexture texture : textures) texture.close();
        textures.clear();
        if (core != null) { core.close(); core = null; }
        if (window != null) { window.closeWindow(); window = null; }
        framebuffer = null;
    }

    private void ensureOpen() { if (closed) throw new IllegalStateException("Renderer is closed."); }

    private void ensureReady() {
        ensureOpen();
        if ((window == null && !offscreen) || framebuffer == null || core == null) {
            throw new IllegalStateException("Create the window first.");
        }
    }
}
