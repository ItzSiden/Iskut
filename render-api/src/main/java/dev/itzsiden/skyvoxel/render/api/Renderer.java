package dev.itzsiden.skyvoxel.render.api;

import java.awt.image.BufferedImage;

/**
 * Rendering boundary for the game. The API intentionally exposes no OpenGL/Vulkan types,
 * allowing any backend (CPU rasterizer, OpenGL, a future Vulkan renderer) to implement the same
 * contract. Frame protocol: beginFrame, draw*, drawUi, endFrame.
 */
public interface Renderer extends AutoCloseable {
    RenderWindow createWindow(WindowConfig config);

    RenderMesh createMesh(MeshData meshData);

    /** Uploads a texture/atlas. The returned resource must be closed before the renderer. */
    RenderTexture createTexture(TextureData textureData);

    void beginFrame(RenderFrame frame);

    /** Draws a mesh; {@code texture} may be null for vertex-colour-only geometry. */
    void draw(RenderMesh mesh, RenderTexture texture, float[] modelMatrix,
              float tintRed, float tintGreen, float tintBlue);

    /** Untextured convenience overload. */
    default void draw(RenderMesh mesh, float[] modelMatrix, float tintRed, float tintGreen, float tintBlue) {
        draw(mesh, null, modelMatrix, tintRed, tintGreen, tintBlue);
    }

    /** Draw a backend-neutral 2D overlay after the 3D scene and before endFrame. */
    void drawUi(UiDrawList drawList);

    void endFrame();

    /** Captures the frame currently being drawn, including its UI overlay. */
    BufferedImage captureScreenshot();

    /** Shading quality hint; GPU backends may ignore it. */
    default RenderQuality quality() { return RenderQuality.SMOOTH; }

    default void setQuality(RenderQuality quality) { }

    String description();

    @Override
    void close();
}
