package dev.itzsiden.skyvoxel.engine.stats;

/** Metadata for a stat. New game-specific stats can be registered without editing PlayerStats. */
public record StatDefinition(String id, String displayName, float defaultBase, float minimum,
                             float maximum, boolean shownInInventory) {
    public StatDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Stat id cannot be blank.");
        id = id.strip().toLowerCase(java.util.Locale.ROOT);
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName cannot be blank.");
        if (!Float.isFinite(defaultBase) || !Float.isFinite(minimum) || !Float.isFinite(maximum) || minimum > maximum) {
            throw new IllegalArgumentException("Invalid stat defaults or bounds for " + id);
        }
    }
}
