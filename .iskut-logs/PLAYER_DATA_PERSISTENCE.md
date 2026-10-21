# Iskut Player Data and Persistence

## Current local-save format

Default path: `saves/players/local-player.json` (relative to the Iskut project root when launched with `run-hub.bat`). Use `--player-id=<safe-id>` for another local profile or `--save-dir=<directory>` to change the save directory.

The game stores one **versioned logical player aggregate per file**. The aggregate contains:

- `schemaVersion`, `playerId`, and `savedAtEpochMillis`
- player currency and base stat values
- current Health and Mana (clamped after derived stats rebuild)
- backpack + hotbar contents, selected hotbar slot, equipment, accessories
- unique item `instanceId`, `customData`, and per-instance modifiers

Derived stats are **not** persisted. On load, the engine applies base stats, validates and applies the inventory snapshot against the current item catalog, rebuilds the equipment modifier source, then restores current Health/Mana within their new maximums. This avoids stale saved totals when an item definition changes.

## Why one file per player?

It gives the local game a simple, debuggable format and a clean import/export unit without coupling the game to a database. `PlayerDataSnapshot` is the logical data-transfer object, and `PlayerDataRepository` is the persistence boundary. The current implementation is `JsonFilePlayerDataRepository`.

When multiplayer and a database arrive, add another implementation of `PlayerDataRepository` (for example, SQL). It can normalize inventory slots, equipment, currencies, and character stats into separate tables while keeping the same logical load/save contract. **Do not** make gameplay systems read or write JSON directly, and do not treat a whole JSON save blob as the only possible database schema.

## Reliability

- Saves are written to a same-directory temporary file, then moved into place atomically where supported.
- Before replacing an existing save, the last version is copied to `<playerId>.json.bak`.
- Invalid JSON or unsupported schema fails load clearly and leaves the existing file untouched. It never silently creates a starter inventory over a broken player save.
- Autosave runs every eight seconds when no item is held by the inventory cursor, and again on normal shutdown.
- The client refuses to serialize an inventory while the UI cursor holds an item. Closing the inventory returns that item to storage/equipment first, or keeps the inventory open if no valid destination is available.
- The starter kit is only generated when the player has no save file.

## Versioning rules

`schemaVersion` is separate from the item catalog and from the database schema. When the save shape changes, introduce a version migration in the persistence layer before accepting the new format. Keep item IDs stable across balance changes; use migrations/compensation for intentionally removed IDs rather than silently discarding unknown stacks.

## Built-in consumable effects

`data/items/consumables/health_potion.item` contains `use_effect=HEAL|35` and the mana potion contains `use_effect=RESTORE_MANA|25`. Press `R` to use the selected hotbar item. A consumable is only consumed when an effect actually changes a resource. Effects are data-declared and dispatched by `ItemUseService`; new effect types can be registered in the service layer without changing the inventory storage model.
