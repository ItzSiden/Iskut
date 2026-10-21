package dev.itzsiden.skyvoxel.engine.gameplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Equipment aggregation point for armor, weapons, accessories, and pet amplifiers. Game-thread owned. */
public final class EquipmentLoadout {
    private final EnumMap<EquipmentSlot, EquipmentItem> slots = new EnumMap<>(EquipmentSlot.class);
    private final List<EquipmentItem> accessories = new ArrayList<>();

    public EquipmentLoadout equip(EquipmentItem item) {
        Objects.requireNonNull(item, "item");
        switch (item.slot()) {
            case ACCESSORY -> {
                accessories.removeIf(existing -> existing.id().equals(item.id()));
                accessories.add(item);
            }
            case PET, WEAPON, HELMET, CHESTPLATE, LEGGINGS, BOOTS -> slots.put(item.slot(), item);
        }
        return this;
    }

    public EquipmentItem get(EquipmentSlot slot) {
        return slot == EquipmentSlot.ACCESSORY ? null : slots.get(Objects.requireNonNull(slot, "slot"));
    }

    public boolean unequip(EquipmentSlot slot) {
        Objects.requireNonNull(slot, "slot");
        if (slot == EquipmentSlot.ACCESSORY) return false;
        return slots.remove(slot) != null;
    }

    public boolean removeAccessory(String id) {
        return accessories.removeIf(item -> item.id().equals(id));
    }

    public List<EquipmentItem> accessories() { return Collections.unmodifiableList(accessories); }
    public Map<EquipmentSlot, EquipmentItem> slots() { return Collections.unmodifiableMap(slots); }

    public List<EquipmentItem> allEquipped() {
        ArrayList<EquipmentItem> result = new ArrayList<>(slots.values());
        result.addAll(accessories);
        return List.copyOf(result);
    }

    public StatModifiers totalModifiers() {
        StatModifiers total = StatModifiers.ZERO;
        for (EquipmentItem item : slots.values()) total = total.plus(item.modifiers());
        for (EquipmentItem item : accessories) total = total.plus(item.modifiers());
        return total;
    }

    public float weaponDamage() {
        EquipmentItem weapon = slots.get(EquipmentSlot.WEAPON);
        return weapon == null ? 1.0f : weapon.baseDamage();
    }

    public String summary() {
        EquipmentItem weapon = slots.get(EquipmentSlot.WEAPON);
        EquipmentItem pet = slots.get(EquipmentSlot.PET);
        long armorCount = List.of(EquipmentSlot.HELMET, EquipmentSlot.CHESTPLATE,
            EquipmentSlot.LEGGINGS, EquipmentSlot.BOOTS).stream().filter(slots::containsKey).count();
        String weaponName = weapon == null ? "None" : weapon.displayName();
        String petName = pet == null ? "None" : pet.displayName();
        return "Weapon: " + weaponName + " | Armor: " + armorCount + "/4 | Pet: " + petName + " | Accessories: " + accessories.size();
    }

    /** A small starter kit used by the prototype until the inventory/equipment UI exists. */
    public static EquipmentLoadout starterKit() {
        EquipmentLoadout loadout = new EquipmentLoadout();
        loadout.equip(new EquipmentItem("traveler_hood", "Traveler's Hood", EquipmentSlot.HELMET,
            new StatModifiers(5, 0, 3, 0, 0, 0, 0), 0));
        loadout.equip(new EquipmentItem("traveler_tunic", "Traveler's Tunic", EquipmentSlot.CHESTPLATE,
            new StatModifiers(12, 0, 8, 0, 0, 0, 0), 0));
        loadout.equip(new EquipmentItem("traveler_leggings", "Traveler's Leggings", EquipmentSlot.LEGGINGS,
            new StatModifiers(8, 0, 5, 0, 0, 0, 0), 0));
        loadout.equip(new EquipmentItem("traveler_boots", "Traveler's Boots", EquipmentSlot.BOOTS,
            new StatModifiers(6, 0, 4, 0, 0, 0.03f, 0), 0));
        loadout.equip(new EquipmentItem("training_blade", "Training Blade", EquipmentSlot.WEAPON,
            new StatModifiers(0, 0, 0, 2, 0, 0, 0), 8));
        loadout.equip(new EquipmentItem("aether_pendant", "Aether Pendant", EquipmentSlot.ACCESSORY,
            new StatModifiers(0, 12, 0, 0, 2, 0, 0), 0));
        loadout.equip(new EquipmentItem("sky_wisp_pet", "Sky Wisp Companion", EquipmentSlot.PET,
            new StatModifiers(0, 0, 2, 3, 5, 0.04f, 0.02f), 0));
        return loadout;
    }
}
