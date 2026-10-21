# Iskut Ocean World: Shoreline & Physics

The active world uses a continuous, non-collidable ocean beneath the authored island hub. This update preserves the relative geometry of existing structures while moving sea level to the requested `Y=1.0`.

## Sea and beach profile

- Ocean plane: exactly `Y=1.0`, one upward-facing quad/two triangles, 1024 world units wide and deep. It has zero thickness.
- Main island walking cap: approximately `Y=1.20`; the authored terrain includes small height variation.
- Dry sand band: collidable portions peak at about `Y=1.21`; the stepped band varies below that.
- Wet sand: collidable, with upper surfaces reaching about `Y=1.06`; its lower transition steps remain below the sea level.
- Shallow-water overlay: `Y=1.0125`, non-collidable.
- Foam overlay: `Y=1.0175`, non-collidable.
- Player spawn remains supported by real island geometry at approximately `Y=1.20`.

The beach is made from authored cuboids and thin decorative overlays, not a continuous terrain mesh. The current active structure has four material bands: sand, wet sand, shallow water and foam. `shore_transition.png` remains in the shared material library for future use; it is not a fifth active geometry band in the current `.vstruct`.

## Render isolation and collision

`data/structures/ocean_surface.vstruct` defines a top-only `PLANE`, placed at world Y=1.0 by `data/maps/main_hub.vmap`. The ocean object is explicitly non-collidable and marked `staticBatchExcluded`. It is retained as one standalone render object, excluded from the static batcher's world-bounds set, contact AO, sunlight visibility and coplanar-face offsets. This prevents the vast ocean surface from changing neighboring block shading or entering physics collision checks.

The shallow-water and foam overlays sit slightly above sea level and are also non-collidable. The map does not add an invisible global floor. `PlayerController` starts the lost-at-sea respawn flow below `feetY < -0.47`, 1.47 units under sea level.

## World layout and structures

- `data/maps/main_hub.vmap` contains the spawn and the list/order of placed `.vstruct` files. Existing structures and spawn are translated upward by +1.18 to preserve the old relationship between the island and the new Y=1 sea level.
- `data/structures/*.vstruct` contains reusable placeable world structures.
- `ocean_surface.vstruct` uses `PLANE` for a flat visual-only world surface.
- `.vstruct` supports `MODEL id ../blocks/models/model.json x y z scale solid [localYawDegrees]` for Blockbench Java Block/Item cuboids. Each eligible element becomes a collider only when `solid=true`; zero-thickness elements remain visual-only.

## Textures

Shared world materials live under `data/textures/world/`. The ocean surface uses a larger tile-world size than near-shore shallow water and foam. Item and Blockbench structure textures can live beside their models under `data/items/textures/` and `data/blocks/textures/`, respectively.

## Regression tests

The dependency-free headless smoke tests are:

- `OceanWorldSmokeTest`: verifies sea level Y=1.0, a two-triangle zero-thickness ocean surface, its exclusion from static batch/AO/coplanar processing, no ocean collider, beach bands and actual spawn support.
- `BlockbenchModelSmokeTest`: loads the starter JSON models and generated icons, imports rectangular-texture and zero-thickness test geometry, and places a model via `.vmap` → `.vstruct` `MODEL`.
- `PlayerPhysicsSmokeTest` and `GameplayFoundationSmokeTest`: cover movement/collision/gameplay regressions.
