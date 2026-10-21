package dev.itzsiden.skyvoxel.engine.items;

/** Stable IDs; colors are presentation metadata, not gameplay logic. */
public enum ItemRarity {
    COMMON("Common", 0xFFCAD2DB),
    UNCOMMON("Uncommon", 0xFF65D38A),
    RARE("Rare", 0xFF67A8FF),
    EPIC("Epic", 0xFFB48BFF),
    LEGENDARY("Legendary", 0xFFFFC857),
    MYTHIC("Mythic", 0xFFFF78CB),
    SPECIAL("Special", 0xFF55D9D1);

    private final String displayName;
    private final int colorArgb;
    ItemRarity(String displayName, int colorArgb) { this.displayName = displayName; this.colorArgb = colorArgb; }
    public String displayName() { return displayName; }
    public int colorArgb() { return colorArgb; }
    public static ItemRarity parse(String value) { return valueOf(value.strip().toUpperCase(java.util.Locale.ROOT)); }
}
