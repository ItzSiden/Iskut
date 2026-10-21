package dev.itzsiden.skyvoxel.engine.items;

import dev.itzsiden.skyvoxel.engine.stats.ModifierOperation;
import java.util.Objects;

/** Immutable modifier declared by an item definition. Runtime source identity is added when equipped. */
public record ItemStatModifier(String statId, ModifierOperation operation, float amount) {
    public ItemStatModifier {
        if (statId == null || statId.isBlank()) throw new IllegalArgumentException("statId cannot be blank.");
        statId = statId.strip().toLowerCase(java.util.Locale.ROOT);
        Objects.requireNonNull(operation, "operation");
        if (!Float.isFinite(amount)) throw new IllegalArgumentException("Modifier amount must be finite.");
        if (operation == ModifierOperation.MULTIPLIER && amount <= 0) throw new IllegalArgumentException("Multiplier must be positive.");
    }
}
