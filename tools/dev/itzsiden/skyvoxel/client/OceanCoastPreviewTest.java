package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.client.map.GameMap;
import dev.itzsiden.skyvoxel.client.map.MapLoader;
import dev.itzsiden.skyvoxel.engine.camera.Camera;
import dev.itzsiden.skyvoxel.engine.render.SceneRenderer;
import dev.itzsiden.skyvoxel.render.api.RenderFrame;
import dev.itzsiden.skyvoxel.render.api.RenderQuality;
import dev.itzsiden.skyvoxel.render.api.SkyboxSettings;
import dev.itzsiden.skyvoxel.render.software.SoftwareRenderer;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

/** Render proof looking out from the updated east beach into the ocean. */
public final class OceanCoastPreviewTest {
    private OceanCoastPreviewTest() { }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(".").toAbsolutePath().normalize();
        Path out = args.length > 0 ? Path.of(args[0]) : root.resolve(".build-check/ocean-coast-preview.png");
        GameMap map = MapLoader.load(root.resolve("data/maps/main_hub.vmap"));
        int width = 960, height = 600;
        Camera camera = new Camera().setPerspective((float) Math.toRadians(72), 0.05f, 180.0f)
            // Near the east coastline, looking outward over the wet sand and sea surface.
            .setPosition(43.2f, 1.95f, 28.0f)
            .setRotation((float) (Math.PI * 0.5), (float) Math.toRadians(-11));
        SkyboxSettings sky = SkyboxSettings.DAYLIGHT;
        try (SoftwareRenderer renderer = SoftwareRenderer.offscreen(width, height, 1, RenderQuality.SMOOTH);
             SceneRenderer sceneRenderer = new SceneRenderer(renderer).setMaxDrawDistance(128).setFrustumCullingEnabled(false)) {
            float[] vp = camera.viewProjection(width, height);
            renderer.beginFrame(new RenderFrame(vp, sky.horizonRed(), sky.horizonGreen(), sky.horizonBlue(), 55, 100, sky));
            sceneRenderer.render(map.renderScene(), vp, 43.2f, 1.95f, 28.0f);
            renderer.endFrame();
            Files.createDirectories(out.toAbsolutePath().getParent());
            ImageIO.write(renderer.framebuffer(), "png", out.toFile());
        }
        System.out.println("OCEAN_COAST_PREVIEW_PASS | screenshot=" + out);
    }
}
