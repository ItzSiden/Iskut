package dev.itzsiden.skyvoxel.engine.scene;

import dev.itzsiden.skyvoxel.render.api.TextureData;

import java.util.Objects;

/**
 * Simple backend-neutral material: an RGB tint and an optional texture (or atlas).
 * Vertex colours are multiplied by the tint, and by the texture when one is set.
 */
public record Material(String name, float red, float green, float blue, TextureData texture) {
    public static final Material WHITE = new Material("white", 1.0f, 1.0f, 1.0f);

    public Material(String name, float red, float green, float blue) {
        this(name, red, green, blue, null);
    }

    /** White-tinted textured material. */
    public static Material textured(String name, TextureData texture) {
        return new Material(name, 1.0f, 1.0f, 1.0f, Objects.requireNonNull(texture, "texture"));
    }

    public Material {
        name = Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("Material name cannot be blank.");
        checkColor(red, "red"); checkColor(green, "green"); checkColor(blue, "blue");
    }

    /** Same material with a different tint (for highlight effects) keeping the texture. */
    public Material withTint(String newName, float r, float g, float b) {
        return new Material(newName, r, g, b, texture);
    }

    private static void checkColor(float value, String field) {
        if (!Float.isFinite(value) || value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException(field + " must be finite and within [0, 1].");
        }
    }
}
