package dev.itzsiden.skyvoxel.engine.items;

/** Item equipment slot schema; accessory is repeated in five independently addressable slots. */
public enum ItemEquipmentSlot {
    NONE, WEAPON, HELMET, CHESTPLATE, LEGGINGS, BOOTS, ACCESSORY, PET;
    public static ItemEquipmentSlot parse(String value) {
        if (value == null || value.isBlank()) return NONE;
        return valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
    }
}
