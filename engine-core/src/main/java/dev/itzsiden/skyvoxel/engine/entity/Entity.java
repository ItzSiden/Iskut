package dev.itzsiden.skyvoxel.engine.entity;

import java.util.Objects;

/** Mutable game-thread entity state. Network code should enqueue snapshots onto the game thread. */
public final class Entity {
    private final String id;
    private final EntityType type;
    private final EntityModel model;
    private String displayName;
    private String subtitle;
    private float x, y, z, yaw;
    private float maxHealth = 20.0f, health = 20.0f;
    private float defense;
    private boolean visible = true;
    private boolean modelVisible = true;
    private boolean showNameplate = true;
    private boolean showHealthBar = true;

    public Entity(String id, EntityType type, EntityModel model, String displayName, String subtitle) {
        this.id = requireText(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.model = Objects.requireNonNull(model, "model");
        this.displayName = requireText(displayName, "displayName");
        this.subtitle = subtitle == null ? "" : subtitle;
        this.showHealthBar = type == EntityType.MOB || type == EntityType.PLAYER;
    }

    public String id() { return id; }
    public EntityType type() { return type; }
    public EntityModel model() { return model; }
    public String displayName() { return displayName; }
    public String subtitle() { return subtitle; }
    public float x() { return x; }
    public float y() { return y; }
    public float z() { return z; }
    public float yaw() { return yaw; }
    public float health() { return health; }
    public float maxHealth() { return maxHealth; }
    public float defense() { return defense; }
    public float healthRatio() { return maxHealth <= 0 ? 0 : Math.max(0, Math.min(1, health / maxHealth)); }

    /** Applies the same diminishing-return defence formula used by the player RPG stats. */
    public float takeDamage(float rawDamage) {
        if (!Float.isFinite(rawDamage) || rawDamage < 0) throw new IllegalArgumentException("Damage must be finite and non-negative.");
        if (health <= 0 || rawDamage == 0) return 0;
        float applied = Math.min(health, rawDamage * (100.0f / (100.0f + defense)));
        health = Math.max(0, health - applied);
        return applied;
    }
    public boolean visible() { return visible; }
    public boolean modelVisible() { return visible && modelVisible; }
    public boolean showNameplate() { return visible && showNameplate; }
    public boolean showHealthBar() { return visible && showHealthBar && maxHealth > 0; }

    public Entity setPosition(float x, float y, float z) {
        this.x = finite(x); this.y = finite(y); this.z = finite(z); return this;
    }
    public Entity setYaw(float yaw) { this.yaw = finite(yaw); return this; }
    public Entity setDisplayName(String value) { displayName = requireText(value, "displayName"); return this; }
    public Entity setSubtitle(String value) { subtitle = value == null ? "" : value; return this; }
    public Entity setHealth(float current, float max) {
        if (!Float.isFinite(current) || !Float.isFinite(max) || max <= 0) throw new IllegalArgumentException("Health must be finite and max health positive.");
        maxHealth = max; health = Math.max(0, Math.min(max, current)); return this;
    }
    public Entity setDefense(float value) {
        if (!Float.isFinite(value) || value < 0) throw new IllegalArgumentException("Defense must be finite and non-negative.");
        defense = value; return this;
    }
    public Entity setVisible(boolean value) { visible = value; return this; }
    public Entity setModelVisible(boolean value) { modelVisible = value; return this; }
    public Entity setShowNameplate(boolean value) { showNameplate = value; return this; }
    public Entity setShowHealthBar(boolean value) { showHealthBar = value; return this; }

    private static float finite(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Entity transform values must be finite.");
        return value;
    }
    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank.");
        return value;
    }
}
