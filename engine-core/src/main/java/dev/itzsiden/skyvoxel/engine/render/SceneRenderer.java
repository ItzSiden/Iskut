package dev.itzsiden.skyvoxel.engine.render;

import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.engine.scene.Mesh;
import dev.itzsiden.skyvoxel.engine.scene.RenderObject;
import dev.itzsiden.skyvoxel.engine.scene.Scene;
import dev.itzsiden.skyvoxel.render.api.RenderMesh;
import dev.itzsiden.skyvoxel.render.api.RenderTexture;
import dev.itzsiden.skyvoxel.render.api.Renderer;
import dev.itzsiden.skyvoxel.render.api.TextureData;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bridges engine scene data to any renderer backend. Per frame it performs sphere frustum culling,
 * optional max-distance culling and optional front-to-back sorting (which lets a CPU rasterizer
 * reject hidden pixels early). Meshes and textures are uploaded lazily and cached.
 */
public final class SceneRenderer implements AutoCloseable {
    private final Renderer renderer;
    private final Map<Mesh, RenderMesh> uploadedMeshes = new IdentityHashMap<>();
    private final Map<TextureData, RenderTexture> uploadedTextures = new IdentityHashMap<>();
    private final Frustum frustum = new Frustum();
    private boolean frustumCullingEnabled = true;
    private boolean frontToBackSorting = true;
    private float maxDrawDistance = Float.POSITIVE_INFINITY;
    private boolean closed;

    // Reused per-frame scratch (no allocation in steady state).
    private RenderObject[] visible = new RenderObject[256];
    private long[] sortKeys = new long[256];

    public SceneRenderer(Renderer renderer) { this.renderer = Objects.requireNonNull(renderer, "renderer"); }

    public SceneRenderer setFrustumCullingEnabled(boolean enabled) {
        ensureOpen(); this.frustumCullingEnabled = enabled; return this;
    }
    public boolean frustumCullingEnabled() { return frustumCullingEnabled; }

    /** Sort opaque draws near-to-far (only when the camera position is supplied to render()). */
    public SceneRenderer setFrontToBackSorting(boolean enabled) { this.frontToBackSorting = enabled; return this; }

    /** Objects whose bounding sphere lies entirely beyond this distance are not drawn (needs camera position). */
    public SceneRenderer setMaxDrawDistance(float distance) {
        this.maxDrawDistance = distance > 0.0f ? distance : Float.POSITIVE_INFINITY;
        return this;
    }

    public SceneRenderStats render(Scene scene, float[] viewProjection) {
        return render(scene, viewProjection, Float.NaN, Float.NaN, Float.NaN);
    }

    public SceneRenderStats render(Scene scene, float[] viewProjection, float camX, float camY, float camZ) {
        ensureOpen(); Objects.requireNonNull(scene, "scene");
        if (viewProjection == null || viewProjection.length != 16) {
            throw new IllegalArgumentException("View-projection matrix must have 16 floats.");
        }
        if (frustumCullingEnabled) frustum.update(viewProjection);
        final boolean haveCamera = !Float.isNaN(camX);
        final float maxSq = maxDrawDistance * maxDrawDistance;

        int total = 0, hidden = 0, culled = 0, count = 0;
        for (RenderObject object : scene.objects()) {
            total++;
            if (!object.visible()) { hidden++; continue; }
            Mesh mesh = object.mesh();
            float[] model = object.transform().modelMatrix();
            float worldX = model[0] * mesh.centerX() + model[4] * mesh.centerY() + model[8] * mesh.centerZ() + model[12];
            float worldY = model[1] * mesh.centerX() + model[5] * mesh.centerY() + model[9] * mesh.centerZ() + model[13];
            float worldZ = model[2] * mesh.centerX() + model[6] * mesh.centerY() + model[10] * mesh.centerZ() + model[14];
            float scale = Math.max(length(model[0], model[1], model[2]),
                Math.max(length(model[4], model[5], model[6]), length(model[8], model[9], model[10])));
            float radius = mesh.boundingRadius() * scale;
            float distSq = 0.0f;
            if (haveCamera) {
                float dx = worldX - camX, dy = worldY - camY, dz = worldZ - camZ;
                distSq = dx * dx + dy * dy + dz * dz;
                if (maxDrawDistance != Float.POSITIVE_INFINITY) {
                    float reach = maxDrawDistance + radius;
                    if (distSq > reach * reach) { culled++; continue; }
                }
            }
            if (frustumCullingEnabled && !frustum.containsSphere(worldX, worldY, worldZ, radius)) { culled++; continue; }

            if (count == visible.length) {
                visible = Arrays.copyOf(visible, count * 2);
                sortKeys = Arrays.copyOf(sortKeys, count * 2);
            }
            visible[count] = object;
            // Positive floats order like their bit patterns, so (distSq bits << 32 | index) sorts by distance.
            sortKeys[count] = ((long) Float.floatToRawIntBits(distSq) << 32) | count;
            count++;
        }
        if (haveCamera && frontToBackSorting && count > 1) Arrays.sort(sortKeys, 0, count);

        int draws = 0, triangles = 0;
        for (int i = 0; i < count; i++) {
            RenderObject object = visible[haveCamera && frontToBackSorting ? (int) (sortKeys[i] & 0xFFFFFFFFL) : i];
            Mesh mesh = object.mesh();
            RenderMesh backendMesh = uploadedMeshes.get(mesh);
            if (backendMesh == null) {
                backendMesh = renderer.createMesh(mesh.data());
                uploadedMeshes.put(mesh, backendMesh);
            }
            Material material = object.material();
            RenderTexture backendTexture = null;
            if (material.texture() != null) {
                backendTexture = uploadedTextures.get(material.texture());
                if (backendTexture == null) {
                    backendTexture = renderer.createTexture(material.texture());
                    uploadedTextures.put(material.texture(), backendTexture);
                }
            }
            renderer.draw(backendMesh, backendTexture, object.transform().modelMatrix(),
                material.red(), material.green(), material.blue());
            draws++; triangles += mesh.triangleCount();
        }
        Arrays.fill(visible, 0, count, null);
        return new SceneRenderStats(total, draws, culled, hidden, draws, triangles);
    }

    private static float length(float x, float y, float z) { return (float) Math.sqrt(x * x + y * y + z * z); }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (RenderMesh mesh : uploadedMeshes.values()) mesh.close();
        uploadedMeshes.clear();
        for (RenderTexture texture : uploadedTextures.values()) texture.close();
        uploadedTextures.clear();
    }

    private void ensureOpen() { if (closed) throw new IllegalStateException("SceneRenderer is closed."); }
}
