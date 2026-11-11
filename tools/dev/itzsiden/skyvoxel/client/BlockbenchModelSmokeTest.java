package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.client.entity.EntityModelLoader;
import dev.itzsiden.skyvoxel.client.entity.JavaBlockModelLoader;
import dev.itzsiden.skyvoxel.client.items.ItemVisualRegistry;
import dev.itzsiden.skyvoxel.client.map.GameMap;
import dev.itzsiden.skyvoxel.client.map.MapLoader;
import dev.itzsiden.skyvoxel.engine.items.ItemDefinitionLoader;
import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.engine.scene.Mesh;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Covers Java Block/Item import, PNG atlas input, item visuals, and .vmap MODEL structure placement. */
public final class BlockbenchModelSmokeTest {
    private BlockbenchModelSmokeTest() { }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(".").toAbsolutePath().normalize();
        Path temp = Files.createTempDirectory(root.resolve("build"), "iskut-blockbench-smoke-");
        try {
            Mesh cube = new Mesh("smoke-cube", CuboidMeshFactory.createUnitCuboid());
            EntityModelLoader legacyLoader = new EntityModelLoader(cube);
            ItemVisualRegistry items = new ItemVisualRegistry(root.resolve("data/items/models"),
                temp.resolve("icons"), legacyLoader, ItemDefinitionLoader.loadDirectory(root.resolve("data/items")));
            require(items.modelCount() >= 20, "Blockbench Java item models should load, got " + items.modelCount());
            require(items.iconCount() == items.modelCount(), "each loaded item model should provide an icon");
            require(items.model("training_blade").isPresent(), "training_blade Java JSON model should load");

            // Standard Blockbench Java JSON permits rectangular texture sizes and zero-thickness visual elements.
            Path textureRoot = temp.resolve("textures");
            Files.createDirectories(textureRoot.resolve("demo"));
            BufferedImage pixels = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < pixels.getHeight(); y++) for (int x = 0; x < pixels.getWidth(); x++)
                pixels.setRGB(x, y, ((x + y) % 2 == 0) ? 0xFF3F91A8 : 0xFF1E536C);
            ImageIO.write(pixels, "png", textureRoot.resolve("demo/asymmetric.png").toFile());
            Path model = temp.resolve("thin_element.json");
            Files.writeString(model, """
                {
                  "format_version":"1.20.0",
                  "texture_size":[32,16],
                  "textures":{"main":"demo/asymmetric", "particle":"#main"},
                  "elements":[
                    {"name":"thin_panel","from":[0,0,8],"to":[16,16,8],"faces":{
                      "north":{"uv":[0,0,16,16],"texture":"#main"},
                      "south":{"uv":[16,0,32,16],"texture":"#main"}
                    }},
                    {"name":"solid_cube","from":[0,0,0],"to":[16,16,16],"faces":{
                      "north":{"uv":[0,0,16,16],"texture":"#main"},
                      "south":{"uv":[0,0,16,16],"texture":"#main"},
                      "east":{"uv":[0,0,16,16],"texture":"#main"},
                      "west":{"uv":[0,0,16,16],"texture":"#main"},
                      "up":{"uv":[0,0,16,16],"texture":"#main"},
                      "down":{"uv":[0,0,16,16],"texture":"#main"}
                    }},
                    {"name":"euler_rotated","from":[0,0,0],"to":[8,8,8],
                     "rotation":{"x":30,"y":20,"z":10,"origin":[8,8,8]},"faces":{
                      "north":{"uv":[0,0,16,16],"texture":"#main"},
                      "south":{"uv":[0,0,16,16],"texture":"#main"},
                      "east":{"uv":[0,0,16,16],"texture":"#main"},
                      "west":{"uv":[0,0,16,16],"texture":"#main"},
                      "up":{"uv":[0,0,16,16],"texture":"#main"},
                      "down":{"uv":[0,0,16,16],"texture":"#main"}
                    }}
                  ]
                }
                """);
            var thinModel = new JavaBlockModelLoader().load(model, textureRoot, null);
            require(thinModel.parts().size() == 3, "all imported elements should render");
            require(!thinModel.parts().get(0).collisionEligible(), "zero-thickness visual panel must not become a collider");
            require(thinModel.parts().get(1).collisionEligible(), "solid cuboid should be eligible for structure collision");
            var eulerPart = thinModel.parts().get(2);
            require(close(eulerPart.rotationX(), (float) Math.toRadians(30)), "Blockbench Euler X rotation should be imported");
            require(close(eulerPart.rotationY(), (float) Math.toRadians(20)), "Blockbench Euler Y rotation should be imported");
            require(close(eulerPart.rotationZ(), (float) Math.toRadians(10)), "Blockbench Euler Z rotation should be imported");
            require(!close(eulerPart.offsetX(), -0.25f) || !close(eulerPart.offsetY(), -0.25f)
                    || !close(eulerPart.offsetZ(), -0.25f),
                "Blockbench Euler rotation should rotate the element around its origin");

            Path tempMap = root.resolve("data/maps/__blockbench_model_smoke.vmap");
            Files.writeString(tempMap, """
                SKYVOXEL_MAP 1
                MAP Blockbench_Model_Smoke
                SPAWN 0 1 0 0
                PLACE blockbench ../structures/blockbench_model_example.vstruct 0 1 0 0
                """);
            try {
                GameMap map = MapLoader.load(tempMap);
                var placedModel = map.scene().objects().stream()
                    .filter(object -> object.id().startsWith("blockbench/stone_example/") && object.collidable())
                    .findFirst().orElseThrow(() -> new AssertionError(
                        "MODEL directive should place an imported, collidable Blockbench structure"));
                require(map.renderScene().size() > 0, "placed model structure should be included in the render scene");
                float[] matrix = placedModel.transform().modelMatrix();
                // The example uses 2.0 x 1.25 x 0.75 scale plus X/Y rotation. Matrix-column lengths
                // are the rendered element dimensions and are also what static collision consumes.
                require(close(length(matrix[0], matrix[1], matrix[2]), 2.0f), "MODEL scaleX should be applied to rendering and collision");
                require(close(length(matrix[4], matrix[5], matrix[6]), 1.25f), "MODEL scaleY should be applied to rendering and collision");
                require(close(length(matrix[8], matrix[9], matrix[10]), 0.75f), "MODEL scaleZ should be applied to rendering and collision");
                require(Math.abs(matrix[2]) > 0.1f, "MODEL rotationY should rotate geometry instead of only changing its scale");

                // Backwards compatibility: legacy uniform scale and optional local-Y rotation must remain accepted.
                assertLegacyModelSyntax(root, temp, "MODEL legacy_uniform ../blocks/models/stone_block.json 0 0 0 1.5 true");
                assertLegacyModelSyntax(root, temp, "MODEL legacy_yaw ../blocks/models/stone_block.json 0 0 0 1.5 true 25");
            } finally {
                Files.deleteIfExists(tempMap);
            }
            System.out.println("BLOCKBENCH_MODEL_PASS | itemModels=" + items.modelCount()
                + " | icons=" + items.iconCount() + " | thinVisualNonSolid=PASS | structureModelPlacement=PASS");
        } finally {
            if (Files.exists(temp)) {
                try (var paths = Files.walk(temp)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void assertLegacyModelSyntax(Path root, Path temp, String modelDirective) throws Exception {
        Path structure = root.resolve("data/structures/__blockbench_legacy_smoke.vstruct");
        Path mapPath = root.resolve("data/maps/__blockbench_legacy_smoke.vmap");
        Files.writeString(structure, "SKYVOXEL_STRUCTURE 1\nNAME Legacy_Model_Smoke\n" + modelDirective + "\n");
        Files.writeString(mapPath, "SKYVOXEL_MAP 1\nMAP Legacy_Model_Smoke\nSPAWN 0 1 0 0\n"
            + "PLACE legacy ../structures/__blockbench_legacy_smoke.vstruct 0 1 0 0\n");
        try {
            GameMap loaded = MapLoader.load(mapPath);
            require(loaded.scene().objects().stream().anyMatch(o -> o.id().startsWith("legacy/")),
                "Legacy MODEL syntax should continue to load: " + modelDirective);
        } finally {
            Files.deleteIfExists(mapPath);
            Files.deleteIfExists(structure);
        }
    }

    private static float length(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static boolean close(float a, float b) { return Math.abs(a - b) < 1.0e-3f; }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
