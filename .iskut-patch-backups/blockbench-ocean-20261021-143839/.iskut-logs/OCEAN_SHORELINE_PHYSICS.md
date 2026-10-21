# Iskut Ocean World: Shoreline & Physics

This update converts the latest authored hub data into an ocean-side island without regenerating the hub or replacing its buildings.

## Sea and beach profile

- Main island walking surface: `Y ~= 0.0`.
- Grass cap: thin visual layer at `Y=0.02`.
- Shore transition: mixed grass/soil/sand pixel-dither texture; top `Y=0.015`. It is collidable so it acts as the shallow lip at the edge rather than a decorative surface the player can fall through.
- Dry sand: top `Y=-0.10`, solid.
- Wet sand: top `Y=-0.16`, solid.
- Ocean plane: top `Y=-0.18`, non-solid.
- Shallow-water strip: top `Y=-0.1675`, non-solid, kept slightly above the sea surface to avoid depth-buffer z-fighting.
- Broken foam strip: top `Y=-0.1625`, non-solid.

The shoreline uses a stepped voxel beach, not a smooth polygonal terrain mesh. The `shore_transition.png` tile mixes grass-green, warm soil, and sand colors into one seamless dithered texture. Sand, wet sand, shallow water, and the broken foam material continue that transition seaward. This is texture-based blending rather than alpha compositing or a water shader.

## Physics

`PlayerController` no longer assumes a universal solid floor at `Y=0`. Player support comes only from collidable map objects. The existing substepped gravity, swept vertical landing checks, jump buffering, coyote time, wall collision and 1.0-unit auto-step remain active.

The ocean plane and shallow-water/foam overlays are not colliders. Falling below `feetY < -1.65` triggers the same defeat/respawn flow with a `Lost at sea` message. This means walking off the island no longer leaves the player standing on an invisible floor.

## Textures

New or improved shared materials under `data/textures/world/`:

- `dirt.png`: warm soil texture for the island's exposed sides.
- `shore_transition.png`: 32x32 seamless pixel-dither blend.
- `wet_sand.png`: darker, cooler near-water beach sand.
- `shallow_water.png`: turquoise shallow-water texture.
- `foam.png`: broken aqua/seafoam ribbon.
- `water.png`: 128x128 seamless low-contrast ocean ripples.

All are loaded by `WorldTextureLibrary` and share world-space UVs. The ocean surface uses a larger texture tile size to reduce obvious repetition; shallow water and foam use smaller scales for near-shore detail.

## Map files

- `data/maps/main_hub.vmap` places the non-solid ocean first, then the existing island plaza, then the beach-transition bands and the remaining authored hub structures.
- `data/structures/plaza.vstruct` keeps the existing top surface and buildings but exposes warm dirt on the main side slabs with a thin grass cap.
- `data/structures/beach_transition.vstruct` provides the shoreline bands.
- `data/structures/ocean_surface.vstruct` defines the 1024-unit ocean plane.

No procedural world generation is used and the existing spawn remains `(0, 0, 12)`.

## Regression tests

Headless smoke tests include `PlayerPhysicsSmokeTest`, `OceanWorldSmokeTest`, `GameplayFoundationSmokeTest` and the existing inventory/rendering tests. They verify real-platform collision, falling below the sea-respawn plane, beach material bands and solidity, ocean level, textures, spawn support and the absence of active sky-island/cloud placements.

## Final coast/physics verification notes

- Player movement has no hidden universal ground at Y=0. If the player leaves the island and no real collider supports them, gravity continues until the below-ocean threshold triggers the sea-respawn flow.
- The large ocean plane is intentionally built with coarse geometry. It is flat and non-collidable, so subdividing it at the detailed terrain-lighting grid wastes CPU and raster work; the dedicated mesh path keeps it at 576 triangles for the current 1024-unit plane.
- The main island uses exposed dirt on the side slab and the stepped transition/sand/wet-sand/shallow-water/foam bands at the perimeter. The water itself is not a solid floor.
- Automated validation checks the actual map's collision support at spawn, all five shoreline material bands, expected band heights/solid flags, no active legacy sky placements, and that water mesh density stays below 2,000 triangles.
