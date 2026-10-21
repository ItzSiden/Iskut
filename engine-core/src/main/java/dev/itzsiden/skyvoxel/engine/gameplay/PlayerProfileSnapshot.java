package dev.itzsiden.skyvoxel.engine.gameplay;

import java.util.List;
import java.util.Objects;

/** Immutable profile payload that can be delivered to the client by a future authoritative server. */
public record PlayerProfileSnapshot(
    String playerId, String displayName, int level,
    float health, float maxHealth, float mana, float maxMana,
    float defense, float strength, float intelligence,
    boolean online, List<String> equippedItemIds
) {
    public PlayerProfileSnapshot {
        if (playerId == null || playerId.isBlank()) throw new IllegalArgumentException("playerId cannot be blank.");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName cannot be blank.");
        if (level < 1) throw new IllegalArgumentException("level must be at least 1.");
        if (!Float.isFinite(health) || !Float.isFinite(maxHealth) || maxHealth <= 0 || health < 0 || health > maxHealth
            || !Float.isFinite(mana) || !Float.isFinite(maxMana) || maxMana < 0 || mana < 0 || mana > maxMana
            || !Float.isFinite(defense) || defense < 0 || !Float.isFinite(strength) || strength < 0
            || !Float.isFinite(intelligence) || intelligence < 0) {
            throw new IllegalArgumentException("Profile stats are outside valid bounds.");
        }
        equippedItemIds = List.copyOf(Objects.requireNonNull(equippedItemIds, "equippedItemIds"));
        for (String itemId : equippedItemIds) if (itemId == null || itemId.isBlank()) throw new IllegalArgumentException("Equipped item IDs cannot be blank.");
    }

    public static PlayerProfileSnapshot from(String playerId, String displayName, int level,
                                             PlayerStats stats, boolean online) {
        Objects.requireNonNull(stats, "stats");
        List<String> itemIds = stats.equipment().allEquipped().stream().map(EquipmentItem::id).toList();
        return new PlayerProfileSnapshot(playerId, displayName, level,
            stats.health(), stats.maxHealth(), stats.mana(), stats.maxMana(),
            stats.defense(), stats.strength(), stats.intelligence(), online, itemIds);
    }
}
