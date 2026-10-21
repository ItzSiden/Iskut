package dev.itzsiden.skyvoxel.engine.stats;

/** Modifier order is deterministic: base + FLAT, then summed PERCENT, then MULTIPLIER products. */
public enum ModifierOperation {
    FLAT,
    PERCENT,
    MULTIPLIER
}
