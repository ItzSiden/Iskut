package dev.itzsiden.skyvoxel.engine.gameplay;

import java.util.Objects;

/** Data-only item definition. Inventories can hold IDs while loadouts use definitions from a registry. */
public record EquipmentItem(String id, String displayName, EquipmentSlot slot,
                            StatModifiers modifiers, float baseDamage) {
    public EquipmentItem {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Item id cannot be blank.");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Item displayName cannot be blank.");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(modifiers, "modifiers");
        if (!Float.isFinite(baseDamage) || baseDamage < 0) throw new IllegalArgumentException("baseDamage must be finite and non-negative.");
        if (slot != EquipmentSlot.WEAPON && baseDamage != 0) throw new IllegalArgumentException("Only weapons can add base damage.");
    }
}
