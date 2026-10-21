package dev.itzsiden.skyvoxel.client.map;

import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.render.api.TextureData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads shared pixel-art world materials once per map. Every object using a material shares its texture. */
final class WorldTextureLibrary {
    private static final List<String> TEXTURE_IDS = List.of(
        "stone",
        "stone_dark",
        "stone_light",
        "slate",
        "dirt",
        "cobble",
        "grass",
        "moss",
        "wood",
        "wood_light",
        "brick",
        "roof_blue",
        "roof_red",
        "gold",
        "crystal",
        "cyan",
        "violet",
        "water",
        "leaves",
        "sand",
        "wet_sand",
        "shore_transition",
        "shallow_water",
        "foam",
        "white",
        "portal",
        "white_wool",
        "yellow_wool",
        "red_wool",
        "cyan_wool"
    );

    private WorldTextureLibrary() { }

    static Map<String, Material> load(Path textureRoot) throws IOException {
        Map<String, Material> result = new LinkedHashMap<>();
        for (String textureId : TEXTURE_IDS) {
            result.put(textureId, loadMaterial(textureRoot, textureId));
        }
        return Map.copyOf(result);
    }

    private static Material loadMaterial(Path textureRoot, String textureId) throws IOException {
        Path file = textureRoot.resolve(textureId + ".png");
        if (!Files.isRegularFile(file)) {
            throw new IOException("Missing Iskut world texture '" + textureId + "': " + file
                + " (install the complete texture/viewmodel patch with data/textures/world assets).");
        }

        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) throw new IOException("Unsupported world texture image: " + file);

        try {
            TextureData texture = TextureData.fromImage("world/" + textureId, image);
            return Material.textured(textureId, texture);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid texture '" + file + "': " + exception.getMessage(), exception);
        }
    }
}
