package dev.itzsiden.skyvoxel.client.entity;

import dev.itzsiden.skyvoxel.engine.entity.EntityModel;
import dev.itzsiden.skyvoxel.render.api.TextureData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Regression tests for Java Block/Item face orientation, explicit UV rectangles and omitted-UV projection. */
public final class BlockbenchUvMappingSmokeTest {
    private BlockbenchUvMappingSmokeTest() { }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory(Path.of("build").toAbsolutePath().normalize(), "iskut-uv-smoke-");
        try {
            Path textureRoot = temp.resolve("textures");
            Files.createDirectories(textureRoot.resolve("demo"));
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int r = 20 + x * 12, g = 20 + y * 12, b = ((x + y) & 1) == 0 ? 40 : 220;
                image.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
            ImageIO.write(image, "png", textureRoot.resolve("demo/checker.png").toFile());

            Path modelPath = temp.resolve("uv_test.json");
            Files.writeString(modelPath, """
                {
                  "format_version":"26.3",
                  "texture_size":[16,16],
                  "textures":{"main":"demo/checker"},
                  "elements":[
                    {"name":"explicit_uv","from":[0,0,0],"to":[16,16,16],"faces":{
                      "north":{"uv":[2,3,10,12],"texture":"#main"},
                      "south":{"uv":[2,3,10,12],"texture":"#main"},
                      "east":{"uv":[2,3,10,12],"texture":"#main"},
                      "west":{"uv":[2,3,10,12],"texture":"#main"},
                      "up":{"uv":[2,3,10,12],"texture":"#main"},
                      "down":{"uv":[2,3,10,12],"texture":"#main"}
                    }},
                    {"name":"automatic_uv","from":[2,3,4],"to":[10,12,14],"faces":{
                      "north":{"texture":"#main"},"south":{"texture":"#main"},
                      "east":{"texture":"#main"},"west":{"texture":"#main"},
                      "up":{"texture":"#main"},"down":{"texture":"#main"}
                    }}
                  ]
                }
                """);

            EntityModel model = new JavaBlockModelLoader().load(modelPath, textureRoot, null);
            require(model.parts().size() == 2, "Expected both explicit and auto-UV elements.");
            TextureData atlas = model.parts().get(0).material().texture();
            require(atlas != null && atlas.width() == 32 && atlas.height() == 32, "Expected one padded 32x32 atlas.");
            float regionU0 = 2.5f / 32.0f, regionU1 = 17.5f / 32.0f;
            float regionV0 = 2.5f / 32.0f, regionV1 = 17.5f / 32.0f;
            float u0 = map(regionU0, regionU1, 2.0f / 16.0f);
            float u1 = map(regionU0, regionU1, 10.0f / 16.0f);
            float v0 = map(regionV0, regionV1, 3.0f / 16.0f);
            float v1 = map(regionV0, regionV1, 12.0f / 16.0f);

            float[] explicit = model.parts().get(0).mesh().data().vertices();
            // Face batch order: UP, SOUTH, EAST, NORTH, WEST, DOWN.
            assertUv(explicit, 0, u0, v0, "UP top-left");
            assertUv(explicit, 4, u0, v1, "SOUTH bottom-left");
            assertUv(explicit, 8, u1, v1, "EAST reversed-Z bottom-left");
            assertUv(explicit, 12, u0, v1, "NORTH reversed-X bottom-left");
            assertUv(explicit, 16, u0, v1, "WEST bottom-left");
            assertUv(explicit, 20, u0, v1, "DOWN reversed-Z bottom-left");
            assertUv(explicit, 10, u0, v0, "EAST reversed-Z upper-right");
            assertUv(explicit, 14, u1, v0, "NORTH reversed-X upper-right");
            assertUv(explicit, 22, u1, v0, "DOWN reversed-Z upper-right");

            float[] automatic = model.parts().get(1).mesh().data().vertices();
            float autoNorthU0 = map(regionU0, regionU1, 6.0f / 16.0f);
            float autoNorthV0 = map(regionV0, regionV1, 4.0f / 16.0f);
            assertUv(automatic, 12, autoNorthU0, map(regionV0, regionV1, 13.0f / 16.0f), "auto-NORTH low-left");
            assertUv(automatic, 14, map(regionU0, regionU1, 14.0f / 16.0f), autoNorthV0, "auto-NORTH upper-right");
            System.out.println("BLOCKBENCH_UV_MAPPING_PASS | directionBasis=PASS | explicitUV=PASS | defaultUV=PASS");
        } finally {
            if (Files.exists(temp)) {
                try (var paths = Files.walk(temp)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
    }

    private static float map(float start, float end, float normalized) {
        return start + normalized * (end - start);
    }

    private static void assertUv(float[] vertices, int vertexIndex, float expectedU, float expectedV, String label) {
        int offset = vertexIndex * 8;
        require(close(vertices[offset + 3], expectedU), label + " U mismatch: expected " + expectedU + ", got " + vertices[offset + 3]);
        require(close(vertices[offset + 4], expectedV), label + " V mismatch: expected " + expectedV + ", got " + vertices[offset + 4]);
    }

    private static boolean close(float a, float b) { return Math.abs(a - b) < 1.0e-5f; }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
