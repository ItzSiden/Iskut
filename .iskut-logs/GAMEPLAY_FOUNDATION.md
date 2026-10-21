# Iskut Gameplay Foundation

This update adds the first reusable RPG/gameplay layer on top of the existing handcrafted-map, skybox, renderer, and `.vmodel` entity pipeline. It keeps the low-cost software renderer path intact and does not enable procedural terrain by default.

## Play controls

- `W A S D`: move
- Mouse: look
- `Space`: jump (jump buffering + coyote time)
- `Shift`: sprint
- `Ctrl`: crouch
- Left mouse on a hostile mob: attack; left mouse on world geometry: select the object
- Right mouse on another player: open the player-profile panel; right mouse elsewhere closes it/clears world selection
- `E`: interact with a registered NPC or a map-defined interactable
- `F3`: toggle debug HUD
- `Esc`: quit

Run `run-hub.bat` from the project root as before.

## Movement and collision

`PlayerController` now combines the existing gravity and buffered/coyote-time jumping with swept horizontal and vertical AABB collision checks. Fast movement/falls are less likely to tunnel through thin walls or platforms at low FPS. Crouch-to-stand checks include both static geometry and live entity hitboxes. Player movement also receives a multiplier from equipped/pet stat modifiers.

Current tuning values:

- Walk: `4.5` world units/second
- Sprint: `7.5` units/second
- Crouch: `2.1` units/second
- Gravity: `20` units/second²
- Jump takeoff velocity: `7.15` units/second (roughly `1.28` units of ideal apex height)
- Jump buffer: `0.14` seconds
- Coyote time: `0.11` seconds
- Fall damage starts beyond `3` units of vertical fall and scales with extra distance

The current hub still has its simple world floor at `Y = 0`. Static-map colliders are built from collidable map objects. Slopes, ladders, moving platforms, step-up assistance, and full rigid-body simulation are not part of this prototype yet.

## Entity hitboxes and interactions

`EntityRaycast` derives a conservative, model-aware AABB from each entity's `.vmodel` parts, including part sizes and rotations. It targets the nearest visible entity in reach and respects collidable world geometry occlusion. The same hitbox source is supplied to the player controller for horizontal entity collision.

`EntityInteractionRegistry` is data-driven: register an entity ID, prompt text, and handler. The guide NPC Elyra is the first registered interaction. Map-defined interactables continue to use the existing map interaction system, so the two paths can coexist.

The RMB profile panel reads from an immutable `PlayerProfileSnapshot` payload. Astra's profile is local demo data; the panel is ready for data received from a future server, but network transport and authoritative profile updates are not implemented yet.

## Combat foundation

`HostileMobSystem` demonstrates a reusable state loop for a hostile mob:

- Aggro radius: `7.5` units
- Chase speed: `2.15` units/second
- Melee range: `1.25` units
- Base hit: `9` damage every `1.35` seconds
- Respawn delay: `9` seconds

The Sky Wisp is the demo hostile mob. It can chase, damage the player, be attacked with left mouse, die, disappear, and respawn. This is deliberately a small first AI behaviour, not a complete pathfinder or combat/loot system.

Both player and entity damage use a diminishing-return defense formula:

`effectiveDamage = rawDamage * 100 / (100 + defense)`

Player death disables input briefly, then restores health/mana and returns the player to the map spawn.

## RPG stats, armor, accessories, and pets

The new `engine-core/gameplay` package provides:

- `PlayerStats`: current/max health and mana, defense, strength, intelligence, mana regeneration, mana spending, healing, damage mitigation, attack-damage calculation, and a temporary-buff modifier hook.
- `EquipmentItem` and `EquipmentSlot`: data-only definitions for weapons, helmet, chestplate, leggings, boots, accessories, and pets.
- `EquipmentLoadout`: slot management and additive stat aggregation across armor, weapon, accessories, and pet.
- `StatModifiers`: shared stat/buff representation. Pets can add passive stats/damage/speed, and future potions/skills can supply additional active modifiers.
- `PlayerProfileSnapshot`: immutable profile data including current stats, online status, level, and equipped item IDs.

The prototype equips a starter loadout so these systems are exercised immediately. The inventory/equipment GUI, item drops, item persistence, skill/spell UI, accessory bag management, and a complete item catalog are future work; the current update supplies the core data APIs rather than pretending those features are already playable.

`Entity` also has its own defense-aware `takeDamage` API, and `EntitySnapshot` carries defense while keeping a compatibility constructor for the older snapshot shape.

## Verification

The project source sets for `render-api`, `engine-core`, `ui-core`, `game-client`, and `renderer-software` compile with Java 21. The dependency-free smoke test lives at:

`tools/dev/itzsiden/skyvoxel/client/GameplayFoundationSmokeTest.java`

It checks starter stat aggregation, defense, mana, entity model raycast, entity collision blocking, snapshot defense replication, jump height/landing, fall damage, raised-platform collision, mob damage/death/respawn, plus loading of the existing handcrafted map and `.vmodel` asset.

The actual desktop window should still be run locally with `run-hub.bat` to validate the complete input/rendering experience on the target machine.
