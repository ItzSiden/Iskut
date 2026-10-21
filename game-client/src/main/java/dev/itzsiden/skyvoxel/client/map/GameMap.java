package dev.itzsiden.skyvoxel.client.map;

import dev.itzsiden.skyvoxel.engine.scene.Scene;

import java.util.Map;
import java.util.Objects;

/** Immutable metadata for a hand-authored map and its optimized render-only representation. */
public record GameMap(String name, Scene scene, Scene renderScene, Spawn spawn, Map<String, Interaction> interactions) {
    /** Backwards-compatible constructor for tools/tests that do not create a render batch scene. */
    public GameMap(String name, Scene scene, Spawn spawn, Map<String, Interaction> interactions) {
        this(name, scene, scene, spawn, interactions);
    }

    public GameMap {
        name = Objects.requireNonNull(name, "name");
        scene = Objects.requireNonNull(scene, "scene");
        renderScene = Objects.requireNonNull(renderScene, "renderScene");
        spawn = Objects.requireNonNull(spawn, "spawn");
        interactions = Map.copyOf(Objects.requireNonNull(interactions, "interactions"));
    }

    /** World-space feet position and yaw in radians. */
    public record Spawn(float x, float y, float z, float yawRadians) {
        public Spawn {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(yawRadians)) {
                throw new IllegalArgumentException("Spawn coordinates must be finite.");
            }
        }
    }

    public enum InteractionType {
        COLLECT_CRYSTAL,
        PORTAL,
        NPC,
        SHOP,
        INFO
    }

    /** Interactions are attached to stable placed-object IDs, e.g. plaza/shard_left. */
    public record Interaction(InteractionType type, String title, String message) {
        public Interaction {
            Objects.requireNonNull(type, "type");
            title = Objects.requireNonNull(title, "title").replace('_', ' ');
            message = Objects.requireNonNull(message, "message").replace('_', ' ');
        }
    }
}
