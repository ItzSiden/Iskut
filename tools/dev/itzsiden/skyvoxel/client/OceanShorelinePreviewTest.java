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

/** Render proof looking from just over the shallows back toward the island edge. */
public final class OceanShorelinePreviewTest {
    private OceanShorelinePreviewTest() { }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(".").toAbsolutePath().normalize();
        Path out = args.length > 0 ? Path.of(args[0]) : root.resolve(".build-check/ocean-shoreline-preview.png");
        GameMap map = MapLoader.load(root.resolve("data/maps/main_hub.vmap"));
        int width=960, height=600;
        float x=64.0f, y=1.85f, z=28.0f;
        Camera camera = new Camera().setPerspective((float)Math.toRadians(72), .05f, 180)
            .setPosition(x,y,z).setRotation((float)-Math.PI/2,(float)Math.toRadians(-5.0));
        SkyboxSettings sky = SkyboxSettings.DAYLIGHT;
        try (SoftwareRenderer renderer=SoftwareRenderer.offscreen(width,height,1,RenderQuality.SMOOTH);
             SceneRenderer sr=new SceneRenderer(renderer).setMaxDrawDistance(128).setFrustumCullingEnabled(false)) {
            float[] vp=camera.viewProjection(width,height);
            renderer.beginFrame(new RenderFrame(vp,sky.horizonRed(),sky.horizonGreen(),sky.horizonBlue(),55,100,sky));
            sr.render(map.renderScene(),vp,x,y,z);
            renderer.endFrame();
            Files.createDirectories(out.toAbsolutePath().getParent());
            ImageIO.write(renderer.framebuffer(),"png",out.toFile());
        }
        System.out.println("OCEAN_SHORELINE_PREVIEW_PASS | screenshot="+out);
    }
}
