# Iskut Inventory, Items & Player Stats Foundation

This system is data-driven and renderer-independent. New item content belongs in `data/items/`, not in Java switch statements.

## Controls

- `I`: open/close inventory.
- `Esc`: close inventory first; otherwise close the client.
- `1`–`9`: select a hotbar slot.
- `R`: use the currently selected hotbar item when it declares a supported consumable effect.
- Left click in inventory: pick up, place, swap, merge or equip/unequip.
- Right click in inventory: take half a stack or place one item from a regular stack. Unique instance-bearing items move intact.
- `Shift` + left click: quick-move between backpack/hotbar or auto-equip compatible gear when a slot is free.
- A held GUI item is recovered before inventory closes or the game saves. If no safe destination exists, the inventory stays open rather than silently deleting it.

## Architecture

### Item catalog

- `ItemDefinition`: immutable static data (stable ID, display name, description, open category ID, rarity, max stack size, icon label, equipment slot, base weapon damage, sell price, tags, static modifiers, and declarative use effects).
- `ItemDefinitionRegistry`: ID lookup, duplicate-ID rejection, category queries and deterministic views that scale to large catalogs.
- `ItemDefinitionLoader`: recursively loads UTF-8 `.item` files from `data/items/`.
- `ItemStack`: runtime item stack points to its definition by `itemId`. Unique instances may add `instanceId`, `customData`, and per-instance stat modifiers for reforges/upgrades/rolled stats, without duplicating static metadata.

One `.item` file per definition keeps large catalogs merge-friendly and easy to validate. IDs should be stable and machine-readable. Names and descriptions are presentation fields; they must not be used as database keys.

### Inventory and equipment

- `InventoryModel`: 27 backpack slots + 9 hotbar slots, selected hotbar index, held cursor item, six named equipment slots (weapon, four armor pieces, pet) and five independent accessory slots.
- Every slot mutation validates the item against the catalog and respects stack size and equipment compatibility.
- Stack merge/split requires compatible item identities. Items with unique instance state cannot be stacked or split, and their identity is preserved while moving/equipping.
- `InventorySnapshot` is an immutable save/network DTO. `applySnapshot` validates a full snapshot before committing state. The cursor is transient and is deliberately excluded from persistent snapshots.
- `InventoryOverlay` builds backend-neutral `UiDrawList` commands; input hit-testing and model mutations do not depend on OpenGL or the software renderer.
- `ItemUseService` handles hotbar consumables. A handler only consumes the item after a supported effect succeeds.

### Player stats

- `StatDefinitionRegistry` provides stat metadata/defaults/ranges for Health, Mana, Defense, Strength, Intelligence, critical chance/damage, attack speed, regeneration and other RPG attributes.
- `StatModifier` declares `sourceId`, `statId`, operation and amount.
- `ModifierOperation`: `FLAT`, `PERCENT`, `MULTIPLIER`.
- `StatCalculator` uses deterministic calculation: `(base + sum(flat)) × max(0, 1 + sum(percent)) × product(multipliers)`, then clamps to stat definition bounds.
- `PlayerStats.setModifierSource(...)` atomically replaces a source such as `inventory_equipment`, `pet:comet_fox`, `area:skyland` or `buff:elixir`. Removing one source leaves others alone.
- Unknown stat IDs can be assigned base values and modifiers without new fields in `PlayerStats`; register definitions when UI metadata and bounds are needed.
- Derived totals are recomputed from base values + current equipment modifiers. Derived totals are not copied into saves, so changing gear doesn't leave stale totals.

## Save design

See `PLAYER_DATA_PERSISTENCE.md`. Local saves use one versioned JSON aggregate per player under `saves/players/`. Gameplay code uses `PlayerDataRepository`, not raw file I/O, so a future database implementation can map the same logical player data to normalized tables without changing inventory/combat code.

## Add an item

Create a UTF-8 file under `data/items/<category>/`, for example:

```properties
id=gale_saber
name=Gale Saber
description=A light blade carrying a steady crosswind.
category=weapon
rarity=RARE
max_stack=1
icon=GS
equipment_slot=WEAPON
base_damage=24
sell_price=180
modifier=strength|FLAT|7
modifier=crit_chance|FLAT|0.05
modifier=attack_speed|PERCENT|0.08
tag=melee
tag=sword
```

Supported rarities: `COMMON`, `UNCOMMON`, `RARE`, `EPIC`, `LEGENDARY`, `MYTHIC`, `SPECIAL`.
Supported equipment slots: `NONE`, `WEAPON`, `HELMET`, `CHESTPLATE`, `LEGGINGS`, `BOOTS`, `ACCESSORY`, `PET`.

For consumables, add a repeatable `use_effect` property such as `use_effect=HEAL|35` or `use_effect=RESTORE_MANA|25`. Current built-in effect handlers are healing and mana restoration. Effects that do nothing (for example, healing at full Health) do not consume an item.

## Current boundaries

The inventory, item catalog, hotbar, equipment/accessory model, modifier aggregation, built-in health/mana consumables, versioned local save/load, backup saves and save repository boundary are implemented. Future extensions include texture-based item icons, timed effect handlers, durability/forging UX, crafting, shops, quest/pet progression, save migrations as schema changes arise, and server-authoritative inventory transactions.

## Verification

From the project root after compilation:

```text
java -cp <compiled-classes> dev.itzsiden.skyvoxel.client.InventorySystemSmokeTest
```

The smoke test checks item catalog loading, stat modifier aggregation, inventory/UI behaviour, selected hotbar use, item instance metadata, JSON save/load, backup creation, and base-vs-derived stat restoration.
