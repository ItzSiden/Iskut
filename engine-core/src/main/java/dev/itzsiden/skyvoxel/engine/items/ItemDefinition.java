package dev.itzsiden.skyvoxel.engine.items;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable catalog record. Inventory/save data stores item IDs, never a copy of this definition. */
public record ItemDefinition(String id, String displayName, String description, String categoryId,
                             ItemRarity rarity, int maxStackSize, String icon, ItemEquipmentSlot equipmentSlot,
                             float baseDamage, long sellPrice, List<ItemStatModifier> modifiers, Set<String> tags,
                             List<ItemUseEffect> useEffects) {
    /** Compatibility constructor for callers that define non-consumable items in code. */
    public ItemDefinition(String id, String displayName, String description, String categoryId,
                          ItemRarity rarity, int maxStackSize, String icon, ItemEquipmentSlot equipmentSlot,
                          float baseDamage, long sellPrice, List<ItemStatModifier> modifiers, Set<String> tags) {
        this(id, displayName, description, categoryId, rarity, maxStackSize, icon, equipmentSlot,
            baseDamage, sellPrice, modifiers, tags, List.of());
    }
    public ItemDefinition {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_.-]{1,95}")) {
            throw new IllegalArgumentException("Item ID must be stable lowercase ASCII (2-96 chars): " + id);
        }
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Item display name cannot be blank.");
        description = description == null ? "" : description.strip();
        if (categoryId == null || categoryId.isBlank()) throw new IllegalArgumentException("categoryId cannot be blank.");
        categoryId = categoryId.strip().toLowerCase(java.util.Locale.ROOT);
        Objects.requireNonNull(rarity, "rarity");
        if (maxStackSize < 1 || maxStackSize > 9999) throw new IllegalArgumentException("maxStackSize must be in [1,9999].");
        if (icon == null || icon.isBlank()) icon = "?";
        if (icon.length() > 4) throw new IllegalArgumentException("Item icon label can contain up to 4 characters.");
        Objects.requireNonNull(equipmentSlot, "equipmentSlot");
        if (!Float.isFinite(baseDamage) || baseDamage < 0) throw new IllegalArgumentException("baseDamage must be finite and non-negative.");
        if (sellPrice < 0) throw new IllegalArgumentException("sellPrice cannot be negative.");
        modifiers = List.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
        useEffects = List.copyOf(Objects.requireNonNull(useEffects, "useEffects"));
        if (equipmentSlot != ItemEquipmentSlot.NONE && maxStackSize != 1) {
            throw new IllegalArgumentException("Equippable items must have maxStackSize=1: " + id);
        }
        if (equipmentSlot != ItemEquipmentSlot.WEAPON && baseDamage != 0) {
            throw new IllegalArgumentException("Only weapon items may define baseDamage: " + id);
        }
    }
    public boolean equippable() { return equipmentSlot != ItemEquipmentSlot.NONE; }
}
