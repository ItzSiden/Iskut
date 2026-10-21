package dev.itzsiden.skyvoxel.engine.entity;

/** Immutable entity state packet shape. A network layer can serialize this record later. */
public record EntitySnapshot(
    String id, EntityType type, String modelId,
    String displayName, String subtitle,
    float x, float y, float z, float yaw,
    float health, float maxHealth, float defense
) {
    /** Backwards-compatible constructor for snapshots created before defense replication existed. */
    public EntitySnapshot(String id, EntityType type, String modelId, String displayName, String subtitle,
                          float x, float y, float z, float yaw, float health, float maxHealth) {
        this(id, type, modelId, displayName, subtitle, x, y, z, yaw, health, maxHealth, 0.0f);
    }
    public EntitySnapshot {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Snapshot id cannot be blank.");
        if (type == null) throw new NullPointerException("type");
        if (modelId == null || modelId.isBlank()) throw new IllegalArgumentException("Snapshot modelId cannot be blank.");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Snapshot displayName cannot be blank.");
        if (subtitle == null) subtitle = "";
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(yaw)) {
            throw new IllegalArgumentException("Snapshot transform must be finite.");
        }
        if (!Float.isFinite(health) || !Float.isFinite(maxHealth) || maxHealth <= 0) {
            throw new IllegalArgumentException("Snapshot health must be finite with positive maxHealth.");
        }
        if (!Float.isFinite(defense) || defense < 0) throw new IllegalArgumentException("Snapshot defense must be finite and non-negative.");
    }
}
