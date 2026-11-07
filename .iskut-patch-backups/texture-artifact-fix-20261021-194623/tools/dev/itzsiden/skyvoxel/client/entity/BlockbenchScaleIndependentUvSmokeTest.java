package dev.itzsiden.skyvoxel.client.entity;

import dev.itzsiden.skyvoxel.engine.entity.EntityModel;
import dev.itzsiden.skyvoxel.client.items.ItemIconGenerator;
import dev.itzsiden.skyvoxel.render.api.MeshData;
import dev.itzsiden.skyvoxel.render.api.TextureData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Regression coverage for high-resolution textures, large UV spans and older Iskut-authored assets. */
public final class BlockbenchScaleIndependentUvSmokeTest {
    private BlockbenchScaleIndependentUvSmokeTest() { }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory(Path.of("build").toAbsolutePath().normalize(), "iskut-scale-uv-");
        try {
            Path textureRoot = temp.resolve("textures");
            Files.createDirectories(textureRoot.resolve("demo"));
            writeTexture(textureRoot.resolve("demo/large.png"), 48);
            writeTexture(textureRoot.resolve("demo/legacy.png"), 8);

            Path standardPath = temp.resolve("standard-large-texture.json");
            Files.writeString(standardPath, """
                {
                  "format_version":"26.3",
                  "credit":"Made with Blockbench",
                  "texture_size":[48,48],
                  "textures":{"main":"demo/large"},
                  "elements":[
                    {"name":"explicit_uv","from":[0,0,0],"to":[48,48,48],"faces":{
                      "north":{"uv":[0,0,16,16],"texture":"#main"},
                      "south":{"uv":[0,0,16,16],"texture":"#main"},
                      "east":{"uv":[0,0,16,16],"texture":"#main"},
                      "west":{"uv":[0,0,16,16],"texture":"#main"},
                      "up":{"uv":[0,0,16,16],"texture":"#main"},
                      "down":{"uv":[0,0,16,16],"texture":"#main"}
                    }},
                    {"name":"repeat_uv","from":[0,0,0],"to":[48,48,48],"faces":{
                      "north":{"uv":[0,0,48,48],"texture":"#main"},
                      "south":{"uv":[0,0,48,48],"texture":"#main"},
                      "east":{"uv":[0,0,48,48],"texture":"#main"},
                      "west":{"uv":[0,0,48,48],"texture":"#main"},
                      "up":{"uv":[0,0,48,48],"texture":"#main"},
                      "down":{"uv":[0,0,48,48],"texture":"#main"}
                    }},
                    {"name":"automatic_uv","from":[2,3,4],"to":[10,12,14],"faces":{
                      "north":{"texture":"#main"},"south":{"texture":"#main"},
                      "east":{"texture":"#main"},"west":{"texture":"#main"},
                      "up":{"texture":"#main"},"down":{"texture":"#main"}
                    }}
                  ]
                }
                """);

            EntityModel standard = new JavaBlockModelLoader().load(standardPath, textureRoot, null);
            require(standard.parts().size() == 3, "Expected explicit, repeated and automatic UV elements.");
            TextureData largeAtlas = standard.parts().get(0).material().texture();
            VModelTextureAtlas.Region largeRegion = regionForSingleTexture(largeAtlas, 48);
            float[] fullTextureFace = standard.parts().get(0).mesh().data().vertices();
            assertNear(fullTextureFace[3], largeRegion.u0(), "48x48 texture uses full U extent for Java UV 0..16");
            assertNear(fullTextureFace[4], largeRegion.v0(), "48x48 texture uses full V extent for Java UV 0..16");
            assertNear(fullTextureFace[2 * MeshData.FLOATS_PER_VERTEX + 3], largeRegion.u1(),
                "EAST face uses the full source texture U extent");

            MeshData repeated = standard.parts().get(1).mesh().data();
            require(repeated.vertexCount() == 216,
                "UV 0..48 should split six faces into 3x3 repeat tiles (216 vertices), got " + repeated.vertexCount());
            assertAllUvsInsideRegion(repeated, largeRegion, "repeat UV must stay in its own atlas region");

            float[] automatic = standard.parts().get(2).mesh().data().vertices();
            float autoNorthU0 = map(largeRegion.u0(), largeRegion.u1(), 6.0f / 16.0f);
            float autoNorthV0 = map(largeRegion.v0(), largeRegion.v1(), 4.0f / 16.0f);
            assertNear(automatic[12 * MeshData.FLOATS_PER_VERTEX + 3], autoNorthU0, "default NORTH UV projection U");
            assertNear(automatic[14 * MeshData.FLOATS_PER_VERTEX + 4], autoNorthV0, "default NORTH UV projection V");
            var thumbnail = ItemIconGenerator.render(standard);
            require(thumbnail.getWidth() == ItemIconGenerator.ICON_SIZE && thumbnail.getHeight() == ItemIconGenerator.ICON_SIZE,
                "Thumbnail generation must support subdivided UV tiles.");

            Path legacyPath = temp.resolve("legacy-iskut.json");
            Files.writeString(legacyPath, """
                {
                  "format_version":"26.3",
                  "credit":"Converted to Blockbench Minecraft Java Block/Item JSON from the original Iskut model; open/save with Blockbench.",
                  "texture_size":[8,8],
                  "textures":{"main":"demo/legacy"},
                  "elements":[{"name":"legacy","from":[0,0,0],"to":[8,8,8],"faces":{
                    "north":{"uv":[0,0,8,8],"texture":"#main"},
                    "south":{"uv":[0,0,8,8],"texture":"#main"},
                    "east":{"uv":[0,0,8,8],"texture":"#main"},
                    "west":{"uv":[0,0,8,8],"texture":"#main"},
                    "up":{"uv":[0,0,8,8],"texture":"#main"},
                    "down":{"uv":[0,0,8,8],"texture":"#main"}
                  }}]
                }
                """);
            EntityModel legacy = new JavaBlockModelLoader().load(legacyPath, textureRoot, null);
            MeshData legacyMesh = legacy.parts().get(0).mesh().data();
            require(legacyMesh.vertexCount() == 24, "Legacy 8x8 texture-pixel UVs should not be needlessly subdivided.");
            VModelTextureAtlas.Region legacyRegion = regionForSingleTexture(legacy.parts().get(0).material().texture(), 8);
            assertAllUvsInsideRegion(legacyMesh, legacyRegion, "legacy UV compatibility region");
            assertNear(minU(legacyMesh), legacyRegion.u0(), "legacy UV min U");
            assertNear(maxU(legacyMesh), legacyRegion.u1(), "legacy UV max U");
            var legacyThumbnail = ItemIconGenerator.render(legacy);
            require(legacyThumbnail.getWidth() == ItemIconGenerator.ICON_SIZE, "Legacy thumbnails remain supported.");

            System.out.println("BLOCKBENCH_SCALE_INDEPENDENT_UV_PASS | highResUV=PASS | largeUVTiling=PASS | defaultUV=PASS | thumbnails=PASS | legacyAssets=PASS");
        } finally {
            if (Files.exists(temp)) {
                try (var paths = Files.walk(temp)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void writeTexture(Path path, int size) throws Exception {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int quadrant = (x < size / 2 ? 0 : 1) + (y < size / 2 ? 0 : 2);
            int[] colors = {0xFFFF2020, 0xFF20FF20, 0xFF2020FF, 0xFFFFFF20};
            int c = colors[quadrant];
            if ((x + y) % 3 == 0) c = 0xFF202020;
            image.setRGB(x, y, c);
        }
        ImageIO.write(image, "png", path.toFile());
    }

    private static VModelTextureAtlas.Region regionForSingleTexture(TextureData atlas, int sourceSize) {
        int atlasWidth = atlas.width(), atlasHeight = atlas.height();
        int tx = 2, ty = 2;
        return new VModelTextureAtlas.Region((tx + 0.5f) / atlasWidth, (ty + 0.5f) / atlasHeight,
            (tx + sourceSize - 0.5f) / atlasWidth, (ty + sourceSize - 0.5f) / atlasHeight,
            tx, ty, sourceSize, sourceSize);
    }

    private static void assertAllUvsInsideRegion(MeshData mesh, VModelTextureAtlas.Region region, String label) {
        float[] vertices = mesh.vertices();
        for (int i = 0; i < mesh.vertexCount(); i++) {
            float u = vertices[i * MeshData.FLOATS_PER_VERTEX + 3];
            float v = vertices[i * MeshData.FLOATS_PER_VERTEX + 4];
            require(u >= region.u0() - 1.0e-5f && u <= region.u1() + 1.0e-5f,
                label + ": U escaped region: " + u);
            require(v >= region.v0() - 1.0e-5f && v <= region.v1() + 1.0e-5f,
                label + ": V escaped region: " + v);
        }
    }

    private static float minU(MeshData mesh) {
        float min = Float.POSITIVE_INFINITY; float[] vertices = mesh.vertices();
        for (int i = 0; i < mesh.vertexCount(); i++) min = Math.min(min, vertices[i * MeshData.FLOATS_PER_VERTEX + 3]);
        return min;
    }
    private static float maxU(MeshData mesh) {
        float max = Float.NEGATIVE_INFINITY; float[] vertices = mesh.vertices();
        for (int i = 0; i < mesh.vertexCount(); i++) max = Math.max(max, vertices[i * MeshData.FLOATS_PER_VERTEX + 3]);
        return max;
    }
    private static float map(float start, float end, float normalized) { return start + normalized * (end - start); }
    private static void assertNear(float actual, float expected, String label) {
        require(Math.abs(actual - expected) < 1.0e-5f, label + ": expected " + expected + ", got " + actual);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
