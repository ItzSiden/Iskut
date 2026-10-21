package dev.itzsiden.skyvoxel.engine.physics;

import dev.itzsiden.skyvoxel.engine.scene.RenderObject;

import java.util.Objects;

/** The nearest collidable scene object intersected by a ray. */
public record RaycastHit(RenderObject object, double distance) {
    public RaycastHit {
        Objects.requireNonNull(object, "object");
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("Raycast distance must be finite and non-negative.");
        }
    }
}
