package dev.itzsiden.skyvoxel.engine.items;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable persistence/network DTO. Empty storage slots are encoded as an empty ID with amount zero. */
public record InventorySnapshot(List<StackData> storage, Map<String, StackData> equipment,
                                List<StackData> accessories, int selectedHotbarIndex) {
    public record StackData(String itemId, int amount, String instanceId, Map<String, String> customData,
                            List<ItemStatModifier> instanceModifiers) {
        public StackData(String itemId, int amount) { this(itemId, amount, "", Map.of(), List.of()); }
        public StackData(String itemId, int amount, String instanceId, Map<String, String> customData) {
            this(itemId, amount, instanceId, customData, List.of());
        }
        public StackData {
            Objects.requireNonNull(itemId, "itemId");
            instanceId = instanceId == null ? "" : instanceId;
            customData = Map.copyOf(Objects.requireNonNull(customData, "customData"));
            instanceModifiers = List.copyOf(Objects.requireNonNull(instanceModifiers, "instanceModifiers"));
            if (itemId.isEmpty()) {
                if (amount != 0 || !instanceId.isEmpty() || !customData.isEmpty() || !instanceModifiers.isEmpty())
                    throw new IllegalArgumentException("Empty slot cannot contain stack metadata.");
            } else if (amount < 1) throw new IllegalArgumentException("Invalid stack snapshot.");
            if ((!instanceId.isEmpty() || !customData.isEmpty() || !instanceModifiers.isEmpty()) && amount != 1)
                throw new IllegalArgumentException("Instance-specific items must have amount=1.");
        }
        public static StackData of(ItemStack stack) { return stack == null ? new StackData("", 0) : new StackData(stack.itemId(), stack.amount(), stack.instanceId(), stack.customData(), stack.instanceModifiers()); }
        public ItemStack toStack() { return itemId.isEmpty() ? null : new ItemStack(itemId, amount, instanceId, customData, instanceModifiers); }
    }
    public InventorySnapshot {
        storage = List.copyOf(Objects.requireNonNull(storage, "storage"));
        equipment = Map.copyOf(Objects.requireNonNull(equipment, "equipment"));
        accessories = List.copyOf(Objects.requireNonNull(accessories, "accessories"));
        if (selectedHotbarIndex < 0 || selectedHotbarIndex >= InventoryModel.HOTBAR_SIZE) throw new IllegalArgumentException("Invalid hotbar index.");
        if (storage.size() != InventoryModel.STORAGE_SIZE || accessories.size() != InventoryModel.ACCESSORY_SLOTS) {
            throw new IllegalArgumentException("Inventory snapshot has incompatible dimensions.");
        }
    }
}
