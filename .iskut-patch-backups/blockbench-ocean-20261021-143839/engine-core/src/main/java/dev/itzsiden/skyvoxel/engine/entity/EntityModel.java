package dev.itzsiden.skyvoxel.engine.entity;

import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.engine.scene.Mesh;

import java.util.List;
import java.util.Objects;

/** Reusable model template: named mesh parts placed relative to an entity's feet origin. */
public record EntityModel(String id, float height, List<Part> parts) {
    public EntityModel {
        id = Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("Model id cannot be blank.");
        if (!Float.isFinite(height) || height <= 0) throw new IllegalArgumentException("Model height must be positive.");
        parts = List.copyOf(Objects.requireNonNull(parts, "parts"));
        if (parts.isEmpty()) throw new IllegalArgumentException("Entity models need at least one part.");
    }

    /**
     * Actual upper bound of authored geometry, measured from the entity feet origin.
     * This is more reliable for nameplates than the optional declared HEIGHT value,
     * which artists may intentionally pad for collision or animation.
     */
    public float headAnchorHeight() {
        float top = 0.0f;
        for (Part part : parts) top = Math.max(top, part.offsetY() + part.height() * 0.5f);
        return Math.max(0.1f, top);
    }

    /** Part offset is measured from the entity origin (normally the centre of its feet). */
    public record Part(String id, Mesh mesh, Material material,
                       float offsetX, float offsetY, float offsetZ,
                       float width, float height, float depth, float rotationY) {
        public Part {
            id = Objects.requireNonNull(id, "id");
            if (id.isBlank()) throw new IllegalArgumentException("Part id cannot be blank.");
            Objects.requireNonNull(mesh, "mesh");
            Objects.requireNonNull(material, "material");
            finite(offsetX); finite(offsetY); finite(offsetZ); finite(rotationY);
            if (!Float.isFinite(width) || !Float.isFinite(height) || !Float.isFinite(depth)
                || width <= 0 || height <= 0 || depth <= 0) {
                throw new IllegalArgumentException("Model part dimensions must be positive and finite.");
            }
        }
        private static void finite(float v) { if (!Float.isFinite(v)) throw new IllegalArgumentException("Model values must be finite."); }
    }
}
