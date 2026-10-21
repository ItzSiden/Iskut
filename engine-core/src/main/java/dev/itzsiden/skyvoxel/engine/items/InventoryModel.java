package dev.itzsiden.skyvoxel.engine.items;

import dev.itzsiden.skyvoxel.engine.gameplay.PlayerStats;
import dev.itzsiden.skyvoxel.engine.stats.StatModifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Game-thread-owned inventory and equipment state. Slots contain compact item IDs/counts, never definitions.
 * Slot writes validate against the catalog; callers can safely build GUIs around this model.
 */
public final class InventoryModel {
    public static final int MAIN_STORAGE_SIZE = 27;
    public static final int HOTBAR_SIZE = 9;
    public static final int HOTBAR_START = MAIN_STORAGE_SIZE;
    public static final int STORAGE_SIZE = MAIN_STORAGE_SIZE + HOTBAR_SIZE;
    public static final int ACCESSORY_SLOTS = 5;

    private final ItemStack[] storage = new ItemStack[STORAGE_SIZE];
    private final EnumMap<ItemEquipmentSlot, ItemStack> equipment = new EnumMap<>(ItemEquipmentSlot.class);
    private final ItemStack[] accessories = new ItemStack[ACCESSORY_SLOTS];
    private ItemStack cursorStack;
    private int selectedHotbarIndex;
    private long revision;

    public static InventoryModel starterLoadout(ItemDefinitionRegistry catalog) {
        Objects.requireNonNull(catalog, "catalog");
        InventoryModel inventory = new InventoryModel();
        inventory.setEquipped(ItemEquipmentSlot.WEAPON, 0, new ItemStack("training_blade", 1), catalog);
        inventory.setEquipped(ItemEquipmentSlot.HELMET, 0, new ItemStack("traveler_hood", 1), catalog);
        inventory.setEquipped(ItemEquipmentSlot.CHESTPLATE, 0, new ItemStack("traveler_tunic", 1), catalog);
        inventory.setEquipped(ItemEquipmentSlot.LEGGINGS, 0, new ItemStack("traveler_leggings", 1), catalog);
        inventory.setEquipped(ItemEquipmentSlot.BOOTS, 0, new ItemStack("traveler_boots", 1), catalog);
        inventory.setEquipped(ItemEquipmentSlot.ACCESSORY, 0, new ItemStack("aether_pendant", 1), catalog);
        inventory.setEquipped(ItemEquipmentSlot.PET, 0, new ItemStack("sky_wisp_pet", 1), catalog);
        inventory.setStorage(0, new ItemStack("wisp_essence", 4), catalog);
        inventory.setStorage(1, new ItemStack("cloud_fiber", 12), catalog);
        inventory.setStorage(2, new ItemStack("elyras_note", 1), catalog);
        inventory.setStorage(3, new ItemStack("moonstone_ore", 5), catalog);
        inventory.setStorage(4, new ItemStack("drift_leather", 20), catalog);
        inventory.setStorage(5, new ItemStack("azure_mushroom", 16), catalog);
        inventory.setStorage(6, new ItemStack("empty_vial", 12), catalog);
        inventory.setStorage(7, new ItemStack("repair_kit", 2), catalog);
        inventory.setStorage(8, new ItemStack("old_compass", 1), catalog);
        inventory.setStorage(HOTBAR_START, new ItemStack("health_potion", 8), catalog);
        inventory.setStorage(HOTBAR_START + 1, new ItemStack("mana_potion", 6), catalog);
        inventory.setStorage(HOTBAR_START + 2, new ItemStack("sky_shard", 16), catalog);
        inventory.setStorage(HOTBAR_START + 3, new ItemStack("copper_ore", 32), catalog);
        inventory.setStorage(HOTBAR_START + 4, new ItemStack("lumen_wood", 16), catalog);
        inventory.setStorage(HOTBAR_START + 5, new ItemStack("sky_crystal", 4), catalog);
        inventory.setStorage(HOTBAR_START + 6, new ItemStack("empty_vial", 16), catalog);
        inventory.setStorage(HOTBAR_START + 7, new ItemStack("cloud_fiber", 12), catalog);
        inventory.setStorage(HOTBAR_START + 8, new ItemStack("wisp_essence", 3), catalog);
        return inventory;
    }

    public List<String> equippedItemIds() {
        ArrayList<String> result = new ArrayList<>();
        equipment.values().stream().filter(Objects::nonNull).map(ItemStack::itemId).forEach(result::add);
        Arrays.stream(accessories).filter(Objects::nonNull).map(ItemStack::itemId).forEach(result::add);
        return List.copyOf(result);
    }

    public long revision() { return revision; }
    public ItemStack storage(int index) { return storage[checkStorageIndex(index)]; }
    public void setStorage(int index, ItemStack stack, ItemDefinitionRegistry catalog) {
        int slot = checkStorageIndex(index);
        validateStack(stack, catalog, false);
        if (!Objects.equals(storage[slot], stack)) { storage[slot] = stack; revision++; }
    }
    public ItemStack hotbar(int index) { if (index < 0 || index >= HOTBAR_SIZE) throw new IndexOutOfBoundsException(index); return storage[HOTBAR_START + index]; }
    public int selectedHotbarIndex() { return selectedHotbarIndex; }
    public void setSelectedHotbarIndex(int index) {
        if (index < 0 || index >= HOTBAR_SIZE) throw new IndexOutOfBoundsException("Hotbar index: " + index);
        if (selectedHotbarIndex != index) { selectedHotbarIndex = index; revision++; }
    }
    public ItemStack selectedHotbarStack() { return hotbar(selectedHotbarIndex); }

    public ItemStack equipped(ItemEquipmentSlot slot) {
        Objects.requireNonNull(slot, "slot");
        return slot == ItemEquipmentSlot.NONE || slot == ItemEquipmentSlot.ACCESSORY ? null : equipment.get(slot);
    }
    public ItemStack accessory(int index) { if (index < 0 || index >= ACCESSORY_SLOTS) throw new IndexOutOfBoundsException(index); return accessories[index]; }
    public ItemStack equipmentAt(ItemEquipmentSlot slot, int accessoryIndex) {
        return slot == ItemEquipmentSlot.ACCESSORY ? accessory(accessoryIndex) : equipped(slot);
    }
    public void setEquipped(ItemEquipmentSlot slot, int accessoryIndex, ItemStack stack, ItemDefinitionRegistry catalog) {
        Objects.requireNonNull(slot, "slot");
        if (slot == ItemEquipmentSlot.NONE) throw new IllegalArgumentException("NONE is not an equipment destination.");
        validateStack(stack, catalog, true);
        if (stack != null) {
            ItemDefinition definition = catalog.require(stack.itemId());
            if (definition.equipmentSlot() != slot) throw new IllegalArgumentException(definition.displayName() + " cannot be equipped in " + slot);
            if (stack.amount() != 1) throw new IllegalArgumentException("Equipped item stacks must have amount 1.");
        }
        if (slot == ItemEquipmentSlot.ACCESSORY) {
            if (accessoryIndex < 0 || accessoryIndex >= ACCESSORY_SLOTS) throw new IndexOutOfBoundsException(accessoryIndex);
            if (!Objects.equals(accessories[accessoryIndex], stack)) { accessories[accessoryIndex] = stack; revision++; }
        } else {
            ItemStack previous = equipment.get(slot);
            if (!Objects.equals(previous, stack)) {
                if (stack == null) equipment.remove(slot); else equipment.put(slot, stack);
                revision++;
            }
        }
    }
    public ItemStack cursorStack() { return cursorStack; }
    public void setCursorStack(ItemStack stack, ItemDefinitionRegistry catalog) {
        validateStack(stack, catalog, false);
        if (!Objects.equals(cursorStack, stack)) { cursorStack = stack; revision++; }
    }
    public void clearCursorStack() { if (cursorStack != null) { cursorStack = null; revision++; } }

    /**
     * Safely return a carried GUI stack before closing/saving. Tries ordinary storage first, then its valid
     * equipment slot. Returns false without discarding anything if no safe destination exists.
     */
    public boolean recoverCursor(ItemDefinitionRegistry catalog) {
        if (cursorStack == null) return true;
        ItemStack carried = cursorStack;
        ItemStack remainder = add(carried, catalog);
        cursorStack = remainder;
        revision++;
        if (remainder == null) return true;
        if (remainder.amount() == 1) {
            ItemDefinition definition = catalog.require(remainder.itemId());
            ItemEquipmentSlot slot = definition.equipmentSlot();
            if (slot != ItemEquipmentSlot.NONE) {
                if (slot == ItemEquipmentSlot.ACCESSORY) {
                    for (int i = 0; i < accessories.length; i++) {
                        if (accessories[i] == null) {
                            accessories[i] = remainder;
                            cursorStack = null;
                            revision++;
                            return true;
                        }
                    }
                } else if (equipment.get(slot) == null) {
                    equipment.put(slot, remainder);
                    cursorStack = null;
                    revision++;
                    return true;
                }
            }
        }
        return false;
    }

    /** Inserts as much as possible, stacking by stable item ID. Returns the uninserted remainder or null. */
    public ItemStack add(ItemStack input, ItemDefinitionRegistry catalog) {
        if (input == null) return null;
        ItemDefinition definition = catalog.require(input.itemId());
        int remaining = input.amount();
        for (int i = 0; i < storage.length && remaining > 0; i++) {
            ItemStack current = storage[i];
            if (current != null && current.canStackWith(input) && current.amount() < definition.maxStackSize()) {
                int moved = Math.min(remaining, definition.maxStackSize() - current.amount());
                storage[i] = new ItemStack(current.itemId(), current.amount() + moved);
                remaining -= moved;
            }
        }
        for (int i = 0; i < storage.length && remaining > 0; i++) {
            if (storage[i] == null) {
                int moved = input.hasInstanceState() ? remaining : Math.min(remaining, definition.maxStackSize());
                storage[i] = input.hasInstanceState() ? input : new ItemStack(input.itemId(), moved);
                remaining -= moved;
            }
        }
        if (remaining != input.amount()) revision++;
        return remaining == 0 ? null : input.withAmount(remaining);
    }

    /** Removes up to amount items from a storage/hotbar slot and returns the removed stack. */
    public ItemStack takeStorage(int index, int amount, ItemDefinitionRegistry catalog) {
        int slot = checkStorageIndex(index);
        if (amount < 1) throw new IllegalArgumentException("amount must be positive.");
        ItemStack current = storage[slot];
        if (current == null) return null;
        ItemDefinition definition = catalog.require(current.itemId());
        if (current.hasInstanceState() && amount < current.amount()) throw new IllegalArgumentException("Cannot split an instance-specific item.");
        int taken = Math.min(amount, current.amount());
        ItemStack removed = current.withAmount(taken);
        storage[slot] = current.withAmount(current.amount() - taken);
        revision++;
        return removed;
    }

    /** Returns total units across backpack and hotbar for a stable item ID. */
    public long countItem(String itemId) {
        if (itemId == null || itemId.isBlank()) return 0;
        String id = itemId.strip().toLowerCase(java.util.Locale.ROOT);
        long total = 0;
        for (ItemStack stack : storage) if (stack != null && stack.itemId().equals(id)) total += stack.amount();
        return total;
    }

    /** Rebuilds only the inventory equipment source; pet, future zones and timed buffs have independent source IDs. */
    public void syncStats(PlayerStats stats, ItemDefinitionRegistry catalog) {
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(catalog, "catalog");
        List<StatModifier> modifiers = new ArrayList<>();
        for (Map.Entry<ItemEquipmentSlot, ItemStack> entry : equipment.entrySet()) {
            appendModifiers(modifiers, "equipment:" + entry.getKey().name().toLowerCase(java.util.Locale.ROOT), entry.getValue(), catalog);
        }
        for (int i = 0; i < accessories.length; i++) appendModifiers(modifiers, "equipment:accessory:" + i, accessories[i], catalog);
        stats.setModifierSource("inventory_equipment", modifiers);
    }

    public InventorySnapshot snapshot() {
        List<InventorySnapshot.StackData> storageData = Arrays.stream(storage).map(InventorySnapshot.StackData::of).toList();
        Map<String, InventorySnapshot.StackData> equipmentData = new LinkedHashMap<>();
        for (ItemEquipmentSlot slot : ItemEquipmentSlot.values()) {
            if (slot == ItemEquipmentSlot.NONE || slot == ItemEquipmentSlot.ACCESSORY) continue;
            equipmentData.put(slot.name(), InventorySnapshot.StackData.of(equipment.get(slot)));
        }
        List<InventorySnapshot.StackData> accessoryData = Arrays.stream(accessories).map(InventorySnapshot.StackData::of).toList();
        return new InventorySnapshot(storageData, equipmentData, accessoryData, selectedHotbarIndex);
    }

    /** Atomically validates a snapshot before changing any live slot. Cursor state is intentionally transient. */
    public void applySnapshot(InventorySnapshot snapshot, ItemDefinitionRegistry catalog) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(catalog, "catalog");
        ItemStack[] nextStorage = new ItemStack[STORAGE_SIZE];
        EnumMap<ItemEquipmentSlot, ItemStack> nextEquipment = new EnumMap<>(ItemEquipmentSlot.class);
        ItemStack[] nextAccessories = new ItemStack[ACCESSORY_SLOTS];
        for (int i = 0; i < STORAGE_SIZE; i++) {
            ItemStack stack = snapshot.storage().get(i).toStack(); validateStack(stack, catalog, false);
            nextStorage[i] = stack;
        }
        for (ItemEquipmentSlot slot : ItemEquipmentSlot.values()) {
            if (slot == ItemEquipmentSlot.NONE || slot == ItemEquipmentSlot.ACCESSORY) continue;
            InventorySnapshot.StackData data = snapshot.equipment().get(slot.name());
            if (data == null) throw new IllegalArgumentException("Snapshot missing equipment slot " + slot);
            ItemStack stack = data.toStack();
            validateStack(stack, catalog, true);
            if (stack != null && catalog.require(stack.itemId()).equipmentSlot() != slot) throw new IllegalArgumentException("Wrong item for equipment slot " + slot);
            if (stack != null) nextEquipment.put(slot, stack);
        }
        for (int i = 0; i < ACCESSORY_SLOTS; i++) {
            ItemStack stack = snapshot.accessories().get(i).toStack();
            validateStack(stack, catalog, true);
            if (stack != null && catalog.require(stack.itemId()).equipmentSlot() != ItemEquipmentSlot.ACCESSORY) throw new IllegalArgumentException("Non-accessory in accessory slot " + i);
            nextAccessories[i] = stack;
        }
        System.arraycopy(nextStorage, 0, storage, 0, STORAGE_SIZE);
        equipment.clear(); equipment.putAll(nextEquipment);
        System.arraycopy(nextAccessories, 0, accessories, 0, ACCESSORY_SLOTS);
        selectedHotbarIndex = snapshot.selectedHotbarIndex();
        cursorStack = null;
        revision++;
    }

    private static void appendModifiers(List<StatModifier> out, String source, ItemStack stack, ItemDefinitionRegistry catalog) {
        if (stack == null) return;
        ItemDefinition definition = catalog.require(stack.itemId());
        for (ItemStatModifier modifier : definition.modifiers()) {
            out.add(new StatModifier(source + ":" + definition.id(), modifier.statId(), modifier.operation(), modifier.amount()));
        }
        for (ItemStatModifier modifier : stack.instanceModifiers()) {
            out.add(new StatModifier(source + ":instance:" + stack.instanceId(), modifier.statId(), modifier.operation(), modifier.amount()));
        }
        if (definition.baseDamage() > 0) out.add(new StatModifier(source + ":" + definition.id(), "weapon_damage", dev.itzsiden.skyvoxel.engine.stats.ModifierOperation.FLAT, definition.baseDamage()));
    }
    private static void validateStack(ItemStack stack, ItemDefinitionRegistry catalog, boolean equipment) {
        if (stack == null) return;
        ItemDefinition definition = catalog.require(stack.itemId());
        if (stack.amount() > definition.maxStackSize()) throw new IllegalArgumentException("Stack exceeds max for " + definition.id());
        if ((stack.hasInstanceState() || equipment && definition.equippable()) && stack.amount() != 1)
            throw new IllegalArgumentException("Instance/equipment stacks must have amount 1.");
    }
    private static int checkStorageIndex(int index) {
        if (index < 0 || index >= STORAGE_SIZE) throw new IndexOutOfBoundsException("Storage slot: " + index);
        return index;
    }
}
