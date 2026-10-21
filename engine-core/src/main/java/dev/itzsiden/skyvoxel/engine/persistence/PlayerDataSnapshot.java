package dev.itzsiden.skyvoxel.engine.persistence;

import dev.itzsiden.skyvoxel.engine.items.InventorySnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Versioned logical player aggregate. Derived stats are deliberately not stored; they are recalculated from base stats + inventory. */
public record PlayerDataSnapshot(int schemaVersion, String playerId, long savedAtEpochMillis,
                                 long coins, Map<String, Float> baseStats,
                                 float health, float mana, InventorySnapshot inventory) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public PlayerDataSnapshot {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported player data schema version: " + schemaVersion);
        if (playerId == null || !playerId.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Player ID must be 1-64 safe ASCII characters.");
        if (savedAtEpochMillis < 0) throw new IllegalArgumentException("savedAtEpochMillis cannot be negative.");
        if (coins < 0) throw new IllegalArgumentException("coins cannot be negative.");
        if (!Float.isFinite(health) || health < 0 || !Float.isFinite(mana) || mana < 0) throw new IllegalArgumentException("Health/mana must be finite and non-negative.");
        Objects.requireNonNull(baseStats, "baseStats");
        Map<String, Float> copied = new LinkedHashMap<>();
        for (Map.Entry<String, Float> entry : baseStats.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || !Float.isFinite(entry.getValue()))
                throw new IllegalArgumentException("Invalid saved base stat.");
            copied.put(entry.getKey().strip().toLowerCase(java.util.Locale.ROOT), entry.getValue());
        }
        baseStats = Map.copyOf(copied);
        Objects.requireNonNull(inventory, "inventory");
    }

    public static PlayerDataSnapshot create(String playerId, long coins, Map<String, Float> baseStats,
                                            float health, float mana, InventorySnapshot inventory) {
        return new PlayerDataSnapshot(CURRENT_SCHEMA_VERSION, playerId, System.currentTimeMillis(),
            coins, baseStats, health, mana, inventory);
    }
}
