package dev.itzsiden.skyvoxel.engine.items;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Runtime stack reference. Static data always comes from ItemDefinition; optional instance data holds future
 * reforges, durability, rolled modifiers or crafted attributes without copying the definition into every slot.
 * Items with instance state are non-stackable and carry a stable instance ID.
 */
public record ItemStack(String itemId, int amount, String instanceId, Map<String, String> customData,
                        List<ItemStatModifier> instanceModifiers) {
    public ItemStack(String itemId, int amount) {
        this(itemId, amount, "", Map.of(), List.of());
    }

    public ItemStack(String itemId, int amount, String instanceId, Map<String, String> customData) {
        this(itemId, amount, instanceId, customData, List.of());
    }

    public ItemStack {
        if (itemId == null || itemId.isBlank()) throw new IllegalArgumentException("itemId cannot be blank.");
        itemId = itemId.strip().toLowerCase(java.util.Locale.ROOT);
        if (amount < 1) throw new IllegalArgumentException("Stack amount must be >= 1.");
        instanceId = instanceId == null ? "" : instanceId.strip();
        customData = Map.copyOf(Objects.requireNonNull(customData, "customData"));
        instanceModifiers = List.copyOf(Objects.requireNonNull(instanceModifiers, "instanceModifiers"));
        for (Map.Entry<String, String> entry : customData.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                throw new IllegalArgumentException("Instance data keys must be nonblank and values non-null.");
            }
        }
        boolean instanceState = !instanceId.isEmpty() || !customData.isEmpty() || !instanceModifiers.isEmpty();
        if (instanceState && amount != 1) throw new IllegalArgumentException("Instance-specific items must have amount=1.");
    }

    public static ItemStack unique(String itemId) {
        return new ItemStack(itemId, 1, UUID.randomUUID().toString(), Map.of(), List.of());
    }

    public boolean hasInstanceState() {
        return !instanceId.isEmpty() || !customData.isEmpty() || !instanceModifiers.isEmpty();
    }

    public boolean canStackWith(ItemStack other) {
        return other != null && itemId.equals(other.itemId) && !hasInstanceState() && !other.hasInstanceState();
    }

    public ItemStack withAmount(int value) {
        return value <= 0 ? null : new ItemStack(itemId, value, instanceId, customData, instanceModifiers);
    }
}
