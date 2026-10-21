package dev.itzsiden.skyvoxel.engine.stats;

import java.util.Objects;

/** A source-labelled runtime modifier, suitable for gear, pets, buffs, areas and future server effects. */
public record StatModifier(String sourceId, String statId, ModifierOperation operation, float amount) {
    public StatModifier {
        if (sourceId == null || sourceId.isBlank()) throw new IllegalArgumentException("sourceId cannot be blank.");
        if (statId == null || statId.isBlank()) throw new IllegalArgumentException("statId cannot be blank.");
        statId = statId.strip().toLowerCase(java.util.Locale.ROOT);
        Objects.requireNonNull(operation, "operation");
        if (!Float.isFinite(amount)) throw new IllegalArgumentException("Modifier amount must be finite.");
        if (operation == ModifierOperation.MULTIPLIER && amount <= 0) {
            throw new IllegalArgumentException("Multiplier modifiers must be greater than zero (e.g. 1.10 for +10%).");
        }
    }
}
